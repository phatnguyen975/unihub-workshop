package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisJtiBlacklistAdapter implements JtiBlacklistPort {

    private final StringRedisTemplate redisTemplate;

    public RedisJtiBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistJti(String jti, Duration ttl) {
        try {
            redisTemplate.opsForValue().set("jti:blacklist:" + jti, "revoked", ttl);
        } catch (Exception e) {
            throw new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable");
        }
    }
}
