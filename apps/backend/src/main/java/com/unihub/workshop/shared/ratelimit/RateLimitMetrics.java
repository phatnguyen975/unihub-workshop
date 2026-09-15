package com.unihub.workshop.shared.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimitMetrics {

    private final MeterRegistry meterRegistry;

    public void incrementRedisUnavailable(String tier) {
        meterRegistry
            .counter("rate_limiter.redis_unavailable", "tier", tier)
            .increment();
    }
}
