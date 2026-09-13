package com.unihub.workshop.shared.resilience;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevChaosMockAdapter implements ChaosMockAdapter {

    private final long latencyMs;
    private final double errorRate;

    public DevChaosMockAdapter(
        @Value("${chaos.latency-ms:0}") long latencyMs,
        @Value("${chaos.error-rate:0.0}") double errorRate
    ) {
        this.latencyMs = latencyMs;
        this.errorRate = errorRate;
    }

    @Override
    public void applyChaos() {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (errorRate > 0) {
            if (ThreadLocalRandom.current().nextDouble() < errorRate) {
                throw new RuntimeException("Simulated chaos error");
            }
        }
    }
}
