package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.workshops.api.WorkshopRegistrationCountPort;
import com.unihub.workshop.workshops.application.ports.in.ReconcileWorkshopCapacityUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopLockPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.ActiveWorkshopCapacity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileWorkshopCapacityUseCaseImpl
    implements ReconcileWorkshopCapacityUseCase
{

    private static final String LOCK_KEY =
        "lock:sweeper:capacity-reconciliation";
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    private final WorkshopLockPort workshopLockPort;
    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationCountPort registrationCountPort;
    private final WorkshopAvailabilityPort availabilityPort;

    @Override
    public void reconcileCapacity() {
        boolean acquired = workshopLockPort.tryLock(LOCK_KEY, LOCK_TTL);
        if (!acquired) {
            log.debug(
                "Capacity reconciliation skipped: lock '{}' is currently held by another node",
                LOCK_KEY
            );
            return;
        }

        try {
            Instant now = Instant.now();
            List<ActiveWorkshopCapacity> activeWorkshops =
                workshopRepository.findActivePublishedWorkshops(now);

            if (activeWorkshops.isEmpty()) {
                log.info(
                    "Capacity reconciliation completed: 0 active workshops evaluated, 0 drift detected at {}",
                    now
                );
                return;
            }

            List<UUID> workshopIds = activeWorkshops
                .stream()
                .map(ActiveWorkshopCapacity::id)
                .toList();

            Map<UUID, Long> activeCounts =
                registrationCountPort.countActiveRegistrationsBatch(
                    workshopIds
                );

            int driftCount = 0;
            for (ActiveWorkshopCapacity workshop : activeWorkshops) {
                UUID id = workshop.id();
                long activeCount = activeCounts.getOrDefault(id, 0L);
                int groundTruth = Math.max(
                    0,
                    workshop.capacityValue() - (int) activeCount
                );

                Integer currentVal = availabilityPort.getCapacity(id);
                if (currentVal == null || currentVal != groundTruth) {
                    try {
                        availabilityPort.setCapacity(id, groundTruth);
                        driftCount++;
                        log.warn(
                            "[CAPACITY_DRIFT_DETECTED] Workshop {}: stale value '{}', corrected to ground truth {}",
                            id,
                            currentVal != null ? currentVal : "absent",
                            groundTruth
                        );
                    } catch (Exception e) {
                        log.warn(
                            "Failed to update drifted capacity for workshop {}: {}",
                            id,
                            e.getMessage()
                        );
                    }
                } else {
                    log.debug(
                        "Workshop {} capacity consistent with ground truth {}",
                        id,
                        groundTruth
                    );
                }
            }

            log.info(
                "Capacity reconciliation completed: {} active workshops evaluated, {} drift corrected at {}",
                activeWorkshops.size(),
                driftCount,
                now
            );
        } catch (Exception e) {
            log.error(
                "Capacity reconciliation encountered an unexpected error: {}",
                e.getMessage(),
                e
            );
            throw e;
        } finally {
            workshopLockPort.releaseLock(LOCK_KEY);
        }
    }
}
