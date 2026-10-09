package com.unihub.workshop.notifications.application.ports.in;

import com.unihub.workshop.notifications.domain.model.InAppNotification;

public record MarkNotificationReadResult(
    InAppNotification notification,
    long unreadCount
) {}
