package com.unihub.workshop.workers.batch_ingestion.outbound.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RabbitMqIngestionEventPublisher implements IngestionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishFileRejectedEvent(UUID organizationId, String sourceFileKey) {
        FileRejectedEvent event = new FileRejectedEvent(organizationId, sourceFileKey);
        rabbitTemplate.convertAndSend(
                "unihub.events",
                "notifications",
                event);
    }

    @Override
    public void publishFileCompletedEvent(UUID organizationId, String sourceFileKey) {
        FileCompletedEvent event = new FileCompletedEvent(organizationId, sourceFileKey);
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
