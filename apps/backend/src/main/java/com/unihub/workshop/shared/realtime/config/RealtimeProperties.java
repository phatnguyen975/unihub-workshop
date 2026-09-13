package com.unihub.workshop.shared.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for real-time SSE streaming and keep-alive cadence.
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
    @DefaultValue("15") int heartbeatIntervalSeconds,
    @DefaultValue("1800000") long streamTimeoutMs
) {
    public RealtimeProperties {
        if (heartbeatIntervalSeconds <= 0) {
            heartbeatIntervalSeconds = 15;
        }
        if (streamTimeoutMs <= 0) {
            streamTimeoutMs = 1800000L;
        }
    }
}
