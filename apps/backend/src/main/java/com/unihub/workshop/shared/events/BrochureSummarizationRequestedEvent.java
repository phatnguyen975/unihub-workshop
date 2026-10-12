package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when an organizer uploads a workshop brochure.
 * Externalized to RabbitMQ queue q.ai.summarization via exchange unihub.events and routing key "ai.summarization".
 */
@Externalized("unihub.events::ai.summarization")
public record BrochureSummarizationRequestedEvent(
    UUID jobId,
    UUID workshopId,
    UUID brochureId,
    String storageKey,
    UUID organizerId,
    Instant occurredAt
) {
    public BrochureSummarizationRequestedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public BrochureSummarizationRequestedEvent(
        UUID jobId,
        UUID workshopId,
        UUID brochureId,
        String storageKey,
        UUID organizerId
    ) {
        this(jobId, workshopId, brochureId, storageKey, organizerId, Instant.now());
    }
}
