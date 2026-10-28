package com.unihub.workshop.registrations.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of workshop registration seat counts snapshot and calculations.
 *
 * @param workshopId unique identifier of the workshop
 * @param capacity configured maximum seat capacity
 * @param confirmedCount current number of registrations in CONFIRMED status
 * @param pendingCount current number of registrations in PENDING status
 * @param availableSeats max(0, capacity - confirmedCount - pendingCount)
 * @param occupancyPercentage (confirmedCount * 100.0) / capacity, rounded to 2 decimal places
 * @param lastUpdatedAt timestamp of latest registration state change or fallback
 */
public record WorkshopRegistrationSeatCounts(
    UUID workshopId,
    int capacity,
    long confirmedCount,
    long pendingCount,
    int availableSeats,
    BigDecimal occupancyPercentage,
    Instant lastUpdatedAt
) {
    public static WorkshopRegistrationSeatCounts of(
        UUID workshopId,
        int capacity,
        long confirmedCount,
        long pendingCount,
        Instant lastUpdatedAt
    ) {
        long effectiveCapacity = Math.max(0, capacity);
        int available = (int) Math.max(
            0,
            effectiveCapacity - confirmedCount - pendingCount
        );

        BigDecimal occupancy = BigDecimal.ZERO.setScale(
            2,
            RoundingMode.HALF_UP
        );
        if (effectiveCapacity > 0) {
            occupancy = BigDecimal.valueOf(confirmedCount * 100.0).divide(
                BigDecimal.valueOf(effectiveCapacity),
                2,
                RoundingMode.HALF_UP
            );
        }

        return new WorkshopRegistrationSeatCounts(
            workshopId,
            capacity,
            confirmedCount,
            pendingCount,
            available,
            occupancy,
            lastUpdatedAt
        );
    }
}
