package com.unihub.workshop.shared.realtime.application.ports.out;

import java.util.Set;
import java.util.UUID;

/**
 * Outbound port to verify whether connected users are authorized to receive
 * real-time events for a specific workshop (as organizer owning the workshop or
 * assigned staff).
 */
public interface WorkshopAuthorizationPort {

    /**
     * Filters a set of user IDs, returning only those authorized for the workshop.
     *
     * @param userIds    the set of user IDs to filter
     * @param workshopId the target workshop ID
     * @return the subset of user IDs authorized for the workshop
     */
    Set<UUID> filterAuthorizedUserIds(Set<UUID> userIds, UUID workshopId);

    /**
     * Checks if a single user is authorized for the given workshop.
     *
     * @param userId     the connected user's ID
     * @param workshopId the target workshop ID
     * @return true if authorized, false otherwise
     */
    default boolean isUserAuthorizedForWorkshop(UUID userId, UUID workshopId) {
        if (userId == null || workshopId == null) {
            return false;
        }
        return filterAuthorizedUserIds(Set.of(userId), workshopId).contains(userId);
    }
}
