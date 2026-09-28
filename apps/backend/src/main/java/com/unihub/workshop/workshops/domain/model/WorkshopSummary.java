package com.unihub.workshop.workshops.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record WorkshopSummary(
    UUID id,
    String slug,
    String title,
    String description,
    String coverImageUrl,
    String location,
    String timezone,
    Instant startsAt,
    Instant endsAt,
    Integer capacity,
    BigDecimal price,
    String currency,
    WorkshopStatus status,
    Integer version,
    int speakerCount,
    Instant createdAt,
    Instant updatedAt
) {}
