package com.unihub.workshop.registrations.contracts;

import java.util.UUID;

/**
 * Facade interface for registration-related operations that workshops module
 * requires.
 * Provides cross-module contract for fetching analytics and registration
 * metrics.
 */
public interface RegistrationWorkshopFacade {

    /**
     * Retrieves analytics metrics for a specific workshop including capacity
     * utilization,
     * registration counts, and attendance data.
     *
     * @param workshopId the UUID of the workshop
     * @return AnalyticsData containing aggregated metrics
     */
    AnalyticsData getWorkshopAnalytics(UUID workshopId);

    /**
     * Retrieves the count of confirmed registrations for a workshop.
     *
     * @param workshopId the UUID of the workshop
     * @return the number of confirmed registrations
     */
    int getConfirmedRegistrationCount(UUID workshopId);

    /**
     * Retrieves the count of pending registrations for a workshop.
     *
     * @param workshopId the UUID of the workshop
     * @return the number of pending registrations
     */
    int getPendingRegistrationCount(UUID workshopId);

    /**
     * Retrieves a paged list of attendees (registrations) for the workshop.
     * Implementations should return an AttendeePage containing items and pagination metadata.
     */
    AttendeePage listAttendees(UUID workshopId, int page, int size, String status);
}
