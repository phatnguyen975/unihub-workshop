package com.unihub.workshop.shared.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(
        @NotNull Tier registrationTier,
        @NotNull Tier browsingTier,
        List<String> registrationPathPatterns) {

    public RateLimitProperties {
        if (registrationPathPatterns == null) {
            registrationPathPatterns = List.of();
        }
    }

    public record Tier(@Min(1) long capacity, @Min(1) long refillTokens, @NotNull Duration refillDuration) {
    }
}
