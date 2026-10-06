package com.unihub.workshop.workers.notifications.core.ports.in;

import java.util.Map;
import java.util.UUID;

public interface DirectEmailDeliveryUseCase {
    void deliverEmail(
        UUID userId,
        String recipientAddress,
        String subject,
        String templateName,
        Map<String, Object> templateModel,
        String idempotencyKey
    );
}
