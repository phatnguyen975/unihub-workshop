package com.unihub.workshop.iam.infrastructure.security;

import com.unihub.workshop.iam.application.ports.out.JtiBlacklistPort;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisJtiBlacklistAdapter implements JtiBlacklistPort {

    private static final String REDIS_JTI_PREFIX = "jti:blacklisted:";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void blacklistJti(String jti, Duration ttl) {
        try {
            redisTemplate
                .opsForValue()
                .set(REDIS_JTI_PREFIX + jti, "revoked", ttl);
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
