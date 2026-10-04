package com.unihub.workshop.workers.search_sync.inbound.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkshopPublishedEventDto(
    UUID workshopId,
    UUID organizationId,
    String title,
    String slug,
    String description,
    String coverImageUrl,
    String location,
    String timezone,
    String startsAt,
    String endsAt,
    Integer capacity,
    BigDecimal price,
    String currency,
    String publishedAt,
    List<SpeakerDto> speakers
) {
    public record SpeakerDto(
        UUID id,
        String name,
        String bio,
        String avatarUrl,
        String linkedinUrl,
        String email,
        Integer sortOrder
    ) {}
}
