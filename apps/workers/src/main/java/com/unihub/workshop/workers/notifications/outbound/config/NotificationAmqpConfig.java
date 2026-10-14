package com.unihub.workshop.workers.notifications.outbound.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
    public ObjectMapper objectMapper() {
        SimpleModule flexibleInstantModule = new SimpleModule();
        flexibleInstantModule.addDeserializer(
            Instant.class,
            new JsonDeserializer<Instant>() {
                @Override
                public Instant deserialize(
                    JsonParser p,
                    DeserializationContext ctxt
                ) throws IOException {
                    String text = p.getText();
                    if (text == null || text.isBlank()) {
                        return null;
                    }
                    text = text.trim();

                    // Try parsing numeric epoch (int, float, scientific notation)
                    if (text.matches("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")) {
                        try {
                            double sec = Double.parseDouble(text);
                            long val = (long) sec;
                            if (val > 100_000_000_000L) {
                                return Instant.ofEpochMilli(val);
                            }
                            long seconds = (long) sec;
                            int nanos = (int) Math.round(
                                (sec - seconds) * 1_000_000_000
                            );
                            return Instant.ofEpochSecond(seconds, nanos);
                        } catch (NumberFormatException ignored) {}
                    }

                    if (text.length() >= 19 && text.charAt(10) == ' ') {
                        text = text.substring(0, 10) + 'T' + text.substring(11);
                    }

                    try {
                        return Instant.parse(text);
                    } catch (DateTimeParseException ignored) {}

                    try {
                        return OffsetDateTime.parse(text).toInstant();
                    } catch (DateTimeParseException ignored) {}

                    try {
                        return LocalDateTime.parse(text).toInstant(
                            ZoneOffset.UTC
                        );
                    } catch (DateTimeParseException ignored) {}

                    return Instant.parse(text + "Z");
                }
            }
        );

        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(flexibleInstantModule)
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

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

    @Bean
    public Binding notificationsWorkshopRegisteredBinding(
        Queue notificationsQueue,
        DirectExchange notificationsEventsExchange
    ) {
        return BindingBuilder.bind(notificationsQueue)
            .to(notificationsEventsExchange)
            .with("workshop.registered");
    }
}
