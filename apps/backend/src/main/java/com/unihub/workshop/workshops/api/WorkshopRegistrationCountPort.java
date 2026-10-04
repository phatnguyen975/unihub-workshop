package com.unihub.workshop.workshops.api;

import java.util.List;
import java.util.Map;
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

    /**
     * Batched count of active registrations for multiple workshops in a single query.
     *
     * @param workshopIds list of workshop identifiers
     * @return map from workshopId to active registration count
     */
    Map<UUID, Long> countActiveRegistrationsBatch(List<UUID> workshopIds);
}
