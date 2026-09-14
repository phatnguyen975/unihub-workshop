package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FileAdmissionServiceTest {

    private IngestionObjectStore objectStore;
    private OrganizationDirectory organizationDirectory;
    private IngestionJobReportWriter reportWriter;
    private IngestionEventPublisher eventPublisher;
    private FileAdmissionService service;

    @BeforeEach
    void setUp() {
        objectStore = mock(IngestionObjectStore.class);
        organizationDirectory = mock(OrganizationDirectory.class);
        reportWriter = mock(IngestionJobReportWriter.class);
        eventPublisher = mock(IngestionEventPublisher.class);
        service = new FileAdmissionService(
            objectStore,
            organizationDirectory,
            reportWriter,
            eventPublisher,
            Set.of("Information Technology", "Chemistry"),
            100 * 1024 * 1024L
        );
    }

    @Test
    void shouldAdmitValidFileAndKeepItInInbox() {
        // Arrange
        var orgId = UUID.randomUUID();
        when(objectStore.listInboxFiles()).thenReturn(
            List.of(
                new SourceFile(
                    "inbox/it/file1.csv",
                    "it",
                    1024L,
                    Instant.parse("2026-08-31T00:00:00Z")
                )
            )
        );
        when(organizationDirectory.findActiveBySlug("it")).thenReturn(
            Optional.of(
                new OrganizationDirectory.OrganizationInfo(
                    orgId,
                    "Information Technology"
                )
            )
        );
        when(objectStore.hasValidHeader("inbox/it/file1.csv")).thenReturn(true);

        // Act
        service.admitFiles();

        // Assert
        verify(objectStore, never()).moveToRejected(any(), any());
        verify(reportWriter, never()).save(any());
        verify(eventPublisher, never()).publishFileRejectedEvent(any(), any());
    }

    @Test
    void shouldRejectFileWhenSizeExceeds100MB() {
        // Arrange
        var orgId = UUID.randomUUID();
        var fileKey = "inbox/it/huge.csv";
        when(objectStore.listInboxFiles()).thenReturn(
            List.of(
                new SourceFile(
                    fileKey,
                    "it",
                    104857601L,
                    Instant.parse("2026-08-31T00:00:00Z")
                ) // > 100MB
            )
        );
        when(organizationDirectory.findActiveBySlug("it")).thenReturn(
            Optional.of(
                new OrganizationDirectory.OrganizationInfo(
                    orgId,
                    "Information Technology"
                )
            )
        );
        when(objectStore.moveToRejected(fileKey, "it")).thenReturn(
            "rejected/it/source-files/huge.csv"
        );

        // Act
        service.admitFiles();

        // Assert
        verify(objectStore).moveToRejected(fileKey, "it");
        verify(eventPublisher).publishFileRejectedEvent(orgId, fileKey);

        var reportCaptor = ArgumentCaptor.forClass(IngestionJobReport.class);
        verify(reportWriter).save(reportCaptor.capture());
        var report = reportCaptor.getValue();
        assertThat(report.outcome()).isEqualTo(
            IngestionJobReport.Outcome.FILE_REJECTED
        );
        assertThat(report.organizationId()).isEqualTo(orgId);
        assertThat(report.sourceFileKey()).isEqualTo(fileKey);
        assertThat(report.fileRejectionSourceKey()).isEqualTo(
            "rejected/it/source-files/huge.csv"
        );
        assertThat(report.rejectArtifactKey()).isNull();
        assertThat(report.processedRowCount()).isZero();
        assertThat(report.rejectedRowCount()).isZero();
    }

    @Test
    void shouldRejectFileWhenHeaderIsInvalid() {
        // Arrange
        var orgId = UUID.randomUUID();
        var fileKey = "inbox/it/bad_header.csv";
        when(objectStore.listInboxFiles()).thenReturn(
            List.of(
                new SourceFile(
                    fileKey,
                    "it",
                    1024L,
                    Instant.parse("2026-08-31T00:00:00Z")
                )
            )
        );
        when(organizationDirectory.findActiveBySlug("it")).thenReturn(
            Optional.of(
                new OrganizationDirectory.OrganizationInfo(
                    orgId,
                    "Information Technology"
                )
            )
        );
        when(objectStore.hasValidHeader(fileKey)).thenReturn(false);
        when(objectStore.moveToRejected(fileKey, "it")).thenReturn(
            "rejected/it/source-files/bad_header.csv"
        );

        // Act
        service.admitFiles();

        // Assert
        verify(objectStore).moveToRejected(fileKey, "it");
        verify(eventPublisher).publishFileRejectedEvent(orgId, fileKey);

        var reportCaptor = ArgumentCaptor.forClass(IngestionJobReport.class);
        verify(reportWriter).save(reportCaptor.capture());
        var report = reportCaptor.getValue();
        assertThat(report.outcome()).isEqualTo(
            IngestionJobReport.Outcome.FILE_REJECTED
        );
        assertThat(report.organizationId()).isEqualTo(orgId);
    }

    @Test
    void shouldRejectFileWhenOrganizationSlugIsUnknown() {
        // Arrange
        var fileKey = "inbox/unknown/file.csv";
        when(objectStore.listInboxFiles()).thenReturn(
            List.of(
                new SourceFile(
                    fileKey,
                    "unknown",
                    1024L,
                    Instant.parse("2026-08-31T00:00:00Z")
                )
            )
        );
        when(organizationDirectory.findActiveBySlug("unknown")).thenReturn(
            Optional.empty()
        );
        when(objectStore.moveToRejected(fileKey, "unknown")).thenReturn(
            "rejected/unknown/source-files/file.csv"
        );

        // Act
        service.admitFiles();

        // Assert
        verify(objectStore).moveToRejected(fileKey, "unknown");
        verify(reportWriter, never()).save(any()); // No orgId to save report for
        verify(eventPublisher, never()).publishFileRejectedEvent(any(), any());
    }

    @Test
    void shouldRejectFileWhenOrganizationFacultyIsNotAllowed() {
        // Arrange
        var orgId = UUID.randomUUID();
        var fileKey = "inbox/unallowed/file.csv";
        when(objectStore.listInboxFiles()).thenReturn(
            List.of(
                new SourceFile(
                    fileKey,
                    "unallowed",
                    1024L,
                    Instant.parse("2026-08-31T00:00:00Z")
                )
            )
        );
        when(organizationDirectory.findActiveBySlug("unallowed")).thenReturn(
            Optional.of(
                new OrganizationDirectory.OrganizationInfo(
                    orgId,
                    "Not In Allow List"
                )
            )
        );
        when(objectStore.moveToRejected(fileKey, "unallowed")).thenReturn(
            "rejected/unallowed/source-files/file.csv"
        );

        // Act
        service.admitFiles();

        // Assert
        verify(objectStore).moveToRejected(fileKey, "unallowed");
        verify(eventPublisher).publishFileRejectedEvent(orgId, fileKey);
    }

    @Test
    void shouldProcessFilesInCreationTimeOrder() {
        // Arrange
        var orgId = UUID.randomUUID();
        when(organizationDirectory.findActiveBySlug("it")).thenReturn(
            Optional.of(
                new OrganizationDirectory.OrganizationInfo(
                    orgId,
                    "Information Technology"
                )
            )
        );

        var file2 = new SourceFile(
            "inbox/it/file2.csv",
            "it",
            105000000L,
            Instant.parse("2026-08-31T02:00:00Z")
        ); // >100MB
        var file1 = new SourceFile(
            "inbox/it/file1.csv",
            "it",
            105000000L,
            Instant.parse("2026-08-31T01:00:00Z")
        ); // >100MB

        when(objectStore.listInboxFiles()).thenReturn(List.of(file2, file1)); // Returned out of order

        // Act
        service.admitFiles();

        // Assert
        var inOrder = inOrder(objectStore);
        inOrder.verify(objectStore).moveToRejected(file1.key(), "it");
        inOrder.verify(objectStore).moveToRejected(file2.key(), "it");
    }
}
