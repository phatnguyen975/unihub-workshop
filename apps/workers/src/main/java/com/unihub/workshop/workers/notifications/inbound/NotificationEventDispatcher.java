package com.unihub.workshop.workers.notifications.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventDispatcher {

    private final List<NotificationEventHandler<?>> handlers;
    private final ObjectMapper objectMapper;

    public NotificationEventDispatcher(
        List<NotificationEventHandler<?>> handlers,
        ObjectMapper objectMapper
    ) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
    }

    public void dispatch(String typeId, JsonNode root)
        throws JsonProcessingException {
        if (typeId == null || typeId.isBlank()) {
            throw new IllegalArgumentException(
                "Missing or blank __TypeId__ header"
            );
        }

        for (NotificationEventHandler<?> handler : handlers) {
            if (typeId.endsWith(handler.supportedEventType())) {
                parseAndHandle(handler, root);
                return;
            }
        }

        throw new IllegalArgumentException(
            "No registered notification handler for type: " + typeId
        );
    }

    private <T extends ConsumedNotificationEvent> void parseAndHandle(
        NotificationEventHandler<T> handler,
        JsonNode root
    ) throws JsonProcessingException {
        T eventDto = objectMapper.treeToValue(root, handler.eventDtoClass());
        handler.handle(eventDto);
    }
}
