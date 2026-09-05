package com.unihub.workshop.shared.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;

@Component
public class RedisJtiValidator {
    private static final String REDIS_JTI_PREFIX = "jti:blacklisted:";
    private final RedisTemplate<String, String> redisTemplate;

    public RedisJtiValidator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isTokenValid(String jti) {
        try {
            String value = redisTemplate.opsForValue().get(REDIS_JTI_PREFIX + jti);
            return value == null;
        } catch (Exception ex) {
            throw new ServiceUnavailableException(
                    "Failed to check token revocation status. Failing closed.", ex);
        }
    }
}
