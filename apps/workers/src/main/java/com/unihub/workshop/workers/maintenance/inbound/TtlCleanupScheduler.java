package com.unihub.workshop.workers.maintenance.inbound;

import com.unihub.workshop.workers.maintenance.core.ports.in.RunTtlCleanup;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TtlCleanupScheduler {

    private final RunTtlCleanup runTtlCleanup;

    @Scheduled(cron = "${app.ttl-cleanup.cron:0 0 */6 * * *}")
    public void runTtlCleanup() {
        runTtlCleanup.cleanExpiredTokens();
    }
}
