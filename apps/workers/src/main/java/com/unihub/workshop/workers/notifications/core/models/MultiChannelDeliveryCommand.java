package com.unihub.workshop.workers.notifications.core.models;

import java.util.Map;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record MultiChannelDeliveryCommand(
    UUID userId,
    String recipientEmail,
    String idempotencyKey,
    boolean sendInApp,
    String inAppEventKey,
    String inAppActionUrl,
    Map<String, Object> inAppPayload,
    boolean sendRealtime,
    String realtimeEventType,
    boolean sendEmail,
    String emailSubject,
    String emailTemplateName,
    Map<String, Object> emailTemplateModel,
    Map<String, byte[]> inlineAttachments
) {
    public MultiChannelDeliveryCommand {
        if (inAppPayload == null) {
            inAppPayload = Map.of();
        }
        if (emailTemplateModel == null) {
            emailTemplateModel = Map.of();
        }
        if (inlineAttachments == null) {
            inlineAttachments = Map.of();
        }
    }
}
