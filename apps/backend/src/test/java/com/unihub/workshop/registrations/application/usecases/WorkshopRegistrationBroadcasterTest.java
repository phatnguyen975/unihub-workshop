package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationRawCounts;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventPublisher;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WorkshopRegistrationBroadcasterTest {

    private RegistrationRepository registrationRepository;
    private WorkshopApi workshopApi;
    private RealtimeEventPublisher realtimeEventPublisher;
    private WorkshopRegistrationBroadcasterImpl broadcaster;

    private final UUID workshopId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-28T14:00:00Z");

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        workshopApi = mock(WorkshopApi.class);
        realtimeEventPublisher = mock(RealtimeEventPublisher.class);

        broadcaster = new WorkshopRegistrationBroadcasterImpl(
            registrationRepository,
            workshopApi,
            realtimeEventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private WorkshopRegistrationDetailsDto createWorkshop(
        UUID id,
        int capacity,
        Instant startsAt
    ) {
        return new WorkshopRegistrationDetailsDto(
            id,
            UUID.randomUUID(),
            "Cloud Computing Workshop",
            "cloud-computing-workshop",
            "PUBLISHED",
            startsAt,
            startsAt.plusSeconds(7200),
            BigDecimal.ZERO,
            capacity,
            "Hall A"
        );
    }

    @Test
    @DisplayName(
        "Scenario 13: Happy path publishes registration.updated with complete 6-field snapshot (FR-15, FR-17)"
    )
    void broadcast_HappyPath_Publishes6FieldSnapshot() {
        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            100,
            now.minusSeconds(3600)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        WorkshopRegistrationRawCounts rawCounts =
            new WorkshopRegistrationRawCounts(72L, 4L, Optional.of(now));
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(rawCounts);

        broadcaster.broadcast(workshopId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<Map<String, Object>>> eventCaptor =
            ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            eventCaptor.capture()
        );

        RealtimeEvent<Map<String, Object>> capturedEvent =
            eventCaptor.getValue();
        assertThat(capturedEvent.eventType()).isEqualTo(
            RealtimeEventType.REGISTRATION_UPDATED.getValue()
        );

        Map<String, Object> payload = capturedEvent.payload();
        assertThat(payload.get("workshopId")).isEqualTo(workshopId);
        assertThat(payload.get("confirmedCount")).isEqualTo(72L);
        assertThat(payload.get("pendingCount")).isEqualTo(4L);
        assertThat(payload.get("capacity")).isEqualTo(100);
        assertThat(payload.get("availableSeats")).isEqualTo(24);
        assertThat(payload.get("lastUpdatedAt")).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "Scenario 14: Available seats floor at 0 when confirmed + pending exceeds capacity (FR-06, BR-08)"
    )
    void broadcast_OverCapacity_FloorsAvailableSeatsAtZero() {
        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            50,
            now.minusSeconds(3600)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        WorkshopRegistrationRawCounts rawCounts =
            new WorkshopRegistrationRawCounts(45L, 10L, Optional.of(now));
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(rawCounts);

        broadcaster.broadcast(workshopId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<Map<String, Object>>> eventCaptor =
            ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            eventCaptor.capture()
        );

        Map<String, Object> payload = eventCaptor.getValue().payload();
        assertThat(payload.get("availableSeats")).isEqualTo(0);
        assertThat(payload.get("confirmedCount")).isEqualTo(45L);
        assertThat(payload.get("pendingCount")).isEqualTo(10L);
    }

    @Test
    @DisplayName("Capacity is zero sets availableSeats to 0 (FR-06, BR-08)")
    void broadcast_CapacityZero_AvailableSeatsIsZero() {
        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            0,
            now.minusSeconds(3600)
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        WorkshopRegistrationRawCounts rawCounts =
            new WorkshopRegistrationRawCounts(0L, 0L, Optional.of(now));
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(rawCounts);

        broadcaster.broadcast(workshopId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<Map<String, Object>>> eventCaptor =
            ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            eventCaptor.capture()
        );

        Map<String, Object> payload = eventCaptor.getValue().payload();
        assertThat(payload.get("capacity")).isEqualTo(0);
        assertThat(payload.get("availableSeats")).isEqualTo(0);
    }

    @Test
    @DisplayName(
        "Fallback timestamp uses workshop.startsAt when rawCounts latestUpdatedAt is empty (FR-08)"
    )
    void broadcast_NoRegistrations_UsesWorkshopStartsAtAsFallbackTimestamp() {
        Instant startsAt = now.minusSeconds(7200);
        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            100,
            startsAt
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        WorkshopRegistrationRawCounts rawCounts =
            new WorkshopRegistrationRawCounts(0L, 0L, Optional.empty());
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(rawCounts);

        broadcaster.broadcast(workshopId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RealtimeEvent<Map<String, Object>>> eventCaptor =
            ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtimeEventPublisher).publishToWorkshop(
            eq(workshopId),
            eventCaptor.capture()
        );

        Map<String, Object> payload = eventCaptor.getValue().payload();
        assertThat(payload.get("lastUpdatedAt")).isEqualTo(startsAt);
    }

    @Test
    @DisplayName(
        "Workshop not found logs warning and does not publish (fail-safe)"
    )
    void broadcast_WorkshopNotFound_DoesNotPublish() {
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );

        assertThatCode(() ->
            broadcaster.broadcast(workshopId)
        ).doesNotThrowAnyException();

        verify(realtimeEventPublisher, never()).publishToWorkshop(any(), any());
    }

    @Test
    @DisplayName(
        "Null workshopId logs warning and does not publish (fail-safe)"
    )
    void broadcast_NullWorkshopId_DoesNotPublish() {
        assertThatCode(() ->
            broadcaster.broadcast(null)
        ).doesNotThrowAnyException();

        verify(realtimeEventPublisher, never()).publishToWorkshop(any(), any());
    }

    @Test
    @DisplayName(
        "Exception during publishToWorkshop is swallowed and logged (does not disrupt caller)"
    )
    void broadcast_PublisherThrows_DoesNotDisruptCaller() {
        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            100,
            now
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(
            new WorkshopRegistrationRawCounts(10L, 2L, Optional.of(now))
        );

        doThrow(new RuntimeException("Redis connection error"))
            .when(realtimeEventPublisher)
            .publishToWorkshop(any(), any());

        assertThatCode(() ->
            broadcaster.broadcast(workshopId)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "Defers execution to afterCommit when Spring transaction synchronization is active"
    )
    void broadcast_TransactionActive_DefersToAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        WorkshopRegistrationDetailsDto workshop = createWorkshop(
            workshopId,
            100,
            now
        );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(
            new WorkshopRegistrationRawCounts(5L, 1L, Optional.of(now))
        );

        broadcaster.broadcast(workshopId);

        // Before commit: nothing published yet
        verify(realtimeEventPublisher, never()).publishToWorkshop(any(), any());

        // Simulate afterCommit
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        // After commit: published!
        verify(realtimeEventPublisher).publishToWorkshop(eq(workshopId), any());
    }
}
