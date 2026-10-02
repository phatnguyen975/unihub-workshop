package com.unihub.workshop.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    List<SpeakerPayload> speakers,
    Instant occurredAt
) {
    public record SpeakerPayload(
        UUID id,
        String name,
        String bio,
        String avatarUrl,
        String linkedinUrl,
        String email,
        Integer sortOrder
    ) {}

    public WorkshopPublishedEvent {
        if (speakers == null) {
            speakers = List.of();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

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
        Instant publishedAt,
        List<SpeakerPayload> speakers
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
            speakers,
            Instant.now()
        );
    }
}
