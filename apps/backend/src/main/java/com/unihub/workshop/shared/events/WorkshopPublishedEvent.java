package com.unihub.workshop.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when a workshop transitions from DRAFT to PUBLISHED.
 * Externalized to RabbitMQ queue q.search via routing key "search".
 */
@Externalized("unihub.events::search")
public record WorkshopPublishedEvent(
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
    Instant occurredAt
) {
    public WorkshopPublishedEvent(
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
        Instant publishedAt
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
            Instant.now()
        );
    }
}
