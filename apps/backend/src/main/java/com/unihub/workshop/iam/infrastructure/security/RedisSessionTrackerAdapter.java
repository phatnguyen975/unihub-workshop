package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.application.ports.out.SessionTrackerPort;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisSessionTrackerAdapter implements SessionTrackerPort {

    private static final String KEY_PREFIX = "session:jti:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void trackSession(UUID sessionId, String jti, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, jti, ttl);
        } catch (Exception e) {
            throw new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable");
        }
    }

    @Override
    public Optional<String> getActiveJti(UUID sessionId) {
        try {
            String jti = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            return Optional.ofNullable(jti);
        } catch (Exception e) {
            throw new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable");
        }
    }

    @Override
    public void removeSession(UUID sessionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            throw new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable");
        }
    }
}
