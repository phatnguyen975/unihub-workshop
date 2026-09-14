package com.unihub.workshop.shared.config;

import com.unihub.workshop.shared.web.RequestLoggingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web-layer configuration for the Backend runtime.
 *
 * <p>
 * Registers cross-cutting Servlet filters that must apply to every request
 * regardless of which handler ultimately processes it.
 */
@Configuration
public class WebConfig {

    /**
     * Registers {@link RequestLoggingFilter} at {@link Ordered#HIGHEST_PRECEDENCE}
     * so it fires before Spring Security's filter chain and any other registered
     * filter.
     *
     * <p>
     * Mapping to {@code /*} ensures coverage of every inbound request — including
     * Actuator endpoints, API endpoints, and any path that Spring Security may
     * reject before it reaches a controller. This guarantees that unauthenticated
     * or unauthorized requests still produce a log entry.
     *
     * @param nodeId the identifier of this backend node, sourced from
     *               {@code management.metrics.tags.host} or {@code HOSTNAME} environment variable
     *               (falls back to {@code "unknown"} if absent)
     * @return the configured filter registration
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
        @Value(
            "${management.metrics.tags.host:${HOSTNAME:unknown}}"
        ) String nodeId
    ) {
        FilterRegistrationBean<RequestLoggingFilter> registration =
            new FilterRegistrationBean<>();
        registration.setFilter(new RequestLoggingFilter(nodeId));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("requestLoggingFilter");
        return registration;
    }
}
