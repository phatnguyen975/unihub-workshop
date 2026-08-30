package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionCheckpointStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CsvIngestionWithQuarantineServiceTest {

    private IngestionCheckpointStore checkpointStore;
    private IngestionObjectStore objectStore;
    private IngestionJobReportWriter reportWriter;
    private IngestionEventPublisher eventPublisher;
    private OrganizationDirectory organizationDirectory;
    private RejectArtifactStore rejectArtifactStore;

    private CsvIngestionWithQuarantineService service;

    @BeforeEach
    void setUp() {
        checkpointStore = mock(IngestionCheckpointStore.class);
        objectStore = mock(IngestionObjectStore.class);
        reportWriter = mock(IngestionJobReportWriter.class);
        eventPublisher = mock(IngestionEventPublisher.class);
        organizationDirectory = mock(OrganizationDirectory.class);
        rejectArtifactStore = mock(RejectArtifactStore.class);

        service = new CsvIngestionWithQuarantineService(checkpointStore, objectStore, reportWriter, eventPublisher,
                organizationDirectory, rejectArtifactStore);
    }

    @Test
    void shouldIngestAndReportWithoutRejects() {
        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/students.csv";
        SourceFile file = new SourceFile(fileKey, "it", 1000L, Instant.now());

        when(organizationDirectory.findBySlug("it"))
                .thenReturn(Optional.of(new OrganizationDirectory.OrganizationInfo(orgId, "it")));
        when(checkpointStore.startIngestionJob(fileKey, orgId, "it")).thenReturn(123L);
        when(checkpointStore.getMetrics(123L)).thenReturn(new IngestionMetrics(50, 0));

        service.ingest(file);

        verify(checkpointStore).startIngestionJob(fileKey, orgId, "it");
        verify(checkpointStore).getMetrics(123L);
        verify(rejectArtifactStore, never()).uploadAndCleanUp(anyLong(), anyString());
        verify(objectStore).moveToArchive(fileKey, "it");
        verify(eventPublisher).publishFileCompletedEvent(orgId, fileKey);

        ArgumentCaptor<IngestionJobReport> reportCaptor = ArgumentCaptor.forClass(IngestionJobReport.class);
        verify(reportWriter).save(reportCaptor.capture());

        IngestionJobReport saved = reportCaptor.getValue();
        assertThat(saved.rejectArtifactKey()).isNull();
    }

    @Test
    void shouldIngestAndReportWithRejects() {
        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/students.csv";
        SourceFile file = new SourceFile(fileKey, "it", 1000L, Instant.now());

        when(organizationDirectory.findBySlug("it"))
                .thenReturn(Optional.of(new OrganizationDirectory.OrganizationInfo(orgId, "it")));
        when(checkpointStore.startIngestionJob(fileKey, orgId, "it")).thenReturn(123L);
        when(checkpointStore.getMetrics(123L)).thenReturn(new IngestionMetrics(40, 10));

        service.ingest(file);

        verify(rejectArtifactStore).uploadAndCleanUp(123L, "it");
        verify(objectStore).moveToArchive(fileKey, "it");
        verify(eventPublisher).publishFileCompletedEvent(orgId, fileKey);

        ArgumentCaptor<IngestionJobReport> reportCaptor = ArgumentCaptor.forClass(IngestionJobReport.class);
        verify(reportWriter).save(reportCaptor.capture());

        IngestionJobReport saved = reportCaptor.getValue();
        assertThat(saved.rejectArtifactKey()).isEqualTo("rejected/it/row-reports/123.csv");
    }
}
