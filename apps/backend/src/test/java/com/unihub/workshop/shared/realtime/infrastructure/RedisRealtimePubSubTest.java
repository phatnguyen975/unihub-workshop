package com.unihub.workshop.shared.realtime.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.realtime.api.RealtimeEvent;
import com.unihub.workshop.shared.realtime.api.RealtimeEventType;
import com.unihub.workshop.shared.realtime.application.ports.out.WorkshopAuthorizationPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class RedisRealtimePubSubTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WorkshopAuthorizationPort workshopAuthorizationPort;

    private ObjectMapper objectMapper;
    private SseEmitterRegistry registry;
    private RedisRealtimePublisherAdapter publisherAdapter;
    private RedisRealtimeSubscriber subscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        registry = new SseEmitterRegistry();
        publisherAdapter = new RedisRealtimePublisherAdapter(redisTemplate);
        subscriber = new RedisRealtimeSubscriber(
            registry,
            workshopAuthorizationPort,
            null
        );
    }

    @Test
    @DisplayName(
        "publishToUser should publish JSON payload to unihub:stream:user:{userId} channel"
    )
    void publishToUser_shouldPublishJsonToUserChannel() {
        UUID userId = UUID.randomUUID();
        RealtimeEvent<Map<String, String>> event = RealtimeEvent.of(
            RealtimeEventType.NOTIFICATION_NEW,
            Map.of("title", "Test Notification")
        );

        publisherAdapter.publishToUser(userId, event);

        String expectedChannel = "unihub:stream:user:" + userId;
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(
            String.class
        );

        verify(redisTemplate).convertAndSend(
            eq(expectedChannel),
            messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue()).contains("notification.new");
        assertThat(messageCaptor.getValue()).contains("Test Notification");
    }

    @Test
    @DisplayName(
        "publishToWorkshop should publish JSON payload to unihub:stream:workshop:{workshopId} channel"
    )
    void publishToWorkshop_shouldPublishJsonToWorkshopChannel() {
        UUID workshopId = UUID.randomUUID();
        RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(
            RealtimeEventType.ATTENDANCE_UPDATED,
            Map.of("checkedInCount", 42, "capacity", 100)
        );

        publisherAdapter.publishToWorkshop(workshopId, event);

        String expectedChannel = "unihub:stream:workshop:" + workshopId;
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(
            String.class
        );

        verify(redisTemplate).convertAndSend(
            eq(expectedChannel),
            messageCaptor.capture()
        );
        assertThat(messageCaptor.getValue()).contains("attendance.updated");
        assertThat(messageCaptor.getValue()).contains("42");
    }

    @Test
    @DisplayName(
        "subscriber should deliver user-targeted event to all registered emitters for that user"
    )
    void subscriber_shouldDeliverUserEventToMatchingEmitters()
        throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);

        registry.register(userId, emitter1);
        registry.register(userId, emitter2);

        UUID eventId = UUID.randomUUID();
        RealtimeEvent<Map<String, String>> event = new RealtimeEvent<>(
            eventId,
            RealtimeEventType.NOTIFICATION_NEW.getValue(),
            Instant.now(),
            Map.of("title", "Hello User")
        );

        String channel = "unihub:stream:user:" + userId;
        String jsonPayload = objectMapper.writeValueAsString(event);
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(emitter1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName(
        "subscriber should deliver workshop event to authorized users using single batch authorization query"
    )
    void subscriber_shouldDeliverWorkshopEventToAuthorizedUsersOnly()
        throws Exception {
        UUID workshopId = UUID.randomUUID();
        UUID authorizedOrgUserId = UUID.randomUUID();
        UUID authorizedStaffUserId = UUID.randomUUID();
        UUID unauthorizedUserId = UUID.randomUUID();

        SseEmitter orgEmitter = mock(SseEmitter.class);
        SseEmitter staffEmitter = mock(SseEmitter.class);
        SseEmitter unauthorizedEmitter = mock(SseEmitter.class);

        registry.register(authorizedOrgUserId, orgEmitter);
        registry.register(authorizedStaffUserId, staffEmitter);
        registry.register(unauthorizedUserId, unauthorizedEmitter);

        when(
            workshopAuthorizationPort.findAuthorizedUserIds(workshopId)
        ).thenReturn(Set.of(authorizedOrgUserId, authorizedStaffUserId));

        RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(
            RealtimeEventType.REGISTRATION_UPDATED,
            Map.of("confirmedCount", 10)
        );

        String channel = "unihub:stream:workshop:" + workshopId;
        String jsonPayload = objectMapper.writeValueAsString(event);
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        // Verify single query called with workshopId
        verify(workshopAuthorizationPort, times(1)).findAuthorizedUserIds(
            workshopId
        );
        verify(orgEmitter, times(1)).send(
            any(SseEmitter.SseEventBuilder.class)
        );
        verify(staffEmitter, times(1)).send(
            any(SseEmitter.SseEventBuilder.class)
        );
        verifyNoInteractions(unauthorizedEmitter);
    }

    @Test
    @DisplayName(
        "subscriber should fail-closed and not deliver workshop events when workshopAuthorizationPort is null"
    )
    void subscriber_shouldFailClosedWhenAuthorizationPortIsNull()
        throws Exception {
        RedisRealtimeSubscriber subscriberWithoutAuthPort =
            new RedisRealtimeSubscriber(registry, null, null);

        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(userId, emitter);

        RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(
            RealtimeEventType.REGISTRATION_UPDATED,
            Map.of("confirmedCount", 10)
        );

        String channel = "unihub:stream:workshop:" + workshopId;
        String jsonPayload = objectMapper.writeValueAsString(event);
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriberWithoutAuthPort.onMessage(message, null);

        // Security requirement: fail-closed, no event delivered
        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName(
        "subscriber should discard workshop event cleanly when no users are authorized"
    )
    void subscriber_shouldDiscardCleanlyWhenNoUsersAuthorized()
        throws Exception {
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(userId, emitter);

        when(
            workshopAuthorizationPort.findAuthorizedUserIds(workshopId)
        ).thenReturn(Collections.emptySet());

        RealtimeEvent<Map<String, Object>> event = RealtimeEvent.of(
            RealtimeEventType.REGISTRATION_UPDATED,
            Map.of("confirmedCount", 10)
        );

        String channel = "unihub:stream:workshop:" + workshopId;
        String jsonPayload = objectMapper.writeValueAsString(event);
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(workshopAuthorizationPort, times(1)).findAuthorizedUserIds(
            workshopId
        );
        verifyNoInteractions(emitter);
    }

    @Test
    @DisplayName(
        "subscriber should discard message safely when no active emitters exist on this node"
    )
    void subscriber_shouldDiscardSafelyWhenNoActiveEmitters() {
        UUID userId = UUID.randomUUID();
        String channel = "unihub:stream:user:" + userId;
        String jsonPayload =
            "{\"eventId\":\"111\",\"eventType\":\"notification.new\",\"payload\":{}}";
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        assertThatCode(() ->
            subscriber.onMessage(message, null)
        ).doesNotThrowAnyException();
        assertThat(registry.getActiveConnectionCount()).isZero();
    }

    @Test
    @DisplayName(
        "subscriber should safely evict broken emitter from registry upon IOException"
    )
    void subscriber_shouldEvictBrokenEmitterUponIOException() throws Exception {
        UUID userId = UUID.randomUUID();
        SseEmitter healthyEmitter = mock(SseEmitter.class);
        SseEmitter brokenEmitter = mock(SseEmitter.class);

        doThrow(new IOException("Broken pipe"))
            .when(brokenEmitter)
            .send(any(SseEmitter.SseEventBuilder.class));

        registry.register(userId, healthyEmitter);
        registry.register(userId, brokenEmitter);
        assertThat(registry.getActiveConnectionCount()).isEqualTo(2);

        String channel = "unihub:stream:user:" + userId;
        String jsonPayload =
            "{\"eventId\":\"222\",\"eventType\":\"notification.new\",\"payload\":{}}";
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(healthyEmitter, times(1)).send(
            any(SseEmitter.SseEventBuilder.class)
        );
        verify(brokenEmitter, times(1)).send(
            any(SseEmitter.SseEventBuilder.class)
        );

        // Broken emitter must be removed from registry
        assertThat(registry.getActiveConnectionCount()).isEqualTo(1);
        assertThat(registry.getEmitters(userId)).containsExactly(
            healthyEmitter
        );
    }

    @Test
    @DisplayName(
        "subscriber should record metrics for events pushed and error disconnects"
    )
    void subscriber_shouldRecordMetricsOnEventPushedAndOnError()
        throws Exception {
        RealtimeMetrics metricsMock = mock(RealtimeMetrics.class);
        RedisRealtimeSubscriber subscriberWithMetrics =
            new RedisRealtimeSubscriber(
                registry,
                workshopAuthorizationPort,
                metricsMock
            );

        UUID userId = UUID.randomUUID();
        SseEmitter healthyEmitter = mock(SseEmitter.class);
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection reset by peer"))
            .when(brokenEmitter)
            .send(any(SseEmitter.SseEventBuilder.class));

        registry.register(userId, healthyEmitter);
        registry.register(userId, brokenEmitter);

        String channel = "unihub:stream:user:" + userId;
        String jsonPayload =
            "{\"eventId\":\"333\",\"eventType\":\"notification.new\",\"payload\":{}}";
        Message message = new DefaultMessage(
            channel.getBytes(StandardCharsets.UTF_8),
            jsonPayload.getBytes(StandardCharsets.UTF_8)
        );

        subscriberWithMetrics.onMessage(message, null);

        verify(metricsMock, times(1)).incrementEventsPushed("notification.new");
        verify(metricsMock, times(1)).incrementDisconnects("error");
    }
}
