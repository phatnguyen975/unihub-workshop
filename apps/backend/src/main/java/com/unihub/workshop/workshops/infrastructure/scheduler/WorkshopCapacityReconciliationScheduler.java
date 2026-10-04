package com.unihub.workshop.workshops.infrastructure.scheduler;

import com.unihub.workshop.workshops.application.ports.in.ReconcileWorkshopCapacityUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopCapacityReconciliationScheduler {

    private final ReconcileWorkshopCapacityUseCase reconcileWorkshopCapacityUseCase;

    @Scheduled(
        cron = "${app.workshops.capacity-reconciliation.cron:0 */5 * * * *}"
    )
    public void run() {
        log.info("Workshop capacity reconciliation triggered by scheduler");
        try {
            reconcileWorkshopCapacityUseCase.reconcileCapacity();
        } catch (Exception e) {
            log.error(
                "Workshop capacity reconciliation execution encountered an error: {}",
                e.getMessage(),
                e
            );
        }
    }
}
