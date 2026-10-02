package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;
import lombok.Builder;

@Builder
public record CancelWorkshopCommand(
    UUID workshopId,
    UUID organizationId,
    UUID callerId,
    Integer version,
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
    Object requestedOrgId
) {}
