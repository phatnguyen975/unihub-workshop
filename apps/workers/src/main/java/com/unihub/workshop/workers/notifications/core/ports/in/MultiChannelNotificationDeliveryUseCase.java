package com.unihub.workshop.workers.notifications.core.ports.in;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;

public interface MultiChannelNotificationDeliveryUseCase {
    void deliverMultiChannel(MultiChannelDeliveryCommand command);
}
