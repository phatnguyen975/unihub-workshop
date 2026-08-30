package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.IngestStudentCsv;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionCheckpointStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

public class CleanCsvIngestionService implements IngestStudentCsv {

    private final IngestionCheckpointStore checkpointStore;
    private final IngestionObjectStore objectStore;
    private final IngestionJobReportWriter reportWriter;
    private final IngestionEventPublisher eventPublisher;
    private final OrganizationDirectory organizationDirectory;

    public CleanCsvIngestionService(
            IngestionCheckpointStore checkpointStore,
            IngestionObjectStore objectStore,
            IngestionJobReportWriter reportWriter,
            IngestionEventPublisher eventPublisher,
            OrganizationDirectory organizationDirectory) {
        this.checkpointStore = checkpointStore;
        this.objectStore = objectStore;
        this.reportWriter = reportWriter;
        this.eventPublisher = eventPublisher;
        this.organizationDirectory = organizationDirectory;
    }

    @Override
    public void ingest(SourceFile file) {
        Optional<OrganizationDirectory.OrganizationInfo> orgInfoOpt = organizationDirectory
                .findBySlug(file.organizationSlug());

        if (orgInfoOpt.isEmpty()) {
            return; // invalid slug, should have been caught in admission
        }

        UUID organizationId = orgInfoOpt.get().id();
        long jobExecutionId = checkpointStore.startIngestionJob(file.key(), organizationId, file.organizationSlug());
        IngestionMetrics metrics = checkpointStore.getMetrics(jobExecutionId);
        objectStore.moveToArchive(file.key(), file.organizationSlug());

        IngestionJobReport report = new IngestionJobReport(
                UUID.randomUUID(),
                jobExecutionId,
                organizationId,
                file.key(),
                IngestionJobReport.Outcome.COMPLETED,
                metrics.processedRowCount(),
                metrics.rejectCount(),
                null,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now());
        reportWriter.save(report);

        eventPublisher.publishFileCompletedEvent(organizationId, file.key());
    }
}
