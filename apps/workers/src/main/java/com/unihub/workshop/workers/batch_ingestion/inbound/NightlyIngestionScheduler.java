package com.unihub.workshop.workers.batch_ingestion.inbound;

import com.unihub.workshop.workers.batch_ingestion.core.ports.in.RunFileAdmission;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NightlyIngestionScheduler {

    private final RunFileAdmission runFileAdmission;

    @Scheduled(cron = "${unihub.batch-ingestion.cron:0 0 2 * * ?}")
    public void runNightlyIngestion() {
        runFileAdmission.admitFiles();
        // In T2, this will also trigger the Spring Batch job for valid files
    }
}
