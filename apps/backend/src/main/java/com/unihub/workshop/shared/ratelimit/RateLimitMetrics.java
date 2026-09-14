package com.unihub.workshop.shared.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RateLimitMetrics {

    private final MeterRegistry meterRegistry;

    public RateLimitMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementRedisUnavailable(String tier) {
        meterRegistry
            .counter("rate_limiter.redis_unavailable", "tier", tier)
            .increment();
    }
}
