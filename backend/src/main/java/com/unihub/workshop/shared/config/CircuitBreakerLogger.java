package com.unihub.workshop.shared.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CircuitBreakerLogger {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerLogger(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void attachListeners() {
        circuitBreakerRegistry.circuitBreaker("stripePayment").getEventPublisher()
                .onStateTransition(event -> {
                    String stateTransition = event.getStateTransition().toString();
                    if (stateTransition.contains("to OPEN")) {
                        log.error("🚨 CIRCUIT BREAKER TRIPPED! State changed to OPEN. Stripe API calls will be blocked.");
                    } else if (stateTransition.contains("to HALF_OPEN")) {
                        log.warn("⚠️ CIRCUIT BREAKER HALF_OPEN. Testing Stripe API availability...");
                    } else if (stateTransition.contains("to CLOSED")) {
                        log.info("✅ CIRCUIT BREAKER RECOVERED! State changed to CLOSED. Resuming normal operations.");
                    } else {
                        log.info("Circuit Breaker State Transition: {}", stateTransition);
                    }
                });
    }
}
