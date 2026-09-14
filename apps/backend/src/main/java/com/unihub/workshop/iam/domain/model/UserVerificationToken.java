package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserVerificationToken(
    UUID id,
    UUID userId,
    String tokenType,
    String tokenHash,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static final String TYPE_ACTIVATION_OTP = "ACTIVATION_OTP";
    public static final String TYPE_PASSWORD_RESET_OTP = "PASSWORD_RESET_OTP";

    public static UserVerificationToken createActivationOtp(
        UUID userId,
        String tokenHash,
        Instant expiresAt
    ) {
        Instant now = Instant.now();
        return new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            TYPE_ACTIVATION_OTP,
            tokenHash,
            expiresAt,
            null,
            now,
            now,
            null,
            null
        );
    }

    public static UserVerificationToken createPasswordResetOtp(
        UUID userId,
        String tokenHash,
        Instant expiresAt
    ) {
        Instant now = Instant.now();
        return new UserVerificationToken(
            UUID.randomUUID(),
            userId,
            TYPE_PASSWORD_RESET_OTP,
            tokenHash,
            expiresAt,
            null,
            now,
            now,
            null,
            null
        );
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isConsumed() {
        return usedAt != null;
    }
}
