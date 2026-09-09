package com.unihub.workshop.shared.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(
        @NotNull Tier registrationTier,
        @NotNull Tier browsingTier,
        Tier streamTier,
        List<String> registrationPathPatterns,
        List<String> streamPathPatterns) {

    @ConstructorBinding
    public RateLimitProperties(
            Tier registrationTier,
            Tier browsingTier,
            Tier streamTier,
            List<String> registrationPathPatterns,
            List<String> streamPathPatterns) {
        this.registrationTier = registrationTier;
        this.browsingTier = browsingTier;
        this.streamTier = streamTier != null ? streamTier : new Tier(10, 10, Duration.ofSeconds(60));
        this.registrationPathPatterns = registrationPathPatterns != null ? registrationPathPatterns : List.of();
        this.streamPathPatterns = streamPathPatterns != null ? streamPathPatterns : List.of("/api/v1/stream/**");
    }

    public RateLimitProperties(
            Tier registrationTier,
            Tier browsingTier,
            List<String> registrationPathPatterns) {
        this(registrationTier, browsingTier, new Tier(10, 10, Duration.ofSeconds(60)), registrationPathPatterns,
                List.of("/api/v1/stream/**"));
    }

    public record Tier(@Min(1) long capacity, @Min(1) long refillTokens, @NotNull Duration refillDuration) {
    }
}
