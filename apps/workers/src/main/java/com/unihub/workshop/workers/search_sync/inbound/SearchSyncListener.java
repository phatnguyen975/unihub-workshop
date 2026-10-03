package com.unihub.workshop.workers.search_sync.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.in.SyncWorkshopIndex;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopCanceledEventDto;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopCompletedEventDto;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopPublishedEventDto;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopRescheduledEventDto;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopUpdatedEventDto;
import com.unihub.workshop.workers.search_sync.inbound.mapper.SearchSyncEventMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
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

    void dispatchMessage(String routingKey, String typeId, JsonNode root)
        throws JsonProcessingException {
        if (
            "workshop.rescheduled".equals(routingKey) ||
            typeId.endsWith("WorkshopRescheduledEvent")
        ) {
            WorkshopRescheduledEventDto dto = objectMapper.treeToValue(
                root,
                WorkshopRescheduledEventDto.class
            );
            validateWorkshopId(
                dto != null ? dto.workshopId() : null,
                "WorkshopRescheduledEvent"
            );
            Instant startsAt = parseInstant(dto.effectiveStartsAt());
            Instant endsAt = parseInstant(dto.effectiveEndsAt());
            syncWorkshopIndex.handleWorkshopRescheduled(
                dto.workshopId(),
                startsAt,
                endsAt,
                dto.timezone()
            );
        } else if (
            "workshop.completed".equals(routingKey) ||
            typeId.endsWith("WorkshopCompletedEvent")
        ) {
            WorkshopCompletedEventDto dto = objectMapper.treeToValue(
                root,
                WorkshopCompletedEventDto.class
            );
            validateWorkshopId(
                dto != null ? dto.workshopId() : null,
                "WorkshopCompletedEvent"
            );
            syncWorkshopIndex.handleWorkshopCompleted(dto.workshopId());
        } else if (
            "workshop.canceled".equals(routingKey) ||
            typeId.endsWith("WorkshopCanceledEvent")
        ) {
            WorkshopCanceledEventDto dto = objectMapper.treeToValue(
                root,
                WorkshopCanceledEventDto.class
            );
            validateWorkshopId(
                dto != null ? dto.workshopId() : null,
                "WorkshopCanceledEvent"
            );
            syncWorkshopIndex.handleWorkshopCanceled(dto.workshopId());
        } else if (
            "search".equals(routingKey) ||
            typeId.endsWith("WorkshopPublishedEvent") ||
            typeId.endsWith("WorkshopUpdatedEvent")
        ) {
            if (
                typeId.endsWith("WorkshopUpdatedEvent") || root.has("updatedAt")
            ) {
                WorkshopUpdatedEventDto dto = objectMapper.treeToValue(
                    root,
                    WorkshopUpdatedEventDto.class
                );
                validateWorkshopId(
                    dto != null ? dto.workshopId() : null,
                    "WorkshopUpdatedEvent"
                );
                WorkshopDocument doc = mapper.toWorkshopDocument(dto);
                syncWorkshopIndex.handleWorkshopUpdated(doc);
            } else {
                WorkshopPublishedEventDto dto = objectMapper.treeToValue(
                    root,
                    WorkshopPublishedEventDto.class
                );
                validateWorkshopId(
                    dto != null ? dto.workshopId() : null,
                    "WorkshopPublishedEvent"
                );
                WorkshopDocument doc = mapper.toWorkshopDocument(dto);
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

    private void validateWorkshopId(UUID workshopId, String eventType) {
        if (workshopId == null) {
            throw new IllegalArgumentException(
                eventType + " missing required workshopId"
            );
        }
    }

    private Instant parseInstant(String value) {
        return value != null && !value.isBlank() ? Instant.parse(value) : null;
    }
}
