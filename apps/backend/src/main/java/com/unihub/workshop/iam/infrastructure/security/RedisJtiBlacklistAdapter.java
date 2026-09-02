package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisJtiBlacklistAdapter implements JtiBlacklistPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklistJti(String jti, Duration ttl) {
        try {
            redisTemplate.opsForValue().set("jti:blacklisted:" + jti, "revoked", ttl);
        } catch (Exception e) {
            throw new ServiceUnavailableException("urn:unihub:error:service-unavailable", "Redis is unavailable");
        }
    }
}
