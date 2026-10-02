package com.unihub.workshop.workers.search_sync.outbound.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchSyncConfig {

    @Value("${app.search-sync.queue:q.search}")
    private String queueName;

    @Value("${app.search-sync.exchange:unihub.events}")
    private String exchangeName;

    @Bean
    public Queue searchQueue() {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public TopicExchange unihubEventsExchange() {
        return ExchangeBuilder.topicExchange(exchangeName)
            .durable(true)
            .build();
    }

    @Bean
    public Binding searchBinding(
        Queue searchQueue,
        TopicExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("search");
    }

    @Bean
    public Binding workshopRescheduledBinding(
        Queue searchQueue,
        TopicExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.rescheduled");
    }

    @Bean
    public Binding workshopCompletedBinding(
        Queue searchQueue,
        TopicExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.completed");
    }

    @Bean
    public Binding workshopCanceledBinding(
        Queue searchQueue,
        TopicExchange unihubEventsExchange
    ) {
        return BindingBuilder.bind(searchQueue)
            .to(unihubEventsExchange)
            .with("workshop.canceled");
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
