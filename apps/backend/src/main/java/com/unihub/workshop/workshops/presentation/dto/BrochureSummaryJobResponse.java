package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrochureSummaryJobResponse(
    UUID jobId,
    UUID brochureId,
    UUID workshopId,
    String status,
    String resultText,
    String errorMessage,
    int attempts,
    Instant completedAt,
    Instant createdAt
) {}
