package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import java.util.UUID;

public interface IngestionEventPublisher {
    void publishFileRejectedEvent(UUID organizationId, String sourceFileKey);

    void publishFileCompletedEvent(UUID organizationId, String sourceFileKey);
}
