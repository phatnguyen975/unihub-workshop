package com.unihub.workshop.shared.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.AbstractRedisClient;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validateTiers() {
        long regCapacity = properties.registrationTier().capacity();
        long browsCapacity = properties.browsingTier().capacity();

        if (regCapacity > browsCapacity) {
            throw new ApplicationContextException(
                    String.format(
                            "Invalid rate limit configuration: registration-tier capacity (%d) must be <= browsing-tier capacity (%d)",
                            regCapacity, browsCapacity));
        }

        long regRefill = properties.registrationTier().refillTokens();
        long browsRefill = properties.browsingTier().refillTokens();
        if (regRefill > browsRefill) {
            throw new ApplicationContextException(
                    String.format(
                            "Invalid rate limit configuration: registration-tier refill (%d) must be <= browsing-tier refill (%d)",
                            regRefill, browsRefill));
        }
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public ProxyManager<byte[]> bucket4jProxyManager(RedisConnectionFactory connectionFactory) {
        if (connectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory) {
            AbstractRedisClient nativeClient = lettuceConnectionFactory.getNativeClient();
            if (nativeClient instanceof RedisClient redisClient) {
                return io.github.bucket4j.redis.lettuce.Bucket4jLettuce.casBasedBuilder(redisClient)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .build();
            }
        }
        throw new IllegalStateException(
                "Bucket4j requires a Lettuce RedisClient but could not obtain one from RedisConnectionFactory");
    }
}
