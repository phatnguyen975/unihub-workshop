package com.unihub.workshop.workers.notifications.core.models;

import java.util.UUID;

public record OrganizationNotificationCommand(
    UUID organizationId,
    String idempotencyPrefix,
    String sourceFileKey,
    String inAppEventKey,
    String inAppActionUrl,
    String emailSubject,
    String emailTemplateName
) {}
