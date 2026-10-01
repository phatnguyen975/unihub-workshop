package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop is rescheduled.
 * Externalized to RabbitMQ exchange unihub.events via routing key "workshop.rescheduled".
 */
@Externalized("unihub.events::workshop.rescheduled")
public record WorkshopRescheduledEvent(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String location,
    String timezone,
    Instant previousStartsAt,
    Instant previousEndsAt,
    Instant newStartsAt,
    Instant newEndsAt,
    Instant updatedAt,
    Instant occurredAt
) {
    public WorkshopRescheduledEvent(
        UUID workshopId,
        UUID organizationId,
        String title,
        String slug,
        String location,
        String timezone,
        Instant previousStartsAt,
        Instant previousEndsAt,
        Instant newStartsAt,
        Instant newEndsAt,
        Instant updatedAt
    ) {
        this(
            workshopId,
            organizationId,
            title,
            slug,
            location,
            timezone,
            previousStartsAt,
            previousEndsAt,
            newStartsAt,
            newEndsAt,
            updatedAt,
            Instant.now()
        );
    }
}
