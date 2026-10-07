package com.unihub.workshop.workers.notifications.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationEventDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @RabbitListener(
        queues = "${app.notifications.queue:q.notifications}",
        ackMode = "MANUAL"
    )
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message
            .getMessageProperties()
            .getReceivedRoutingKey();
        Object typeIdHeader = message
            .getMessageProperties()
            .getHeader("__TypeId__");
        String typeId = typeIdHeader != null ? typeIdHeader.toString() : "";

        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            if (root == null || !root.isObject()) {
                log.error(
                    "Rejecting malformed message body on routingKey '{}'",
                    routingKey
                );
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            dispatcher.dispatch(typeId, root);
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            String excerpt =
                message.getBody() != null
                    ? new String(message.getBody(), StandardCharsets.UTF_8)
                    : "";
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "...";
            }
            log.error(
                "Rejecting invalid or unmapped event on routingKey '{}', typeId '{}', payloadExcerpt '{}': {}",
                routingKey,
                typeId,
                excerpt,
                e.getMessage()
            );
            // Move to DLQ without requeue
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error(
                "Transient error processing notification event on routingKey '{}', typeId '{}': {}",
                routingKey,
                typeId,
                e.getMessage(),
                e
            );
            // Transient infrastructure failure -> requeue for retry
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
