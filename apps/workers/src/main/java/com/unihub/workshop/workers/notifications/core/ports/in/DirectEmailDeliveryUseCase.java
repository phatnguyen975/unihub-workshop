package com.unihub.workshop.workers.notifications.core.ports.in;

import com.unihub.workshop.workers.notifications.core.models.DirectEmailDeliveryCommand;

public interface DirectEmailDeliveryUseCase {
    void deliverEmail(DirectEmailDeliveryCommand command);
}
