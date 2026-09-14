package com.unihub.workshop.shared.security;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisJtiValidator {

    private static final String REDIS_JTI_PREFIX = "jti:blacklisted:";
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    public RedisJtiValidator(
        RedisTemplate<String, String> redisTemplate,
        MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public boolean isTokenValid(String jti) {
        try {
            String value = redisTemplate
                .opsForValue()
                .get(REDIS_JTI_PREFIX + jti);
            return value == null;
        } catch (Exception ex) {
            meterRegistry
                .counter("auth.blacklist.redis_unavailable")
                .increment();
            throw new ServiceUnavailableException(
                "Failed to check token revocation status. Failing closed.",
                ex
            );
        }
    }
}
