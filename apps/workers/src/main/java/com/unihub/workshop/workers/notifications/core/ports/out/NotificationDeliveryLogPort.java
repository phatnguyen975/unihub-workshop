package com.unihub.workshop.workers.notifications.core.ports.out;

import com.unihub.workshop.workers.notifications.core.models.DeliveryChannel;
import java.util.UUID;

public interface NotificationDeliveryLogPort {
    boolean isAlreadySent(
        String recipientAddress,
        DeliveryChannel channel,
        String idempotencyKey
    );

    UUID createPendingLog(
        UUID userId,
        DeliveryChannel channel,
        String recipientAddress,
        String idempotencyKey
    );

    void markSent(UUID logId, String providerMessageId);

    void markFailed(UUID logId, String errorReason);
}
