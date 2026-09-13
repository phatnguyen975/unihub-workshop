package com.unihub.workshop.shared.resilience;

import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure configuration for Resilience4j circuit breakers and metrics.
 *
 * Note: The actual circuit breaker defaults (thresholds, window sizes, etc.)
 * and Micrometer bindings are driven via auto-configuration from application.yml.
 * This class serves as the module-level anchor for any custom Resilience4j
 * beans or advanced programmatic configuration that may be added in the future.
 */
@Configuration
public class ResilienceConfig {}
