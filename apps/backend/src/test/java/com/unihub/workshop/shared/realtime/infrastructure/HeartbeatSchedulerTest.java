package com.unihub.workshop.shared.realtime.infrastructure;

import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatScheduler Unit Tests")
class HeartbeatSchedulerTest {

    private SseEmitterRegistry registry;
    private HeartbeatScheduler scheduler;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
        scheduler = new HeartbeatScheduler(registry, null);
    }

    @Test
    @DisplayName("should emit ping comment to all registered active emitters")
    void sendHeartbeat_shouldEmitCommentToAllActiveEmitters() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);
        SseEmitter emitter3 = mock(SseEmitter.class);

        registry.register(user1, emitter1);
        registry.register(user1, emitter2);
        registry.register(user2, emitter3);

        assertThat(registry.getActiveConnectionCount()).isEqualTo(3);

        scheduler.sendHeartbeat();

        verify(emitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter3, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(registry.getActiveConnectionCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("should safely evict broken emitter from registry upon IOException without throwing exception")
    void sendHeartbeat_shouldEvictBrokenEmitter_withoutThrowingException() throws Exception {
        UUID user1 = UUID.randomUUID();
        SseEmitter healthyEmitter = mock(SseEmitter.class);
        SseEmitter brokenEmitter = mock(SseEmitter.class);

        doThrow(new IOException("Connection reset by peer"))
                .when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        registry.register(user1, healthyEmitter);
        registry.register(user1, brokenEmitter);

        assertThat(registry.getActiveConnectionCount()).isEqualTo(2);

        assertThatCode(() -> scheduler.sendHeartbeat()).doesNotThrowAnyException();

        verify(healthyEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(brokenEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        // Broken emitter should be evicted, healthy remains
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);
        assertThat(registry.getEmitters(user1)).containsExactly(healthyEmitter);
    }

    @Test
    @DisplayName("should be annotated with @Scheduled fixedDelay and 15s default interval")
    void sendHeartbeat_isAnnotatedWithScheduled() throws NoSuchMethodException {
        Method method = HeartbeatScheduler.class.getDeclaredMethod("sendHeartbeat");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${app.realtime.heartbeat-interval-seconds:15}");
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
    }
}
