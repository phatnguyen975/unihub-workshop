package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;
import lombok.Builder;

@Builder
public record UpdateWorkshopCommand(
    UUID workshopId,
    UUID organizationId,
    UUID callerId,
    Integer version,
    String title,
    String description,
    String coverImageUrl,
    Integer capacity,
    Object startsAt,
    Object endsAt,
    Object price,
    Object currency,
    Object status,
    Object requestedOrgId,
    Object speakers
) {}
