package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.events.WorkshopCompletedEvent;
import com.unihub.workshop.workshops.application.ports.out.WorkshopLockPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.CompletedWorkshopDetails;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("SweepWorkshopLifecycleUseCaseImpl Unit Tests")
class SweepWorkshopLifecycleUseCaseImplTest {

    private static final String LOCK_KEY = "lock:sweeper:lifecycle";
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    @Mock
    private WorkshopLockPort workshopLockPort;

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SweepWorkshopLifecycleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new SweepWorkshopLifecycleUseCaseImpl(
            workshopLockPort,
            workshopRepository,
            eventPublisher
        );
    }

    @Test
    @DisplayName("Skips execution when lock is already held by another node")
    void sweepLifecycle_lockHeld_skips() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(false);

        useCase.sweepLifecycle();

        verify(workshopLockPort, never()).releaseLock(any());
        verifyNoInteractions(workshopRepository, eventPublisher);
    }

    @Test
    @DisplayName(
        "Releases lock and publishes no events when zero workshops are eligible"
    )
    void sweepLifecycle_zeroEligible_releasesLock() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.completeExpiredWorkshops(any(Instant.class))
        ).thenReturn(List.of());

        useCase.sweepLifecycle();

        verify(workshopLockPort).releaseLock(LOCK_KEY);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Publishes WorkshopCompletedEvent for each completed workshop and releases lock"
    )
    void sweepLifecycle_eligibleWorkshops_publishesEventsAndReleasesLock() {
        UUID id1 = UUID.randomUUID();
        UUID org1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        Instant startsAt = Instant.now().minus(Duration.ofHours(2));
        Instant endsAt = Instant.now().minus(Duration.ofMinutes(10));
        Instant completedAt = Instant.now();

        CompletedWorkshopDetails w1 = new CompletedWorkshopDetails(
            id1,
            org1,
            "Workshop 1",
            "workshop-1",
            startsAt,
            endsAt,
            completedAt
        );
        CompletedWorkshopDetails w2 = new CompletedWorkshopDetails(
            id2,
            org2,
            "Workshop 2",
            "workshop-2",
            startsAt,
            endsAt,
            completedAt
        );

        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.completeExpiredWorkshops(any(Instant.class))
        ).thenReturn(List.of(w1, w2));

        useCase.sweepLifecycle();

        ArgumentCaptor<WorkshopCompletedEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopCompletedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<WorkshopCompletedEvent> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).workshopId()).isEqualTo(id1);
        assertThat(events.get(0).organizationId()).isEqualTo(org1);
        assertThat(events.get(0).title()).isEqualTo("Workshop 1");
        assertThat(events.get(0).slug()).isEqualTo("workshop-1");
        assertThat(events.get(1).workshopId()).isEqualTo(id2);

        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }

    @Test
    @DisplayName("Releases lock when database operation fails with exception")
    void sweepLifecycle_dbFailure_releasesLockAndThrows() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.completeExpiredWorkshops(any(Instant.class))
        ).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> useCase.sweepLifecycle())
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database error");

        verify(workshopLockPort).releaseLock(LOCK_KEY);
        verifyNoInteractions(eventPublisher);
    }
}
