package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record RefreshToken(
    UUID id,
    UUID userId,
    String tokenHash,
    String deviceInfo,
    String ipAddress,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        String deviceInfo,
        String ipAddress,
        Instant expiresAt,
        Instant revokedAt
    ) {
        this(
            id,
            userId,
            tokenHash,
            deviceInfo,
            ipAddress,
            expiresAt,
            revokedAt,
            Instant.now(),
            Instant.now(),
            null,
            null
        );
    }
}
