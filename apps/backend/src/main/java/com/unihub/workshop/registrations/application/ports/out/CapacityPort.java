package com.unihub.workshop.registrations.application.ports.out;

import java.util.UUID;
import java.util.function.IntSupplier;

public interface CapacityPort {
    /**
     * Atomically attempts to decrement available workshop capacity in Redis.
     * On cache miss, lazily initializes the counter using activeRegistrationsSupplier.
     * If the resulting value is < 0, executes an immediate compensating increment and returns false.
     *
     * @param workshopId target workshop ID
     * @param totalCapacity configured workshop capacity
     * @param activeRegistrationsSupplier supplier for current active registration count on cache miss
     * @return true if capacity slot was secured, false if workshop is at capacity
     */
    boolean tryAcquireCapacity(
        UUID workshopId,
        int totalCapacity,
        IntSupplier activeRegistrationsSupplier
    );

    /**
     * Executes a compensating increment (INCR) to release a previously acquired capacity slot.
     *
     * @param workshopId target workshop ID
     */
    void releaseCapacity(UUID workshopId);
}
