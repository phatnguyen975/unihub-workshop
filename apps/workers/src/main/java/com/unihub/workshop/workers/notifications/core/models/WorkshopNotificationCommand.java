package com.unihub.workshop.workers.notifications.core.models;

import java.util.Map;
import java.util.UUID;

public record WorkshopNotificationCommand(
    UUID workshopId,
    String idempotencyPrefix,
    String slug,
    String inAppEventKey,
    String inAppActionUrl,
    Map<String, Object> inAppPayload,
    String emailSubject,
    String emailTemplateName,
    Map<String, Object> emailTemplateModel
) {}
