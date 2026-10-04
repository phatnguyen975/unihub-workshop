package com.unihub.workshop.workers.search_sync.outbound.config;

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
public class SearchSyncConfig {

    @Value("${app.search-sync.queue:q.search}")
    private String queueName;

    @Value("${app.search-sync.exchange:unihub.events}")
    private String exchangeName;

    @Value("${app.search-sync.dead-letter-exchange:unihub.dlx}")
    private String deadLetterExchange;

    @Value("${app.search-sync.dead-letter-routing-key:dlq}")
    private String deadLetterRoutingKey;

    @Bean
    public Queue searchQueue() {
        return QueueBuilder.durable(queueName)
            .deadLetterExchange(deadLetterExchange)
            .deadLetterRoutingKey(deadLetterRoutingKey)
            .build();
    }

    @Bean
    public DirectExchange unihubEventsExchange() {
        return ExchangeBuilder.directExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean
    public Binding searchBinding(
        Queue searchQueue,
        DirectExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("search");
    }

    @Bean
    public Binding workshopRescheduledBinding(
        Queue searchQueue,
        DirectExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.rescheduled");
    }

    @Bean
    public Binding workshopCompletedBinding(
        Queue searchQueue,
        DirectExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.completed");
    }

    @Bean
    public Binding workshopCanceledBinding(
        Queue searchQueue,
        DirectExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.canceled");
    }
}
