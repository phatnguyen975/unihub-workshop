package com.unihub.workshop.workers.batch_ingestion.inbound;

import static org.mockito.Mockito.*;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.IngestStudentCsv;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.RunFileAdmission;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NightlyIngestionSchedulerTest {

    @Test
    void shouldTriggerFileAdmissionAndIngestion() {
        RunFileAdmission runFileAdmission = mock(RunFileAdmission.class);
        IngestionObjectStore objectStore = mock(IngestionObjectStore.class);
        IngestStudentCsv ingestStudentCsv = mock(IngestStudentCsv.class);

        SourceFile validFile = new SourceFile(
            "inbox/it/f1.csv",
            "it",
            1024L,
            Instant.now()
        );
        when(objectStore.listInboxFiles()).thenReturn(List.of(validFile));

        NightlyIngestionScheduler scheduler = new NightlyIngestionScheduler(
            runFileAdmission,
            objectStore,
            ingestStudentCsv
        );

        scheduler.runNightlyIngestion();

        verify(runFileAdmission).admitFiles();
        verify(objectStore).listInboxFiles();
        verify(ingestStudentCsv).ingest(validFile);
    }
}
