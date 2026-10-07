package com.unihub.workshop.workers.notifications.core.ports.out;

import java.util.UUID;

public interface RealtimeEventPublisherPort {
    void publishNotificationSignal(UUID userId, String eventType);
}
