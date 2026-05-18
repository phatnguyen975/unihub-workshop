package com.unihub.workshop.iam.domain.events;

public record UserAccountCreatedEvent(
        String email,
        String fullName,
        String role,
        String rawTemporaryPassword) {
}
