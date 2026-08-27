package com.unihub.workshop.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(@NotNull Tier registrationTier, @NotNull Tier browsingTier) {
    public record Tier(@Min(1) long capacity, @Min(1) long refillTokens, @NotNull Duration refillDuration) {
    }
}
