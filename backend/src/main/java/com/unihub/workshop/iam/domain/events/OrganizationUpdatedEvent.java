package com.unihub.workshop.iam.domain.events;

import java.util.UUID;

public record OrganizationUpdatedEvent(
        UUID organizationId,
        UUID organizerId) {
}
