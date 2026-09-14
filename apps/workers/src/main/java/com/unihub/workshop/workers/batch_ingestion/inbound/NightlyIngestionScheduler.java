package com.unihub.workshop.workers.batch_ingestion.inbound;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.IngestStudentCsv;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.RunFileAdmission;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NightlyIngestionScheduler {

    private final RunFileAdmission runFileAdmission;
    private final IngestionObjectStore objectStore;
    private final IngestStudentCsv ingestStudentCsv;

    @Scheduled(cron = "${app.batch-ingestion.cron:0 0 2 * * ?}")
    public void runNightlyIngestion() {
        runFileAdmission.admitFiles();

        List<SourceFile> validFiles = objectStore.listInboxFiles();
        validFiles
            .stream()
            .sorted(Comparator.comparing(SourceFile::createdAt))
            .forEach(ingestStudentCsv::ingest);
    }
}
