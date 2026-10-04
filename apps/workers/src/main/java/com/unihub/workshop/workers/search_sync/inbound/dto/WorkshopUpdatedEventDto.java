package com.unihub.workshop.workers.search_sync.inbound.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkshopUpdatedEventDto(
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
    String updatedAt
) {}
