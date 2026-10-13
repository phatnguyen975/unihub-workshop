package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record AiSummaryJob(
    UUID id,
    UUID workshopId,
    UUID brochureId,
    AiSummaryJobStatus status,
    String resultText,
    String errorMessage,
    int attempts,
    Instant nextRetryAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static AiSummaryJob createProcessing(
        UUID workshopId,
        UUID brochureId,
        UUID createdBy
    ) {
        Instant now = Instant.now();
        return AiSummaryJob.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .brochureId(brochureId)
            .status(AiSummaryJobStatus.PROCESSING)
            .attempts(0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
    }
}
