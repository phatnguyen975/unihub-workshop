package com.unihub.workshop.workers.notifications.outbound.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationAmqpConfig {

    @Value("${app.notifications.queue:q.notifications}")
    private String queueName;

    @Value("${app.notifications.exchange:unihub.events}")
    private String exchangeName;

    @Value("${app.notifications.dlx:unihub.dlx}")
    private String deadLetterExchange;

    @Value("${app.notifications.dlq-routing-key:dlq}")
    private String deadLetterRoutingKey;

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(queueName)
            .deadLetterExchange(deadLetterExchange)
            .deadLetterRoutingKey(deadLetterRoutingKey)
            .build();
    }

    @Bean
    public DirectExchange notificationsEventsExchange() {
        return ExchangeBuilder.directExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean
    public Binding notificationsBinding(
        Queue notificationsQueue,
        DirectExchange notificationsEventsExchange
    ) {
        return BindingBuilder.bind(notificationsQueue)
            .to(notificationsEventsExchange)
            .with("notifications");
    }

    @Bean
    public Binding notificationsRescheduledBinding(
        Queue notificationsQueue,
        DirectExchange notificationsEventsExchange
    ) {
        return BindingBuilder.bind(notificationsQueue)
            .to(notificationsEventsExchange)
            .with("workshop.rescheduled");
    }

    @Bean
    public Binding notificationsCanceledBinding(
        Queue notificationsQueue,
        DirectExchange notificationsEventsExchange
    ) {
        return BindingBuilder.bind(notificationsQueue)
            .to(notificationsEventsExchange)
            .with("workshop.canceled");
    }
}
