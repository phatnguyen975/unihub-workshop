package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestionJobItemResponse(
    UUID id,
    String sourceFileKey,
    String outcome,
    int processedRowCount,
    int rejectedRowCount,
    Instant completedAt,
    boolean artifactAvailable
) {}
