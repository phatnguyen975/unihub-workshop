package com.unihub.workshop.shared.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitConfigTest {

    @Test
    void testValidationFailsIfRegistrationTierCapacityGreaterThanBrowsingTier() {
        RateLimitProperties props = new RateLimitProperties(
                new RateLimitProperties.Tier(100, 10, Duration.ofSeconds(10)),
                new RateLimitProperties.Tier(50, 50, Duration.ofSeconds(60)));

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("registration-tier capacity (100) must be <= browsing-tier capacity (50)");
    }

    @Test
    void testValidationFailsIfRegistrationTierRefillGreaterThanBrowsingTier() {
        RateLimitProperties props = new RateLimitProperties(
                new RateLimitProperties.Tier(50, 100, Duration.ofSeconds(10)),
                new RateLimitProperties.Tier(50, 50, Duration.ofSeconds(60)));

        RateLimitConfig config = new RateLimitConfig(props);

        assertThatThrownBy(config::validateTiers)
                .isInstanceOf(ApplicationContextException.class)
                .hasMessageContaining("registration-tier refill (100) must be <= browsing-tier refill (50)");
    }
}
