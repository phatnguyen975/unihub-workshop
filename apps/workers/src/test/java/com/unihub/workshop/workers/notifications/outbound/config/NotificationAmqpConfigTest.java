package com.unihub.workshop.workers.notifications.outbound.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

class NotificationAmqpConfigTest {

    private static final String QUEUE_NAME = "q.notifications";
    private static final String EXCHANGE_NAME = "unihub.events";
    private static final String DLX = "unihub.dlx";
    private static final String DLQ_KEY = "dlq";

    private final NotificationAmqpConfig config = new NotificationAmqpConfig();

    /**
     * Builds a Queue directly (bypassing Spring container) so that tests
     * can invoke bean methods that accept Queue/DirectExchange as parameters
     * without needing a Spring ApplicationContext.
     */
    private Queue buildQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(DLQ_KEY)
            .build();
    }

    private DirectExchange buildExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_NAME)
            .durable(true)
            .build();
    }

    @Test
    @DisplayName(
        "Should create binding for registration.canceled routing key to q.notifications"
    )
    void shouldCreateBindingForRegistrationCanceled() {
        Binding binding = config.notificationsRegistrationCanceledBinding(
            buildQueue(),
            buildExchange()
        );

        assertNotNull(binding);
        assertEquals(QUEUE_NAME, binding.getDestination());
        assertEquals(EXCHANGE_NAME, binding.getExchange());
        assertEquals("registration.canceled", binding.getRoutingKey());
    }

    @Test
    @DisplayName(
        "Should create binding for workshop.registered routing key to q.notifications"
    )
    void shouldCreateBindingForWorkshopRegistered() {
        Binding binding = config.notificationsWorkshopRegisteredBinding(
            buildQueue(),
            buildExchange()
        );

        assertNotNull(binding);
        assertEquals(QUEUE_NAME, binding.getDestination());
        assertEquals(EXCHANGE_NAME, binding.getExchange());
        assertEquals("workshop.registered", binding.getRoutingKey());
    }

    @Test
    @DisplayName(
        "Should create binding for workshop.canceled routing key to q.notifications"
    )
    void shouldCreateBindingForWorkshopCanceled() {
        Binding binding = config.notificationsCanceledBinding(
            buildQueue(),
            buildExchange()
        );

        assertNotNull(binding);
        assertEquals(QUEUE_NAME, binding.getDestination());
        assertEquals(EXCHANGE_NAME, binding.getExchange());
        assertEquals("workshop.canceled", binding.getRoutingKey());
    }

    @Test
    @DisplayName(
        "Should create binding for workshop.rescheduled routing key to q.notifications"
    )
    void shouldCreateBindingForWorkshopRescheduled() {
        Binding binding = config.notificationsRescheduledBinding(
            buildQueue(),
            buildExchange()
        );

        assertNotNull(binding);
        assertEquals(QUEUE_NAME, binding.getDestination());
        assertEquals(EXCHANGE_NAME, binding.getExchange());
        assertEquals("workshop.rescheduled", binding.getRoutingKey());
    }
}
