package com.unihub.workshop.workers.batch_ingestion.core.models;

import java.time.Instant;
import java.util.UUID;

public record IngestionJobReport(
        UUID id,
        Long jobExecutionId,
        UUID organizationId,
        String sourceFileKey,
        Outcome outcome,
        int processedRowCount,
        int rejectedRowCount,
        String fileRejectionSourceKey,
        String rejectArtifactKey,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
    public enum Outcome {
        COMPLETED,
        FILE_REJECTED
    }
}
