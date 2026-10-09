package com.unihub.workshop.notifications.presentation.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadResult;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.presentation.dto.MarkNotificationReadResponse;
import com.unihub.workshop.notifications.presentation.dto.NotificationPageData;
import com.unihub.workshop.notifications.presentation.dto.NotificationResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface NotificationPresentationMapper {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(
        target = "payload",
        source = "payload",
        qualifiedByName = "mapPayload"
    )
    @Mapping(target = "isRead", source = "read")
    NotificationResponse toResponse(InAppNotification notification);

    List<NotificationResponse> toResponseList(
        List<InAppNotification> notifications
    );

    default NotificationPageData toPageData(NotificationsResult result) {
        if (result == null) {
            return null;
        }
        List<NotificationResponse> items = toResponseList(result.items());
        return NotificationPageData.of(
            items,
            result.unreadCount(),
            result.nextCursor(),
            result.hasMore()
        );
    }

    default MarkNotificationReadResponse toMarkReadResponse(
        MarkNotificationReadResult result
    ) {
        if (result == null) {
            return null;
        }
        return new MarkNotificationReadResponse(
            toResponse(result.notification()),
            result.unreadCount()
        );
    }

    @Named("mapPayload")
    default Map<String, Object> mapPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = new HashMap<>(
                OBJECT_MAPPER.readValue(
                    payload,
                    new TypeReference<Map<String, Object>>() {}
                )
            );
            map.remove("idempotencyKey");
            map.values().removeIf(Objects::isNull);
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
