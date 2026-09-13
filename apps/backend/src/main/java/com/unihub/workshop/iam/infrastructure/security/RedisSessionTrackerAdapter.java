package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSessionTrackerAdapter implements SessionTrackerPort {

    private static final String KEY_PREFIX = "session:jti:";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void trackSession(UUID sessionId, String jti, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, jti, ttl);
        } catch (Exception e) {
            meterRegistry
                .counter("auth.blacklist.redis_unavailable")
                .increment();
            throw new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            );
        }
    }

    @Override
    public Optional<String> getActiveJti(UUID sessionId) {
        try {
            String jti = redisTemplate
                .opsForValue()
                .get(KEY_PREFIX + sessionId);
            return Optional.ofNullable(jti);
        } catch (Exception e) {
            meterRegistry
                .counter("auth.blacklist.redis_unavailable")
                .increment();
            throw new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            );
        }
    }

    @Override
    public Duration getRemainingTtl(UUID sessionId) {
        try {
            Long expire = redisTemplate.getExpire(
                KEY_PREFIX + sessionId,
                TimeUnit.SECONDS
            );
            if (expire != null && expire > 0) {
                return Duration.ofSeconds(expire);
            }
            return Duration.ofMinutes(60);
        } catch (Exception e) {
            meterRegistry
                .counter("auth.blacklist.redis_unavailable")
                .increment();
            throw new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            );
        }
    }

    @Override
    public void removeSession(UUID sessionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            meterRegistry
                .counter("auth.blacklist.redis_unavailable")
                .increment();
            throw new ServiceUnavailableException(
                "urn:unihub:error:service-unavailable",
                "Redis is unavailable"
            );
        }
    }
}
