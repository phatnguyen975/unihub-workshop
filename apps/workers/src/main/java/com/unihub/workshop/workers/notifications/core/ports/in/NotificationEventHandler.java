package com.unihub.workshop.workers.notifications.core.ports.in;

public interface NotificationEventHandler<T extends ConsumedNotificationEvent> {
    String supportedEventType();

    Class<T> eventDtoClass();

    void handle(T event);
}
