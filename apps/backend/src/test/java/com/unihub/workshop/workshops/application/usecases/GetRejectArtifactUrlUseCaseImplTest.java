package com.unihub.workshop.workshops.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.security.SecurityProperties;
import com.unihub.workshop.workshops.application.ports.out.ArtifactPresignPort;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetRejectArtifactUrlUseCaseImplTest {

    private IngestionJobReportRepository repository;
    private ArtifactPresignPort presignPort;
    private GetRejectArtifactUrlUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse(
        "2026-08-31T02:15:30Z"
    );
    private static final long TTL_MINUTES = 15;

    @BeforeEach
    void setUp() {
        repository = mock(IngestionJobReportRepository.class);
        presignPort = mock(ArtifactPresignPort.class);
        SecurityProperties securityProperties = new SecurityProperties(
            List.of(),
            (int) TTL_MINUTES,
            60,
            7
        );
        useCase = new GetRejectArtifactUrlUseCaseImpl(
            repository,
            presignPort,
            securityProperties
        );
    }

    private IngestionJobReport.IngestionJobReportBuilder baseReportBuilder() {
        return IngestionJobReport.builder()
            .id(JOB_ID)
            .jobExecutionId(123L)
            .organizationId(ORG_ID)
            .sourceFileKey("source.csv")
            .completedAt(FIXED_NOW)
            .createdAt(FIXED_NOW)
            .updatedAt(FIXED_NOW);
    }

    @Test
    void execute_WhenFileRejected_ReturnsSourceFileUrl()
        throws MalformedURLException {
        IngestionJobReport report = baseReportBuilder()
            .outcome(IngestionJobOutcome.FILE_REJECTED)
            .fileRejectionSourceKey("rejected_source.csv")
            .build();

        when(
            repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)
        ).thenReturn(Optional.of(report));
        when(
            presignPort.generateDownloadUrl(
                "rejected_source.csv",
                Duration.ofMinutes(TTL_MINUTES)
            )
        ).thenReturn(java.net.URI.create("https://minio/source").toURL());

        RejectArtifactUrlResult response = useCase.execute(JOB_ID, ORG_ID);

        assertEquals(JOB_ID, response.jobId());
        assertEquals("SOURCE_FILE", response.artifactType());
        assertEquals("https://minio/source", response.downloadUrl());
        assertTrue(
            response
                .expiresAt()
                .isAfter(Instant.now().plus(Duration.ofMinutes(14)))
        );
        assertTrue(
            response
                .expiresAt()
                .isBefore(Instant.now().plus(Duration.ofMinutes(16)))
        );
    }

    @Test
    void execute_WhenCompletedWithRejects_ReturnsRowReportUrl()
        throws MalformedURLException {
        IngestionJobReport report = baseReportBuilder()
            .outcome(IngestionJobOutcome.COMPLETED)
            .processedRowCount(100)
            .rejectedRowCount(5)
            .rejectArtifactKey("reject_artifact.csv")
            .build();

        when(
            repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)
        ).thenReturn(Optional.of(report));
        when(
            presignPort.generateDownloadUrl(
                "reject_artifact.csv",
                Duration.ofMinutes(TTL_MINUTES)
            )
        ).thenReturn(java.net.URI.create("https://minio/row").toURL());

        RejectArtifactUrlResult response = useCase.execute(JOB_ID, ORG_ID);

        assertEquals(JOB_ID, response.jobId());
        assertEquals("ROW_REPORT", response.artifactType());
        assertEquals("https://minio/row", response.downloadUrl());
        assertTrue(
            response
                .expiresAt()
                .isAfter(Instant.now().plus(Duration.ofMinutes(14)))
        );
        assertTrue(
            response
                .expiresAt()
                .isBefore(Instant.now().plus(Duration.ofMinutes(16)))
        );
    }

    @Test
    void execute_WhenCompletedCleanRun_ThrowsException() {
        IngestionJobReport report = baseReportBuilder()
            .outcome(IngestionJobOutcome.COMPLETED)
            .processedRowCount(100)
            .rejectedRowCount(0)
            .build();

        when(
            repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)
        ).thenReturn(Optional.of(report));

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
            useCase.execute(JOB_ID, ORG_ID)
        );
        assertTrue(
            ex
                .getMessage()
                .contains("does not have an applicable reject artifact")
        );
    }

    @Test
    void execute_WhenFileRejectedButNoKey_ThrowsException() {
        IngestionJobReport report = baseReportBuilder()
            .outcome(IngestionJobOutcome.FILE_REJECTED)
            .build();

        when(
            repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)
        ).thenReturn(Optional.of(report));

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
            useCase.execute(JOB_ID, ORG_ID)
        );
        assertTrue(
            ex
                .getMessage()
                .contains("does not have an applicable reject artifact")
        );
    }

    @Test
    void execute_WhenNotFound_ThrowsException() {
        when(
            repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)
        ).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () ->
            useCase.execute(JOB_ID, ORG_ID)
        );
        assertTrue(ex.getMessage().contains("IngestionJobReport not found"));
    }

    @Test
    void execute_WhenJobIdNull_ThrowsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            useCase.execute(null, ORG_ID)
        );
        assertEquals("jobId must not be null", ex.getMessage());
    }

    @Test
    void execute_WhenOrganizationIdNull_ThrowsBadRequestException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            useCase.execute(JOB_ID, null)
        );
        assertEquals("organizationId must not be null", ex.getMessage());
    }
}
