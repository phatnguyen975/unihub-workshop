package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.domain.repository.TokenStore;
import com.unihub.workshop.shared.exception.AppException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenStore implements TokenStore {

    private static final String ACCESS_BLACKLIST_PREFIX = "auth:access:blacklist:";
    private static final String REFRESH_BLACKLIST_PREFIX = "auth:refresh:blacklist:";
    private static final String REFRESH_REVOKED_AFTER_PREFIX = "auth:refresh:revoked-after:";
    private static final String RESET_TOKEN_PREFIX = "auth:reset:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTokenStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
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
    public void storeResetToken(String resetToken, String email, Duration ttl) {
        if (resetToken == null || resetToken.isBlank() || email == null || email.isBlank()) {
            return;
        }
        runRedis(() -> stringRedisTemplate.opsForValue().set(resetTokenKey(resetToken), email, ttl));
    }

    @Override
    public String consumeResetToken(String resetToken) {
        if (resetToken == null || resetToken.isBlank()) {
            return null;
        }
        String key = resetTokenKey(resetToken);
        return callRedis(() -> {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                stringRedisTemplate.delete(key);
            }
            return value;
        });
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

    private String resetTokenKey(String resetToken) {
        return RESET_TOKEN_PREFIX + resetToken;
    }

    private void runRedis(RedisRunnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Redis token store unavailable",
                    "redis_unavailable");
        }
    }

    private <T> T callRedis(RedisSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Redis token store unavailable",
                    "redis_unavailable");
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
