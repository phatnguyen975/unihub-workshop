package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.application.ports.out.RealtimeMetricsPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics component exposing real-time SSE observability:
 * - realtime.sse.active_connections (Gauge)
 * - realtime.events.pushed.total (Counter tagged with event_type)
 * - realtime.sse.disconnects.total (Counter tagged with reason)
 */
@Component
public class RealtimeMetrics implements RealtimeMetricsPort {

    private final MeterRegistry meterRegistry;

    public RealtimeMetrics(MeterRegistry meterRegistry, SseEmitterRegistry registry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null && registry != null) {
            Gauge.builder("realtime.sse.active_connections", registry, SseEmitterRegistry::getActiveConnectionCount)
                    .description("Number of active SSE connections currently open in memory")
                    .register(meterRegistry);
        }
    }

    public void incrementEventsPushed(String eventType) {
        if (meterRegistry != null) {
            meterRegistry
                    .counter("realtime.events.pushed.total", "event_type", eventType != null ? eventType : "unknown")
                    .increment();
        }
    }

    public void incrementDisconnects(String reason) {
        if (meterRegistry != null) {
            meterRegistry.counter("realtime.sse.disconnects.total", "reason", reason != null ? reason : "unknown")
                    .increment();
        }
    }
}
