package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;
import lombok.Builder;

@Builder
public record PublishWorkshopCommand(
    UUID workshopId,
    UUID organizationId,
    UUID callerId,
    Integer version
) {}
