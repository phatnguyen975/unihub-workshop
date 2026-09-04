package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy) {

    public static PasswordResetToken create(UUID userId, String tokenHash, Instant expiresAt) {
        Instant now = Instant.now();
        return new PasswordResetToken(
                UUID.randomUUID(),
                userId,
                tokenHash,
                expiresAt,
                null,
                now,
                now,
                null,
                null);
    }

    public PasswordResetToken consume() {
        Instant now = Instant.now();
        return new PasswordResetToken(
                id,
                userId,
                tokenHash,
                expiresAt,
                now,
                createdAt,
                now,
                createdBy,
                updatedBy);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isConsumed() {
        return usedAt != null;
    }
}
