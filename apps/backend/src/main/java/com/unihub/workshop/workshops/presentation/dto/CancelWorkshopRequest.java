package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record CancelWorkshopRequest(
    @NotNull(message = "version is required") Integer version,
    String reason,
    Object title,
    Object description,
    Object coverImageUrl,
    Object location,
    Object timezone,
    Object startsAt,
    Object endsAt,
    Object capacity,
    Object price,
    Object currency,
    Object speakers,
    Object status,
    Object organizationId
) {}
