package com.unihub.workshop.shared.realtime.api;

import java.util.UUID;

/**
 * Public module API for publishing real-time platform events across backend instances.
 * Injected by domain modules and background workers to broadcast updates.
 */
public interface RealtimeEventPublisher {

    /**
     * Publishes a real-time event targeting a specific user across all their active
     * sessions.
     *
     * @param userId the target user ID
     * @param event  the realtime event payload
     */
    void publishToUser(UUID userId, RealtimeEvent<?> event);

    /**
     * Publishes a real-time event targeting a specific workshop (delivered to
     * authorized organizers and staff).
     *
     * @param workshopId the target workshop ID
     * @param event      the realtime event payload
     */
    void publishToWorkshop(UUID workshopId, RealtimeEvent<?> event);
}
