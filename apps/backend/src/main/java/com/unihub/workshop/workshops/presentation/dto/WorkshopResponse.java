package com.unihub.workshop.workshops.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkshopResponse(
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
    Integer version,
    Instant createdAt,
    Instant updatedAt,
    List<SpeakerResponse> speakers
) {}

