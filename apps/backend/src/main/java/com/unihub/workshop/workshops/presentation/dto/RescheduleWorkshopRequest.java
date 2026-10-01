package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleWorkshopRequest(
    @NotNull(message = "version is required") Integer version,

    @NotNull(message = "startsAt is required") Instant startsAt,

    @NotNull(message = "endsAt is required") Instant endsAt,

    Object title,
    Object description,
    Object coverImageUrl,
    Object location,
    Object capacity,
    Object price,
    Object currency,
    Object speakers,
    Object status,
    Object organizationId
) {}
