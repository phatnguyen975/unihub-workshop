package com.unihub.workshop.iam.domain.events;

import java.util.UUID;

public record OrganizerSeededEvent(
        UUID organizerId,
        String email,
        String fullName,
        String temporaryPassword) {
}
