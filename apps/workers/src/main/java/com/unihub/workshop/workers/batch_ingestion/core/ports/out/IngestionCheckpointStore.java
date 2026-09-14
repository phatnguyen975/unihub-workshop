package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import com.unihub.workshop.workers.batch_ingestion.core.models.IngestionMetrics;
import java.util.UUID;

public interface IngestionCheckpointStore {
    long startIngestionJob(
        String sourceFileKey,
        UUID organizationId,
        String organizationSlug,
        String organizationName
    );

    IngestionMetrics getMetrics(long jobExecutionId);
}
