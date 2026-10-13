package com.unihub.workshop.workers.ai_processing.outbound.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AiProcessingAmqpConfig {

    private final AiProcessingProperties properties;

    @Bean
    public Queue aiSummarizationQueue() {
        return QueueBuilder.durable(properties.getQueue())
            .deadLetterExchange(properties.getDlx())
            .deadLetterRoutingKey(properties.getDlqRoutingKey())
            .build();
    }

    @Bean
    public DirectExchange aiSummarizationEventsExchange() {
        return ExchangeBuilder.directExchange(properties.getExchange())
            .durable(true)
            .build();
    }

    @Bean
    public Binding aiSummarizationBinding(
        Queue aiSummarizationQueue,
        DirectExchange aiSummarizationEventsExchange
    ) {
        return BindingBuilder.bind(aiSummarizationQueue)
            .to(aiSummarizationEventsExchange)
            .with(properties.getRoutingKey());
    }
}
