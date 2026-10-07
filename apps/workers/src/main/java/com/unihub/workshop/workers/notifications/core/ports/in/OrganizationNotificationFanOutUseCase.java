package com.unihub.workshop.workers.notifications.core.ports.in;

import com.unihub.workshop.workers.notifications.core.models.OrganizationNotificationCommand;

public interface OrganizationNotificationFanOutUseCase {
    void fanOutOrganizationNotification(
        OrganizationNotificationCommand command
    );
}
