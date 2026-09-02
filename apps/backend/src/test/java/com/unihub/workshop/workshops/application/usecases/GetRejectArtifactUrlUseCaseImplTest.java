package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.out.ArtifactPresignPort;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetRejectArtifactUrlUseCaseImplImplTest {

    private IngestionJobReportRepository repository;
    private ArtifactPresignPort presignPort;
    private GetRejectArtifactUrlUseCaseImpl useCase;

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-08-31T02:15:30Z");
    private static final long TTL_MINUTES = 15;

    @BeforeEach
    void setUp() {
        repository = mock(IngestionJobReportRepository.class);
        presignPort = mock(ArtifactPresignPort.class);
        useCase = new GetRejectArtifactUrlUseCaseImpl(repository, presignPort, TTL_MINUTES);
    }

    @Test
    void execute_WhenFileRejected_ReturnsSourceFileUrl() throws MalformedURLException {
        IngestionJobReport report = new IngestionJobReport(
                JOB_ID, 123L, ORG_ID, "source.csv", IngestionJobOutcome.FILE_REJECTED,
                0, 0, "rejected_source.csv", null, FIXED_NOW, FIXED_NOW, FIXED_NOW);

        when(repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)).thenReturn(Optional.of(report));
        when(presignPort.generateDownloadUrl("rejected_source.csv", Duration.ofMinutes(TTL_MINUTES)))
                .thenReturn(java.net.URI.create("https://minio/source").toURL());

        RejectArtifactUrlResult response = useCase.execute(JOB_ID, ORG_ID);

        assertEquals(JOB_ID, response.jobId());
        assertEquals("SOURCE_FILE", response.artifactType());
        assertEquals("https://minio/source", response.downloadUrl());
        assertTrue(response.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
        assertTrue(response.expiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(16))));
    }

    @Test
    void execute_WhenCompletedWithRejects_ReturnsRowReportUrl() throws MalformedURLException {
        IngestionJobReport report = new IngestionJobReport(
                JOB_ID, 123L, ORG_ID, "source.csv", IngestionJobOutcome.COMPLETED,
                100, 5, null, "reject_artifact.csv", FIXED_NOW, FIXED_NOW, FIXED_NOW);

        when(repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)).thenReturn(Optional.of(report));
        when(presignPort.generateDownloadUrl("reject_artifact.csv", Duration.ofMinutes(TTL_MINUTES)))
                .thenReturn(java.net.URI.create("https://minio/row").toURL());

        RejectArtifactUrlResult response = useCase.execute(JOB_ID, ORG_ID);

        assertEquals(JOB_ID, response.jobId());
        assertEquals("ROW_REPORT", response.artifactType());
        assertEquals("https://minio/row", response.downloadUrl());
        assertTrue(response.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
        assertTrue(response.expiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(16))));
    }

    @Test
    void execute_WhenCompletedCleanRun_ThrowsException() {
        IngestionJobReport report = new IngestionJobReport(
                JOB_ID, 123L, ORG_ID, "source.csv", IngestionJobOutcome.COMPLETED,
                100, 0, null, null, FIXED_NOW, FIXED_NOW, FIXED_NOW);

        when(repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)).thenReturn(Optional.of(report));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.execute(JOB_ID, ORG_ID));
        assertTrue(ex.getMessage().contains("does not have an applicable reject artifact"));
    }

    @Test
    void execute_WhenFileRejectedButNoKey_ThrowsException() {
        IngestionJobReport report = new IngestionJobReport(
                JOB_ID, 123L, ORG_ID, "source.csv", IngestionJobOutcome.FILE_REJECTED,
                0, 0, null, null, FIXED_NOW, FIXED_NOW, FIXED_NOW);

        when(repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)).thenReturn(Optional.of(report));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.execute(JOB_ID, ORG_ID));
        assertTrue(ex.getMessage().contains("does not have an applicable reject artifact"));
    }

    @Test
    void execute_WhenNotFound_ThrowsException() {
        when(repository.findByIdAndOrganizationIdFromPrimary(JOB_ID, ORG_ID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> useCase.execute(JOB_ID, ORG_ID));
        assertTrue(ex.getMessage().contains("IngestionJobReport not found"));
    }
}
