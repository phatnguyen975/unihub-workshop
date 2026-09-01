package com.unihub.workshop.workshops.domain.model;

import java.time.Instant;
import java.util.UUID;

public record IngestionJobReport(
        UUID id,
        Long jobExecutionId,
        UUID organizationId,
        String sourceFileKey,
        IngestionJobOutcome outcome,
        int processedRowCount,
        int rejectedRowCount,
        String fileRejectionSourceKey,
        String rejectArtifactKey,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isArtifactAvailable() {
        if (outcome == IngestionJobOutcome.FILE_REJECTED) {
            return fileRejectionSourceKey != null;
        }
        if (outcome == IngestionJobOutcome.COMPLETED) {
            return rejectArtifactKey != null && rejectedRowCount > 0;
        }
        return false;
    }
}
