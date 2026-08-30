package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionCheckpointStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CleanCsvIngestionServiceTest {

    private IngestionCheckpointStore checkpointStore;
    private IngestionObjectStore objectStore;
    private IngestionJobReportWriter reportWriter;
    private IngestionEventPublisher eventPublisher;
    private OrganizationDirectory organizationDirectory;

    private CleanCsvIngestionService service;

    @BeforeEach
    void setUp() {
        checkpointStore = mock(IngestionCheckpointStore.class);
        objectStore = mock(IngestionObjectStore.class);
        reportWriter = mock(IngestionJobReportWriter.class);
        eventPublisher = mock(IngestionEventPublisher.class);
        organizationDirectory = mock(OrganizationDirectory.class);

        service = new CleanCsvIngestionService(checkpointStore, objectStore, reportWriter, eventPublisher,
                organizationDirectory);
    }

    @Test
    void shouldIngestAndReport() {
        // Arrange
        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/students.csv";
        SourceFile file = new SourceFile(fileKey, "it", 1000L, Instant.now());

        when(organizationDirectory.findBySlug("it"))
                .thenReturn(Optional.of(new OrganizationDirectory.OrganizationInfo(orgId, "it")));
        when(checkpointStore.startIngestionJob(fileKey, orgId, "it")).thenReturn(123L);
        when(checkpointStore.getMetrics(123L)).thenReturn(new IngestionMetrics(50, 0));

        // Act
        service.ingest(file);

        // Assert
        verify(checkpointStore).startIngestionJob(fileKey, orgId, "it");
        verify(checkpointStore).getMetrics(123L);
        verify(objectStore).moveToArchive(fileKey, "it");
        verify(eventPublisher).publishFileCompletedEvent(orgId, fileKey);

        ArgumentCaptor<IngestionJobReport> reportCaptor = ArgumentCaptor.forClass(IngestionJobReport.class);
        verify(reportWriter).save(reportCaptor.capture());

        IngestionJobReport saved = reportCaptor.getValue();
        assertThat(saved.jobExecutionId()).isEqualTo(123L);
        assertThat(saved.organizationId()).isEqualTo(orgId);
        assertThat(saved.sourceFileKey()).isEqualTo(fileKey);
        assertThat(saved.outcome()).isEqualTo(IngestionJobReport.Outcome.COMPLETED);
        assertThat(saved.processedRowCount()).isEqualTo(50);
        assertThat(saved.rejectedRowCount()).isZero();
    }
}
