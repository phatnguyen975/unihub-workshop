package com.unihub.workshop.registrations.application.ports.out;

import java.util.UUID;

/**
 * Outbound port for broadcasting real-time workshop seat count snapshots
 * on registration state transitions (CONFIRMED, CANCELED).
 */
public interface WorkshopRegistrationBroadcaster {
    /**
     * Computes the current 6-field seat count snapshot for the workshop and broadcasts
     * a {@code registration.updated} event to the workshop channel {@code unihub:stream:workshop:{workshopId}}.
     *
     * @param workshopId unique identifier of the target workshop
     */
    void broadcast(UUID workshopId);
}
