package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID userId,
        String tokenHash,
        String deviceInfo,
        String ipAddress,
        Instant expiresAt,
        Instant revokedAt) {
}
