package com.unihub.workshop.shared.events;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventPublisherConfig {

    @Bean(name = "eventPublisher")
    public ApplicationEventPublisher eventPublisher(ApplicationContext context) {
        return context;
    }
}
