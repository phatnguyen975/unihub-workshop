package com.unihub.workshop.workers.notifications.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationEventDispatcher dispatcher;

    @Mock
    private Channel channel;

    private NotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationListener(dispatcher, new ObjectMapper());
    }

    @Test
    @DisplayName("Should acknowledge message when dispatched successfully")
    void shouldAcknowledgeMessageOnSuccessfulDispatch() throws Exception {
        byte[] body =
            "{\"userId\":\"123e4567-e89b-12d3-a456-426614174000\"}".getBytes();
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        props.setHeader("__TypeId__", "OtpRequestedEvent");
        props.setReceivedRoutingKey("notifications");
        Message message = new Message(body, props);

        listener.onMessage(message, channel);

        verify(dispatcher).dispatch(eq("OtpRequestedEvent"), any());
        verify(channel).basicAck(42L, false);
    }

    @Test
    @DisplayName(
        "Should reject message without requeue (DLQ) when type is unknown or body malformed"
    )
    void shouldNackWithoutRequeueOnMalformedJsonOrUnmappedType()
        throws Exception {
        byte[] body = "{\"invalid\":\"data\"}".getBytes();
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        props.setHeader("__TypeId__", "UnknownEvent");
        props.setReceivedRoutingKey("notifications");
        Message message = new Message(body, props);

        doThrow(new IllegalArgumentException("No handler"))
            .when(dispatcher)
            .dispatch(eq("UnknownEvent"), any());

        listener.onMessage(message, channel);

        verify(channel).basicNack(42L, false, false);
    }

    @Test
    @DisplayName(
        "Should reject message with requeue (retry) when transient error occurs"
    )
    void shouldNackWithRequeueOnTransientException() throws Exception {
        byte[] body = "{\"userId\":\"123\"}".getBytes();
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        props.setHeader("__TypeId__", "OtpRequestedEvent");
        props.setReceivedRoutingKey("notifications");
        Message message = new Message(body, props);

        doThrow(new RuntimeException("Database connection timeout"))
            .when(dispatcher)
            .dispatch(eq("OtpRequestedEvent"), any());

        listener.onMessage(message, channel);

        verify(channel).basicNack(42L, false, true);
    }
}
