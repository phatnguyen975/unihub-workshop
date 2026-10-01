package com.unihub.workshop.workshops.api;

import java.util.UUID;

/**
 * Cross-module Service Provider Interface (SPI) port for querying workshop registration counts.
 */
public interface WorkshopRegistrationCountPort {
    /**
     * Counts active registrations (e.g. PENDING or CONFIRMED) for the given workshop.
     *
     * @param workshopId unique identifier of the workshop
     * @return the count of active registrations, or 0 if none or workshopId is null
     */
    long countActiveRegistrations(UUID workshopId);
}
