package com.unihub.workshop.notifications.infrastructure.inapp;

import java.util.UUID;

public interface InAppNotificationProvider {

    void sendInAppNotification(UUID userId, String title, String message, String type, UUID referenceId);
}
