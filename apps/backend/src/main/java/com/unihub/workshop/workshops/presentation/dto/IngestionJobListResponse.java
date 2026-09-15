package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record IngestionJobListResponse(
    List<IngestionJobItem> items,
    CursorResponse cursor
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public record IngestionJobItem(
        UUID id,
        String sourceFileKey,
        String outcome,
        int processedRowCount,
        int rejectedRowCount,
        Instant completedAt,
        boolean artifactAvailable
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Builder
    public record CursorResponse(String nextCursor, boolean hasMore) {}
}
