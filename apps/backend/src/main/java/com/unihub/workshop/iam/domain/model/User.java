package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        UUID organizationId,
        UserRole role,
        String email,
        String passwordHash,
        boolean isActive,
        boolean isTemporaryPassword,
        Instant lastLoginAt) {
}
