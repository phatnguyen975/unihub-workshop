package com.unihub.workshop.iam.domain.events;

import java.util.UUID;

public record StaffStatusUpdatedEvent(
        UUID userId,
        UUID organizationId,
        String status) {
}
