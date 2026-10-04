package com.unihub.workshop.search.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SearchModuleConfig {

    public static final String CIRCUIT_BREAKER_NAME = "elasticsearch";

    @Bean
    public CircuitBreaker elasticsearchCircuitBreaker(
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    }

    @Bean(name = "searchTaskExecutor")
    public Executor searchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("search-swr-");
        executor.initialize();
        return executor;
    }
}
