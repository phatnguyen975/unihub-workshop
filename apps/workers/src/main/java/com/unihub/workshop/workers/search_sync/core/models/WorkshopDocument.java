package com.unihub.workshop.workers.search_sync.core.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pure Java domain model representing a workshop indexed in Elasticsearch.
 */
public record WorkshopDocument(
    UUID id,
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
    String status,
    Instant publishedAt,
    List<SpeakerDocument> speakers
) {
    public record SpeakerDocument(
        UUID id,
        String name,
        String bio,
        String avatarUrl,
        String linkedinUrl,
        String email,
        Integer sortOrder
    ) {}
}
