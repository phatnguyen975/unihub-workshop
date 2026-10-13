package com.unihub.workshop.workshops.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkshopBrochureTest {

    @Test
    @DisplayName(
        "deactivate() should return a new brochure record with isActive = false"
    )
    void deactivateShouldReturnInactiveBrochure() {
        UUID id = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkshopBrochure brochure = WorkshopBrochure.builder()
            .id(id)
            .workshopId(workshopId)
            .storageKey("workshops/" + workshopId + "/brochures/" + id + ".pdf")
            .fileName("brochure.pdf")
            .contentType("application/pdf")
            .byteSize(1024L)
            .checksumSha256("checksum123")
            .isActive(true)
            .uploadedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(userId)
            .updatedBy(userId)
            .build();

        WorkshopBrochure deactivated = brochure.deactivate();

        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.id()).isEqualTo(id);
        assertThat(deactivated.workshopId()).isEqualTo(workshopId);
        assertThat(deactivated.storageKey()).isEqualTo(brochure.storageKey());
    }

    @Test
    @DisplayName(
        "AiSummaryJob.createProcessing() should initialize job with status PROCESSING and attempts 0"
    )
    void createProcessingShouldInitializeJob() {
        UUID workshopId = UUID.randomUUID();
        UUID brochureId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiSummaryJob job = AiSummaryJob.createProcessing(
            workshopId,
            brochureId,
            userId
        );

        assertThat(job.id()).isNotNull();
        assertThat(job.workshopId()).isEqualTo(workshopId);
        assertThat(job.brochureId()).isEqualTo(brochureId);
        assertThat(job.status()).isEqualTo(AiSummaryJobStatus.PROCESSING);
        assertThat(job.attempts()).isZero();
        assertThat(job.resultText()).isNull();
        assertThat(job.errorMessage()).isNull();
        assertThat(job.createdBy()).isEqualTo(userId);
    }
}
