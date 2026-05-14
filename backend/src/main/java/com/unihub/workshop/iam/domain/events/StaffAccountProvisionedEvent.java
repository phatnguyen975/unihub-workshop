package com.unihub.workshop.iam.domain.events;

import java.util.UUID;

public record StaffAccountProvisionedEvent(
        UUID staffUserId,
        UUID organizationId,
        String email,
        String fullName,
        String temporaryPassword) {
}
