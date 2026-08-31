package com.unihub.workshop.workers.batch_ingestion.core.usecases;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionJobReport;
import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.in.RunFileAdmission;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionJobReportWriter;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FileAdmissionService implements RunFileAdmission {

    private final IngestionObjectStore objectStore;
    private final OrganizationDirectory organizationDirectory;
    private final IngestionJobReportWriter reportWriter;
    private final IngestionEventPublisher eventPublisher;
    private final Set<String> expectedFaculties;
    private final long maxFileSizeBytes;

    public FileAdmissionService(
            IngestionObjectStore objectStore,
            OrganizationDirectory organizationDirectory,
            IngestionJobReportWriter reportWriter,
            IngestionEventPublisher eventPublisher,
            Set<String> expectedFaculties,
            long maxFileSizeBytes) {
        this.objectStore = objectStore;
        this.organizationDirectory = organizationDirectory;
        this.reportWriter = reportWriter;
        this.eventPublisher = eventPublisher;
        this.expectedFaculties = expectedFaculties;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public void admitFiles() {
        List<SourceFile> inboxFiles = objectStore.listInboxFiles();

        inboxFiles.stream()
                .sorted(Comparator.comparing(SourceFile::createdAt))
                .forEach(this::processFile);
    }

    private void processFile(SourceFile file) {
        Optional<OrganizationDirectory.OrganizationInfo> orgInfoOpt = organizationDirectory
                .findBySlug(file.organizationSlug());

        if (orgInfoOpt.isEmpty()) {
            objectStore.moveToRejected(file.key(), file.organizationSlug());
            return;
        }

        OrganizationDirectory.OrganizationInfo orgInfo = orgInfoOpt.get();

        if (!expectedFaculties.contains(orgInfo.name())) {
            rejectFile(file, orgInfo.id());
            return;
        }

        if (file.sizeBytes() > maxFileSizeBytes) {
            rejectFile(file, orgInfo.id());
            return;
        }

        if (!objectStore.hasValidHeader(file.key())) {
            rejectFile(file, orgInfo.id());
            return;
        }
    }

    private void rejectFile(SourceFile file, UUID organizationId) {
        String rejectedKey = objectStore.moveToRejected(file.key(), file.organizationSlug());

        IngestionJobReport report = new IngestionJobReport(
                UUID.randomUUID(),
                null,
                organizationId,
                file.key(),
                IngestionJobReport.Outcome.FILE_REJECTED,
                0,
                0,
                rejectedKey,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now());
        reportWriter.save(report);

        eventPublisher.publishFileRejectedEvent(organizationId, file.key());
    }
}
