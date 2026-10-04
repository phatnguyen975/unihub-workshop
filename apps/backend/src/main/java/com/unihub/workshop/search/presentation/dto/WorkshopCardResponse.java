package com.unihub.workshop.search.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkshopCardResponse(
    UUID id,
    String slug,
    String title,
    String coverImageUrl,
    String location,
    String startsAt,
    String endsAt,
    String timezone,
    String price,
    String currency,
    Integer capacity,
    Integer availableSeats,
    String priceType,
    UUID organizationId
) {}
