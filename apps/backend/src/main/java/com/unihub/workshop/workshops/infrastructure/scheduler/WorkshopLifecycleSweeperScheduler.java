package com.unihub.workshop.workshops.infrastructure.scheduler;

import com.unihub.workshop.workshops.application.ports.in.SweepWorkshopLifecycleUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopLifecycleSweeperScheduler {

    private final SweepWorkshopLifecycleUseCase sweepWorkshopLifecycleUseCase;

    @Scheduled(cron = "${app.workshops.sweeper.cron:0 */5 * * * *}")
    public void run() {
        log.info("Workshop lifecycle sweeper triggered by scheduler");
        try {
            sweepWorkshopLifecycleUseCase.sweepLifecycle();
        } catch (Exception e) {
            log.error(
                "Workshop lifecycle sweeper execution encountered an error: {}",
                e.getMessage(),
                e
            );
        }
    }
}
