package com.unihub.workshop.shared.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for the Backend runtime.
 *
 * <p>
 * Registers {@code application} and {@code host} as common tags on the shared
 * {@link MeterRegistry} so that every metric automatically carries these
 * labels, with {@code application="unihub-backend"} distinct from the workers
 * runtime.
 */
@Configuration
public class ObservabilityConfig {

    private final String applicationName;
    private final String host;

    /**
     * Constructor used by Spring (property injection) and directly by unit tests.
     *
     * @param applicationName value of {@code spring.application.name}
     * @param host            value of {@code management.metrics.tags.host}
     */
    public ObservabilityConfig(
        @Value("${spring.application.name}") String applicationName,
        @Value(
            "${management.metrics.tags.host:${HOSTNAME:unknown}}"
        ) String host
    ) {
        this.applicationName = applicationName;
        this.host = host;
    }

    /**
     * Registers {@code application} and {@code host} as common tags on every meter.
     *
     * <p>
     * Exposed as a {@link MeterRegistryCustomizer} bean so that Spring Boot's
     * auto-configuration applies these tags before any meter is created, including
     * the auto-configured JVM, HikariCP, Tomcat, and Logback binders.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> observabilityCommonTags() {
        return this::configureCommonTags;
    }

    /**
     * Applies common tags to the given registry.
     *
     * <p>
     * Separated from {@link #observabilityCommonTags()} to allow unit tests to call
     * this method directly on a
     * {@link io.micrometer.core.instrument.simple.SimpleMeterRegistry}
     * without requiring a Spring application context.
     *
     * @param registry the registry to configure
     */
    void configureCommonTags(MeterRegistry registry) {
        registry
            .config()
            .commonTags("application", applicationName, "host", host);
    }
}
