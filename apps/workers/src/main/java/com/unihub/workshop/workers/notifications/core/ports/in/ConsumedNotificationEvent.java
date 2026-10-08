package com.unihub.workshop.workers.notifications.core.ports.in;

public interface ConsumedNotificationEvent {
    String eventType();

    String idempotencyKey();
}
