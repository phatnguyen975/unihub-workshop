package com.unihub.workshop.workers.search_sync.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.in.SyncWorkshopIndex;
import com.unihub.workshop.workers.search_sync.inbound.mapper.SearchSyncEventMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncListener {

    private final SyncWorkshopIndex syncWorkshopIndex;
    private final SearchSyncEventMapper mapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(
        queues = "${app.search-sync.queue:q.search}",
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

            dispatchMessage(routingKey, typeId, root);
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error(
                "Rejecting unparseable or invalid event structure on routingKey '{}': {}",
                routingKey,
                e.getMessage()
            );
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error(
                "Transient error processing message on routingKey '{}': {}",
                routingKey,
                e.getMessage(),
                e
            );
            channel.basicNack(deliveryTag, false, true);
        }
    }

    void dispatchMessage(String routingKey, String typeId, JsonNode root) {
        if (
            "workshop.rescheduled".equals(routingKey) ||
            typeId.endsWith("WorkshopRescheduledEvent")
        ) {
            SearchSyncEventMapper.ReschedulePayload payload =
                mapper.toReschedulePayload(root);
            syncWorkshopIndex.handleWorkshopRescheduled(
                payload.workshopId(),
                payload.startsAt(),
                payload.endsAt(),
                payload.timezone()
            );
        } else if (
            "workshop.completed".equals(routingKey) ||
            typeId.endsWith("WorkshopCompletedEvent")
        ) {
            syncWorkshopIndex.handleWorkshopCompleted(
                mapper.extractWorkshopId(root, "WorkshopCompletedEvent")
            );
        } else if (
            "workshop.canceled".equals(routingKey) ||
            typeId.endsWith("WorkshopCanceledEvent")
        ) {
            syncWorkshopIndex.handleWorkshopCanceled(
                mapper.extractWorkshopId(root, "WorkshopCanceledEvent")
            );
        } else if (
            "search".equals(routingKey) ||
            typeId.endsWith("WorkshopPublishedEvent") ||
            typeId.endsWith("WorkshopUpdatedEvent")
        ) {
            WorkshopDocument doc = mapper.toWorkshopDocument(root);
            if (
                typeId.endsWith("WorkshopUpdatedEvent") || root.has("updatedAt")
            ) {
                syncWorkshopIndex.handleWorkshopUpdated(doc);
            } else {
                syncWorkshopIndex.handleWorkshopPublished(doc);
            }
        } else {
            throw new IllegalArgumentException(
                "Unknown event routingKey: " +
                    routingKey +
                    ", typeId: " +
                    typeId
            );
        }
    }
}
