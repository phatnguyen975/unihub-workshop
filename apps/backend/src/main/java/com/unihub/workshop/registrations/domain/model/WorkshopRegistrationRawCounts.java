package com.unihub.workshop.registrations.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Aggregated raw counts and latest timestamp of registrations for a workshop.
 *
 * @param confirmedCount number of registrations in CONFIRMED status
 * @param pendingCount number of registrations in PENDING status
 * @param latestUpdatedAt timestamp of the most recent registration update/creation, if any exists
 */
public record WorkshopRegistrationRawCounts(
    long confirmedCount,
    long pendingCount,
    Optional<Instant> latestUpdatedAt
) {
    public static WorkshopRegistrationRawCounts empty() {
        return new WorkshopRegistrationRawCounts(0L, 0L, Optional.empty());
    }
}
