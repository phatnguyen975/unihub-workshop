package com.unihub.workshop.workers.notifications.core.models;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record DirectEmailDeliveryCommand(
    UUID userId,
    String recipientAddress,
    String subject,
    String templateName,
    Map<String, Object> templateModel,
    Map<String, byte[]> inlineAttachments,
    String idempotencyKey
) {
    public DirectEmailDeliveryCommand {
        if (templateModel == null) {
            templateModel = Map.of();
        }
        if (inlineAttachments == null) {
            inlineAttachments = Map.of();
        }
    }
}
