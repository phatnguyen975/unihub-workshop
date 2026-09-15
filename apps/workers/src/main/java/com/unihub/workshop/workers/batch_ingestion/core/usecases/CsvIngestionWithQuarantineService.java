package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.IngestStudentCsv;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionCheckpointStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CsvIngestionWithQuarantineService implements IngestStudentCsv {

    private final IngestionCheckpointStore checkpointStore;
    private final IngestionObjectStore objectStore;
    private final IngestionJobReportWriter reportWriter;
    private final IngestionEventPublisher eventPublisher;
    private final OrganizationDirectory organizationDirectory;
    private final RejectArtifactStore rejectArtifactStore;

    @Override
    public void ingest(SourceFile file) {
        Optional<OrganizationDirectory.OrganizationInfo> orgInfoOpt =
            organizationDirectory.findActiveBySlug(file.organizationSlug());

        if (orgInfoOpt.isEmpty()) {
            return;
        }

        OrganizationDirectory.OrganizationInfo orgInfo = orgInfoOpt.get();
        UUID organizationId = orgInfo.id();

        long jobExecutionId = checkpointStore.startIngestionJob(
            file.key(),
            organizationId,
            file.organizationSlug(),
            orgInfo.name()
        );
        IngestionMetrics metrics = checkpointStore.getMetrics(jobExecutionId);

        String rejectArtifactKey = null;
        if (metrics.rejectCount() > 0) {
            rejectArtifactKey = rejectArtifactStore.uploadAndCleanUp(
                jobExecutionId,
                file.organizationSlug(),
                file.key()
            );
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
            Instant.now()
        );
        reportWriter.save(report);

        eventPublisher.publishFileCompletedEvent(organizationId, file.key());
    }
}
