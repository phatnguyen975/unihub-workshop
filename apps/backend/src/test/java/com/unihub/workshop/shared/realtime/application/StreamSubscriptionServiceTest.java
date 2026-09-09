package com.unihub.workshop.shared.realtime.application;

import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.realtime.application.ports.out.UserStatusPort;
import com.unihub.workshop.shared.realtime.domain.SseEmitterRegistry;
import com.unihub.workshop.shared.realtime.infrastructure.RealtimeMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("StreamSubscriptionService Unit Tests")
class StreamSubscriptionServiceTest {

    private SseEmitterRegistry registry;
    private UserStatusPort userStatusPort;
    private StreamSubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        registry = mock(SseEmitterRegistry.class);
        userStatusPort = mock(UserStatusPort.class);
        subscriptionService = new StreamSubscriptionService(registry, userStatusPort, null, 1_800_000L);
    }

    @Test
    @DisplayName("should create and register SseEmitter when user is active")
    void shouldCreateAndRegisterEmitter_whenUserIsActive() {
        UUID userId = UUID.randomUUID();
        when(userStatusPort.isUserActive(userId)).thenReturn(true);

        SseEmitter emitter = subscriptionService.subscribe(userId, "STUDENT", null);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(1_800_000L);
        verify(registry).register(userId, emitter);
    }

    @Test
    @DisplayName("should throw ForbiddenException when user is inactive or banned")
    void shouldThrowForbiddenException_whenUserIsInactive() {
        UUID userId = UUID.randomUUID();
        when(userStatusPort.isUserActive(userId)).thenReturn(false);

        assertThatThrownBy(() -> subscriptionService.subscribe(userId, "STUDENT", null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User account is inactive or banned")
                .extracting("type")
                .isEqualTo("urn:unihub:error:account-inactive");

        verifyNoInteractions(registry);
    }

    @Test
    @DisplayName("should allow subscription when userStatusPort is null (fallback mode)")
    void shouldAllowSubscription_whenUserStatusPortIsNull() {
        StreamSubscriptionService serviceWithoutPort = new StreamSubscriptionService(registry, null, null, 60_000L);
        UUID userId = UUID.randomUUID();

        SseEmitter emitter = serviceWithoutPort.subscribe(userId, "ORGANIZER", "org-1");

        assertThat(emitter).isNotNull();
        verify(registry).register(userId, emitter);
    }

    @Test
    @DisplayName("handleDisconnect should remove emitter from registry and record disconnect metrics")
    void handleDisconnect_shouldRemoveFromRegistryAndRecordMetrics() {
        RealtimeMetrics metricsMock = mock(RealtimeMetrics.class);
        StreamSubscriptionService serviceWithMetrics = new StreamSubscriptionService(registry, userStatusPort,
                metricsMock, 60_000L);
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(60_000L);

        serviceWithMetrics.handleDisconnect(userId, emitter, "client_abort");
        verify(registry).remove(userId, emitter);
        verify(metricsMock).incrementDisconnects("client_abort");

        serviceWithMetrics.handleDisconnect(userId, emitter, "timeout");
        verify(metricsMock).incrementDisconnects("timeout");

        serviceWithMetrics.handleDisconnect(userId, emitter, "error");
        verify(metricsMock).incrementDisconnects("error");
    }
}
