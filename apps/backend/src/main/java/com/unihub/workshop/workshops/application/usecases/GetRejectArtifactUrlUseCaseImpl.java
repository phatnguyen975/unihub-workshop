package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.SecurityProperties;
import com.unihub.workshop.workshops.application.ports.in.GetRejectArtifactUrlUseCase;
import com.unihub.workshop.workshops.application.ports.out.ArtifactPresignPort;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetRejectArtifactUrlUseCaseImpl
    implements GetRejectArtifactUrlUseCase
{

    private final IngestionJobReportRepository repository;
    private final ArtifactPresignPort presignPort;
    private final SecurityProperties securityProperties;

    public RejectArtifactUrlResult execute(UUID jobId, UUID organizationId) {
        if (jobId == null) {
            throw new BadRequestException("jobId must not be null");
        }
        if (organizationId == null) {
            throw new BadRequestException("organizationId must not be null");
        }

        // Must use primary data source to prevent replication lag
        IngestionJobReport report = repository
            .findByIdAndOrganizationIdFromPrimary(jobId, organizationId)
            .orElseThrow(() ->
                new NotFoundException("IngestionJobReport not found")
            );

        String artifactType;
        String objectKey;

        if (
            report.outcome() == IngestionJobOutcome.FILE_REJECTED &&
            report.fileRejectionSourceKey() != null
        ) {
            artifactType = "SOURCE_FILE";
            objectKey = report.fileRejectionSourceKey();
        } else if (
            report.outcome() == IngestionJobOutcome.COMPLETED &&
            report.rejectArtifactKey() != null &&
            report.rejectedRowCount() > 0
        ) {
            artifactType = "ROW_REPORT";
            objectKey = report.rejectArtifactKey();
        } else {
            throw new NotFoundException(
                "IngestionJobReport does not have an applicable reject artifact"
            );
        }

        Duration ttl = Duration.ofMinutes(
            securityProperties.presignTtlMinutes()
        );
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        URL downloadUrl = presignPort.generateDownloadUrl(objectKey, ttl);

        return new RejectArtifactUrlResult(
            jobId,
            artifactType,
            downloadUrl.toString(),
            expiresAt
        );
    }
}
