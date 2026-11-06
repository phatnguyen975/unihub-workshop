package com.unihub.workshop.attendances.application.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AttendanceSyncEventPublisherTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final Integer CAPACITY = 100;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

    @Captor
    private ArgumentCaptor<RealtimeEvent<?>> eventCaptor;

    private AttendanceSyncEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AttendanceSyncEventPublisher(
            attendanceRepository,
            realtimeEventPublisher
        );
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName(
        "an active transaction defers the event and publishes nothing while it is still open"
    )
    void publishAfterCommit_activeTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        verifyNoInteractions(attendanceRepository, realtimeEventPublisher);

        TransactionSynchronizationManager.getSynchronizations().forEach(
            TransactionSynchronization::afterCommit
        );

        verify(realtimeEventPublisher).publishToWorkshop(
            eq(WORKSHOP_ID),
            any()
        );
    }

    @Test
    @DisplayName("a rolled-back transaction publishes no event")
    void afterCompletion_rolledBack_publishesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        TransactionSynchronizationManager.getSynchronizations().forEach(
            synchronization ->
                synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK
                )
        );

        verifyNoInteractions(attendanceRepository, realtimeEventPublisher);
    }

    @Test
    @DisplayName(
        "without an active transaction the event is published immediately"
    )
    void publishAfterCommit_noActiveTransaction_publishesImmediately() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(42L);

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        verify(realtimeEventPublisher).publishToWorkshop(
            eq(WORKSHOP_ID),
            any()
        );
    }

    @Test
    @DisplayName(
        "the payload carries the workshop id, the post-write VALID count, the capacity and a server timestamp"
    )
    void publishAfterCommit_payloadCarriesCounterSnapshot() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(42L);

        Instant before = Instant.now();
        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);
        Instant after = Instant.now();

        Map<?, ?> payload = capturePayload();

        assertThat(payload.keySet()).isEqualTo(
            Set.of("workshopId", "checkedInCount", "capacity", "lastSyncedAt")
        );
        assertThat(payload.get("workshopId")).isEqualTo(WORKSHOP_ID);
        assertThat(payload.get("checkedInCount")).isEqualTo(42L);
        assertThat(payload.get("capacity")).isEqualTo(CAPACITY);
        assertThat(payload.get("lastSyncedAt")).isInstanceOf(Instant.class);
        assertThat((Instant) payload.get("lastSyncedAt")).isBetween(
            before,
            after
        );
    }

    @Test
    @DisplayName("the event is published as attendance.updated")
    void publishAfterCommit_usesAttendanceUpdatedEventType() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(1L);

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        verify(realtimeEventPublisher).publishToWorkshop(
            eq(WORKSHOP_ID),
            eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(
            RealtimeEventType.ATTENDANCE_UPDATED.getValue()
        );
    }

    @Test
    @DisplayName(
        "the counter snapshot is read for the workshop of the committed sync"
    )
    void publishAfterCommit_countsValidRowsOfTheWorkshop() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(3L);

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        verify(attendanceRepository).countValidByWorkshopId(WORKSHOP_ID);
    }

    @Test
    @DisplayName("an unknown workshop capacity is reported as zero")
    void publishAfterCommit_unknownCapacity_reportsZero() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(1L);

        publisher.publishAfterCommit(WORKSHOP_ID, null);

        assertThat(capturePayload().get("capacity")).isEqualTo(0);
    }

    @Test
    @DisplayName(
        "a counter read failure never breaks the already-committed sync"
    )
    void publishAfterCommit_counterReadFails_swallowsFailure() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenThrow(new IllegalStateException("primary unavailable"));

        publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY);

        verifyNoInteractions(realtimeEventPublisher);
    }

    @Test
    @DisplayName("a publisher failure never breaks the already-committed sync")
    void publishAfterCommit_publisherFails_swallowsFailure() {
        when(
            attendanceRepository.countValidByWorkshopId(WORKSHOP_ID)
        ).thenReturn(1L);
        doThrow(new IllegalStateException("redis unavailable"))
            .when(realtimeEventPublisher)
            .publishToWorkshop(eq(WORKSHOP_ID), any());

        assertThatCode(() ->
            publisher.publishAfterCommit(WORKSHOP_ID, CAPACITY)
        ).doesNotThrowAnyException();
    }

    private Map<?, ?> capturePayload() {
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(WORKSHOP_ID),
            eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().payload()).isInstanceOf(Map.class);
        return (Map<?, ?>) eventCaptor.getValue().payload();
    }
}
