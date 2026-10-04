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

    /**
     * Deletes the Redis availability counter key `workshop:{id}:capacity`.
     *
     * @param workshopId unique identifier of the workshop
     */
    void deleteCapacity(UUID workshopId);

    /**
     * Retrieves the current integer value of the Redis availability counter `workshop:{id}:capacity`.
     *
     * @param workshopId unique identifier of the workshop
     * @return current capacity in Redis, or null if key does not exist or value is unparseable
     */
    Integer getCapacity(UUID workshopId);

    /**
     * Unconditionally sets the Redis availability counter `workshop:{id}:capacity`.
     *
     * @param workshopId unique identifier of the workshop
     * @param capacity ground-truth remaining capacity to set
     */
    void setCapacity(UUID workshopId, int capacity);
}
