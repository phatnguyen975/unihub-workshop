package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.workshops.application.ports.out.ArtifactPresignPort;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.RejectArtifactUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class GetRejectArtifactUrlUseCase {

    private final IngestionJobReportRepository repository;
    private final ArtifactPresignPort presignPort;
    private final long presignTtlMinutes;

    public GetRejectArtifactUrlUseCase(
            IngestionJobReportRepository repository,
            ArtifactPresignPort presignPort,
            @Value("${app.security.presign-ttl-minutes:15}") long presignTtlMinutes) {
        this.repository = repository;
        this.presignPort = presignPort;
        this.presignTtlMinutes = presignTtlMinutes;
    }

    public RejectArtifactUrlResponse execute(UUID jobId, UUID organizationId) {
        // Must use primary data source to prevent replication lag
        IngestionJobReport report = repository.findByIdAndOrganizationIdFromPrimary(jobId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("IngestionJobReport not found"));

        String artifactType;
        String objectKey;

        if (report.outcome() == IngestionJobOutcome.FILE_REJECTED && report.fileRejectionSourceKey() != null) {
            artifactType = "SOURCE_FILE";
            objectKey = report.fileRejectionSourceKey();
        } else if (report.outcome() == IngestionJobOutcome.COMPLETED
                && report.rejectArtifactKey() != null
                && report.rejectedRowCount() > 0) {
            artifactType = "ROW_REPORT";
            objectKey = report.rejectArtifactKey();
        } else {
            throw new IllegalArgumentException("IngestionJobReport does not have an applicable reject artifact");
        }

        Duration ttl = Duration.ofMinutes(presignTtlMinutes);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        URL downloadUrl = presignPort.generateDownloadUrl(objectKey, ttl);

        return new RejectArtifactUrlResponse(
                jobId,
                artifactType,
                downloadUrl.toString(),
                expiresAt);
    }
}
