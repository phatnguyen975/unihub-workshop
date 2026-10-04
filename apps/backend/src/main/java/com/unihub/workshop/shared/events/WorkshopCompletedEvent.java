package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop is completed.
 * Externalized to RabbitMQ exchange unihub.events via routing key "workshop.completed".
 */
@Externalized("unihub.events::workshop.completed")
public record WorkshopCompletedEvent(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    Instant startsAt,
    Instant endsAt,
    Instant completedAt,
    Instant occurredAt
) {
    public WorkshopCompletedEvent {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public WorkshopCompletedEvent(
        UUID workshopId,
        UUID organizationId,
        String title,
        String slug,
        Instant startsAt,
        Instant endsAt,
        Instant completedAt
    ) {
        this(
            workshopId,
            organizationId,
            title,
            slug,
            startsAt,
            endsAt,
            completedAt,
            Instant.now()
        );
    }
}
