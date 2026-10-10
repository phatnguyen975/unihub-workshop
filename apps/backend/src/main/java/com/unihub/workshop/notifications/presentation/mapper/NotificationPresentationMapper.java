package com.unihub.workshop.notifications.presentation.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.notifications.application.ports.in.MarkAllReadResult;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadResult;
import com.unihub.workshop.notifications.application.ports.in.NotificationsResult;
import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.presentation.dto.MarkAllReadResponse;
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
public abstract class NotificationPresentationMapper {

    private final ObjectMapper objectMapper;

    protected NotificationPresentationMapper() {
        this(new ObjectMapper());
    }

    protected NotificationPresentationMapper(ObjectMapper objectMapper) {
        this.objectMapper =
            objectMapper != null ? objectMapper : new ObjectMapper();
    }

    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Mapping(
        target = "payload",
        source = "payload",
        qualifiedByName = "mapPayload"
    )
    @Mapping(target = "isRead", source = "read")
    public abstract NotificationResponse toResponse(
        InAppNotification notification
    );

    public abstract List<NotificationResponse> toResponseList(
        List<InAppNotification> notifications
    );

    public NotificationPageData toPageData(NotificationsResult result) {
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

    public MarkNotificationReadResponse toMarkReadResponse(
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

    public MarkAllReadResponse toMarkAllReadResponse(MarkAllReadResult result) {
        if (result == null) {
            return null;
        }
        return MarkAllReadResponse.of(
            result.updatedCount(),
            result.unreadCount()
        );
    }

    @Named("mapPayload")
    public Map<String, Object> mapPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = new HashMap<>(
                getObjectMapper().readValue(
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
