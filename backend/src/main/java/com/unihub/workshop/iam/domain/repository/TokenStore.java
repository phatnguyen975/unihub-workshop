package com.unihub.workshop.iam.domain.repository;

import java.time.Duration;
import java.time.Instant;

public interface TokenStore {

    void blacklistAccessToken(String jti, Duration ttl);

    boolean isAccessTokenBlacklisted(String jti);

    void blacklistRefreshToken(String jti, Duration ttl);

    boolean isRefreshTokenBlacklisted(String jti);

    void revokeRefreshTokens(String userId, Instant revokedAfter, Duration ttl);

    Instant getRefreshTokensRevokedAfter(String userId);

    void storeResetToken(String resetToken, String email, Duration ttl);

    String consumeResetToken(String resetToken);
}
