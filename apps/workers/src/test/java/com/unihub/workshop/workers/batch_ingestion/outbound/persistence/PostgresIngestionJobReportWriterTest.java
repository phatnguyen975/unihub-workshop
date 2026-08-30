package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgresIngestionJobReportWriterTest {

    @Test
    void shouldMapAndSaveReport() {
        IngestionJobReportRepository repository = mock(IngestionJobReportRepository.class);
        PostgresIngestionJobReportWriter writer = new PostgresIngestionJobReportWriter(repository);

        Instant now = Instant.now();
        UUID orgId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        
        IngestionJobReport report = new IngestionJobReport(
            reportId,
            -1L,
            orgId,
            "inbox/it/file.csv",
            IngestionJobReport.Outcome.FILE_REJECTED,
            0,
            0,
            "rejected/it/source-files/file.csv",
            null,
            now,
            now,
            now
        );

        writer.save(report);

        ArgumentCaptor<IngestionJobReportEntity> captor = ArgumentCaptor.forClass(IngestionJobReportEntity.class);
        verify(repository).save(captor.capture());

        IngestionJobReportEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(reportId);
        assertThat(entity.getJobExecutionId()).isEqualTo(-1L);
        assertThat(entity.getOrganizationId()).isEqualTo(orgId);
        assertThat(entity.getSourceFileKey()).isEqualTo("inbox/it/file.csv");
        assertThat(entity.getOutcome()).isEqualTo("FILE_REJECTED");
        assertThat(entity.getProcessedRowCount()).isZero();
        assertThat(entity.getRejectedRowCount()).isZero();
        assertThat(entity.getFileRejectionSourceKey()).isEqualTo("rejected/it/source-files/file.csv");
        assertThat(entity.getRejectArtifactKey()).isNull();
        assertThat(entity.getCompletedAt()).isEqualTo(now);
    }
}
