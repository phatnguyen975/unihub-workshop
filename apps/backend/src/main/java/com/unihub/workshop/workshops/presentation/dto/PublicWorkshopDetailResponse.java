package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicWorkshopDetailResponse(
    UUID id,
    String slug,
    String title,
    String description,
    String coverImageUrl,
    String location,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    String price,
    String currency,
    Integer capacity,
    Integer availableSeats,
    String priceType,
    UUID organizationId,
    Instant publishedAt,
    String status,
    List<PublicSpeakerResponse> speakers
) {}
