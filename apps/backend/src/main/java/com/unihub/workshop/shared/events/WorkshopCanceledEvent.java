package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop is canceled.
 * Externalized to RabbitMQ exchange unihub.events via routing key "workshop.canceled".
 */
@Externalized("unihub.events::workshop.canceled")
public record WorkshopCanceledEvent(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    Instant startsAt,
    Instant endsAt,
    Instant canceledAt,
    String reason,
    Instant occurredAt
) {
    public WorkshopCanceledEvent(
        UUID workshopId,
        UUID organizationId,
        String title,
        String slug,
        Instant startsAt,
        Instant endsAt,
        Instant canceledAt,
        String reason
    ) {
        this(
            workshopId,
            organizationId,
            title,
            slug,
            startsAt,
            endsAt,
            canceledAt,
            reason,
            Instant.now()
        );
    }
}
