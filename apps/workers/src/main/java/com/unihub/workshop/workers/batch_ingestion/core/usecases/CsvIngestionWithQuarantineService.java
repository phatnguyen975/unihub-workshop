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
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Service
public class CsvIngestionWithQuarantineService implements IngestStudentCsv {

    private final IngestionCheckpointStore checkpointStore;
    private final IngestionObjectStore objectStore;
    private final IngestionJobReportWriter reportWriter;
    private final IngestionEventPublisher eventPublisher;
    private final OrganizationDirectory organizationDirectory;
    private final RejectArtifactStore rejectArtifactStore;

    public CsvIngestionWithQuarantineService(
            IngestionCheckpointStore checkpointStore,
            IngestionObjectStore objectStore,
            IngestionJobReportWriter reportWriter,
            IngestionEventPublisher eventPublisher,
            OrganizationDirectory organizationDirectory,
            RejectArtifactStore rejectArtifactStore) {
        this.checkpointStore = checkpointStore;
        this.objectStore = objectStore;
        this.reportWriter = reportWriter;
        this.eventPublisher = eventPublisher;
        this.organizationDirectory = organizationDirectory;
        this.rejectArtifactStore = rejectArtifactStore;
    }

    @Override
    public void ingest(SourceFile file) {
        Optional<OrganizationDirectory.OrganizationInfo> orgInfoOpt = organizationDirectory
                .findBySlug(file.organizationSlug());

        if (orgInfoOpt.isEmpty()) {
            return;
        }

        UUID organizationId = orgInfoOpt.get().id();
        long jobExecutionId = checkpointStore.startIngestionJob(file.key(), organizationId, file.organizationSlug());
        IngestionMetrics metrics = checkpointStore.getMetrics(jobExecutionId);

        String rejectArtifactKey = null;
        if (metrics.rejectCount() > 0) {
            rejectArtifactStore.uploadAndCleanUp(jobExecutionId, file.organizationSlug());
            rejectArtifactKey = String.format("rejected/%s/row-reports/%d.csv", file.organizationSlug(),
                    jobExecutionId);
        }

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
                rejectArtifactKey,
                Instant.now(),
                Instant.now(),
                Instant.now());
        reportWriter.save(report);

        eventPublisher.publishFileCompletedEvent(organizationId, file.key());
    }
}
