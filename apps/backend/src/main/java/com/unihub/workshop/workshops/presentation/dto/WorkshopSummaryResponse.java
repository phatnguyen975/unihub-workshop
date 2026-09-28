package com.unihub.workshop.workshops.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record WorkshopSummaryResponse(
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
    String status,
    Integer version,
    int speakerCount,
    Instant createdAt,
    Instant updatedAt
) {}
