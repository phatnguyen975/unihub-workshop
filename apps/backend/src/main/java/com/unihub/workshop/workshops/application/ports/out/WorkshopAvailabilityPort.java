package com.unihub.workshop.workshops.application.ports.out;

import java.util.UUID;

public interface WorkshopAvailabilityPort {
    /**
     * Resolves the available seats for a workshop.
     * Reads from Redis key `workshop:{id}:capacity`.
     * If cache misses or fails, falls back to totalCapacity - active registrations (PENDING, CONFIRMED).
     *
     * @param workshopId unique identifier of the workshop
     * @param totalCapacity total capacity of the workshop
     * @return non-negative remaining seat count
     */
    int resolveAvailableSeats(UUID workshopId, int totalCapacity);
}
