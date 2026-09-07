package com.unihub.workshop.iam.application.ports.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.RefreshToken;

public interface RefreshTokenRepository {
    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllActiveTokensForUser(UUID userId, Instant revokedAt);

    CursorPage<ActiveSession> findActiveSessions(UUID userId, String cursor, int limit);

    Optional<RefreshToken> findActiveSessionByIdAndUserId(UUID id, UUID userId, Instant now);

    boolean revokeActiveSession(UUID id, UUID userId, Instant revokedAt);
}
