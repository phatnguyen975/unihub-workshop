package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopLockPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.ActiveWorkshopCapacity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconcileWorkshopCapacityUseCaseImpl Unit Tests")
class ReconcileWorkshopCapacityUseCaseImplTest {

    private static final String LOCK_KEY =
        "lock:sweeper:capacity-reconciliation";
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    @Mock
    private WorkshopLockPort workshopLockPort;

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopRegistrationCountPort registrationCountPort;

    @Mock
    private WorkshopAvailabilityPort availabilityPort;

    private ReconcileWorkshopCapacityUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReconcileWorkshopCapacityUseCaseImpl(
            workshopLockPort,
            workshopRepository,
            registrationCountPort,
            availabilityPort
        );
    }

    @Test
    @DisplayName("Skips execution when lock is already held by another node")
    void reconcileCapacity_lockHeld_skips() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(false);

        useCase.reconcileCapacity();

        verify(workshopLockPort, never()).releaseLock(any());
        verifyNoInteractions(
            workshopRepository,
            registrationCountPort,
            availabilityPort
        );
    }

    @Test
    @DisplayName(
        "Releases lock and exits when zero active published workshops exist"
    )
    void reconcileCapacity_zeroActiveWorkshops_releasesLock() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenReturn(List.of());

        useCase.reconcileCapacity();

        verify(workshopLockPort).releaseLock(LOCK_KEY);
        verifyNoInteractions(registrationCountPort, availabilityPort);
    }

    @Test
    @DisplayName("Detects drift and overwrites Redis counter with ground truth")
    void reconcileCapacity_driftDetected_correctsCache() {
        UUID workshopId = UUID.randomUUID();
        ActiveWorkshopCapacity workshop = new ActiveWorkshopCapacity(
            workshopId,
            100
        );

        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenReturn(List.of(workshop));
        when(
            registrationCountPort.countActiveRegistrationsBatch(
                List.of(workshopId)
            )
        ).thenReturn(Map.of(workshopId, 60L));
        // Ground truth: max(0, 100 - 60) = 40. Redis has stale 30.
        when(availabilityPort.getCapacity(workshopId)).thenReturn(30);

        useCase.reconcileCapacity();

        verify(availabilityPort).setCapacity(workshopId, 40);
        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }

    @Test
    @DisplayName("Corrects missing Redis counter by setting ground truth")
    void reconcileCapacity_missingCacheKey_setsGroundTruth() {
        UUID workshopId = UUID.randomUUID();
        ActiveWorkshopCapacity workshop = new ActiveWorkshopCapacity(
            workshopId,
            50
        );

        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenReturn(List.of(workshop));
        when(
            registrationCountPort.countActiveRegistrationsBatch(
                List.of(workshopId)
            )
        ).thenReturn(Map.of(workshopId, 20L));
        // Redis key absent: returns null
        when(availabilityPort.getCapacity(workshopId)).thenReturn(null);

        useCase.reconcileCapacity();

        verify(availabilityPort).setCapacity(workshopId, 30);
        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }

    @Test
    @DisplayName(
        "Takes no corrective write when Redis counter matches ground truth"
    )
    void reconcileCapacity_noDrift_doesNotWrite() {
        UUID workshopId = UUID.randomUUID();
        ActiveWorkshopCapacity workshop = new ActiveWorkshopCapacity(
            workshopId,
            80
        );

        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenReturn(List.of(workshop));
        when(
            registrationCountPort.countActiveRegistrationsBatch(
                List.of(workshopId)
            )
        ).thenReturn(Map.of(workshopId, 30L));
        // Ground truth: max(0, 80 - 30) = 50. Redis has 50.
        when(availabilityPort.getCapacity(workshopId)).thenReturn(50);

        useCase.reconcileCapacity();

        verify(availabilityPort, never()).setCapacity(any(), anyInt());
        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }

    @Test
    @DisplayName(
        "Fetches registration counts in a single batch call for multiple workshops"
    )
    void reconcileCapacity_multipleWorkshops_singleBatchCall() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ActiveWorkshopCapacity w1 = new ActiveWorkshopCapacity(id1, 100);
        ActiveWorkshopCapacity w2 = new ActiveWorkshopCapacity(id2, 50);

        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenReturn(List.of(w1, w2));
        when(
            registrationCountPort.countActiveRegistrationsBatch(
                List.of(id1, id2)
            )
        ).thenReturn(Map.of(id1, 10L, id2, 5L));
        when(availabilityPort.getCapacity(id1)).thenReturn(90);
        when(availabilityPort.getCapacity(id2)).thenReturn(45);

        useCase.reconcileCapacity();

        verify(registrationCountPort, times(1)).countActiveRegistrationsBatch(
            List.of(id1, id2)
        );
        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }

    @Test
    @DisplayName("Releases lock when unexpected exception occurs")
    void reconcileCapacity_unexpectedException_releasesLockAndThrows() {
        when(workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL)).thenReturn(true);
        when(
            workshopRepository.findActivePublishedWorkshops(any(Instant.class))
        ).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> useCase.reconcileCapacity())
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database error");

        verify(workshopLockPort).releaseLock(LOCK_KEY);
    }
}
