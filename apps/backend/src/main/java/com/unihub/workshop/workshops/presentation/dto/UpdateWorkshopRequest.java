package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWorkshopRequest(
    @NotNull(message = "version is required") Integer version,

    @Size(max = 255, message = "title must not exceed 255 characters")
    String title,

    @Size(max = 5000, message = "description must not exceed 5000 characters")
    String description,

    @Size(max = 2048, message = "coverImageUrl must not exceed 2048 characters")
    String coverImageUrl,

    @Min(value = 1, message = "capacity must be at least 1")
    @Max(value = 1000000, message = "capacity must not exceed 1,000,000")
    Integer capacity,

    Object startsAt,
    Object endsAt,
    Object price,
    Object currency,
    Object status,
    Object organizationId,
    Object speakers
) {}
