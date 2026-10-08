package com.unihub.workshop.workers.batch_ingestion.outbound.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionEventPublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitMqIngestionEventPublisher
    implements IngestionEventPublisher
{

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void publishFileRejectedEvent(
        UUID organizationId,
        String sourceFileKey
    ) {
        try {
            FileRejectedEvent event = new FileRejectedEvent(
                organizationId,
                sourceFileKey
            );
            byte[] body = objectMapper.writeValueAsBytes(event);

            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("__TypeId__", "FileRejectedEvent");
            Message message = new Message(body, props);

            rabbitTemplate.send("unihub.events", "notifications", message);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to serialize FileRejectedEvent",
                e
            );
        }
    }

    @Override
    public void publishFileCompletedEvent(
        UUID organizationId,
        String sourceFileKey
    ) {
        try {
            FileCompletedEvent event = new FileCompletedEvent(
                organizationId,
                sourceFileKey
            );
            byte[] body = objectMapper.writeValueAsBytes(event);

            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("__TypeId__", "FileCompletedEvent");
            Message message = new Message(body, props);

            rabbitTemplate.send("unihub.events", "notifications", message);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to serialize FileCompletedEvent",
                e
            );
        }
    }

    public record FileRejectedEvent(
        @JsonProperty("organizationId") UUID organizationId,
        @JsonProperty("sourceFileKey") String sourceFileKey
    ) {}

    public record FileCompletedEvent(
        @JsonProperty("organizationId") UUID organizationId,
        @JsonProperty("sourceFileKey") String sourceFileKey
    ) {}
}
