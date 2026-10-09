package com.unihub.workshop.notifications.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
    UUID id,
    String eventKey,
    Map<String, Object> payload,
    boolean isRead,
    Instant readAt,
    String actionUrl,
    Instant createdAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static NotificationResponse from(InAppNotification notification) {
        return from(notification, OBJECT_MAPPER);
    }

    public static NotificationResponse from(
        InAppNotification notification,
        ObjectMapper objectMapper
    ) {
        if (notification == null) {
            return null;
        }

        Map<String, Object> payloadMap;
        if (
            notification.getPayload() != null &&
            !notification.getPayload().isBlank()
        ) {
            try {
                payloadMap = new HashMap<>(
                    objectMapper.readValue(
                        notification.getPayload(),
                        new TypeReference<Map<String, Object>>() {}
                    )
                );
                payloadMap.remove("idempotencyKey");
                payloadMap.values().removeIf(Objects::isNull);
            } catch (Exception e) {
                payloadMap = Map.of();
            }
        } else {
            payloadMap = Map.of();
        }

        return NotificationResponse.builder()
            .id(notification.getId())
            .eventKey(notification.getEventKey())
            .payload(payloadMap)
            .isRead(notification.isRead())
            .readAt(notification.getReadAt())
            .actionUrl(notification.getActionUrl())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
