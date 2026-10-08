package com.unihub.workshop.workers.notifications.core.ports.out;

import java.util.Map;
import java.util.UUID;

public interface InAppNotificationPort {
    boolean isAlreadyCreated(UUID userId, String idempotencyKey);

    void createNotification(
        UUID userId,
        String eventKey,
        String actionUrl,
        Map<String, Object> payload,
        String idempotencyKey
    );
}
