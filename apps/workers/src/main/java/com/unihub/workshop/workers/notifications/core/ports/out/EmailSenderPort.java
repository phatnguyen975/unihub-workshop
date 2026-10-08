package com.unihub.workshop.workers.notifications.core.ports.out;

import com.unihub.workshop.workers.notifications.core.models.EmailMessage;

public interface EmailSenderPort {
    void send(EmailMessage message);
}
