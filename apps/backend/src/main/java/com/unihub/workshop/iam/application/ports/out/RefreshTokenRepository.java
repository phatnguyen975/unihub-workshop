package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.ActiveSession;
import com.unihub.workshop.iam.domain.model.CursorPage;
import com.unihub.workshop.iam.domain.model.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void revokeAllActiveTokensForUser(UUID userId, Instant revokedAt);

    CursorPage<ActiveSession> findActiveSessions(
        UUID userId,
        String cursor,
        int limit
    );

    Optional<RefreshToken> findActiveSessionByIdAndUserId(
        UUID id,
        UUID userId,
        Instant now
    );

    boolean revokeActiveSession(UUID id, UUID userId, Instant revokedAt);

    List<RefreshToken> findActiveRemoteSessions(
        UUID userId,
        UUID currentSessionId,
        Instant now
    );

    int revokeAllOtherActiveSessions(
        UUID userId,
        UUID currentSessionId,
        Instant revokedAt
    );

    List<RefreshToken> findAllActiveSessionsForUser(UUID userId, Instant now);
}
