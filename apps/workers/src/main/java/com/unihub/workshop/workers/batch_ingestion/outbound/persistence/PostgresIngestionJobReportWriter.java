package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresIngestionJobReportWriter
    implements IngestionJobReportWriter
{

    private final IngestionJobReportRepository repository;

    @Override
    public void save(IngestionJobReport report) {
        IngestionJobReportEntity entity = new IngestionJobReportEntity();
        entity.setId(report.id());
        entity.setJobExecutionId(report.jobExecutionId());
        entity.setOrganizationId(report.organizationId());
        entity.setSourceFileKey(report.sourceFileKey());
        entity.setOutcome(report.outcome().name());
        entity.setProcessedRowCount(report.processedRowCount());
        entity.setRejectedRowCount(report.rejectedRowCount());
        entity.setFileRejectionSourceKey(report.fileRejectionSourceKey());
        entity.setRejectArtifactKey(report.rejectArtifactKey());
        entity.setCompletedAt(report.completedAt());
        entity.setCreatedAt(report.createdAt());
        entity.setUpdatedAt(report.updatedAt());

        repository.save(entity);
    }
}
