package com.unihub.workshop.shared.realtime.application.ports.out;

import java.util.UUID;

/**
 * Outbound port to verify whether a connected user is authorized to receive
 * real-time events for a specific workshop (as organizer owning the workshop or
 * assigned staff).
 */
public interface WorkshopAuthorizationPort {

    /**
     * Checks if the given user is authorized for the given workshop.
     *
     * @param userId     the connected user's ID
     * @param workshopId the target workshop ID
     * @return true if the user is authorized, false otherwise
     */
    boolean isUserAuthorizedForWorkshop(UUID userId, UUID workshopId);
}
