package com.unihub.workshop.workers.notifications.inbound.dto;

import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.util.UUID;

public record FileCompletedEventDto(
    UUID organizationId,
    String sourceFileKey
) implements ConsumedNotificationEvent {
    @Override
    public String eventType() {
        return "FileCompletedEvent";
    }

    @Override
    public String idempotencyKey() {
        return "FILE_COMPLETED:" + organizationId + ":" + sourceFileKey;
    }
}
