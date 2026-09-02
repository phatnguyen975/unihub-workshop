package com.unihub.workshop.iam.application.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.unihub.workshop.iam.domain.model.RefreshToken;

public interface RefreshTokenRepository {
    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllActiveTokensForUser(UUID userId, Instant revokedAt);
}
