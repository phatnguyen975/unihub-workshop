package com.unihub.workshop.workers.search_sync.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.in.SyncWorkshopIndex;
import com.unihub.workshop.workers.search_sync.inbound.mapper.SearchSyncEventMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SearchSyncListenerTest {

    private SyncWorkshopIndex syncWorkshopIndex;
    private SearchSyncEventMapper mapper;
    private ObjectMapper objectMapper;
    private Channel channel;
    private SearchSyncListener listener;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final long DELIVERY_TAG = 12345L;

    @BeforeEach
    void setUp() {
        syncWorkshopIndex = mock(SyncWorkshopIndex.class);
        mapper = new SearchSyncEventMapper();
        objectMapper = new ObjectMapper();
        channel = mock(Channel.class);
        listener = new SearchSyncListener(
            syncWorkshopIndex,
            mapper,
            objectMapper
        );
    }

    private Message createAmqpMessage(
        String json,
        String routingKey,
        String typeId
    ) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setReceivedRoutingKey(routingKey);
        if (typeId != null) {
            properties.setHeader("__TypeId__", typeId);
        }
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    @DisplayName("Successfully process WorkshopPublishedEvent and ACK")
    void shouldProcessPublishedEventAndAck() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "organizationId": "%s",
            "title": "Cloud Native Architecture",
            "slug": "cloud-native-architecture",
            "capacity": 100,
            "startsAt": "2026-11-20T10:00:00Z",
            "endsAt": "2026-11-20T14:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh",
            "speakers": [
                {
                    "id": "%s",
                    "name": "Grace Hopper",
                    "email": "grace@navy.mil"
                }
            ]
        }
        """.formatted(WORKSHOP_ID, ORG_ID, UUID.randomUUID());

        Message message = createAmqpMessage(
            json,
            "search",
            "com.unihub.workshop.shared.events.WorkshopPublishedEvent"
        );

        listener.onMessage(message, channel);

        verify(syncWorkshopIndex).handleWorkshopPublished(
            any(WorkshopDocument.class)
        );
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Successfully process WorkshopUpdatedEvent and ACK")
    void shouldProcessUpdatedEventAndAck() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "organizationId": "%s",
            "title": "Cloud Native Architecture Updated",
            "slug": "cloud-native-architecture",
            "capacity": 120,
            "updatedAt": "2026-10-01T12:00:00Z"
        }
        """.formatted(WORKSHOP_ID, ORG_ID);

        Message message = createAmqpMessage(
            json,
            "search",
            "com.unihub.workshop.shared.events.WorkshopUpdatedEvent"
        );

        listener.onMessage(message, channel);

        verify(syncWorkshopIndex).handleWorkshopUpdated(
            any(WorkshopDocument.class)
        );
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Successfully process WorkshopRescheduledEvent and ACK")
    void shouldProcessRescheduledEventAndAck() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "organizationId": "%s",
            "newStartsAt": "2026-11-25T10:00:00Z",
            "newEndsAt": "2026-11-25T14:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh"
        }
        """.formatted(WORKSHOP_ID, ORG_ID);

        Message message = createAmqpMessage(
            json,
            "workshop.rescheduled",
            "com.unihub.workshop.shared.events.WorkshopRescheduledEvent"
        );

        listener.onMessage(message, channel);

        verify(syncWorkshopIndex).handleWorkshopRescheduled(
            eq(WORKSHOP_ID),
            eq(Instant.parse("2026-11-25T10:00:00Z")),
            eq(Instant.parse("2026-11-25T14:00:00Z")),
            eq("Asia/Ho_Chi_Minh")
        );
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Successfully process WorkshopCompletedEvent and ACK")
    void shouldProcessCompletedEventAndAck() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "completedAt": "2026-11-20T14:00:00Z"
        }
        """.formatted(WORKSHOP_ID);

        Message message = createAmqpMessage(
            json,
            "workshop.completed",
            "com.unihub.workshop.shared.events.WorkshopCompletedEvent"
        );

        listener.onMessage(message, channel);

        verify(syncWorkshopIndex).handleWorkshopCompleted(WORKSHOP_ID);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("Successfully process WorkshopCanceledEvent and ACK")
    void shouldProcessCanceledEventAndAck() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "canceledAt": "2026-10-02T08:00:00Z"
        }
        """.formatted(WORKSHOP_ID);

        Message message = createAmqpMessage(
            json,
            "workshop.canceled",
            "com.unihub.workshop.shared.events.WorkshopCanceledEvent"
        );

        listener.onMessage(message, channel);

        verify(syncWorkshopIndex).handleWorkshopCanceled(WORKSHOP_ID);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("NACK without requeue when message JSON is malformed")
    void shouldNackWithoutRequeueOnMalformedJson() throws IOException {
        Message message = createAmqpMessage("not-a-valid-json", "search", null);

        listener.onMessage(message, channel);

        verifyNoInteractions(syncWorkshopIndex);
        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    @DisplayName("NACK without requeue on unknown event routing key / type")
    void shouldNackWithoutRequeueOnUnknownEvent() throws IOException {
        Message message = createAmqpMessage(
            "{\"some\":\"field\"}",
            "unknown.routing.key",
            "UnknownEvent"
        );

        listener.onMessage(message, channel);

        verifyNoInteractions(syncWorkshopIndex);
        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    @DisplayName(
        "NACK with requeue when sync operation throws transient exception"
    )
    void shouldNackWithRequeueOnTransientException() throws IOException {
        String json = """
        {
            "workshopId": "%s",
            "canceledAt": "2026-10-02T08:00:00Z"
        }
        """.formatted(WORKSHOP_ID);

        Message message = createAmqpMessage(
            json,
            "workshop.canceled",
            "com.unihub.workshop.shared.events.WorkshopCanceledEvent"
        );

        doThrow(new RuntimeException("Elasticsearch connection timed out"))
            .when(syncWorkshopIndex)
            .handleWorkshopCanceled(WORKSHOP_ID);

        listener.onMessage(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
    }
}
