package com.unihub.workshop.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a PUBLISHED workshop's presentation or capacity is updated.
 * Externalized to RabbitMQ queue q.search via routing key "search".
 */
@Externalized("unihub.events::search")
public record WorkshopUpdatedEvent(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String description,
    String coverImageUrl,
    String location,
    String timezone,
    Instant startsAt,
    Instant endsAt,
    Integer capacity,
    BigDecimal price,
    String currency,
    Instant publishedAt,
    Instant updatedAt,
    Instant occurredAt
) {
    public WorkshopUpdatedEvent(
        UUID workshopId,
        UUID organizationId,
        String title,
        String slug,
        String description,
        String coverImageUrl,
        String location,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        BigDecimal price,
        String currency,
        Instant publishedAt,
        Instant updatedAt
    ) {
        this(
            workshopId,
            organizationId,
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            startsAt,
            endsAt,
            capacity,
            price,
            currency,
            publishedAt,
            updatedAt,
            Instant.now()
        );
    }
}
