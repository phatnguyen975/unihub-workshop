package com.unihub.workshop.shared.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class RealtimeMetricsTest {

    private MeterRegistry meterRegistry;
    private SseEmitterRegistry registry;
    private RealtimeMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registry = new SseEmitterRegistry();
        metrics = new RealtimeMetrics(meterRegistry, registry);
    }

    @Test
    @DisplayName(
        "active_connections gauge should track in-memory registry connections dynamically"
    )
    void activeConnectionsGauge_shouldReflectRegistryCount() {
        Gauge gauge = meterRegistry
            .find("realtime.sse.active_connections")
            .gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);

        UUID userId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(60_000L);
        registry.register(userId, emitter);

        assertThat(gauge.value()).isEqualTo(1.0);

        registry.remove(userId, emitter);
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName(
        "incrementEventsPushed should increment counter tagged with event_type"
    )
    void incrementEventsPushed_shouldIncrementTaggedCounter() {
        metrics.incrementEventsPushed("notification.new");
        metrics.incrementEventsPushed("notification.new");
        metrics.incrementEventsPushed("attendance.updated");

        Counter notifCounter = meterRegistry
            .find("realtime.events.pushed.total")
            .tag("event_type", "notification.new")
            .counter();
        assertThat(notifCounter).isNotNull();
        assertThat(notifCounter.count()).isEqualTo(2.0);

        Counter attendCounter = meterRegistry
            .find("realtime.events.pushed.total")
            .tag("event_type", "attendance.updated")
            .counter();
        assertThat(attendCounter).isNotNull();
        assertThat(attendCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "incrementDisconnects should increment counter tagged with reason"
    )
    void incrementDisconnects_shouldIncrementTaggedCounter() {
        metrics.incrementDisconnects("client_abort");
        metrics.incrementDisconnects("timeout");
        metrics.incrementDisconnects("error");
        metrics.incrementDisconnects("error");

        Counter abortCounter = meterRegistry
            .find("realtime.sse.disconnects.total")
            .tag("reason", "client_abort")
            .counter();
        assertThat(abortCounter).isNotNull();
        assertThat(abortCounter.count()).isEqualTo(1.0);

        Counter timeoutCounter = meterRegistry
            .find("realtime.sse.disconnects.total")
            .tag("reason", "timeout")
            .counter();
        assertThat(timeoutCounter).isNotNull();
        assertThat(timeoutCounter.count()).isEqualTo(1.0);

        Counter errorCounter = meterRegistry
            .find("realtime.sse.disconnects.total")
            .tag("reason", "error")
            .counter();
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(2.0);
    }
}
