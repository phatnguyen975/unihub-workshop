package com.unihub.workshop.shared.resilience;

/**
 * Base interface for injecting configurable artificial latency and simulated
 * failure rates. Used exclusively for testing Resilience4j circuit breakers in
 * the `dev` profile.
 */
public interface ChaosMockAdapter {

    /**
     * Applies simulated chaos (latency and/or exceptions) based on configuration.
     * Should be called by downstream features wrapping external service calls.
     */
    void applyChaos();
}
