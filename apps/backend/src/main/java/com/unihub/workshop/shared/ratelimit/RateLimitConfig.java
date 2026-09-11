package com.unihub.workshop.shared.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validateTiers() {
        long defaultCapacity = properties.defaultTier().capacity();
        long defaultRefill = properties.defaultTier().refillTokens();

        if (properties.tiers() != null) {
            properties.tiers().forEach((name, tierConfig) -> {
                validateSubTier(
                    tierConfig.toTier(),
                    name + "-tier",
                    defaultCapacity,
                    defaultRefill
                );
            });
        }
    }

    private void validateSubTier(
        RateLimitProperties.Tier tier,
        String tierName,
        long maxCapacity,
        long maxRefill
    ) {
        if (tier.capacity() > maxCapacity) {
            throw new ApplicationContextException(
                String.format(
                    "Invalid rate limit configuration: %s capacity (%d) must be <= default-tier capacity (%d)",
                    tierName,
                    tier.capacity(),
                    maxCapacity
                )
            );
        }
        if (tier.refillTokens() > maxRefill) {
            throw new ApplicationContextException(
                String.format(
                    "Invalid rate limit configuration: %s refill (%d) must be <= default-tier refill (%d)",
                    tierName,
                    tier.refillTokens(),
                    maxRefill
                )
            );
        }
    }

    @Bean
    @Profile("!test")
    public ProxyManager<byte[]> bucket4jProxyManager(
        RedisConnectionFactory connectionFactory
    ) {
        if (
            connectionFactory instanceof
                LettuceConnectionFactory lettuceConnectionFactory
        ) {
            AbstractRedisClient nativeClient =
                lettuceConnectionFactory.getNativeClient();
            if (nativeClient instanceof RedisClient redisClient) {
                return Bucket4jLettuce.casBasedBuilder(redisClient)
                    .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                            Duration.ofSeconds(10)
                        )
                    )
                    .build();
            }
        }
        throw new IllegalStateException(
            "Bucket4j requires a Lettuce RedisClient but could not obtain one from RedisConnectionFactory"
        );
    }
}
