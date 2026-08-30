package com.unihub.workshop.workers.batch_ingestion.outbound.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RabbitMqIngestionEventPublisher implements IngestionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqIngestionEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishFileRejectedEvent(UUID organizationId, String sourceFileKey) {
        FileRejectedEvent event = new FileRejectedEvent(organizationId, sourceFileKey);
        // Deferred fan-out: The notifications module will consume this event and decide
        // whether to send an in-app notification or an email broadcast.
        // We do not directly deliver to either channel here.
        rabbitTemplate.convertAndSend(
                "unihub.events",
                "notifications",
                event);
    }

    @Override
    public void publishFileCompletedEvent(UUID organizationId, String sourceFileKey) {
        FileCompletedEvent event = new FileCompletedEvent(organizationId, sourceFileKey);
        // Deferred fan-out: The notifications module will consume this event and decide
        // whether to send an in-app notification or an email broadcast.
        // We do not directly deliver to either channel here.
        rabbitTemplate.convertAndSend(
                "unihub.events",
                "notifications",
                event);
    }

    public record FileRejectedEvent(
            @JsonProperty("organizationId") UUID organizationId,
            @JsonProperty("sourceFileKey") String sourceFileKey) {
    }

    public record FileCompletedEvent(
            @JsonProperty("organizationId") UUID organizationId,
            @JsonProperty("sourceFileKey") String sourceFileKey) {
    }
}
