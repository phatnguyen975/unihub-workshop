package com.unihub.workshop.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(
    @NotNull @Valid Tier defaultTier,
    Map<String, @Valid TierConfig> tiers,
    List<String> excludedPaths
) {
    @ConstructorBinding
    public RateLimitProperties(
        Tier defaultTier,
        Map<String, TierConfig> tiers,
        List<String> excludedPaths
    ) {
        this.defaultTier = defaultTier;
        this.tiers =
            tiers != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(tiers))
                : Map.of();
        this.excludedPaths =
            excludedPaths != null ? List.copyOf(excludedPaths) : List.of();
    }

    public TierConfig getTier(String name) {
        return tiers.get(name);
    }

    public Tier registrationTier() {
        TierConfig cfg = getTier("registration");
        return cfg != null ? cfg.toTier() : null;
    }

    public Tier authTier() {
        TierConfig cfg = getTier("auth");
        return cfg != null ? cfg.toTier() : null;
    }

    public Tier streamTier() {
        TierConfig cfg = getTier("stream");
        return cfg != null ? cfg.toTier() : null;
    }

    public List<String> registrationPathPatterns() {
        TierConfig cfg = getTier("registration");
        return cfg != null ? cfg.pathPatterns() : List.of();
    }

    public List<String> authPathPatterns() {
        TierConfig cfg = getTier("auth");
        return cfg != null ? cfg.pathPatterns() : List.of();
    }

    public List<String> streamPathPatterns() {
        TierConfig cfg = getTier("stream");
        return cfg != null ? cfg.pathPatterns() : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Tier(
        @Min(1) long capacity,
        @Min(1) long refillTokens,
        @NotNull Duration refillDuration
    ) {}

    public record TierConfig(
        @Min(1) long capacity,
        @Min(1) long refillTokens,
        @NotNull Duration refillDuration,
        List<String> pathPatterns
    ) {
        public TierConfig {
            pathPatterns =
                pathPatterns != null ? List.copyOf(pathPatterns) : List.of();
        }

        public Tier toTier() {
            return new Tier(capacity, refillTokens, refillDuration);
        }
    }

    public static class Builder {

        private Tier defaultTier;
        private final Map<String, TierConfig> tiers = new LinkedHashMap<>();
        private List<String> excludedPaths = List.of();

        public Builder defaultTier(Tier defaultTier) {
            this.defaultTier = defaultTier;
            return this;
        }

        public Builder defaultTier(
            long capacity,
            long refillTokens,
            Duration refillDuration
        ) {
            this.defaultTier = new Tier(capacity, refillTokens, refillDuration);
            return this;
        }

        public Builder addTier(
            String name,
            long capacity,
            long refillTokens,
            Duration refillDuration,
            List<String> pathPatterns
        ) {
            this.tiers.put(
                name,
                new TierConfig(
                    capacity,
                    refillTokens,
                    refillDuration,
                    pathPatterns
                )
            );
            return this;
        }

        public Builder addTier(
            String name,
            Tier tier,
            List<String> pathPatterns
        ) {
            this.tiers.put(
                name,
                new TierConfig(
                    tier.capacity(),
                    tier.refillTokens(),
                    tier.refillDuration(),
                    pathPatterns
                )
            );
            return this;
        }

        public Builder excludedPaths(List<String> excludedPaths) {
            this.excludedPaths =
                excludedPaths != null ? List.copyOf(excludedPaths) : List.of();
            return this;
        }

        public RateLimitProperties build() {
            return new RateLimitProperties(defaultTier, tiers, excludedPaths);
        }
    }
}
