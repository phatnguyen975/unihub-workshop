package com.unihub.workshop.shared.config;

import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(properties.allowedOriginPatterns());

        config.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        config.setAllowedHeaders(
            Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Correlation-ID",
                "Idempotency-Key",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Retry-After"
            )
        );

        config.setExposedHeaders(
            Arrays.asList(
                "X-Correlation-ID",
                "Idempotency-Key",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Retry-After"
            )
        );

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
