package com.unihub.workshop.shared.realtime.application.ports.out;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port to verify whether connected users are authorized to receive
 * real-time events for a specific workshop (as organizer owning the workshop or
 * assigned staff).
 */
public interface WorkshopAuthorizationPort {
    /**
     * Finds all user IDs authorized to receive real-time events for a specific workshop
     * (organizers of the owning organization and assigned staff).
     *
     * @param workshopId the target workshop ID
     * @return the set of authorized user IDs
     */
    Set<UUID> findAuthorizedUserIds(UUID workshopId);

    /**
     * Filters a set of user IDs, returning only those authorized for the workshop.
     *
     * @param userIds    the set of user IDs to filter
     * @param workshopId the target workshop ID
     * @return the subset of user IDs authorized for the workshop
     */
    default Set<UUID> filterAuthorizedUserIds(
        Set<UUID> userIds,
        UUID workshopId
    ) {
        if (userIds == null || userIds.isEmpty() || workshopId == null) {
            return Collections.emptySet();
        }
        Set<UUID> authorized = findAuthorizedUserIds(workshopId);
        if (authorized == null || authorized.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> result = new HashSet<>(userIds);
        result.retainAll(authorized);
        return result;
    }

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
        Set<UUID> authorized = findAuthorizedUserIds(workshopId);
        return authorized != null && authorized.contains(userId);
    }
}
