package com.unihub.workshop.workers.notifications.core.ports.in;

import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;

public interface WorkshopNotificationFanOutUseCase {
    void fanOutWorkshopNotification(WorkshopNotificationCommand command);
}
