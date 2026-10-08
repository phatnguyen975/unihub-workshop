package com.unihub.workshop.workers.notifications.core.models;

import java.util.Map;
import java.util.UUID;

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
    Map<String, Object> emailTemplateModel
) {}
