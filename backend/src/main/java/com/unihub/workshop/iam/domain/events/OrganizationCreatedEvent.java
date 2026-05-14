package com.unihub.workshop.iam.domain.events;

import java.util.UUID;

public record OrganizationCreatedEvent(
        UUID organizationId,
        UUID organizerId,
        String name) {
}
