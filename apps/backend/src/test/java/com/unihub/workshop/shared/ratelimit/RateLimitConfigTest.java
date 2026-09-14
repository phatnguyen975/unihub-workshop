package com.unihub.workshop.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextException;

class RateLimitConfigTest {

    @Test
    void testValidationFailsIfRegistrationTierCapacityGreaterThanDefaultTier() {
        RateLimitProperties props = RateLimitProperties.builder()
            .defaultTier(50, 50, Duration.ofSeconds(60))
            .addTier("registration", 100, 10, Duration.ofSeconds(10), List.of())
            .build();

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
            .isInstanceOf(ApplicationContextException.class)
            .hasMessageContaining(
                "registration-tier capacity (100) must be <= default-tier capacity (50)"
            );
    }

    @Test
    void testValidationFailsIfRegistrationTierRefillGreaterThanDefaultTier() {
        RateLimitProperties props = RateLimitProperties.builder()
            .defaultTier(50, 50, Duration.ofSeconds(60))
            .addTier("registration", 50, 100, Duration.ofSeconds(10), List.of())
            .build();

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
            .isInstanceOf(ApplicationContextException.class)
            .hasMessageContaining(
                "registration-tier refill (100) must be <= default-tier refill (50)"
            );
    }

    @Test
    void testValidationFailsIfAuthTierCapacityGreaterThanDefaultTier() {
        RateLimitProperties props = RateLimitProperties.builder()
            .defaultTier(50, 50, Duration.ofSeconds(60))
            .addTier("registration", 5, 5, Duration.ofSeconds(10), List.of())
            .addTier("stream", 10, 10, Duration.ofSeconds(60), List.of())
            .addTier("auth", 80, 20, Duration.ofSeconds(60), List.of())
            .build();

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
            .isInstanceOf(ApplicationContextException.class)
            .hasMessageContaining(
                "auth-tier capacity (80) must be <= default-tier capacity (50)"
            );
    }

    @Test
    void testValidationFailsIfAuthTierRefillGreaterThanDefaultTier() {
        RateLimitProperties props = RateLimitProperties.builder()
            .defaultTier(50, 50, Duration.ofSeconds(60))
            .addTier("registration", 5, 5, Duration.ofSeconds(10), List.of())
            .addTier("stream", 10, 10, Duration.ofSeconds(60), List.of())
            .addTier("auth", 20, 80, Duration.ofSeconds(60), List.of())
            .build();

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
            .isInstanceOf(ApplicationContextException.class)
            .hasMessageContaining(
                "auth-tier refill (80) must be <= default-tier refill (50)"
            );
    }
}
