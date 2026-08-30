package com.unihub.workshop.workers.batch_ingestion.inbound;

import com.unihub.workshop.workers.batch_ingestion.core.ports.in.RunFileAdmission;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NightlyIngestionSchedulerTest {

    @Test
    void shouldTriggerFileAdmission() {
        RunFileAdmission runFileAdmission = mock(RunFileAdmission.class);
        NightlyIngestionScheduler scheduler = new NightlyIngestionScheduler(runFileAdmission);

        scheduler.runNightlyIngestion();

        verify(runFileAdmission).admitFiles();
    }
}
