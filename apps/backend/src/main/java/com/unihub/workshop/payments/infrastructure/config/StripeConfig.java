package com.unihub.workshop.payments.infrastructure.config;

import com.stripe.StripeClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    public static final String STRIPE_CIRCUIT_BREAKER_NAME = "stripe-payment";

    @Bean
    @ConditionalOnMissingBean
    public StripeClient stripeClient(
        @Value("${stripe.secret-key:sk_test_dummy}") String secretKey
    ) {
        return new StripeClient(secretKey);
    }

    @Bean(name = "stripeCircuitBreaker")
    @ConditionalOnMissingBean(name = "stripeCircuitBreaker")
    public CircuitBreaker stripeCircuitBreaker(
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(
            STRIPE_CIRCUIT_BREAKER_NAME
        );
    }
}
