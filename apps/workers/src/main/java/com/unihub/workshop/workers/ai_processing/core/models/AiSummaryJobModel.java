package com.unihub.workshop.workers.ai_processing.core.models;

import java.time.Instant;
import java.util.UUID;

public record AiSummaryJobModel(
    UUID id,
    UUID workshopId,
    UUID brochureId,
    AiSummaryJobStatusModel status,
    String resultText,
    String errorMessage,
    int attempts,
    Instant nextRetryAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {}
