package com.unihub.workshop.workers.batch_ingestion.core.models;

import java.time.Instant;

public record SourceFile(
        String key,
        String organizationSlug,
        long sizeBytes,
        Instant createdAt) {
}
