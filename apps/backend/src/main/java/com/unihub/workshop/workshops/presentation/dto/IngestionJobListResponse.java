package com.unihub.workshop.workshops.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IngestionJobListResponse(
        List<IngestionJobItem> items,
        CursorResponse cursor) {
    public record IngestionJobItem(
            UUID id,
            String sourceFileKey,
            String outcome,
            int processedRowCount,
            int rejectedRowCount,
            Instant completedAt,
            boolean artifactAvailable) {
    }

    public record CursorResponse(
            String nextCursor,
            boolean hasMore) {
    }
}
