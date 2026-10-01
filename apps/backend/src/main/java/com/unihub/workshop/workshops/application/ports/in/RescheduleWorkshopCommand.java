package com.unihub.workshop.workshops.application.ports.in;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record RescheduleWorkshopCommand(
    UUID workshopId,
    UUID organizationId,
    UUID callerId,
    Integer version,
    Instant startsAt,
    Instant endsAt,
    Object title,
    Object description,
    Object coverImageUrl,
    Object location,
    Object capacity,
    Object price,
    Object currency,
    Object speakers,
    Object status,
    Object requestedOrgId
) {}
