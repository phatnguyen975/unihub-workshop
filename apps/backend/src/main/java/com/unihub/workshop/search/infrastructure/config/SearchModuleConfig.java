package com.unihub.workshop.search.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchModuleConfig {

    public static final String CIRCUIT_BREAKER_NAME = "elasticsearch";

    @Bean
    public CircuitBreaker elasticsearchCircuitBreaker(
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    }
}
