package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import com.unihub.workshop.iam.domain.repository.PasswordResetTokenRepository;
import com.unihub.workshop.iam.domain.repository.TokenStore;
import com.unihub.workshop.shared.exception.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenStore implements TokenStore {

    private static final String ACCESS_BLACKLIST_PREFIX = "auth:access:blacklist:";
    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh:blacklist:";
    private static final String REFRESH_REVOKED_AFTER_PREFIX = "auth:refresh:revoked-after:";

    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public RedisTokenStore(
            StringRedisTemplate stringRedisTemplate,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Override
    public void blacklistAccessToken(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        runRedis(() -> stringRedisTemplate.opsForValue().set(accessBlacklistKey(jti), "1", ttl));
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(callRedis(() -> stringRedisTemplate.hasKey(accessBlacklistKey(jti))));
    }

    @Override
    public void blacklistRefreshToken(String jti, Duration ttl) {
        if (jti == null || jti.isBlank() || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        runRedis(() -> stringRedisTemplate.opsForValue().set(refreshBlacklistKey(jti), "1", ttl));
    }

    @Override
    public boolean isRefreshTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(callRedis(() -> stringRedisTemplate.hasKey(refreshBlacklistKey(jti))));
    }

    @Override
    public void revokeRefreshTokens(String userId, Instant revokedAfter, Duration ttl) {
        if (userId == null || userId.isBlank() || revokedAfter == null) {
            return;
        }
        Duration safeTtl = ttl == null ? Duration.ZERO : ttl;
        if (safeTtl.isZero() || safeTtl.isNegative()) {
            safeTtl = Duration.ofDays(30);
        }
        Duration ttlToUse = safeTtl;
        runRedis(() -> stringRedisTemplate.opsForValue().set(
                refreshRevokedAfterKey(userId),
                String.valueOf(revokedAfter.getEpochSecond()),
                ttlToUse));
    }

    @Override
    public Instant getRefreshTokensRevokedAfter(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String value = callRedis(() -> stringRedisTemplate.opsForValue().get(refreshRevokedAfterKey(userId)));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void storeResetToken(String resetToken, String userId, Duration ttl) {
        if (resetToken == null || resetToken.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Duration effectiveTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(15) : ttl;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(parsedUserId);
        token.setTokenHash(sha256Hex(resetToken));
        token.setExpiresAt(now.plus(effectiveTtl));
        token.setUsedAt(null);
        passwordResetTokenRepository.save(token);
    }

    @Override
    public String consumeResetToken(String resetToken) {
        if (resetToken == null || resetToken.isBlank()) {
            return null;
        }

        String tokenHash = sha256Hex(resetToken);
        PasswordResetToken token = passwordResetTokenRepository.findActiveByTokenHash(tokenHash)
                .orElse(null);
        if (token == null) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            return null;
        }

        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);
        return token.getUserId() == null ? null : token.getUserId().toString();
    }

    private String accessBlacklistKey(String jti) {
        return ACCESS_BLACKLIST_PREFIX + jti;
    }

    private String refreshBlacklistKey(String jti) {
        return REFRESH_BLACKLIST_PREFIX + jti;
    }

    private String refreshRevokedAfterKey(String userId) {
        return REFRESH_REVOKED_AFTER_PREFIX + userId;
    }

    private String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Token hashing unavailable", "token_hashing");
        }
    }

    private void runRedis(RedisRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Redis token store unavailable", "redis_unavailable");
        }
    }

    private <T> T callRedis(RedisSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Redis token store unavailable", "redis_unavailable");
        }
    }

    @FunctionalInterface
    private interface RedisRunnable {
        void run();
    }

    @FunctionalInterface
    private interface RedisSupplier<T> {
        T get();
    }
}
