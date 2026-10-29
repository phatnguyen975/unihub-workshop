package com.unihub.workshop.registrations.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Presentation DTO for workshop registration seat count summary.
 *
 * @param workshopId unique identifier of the workshop
 * @param capacity configured maximum seat capacity
 * @param confirmedCount number of registrations in CONFIRMED status
 * @param pendingCount number of registrations in PENDING status
 * @param availableSeats max(0, capacity - confirmedCount - pendingCount)
 * @param occupancyPercentage (confirmedCount * 100.0) / capacity, rounded to 2 decimal places
 * @param lastUpdatedAt timestamp of latest registration state change or fallback
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkshopRegistrationSummaryResponse(
    UUID workshopId,
    int capacity,
    long confirmedCount,
    long pendingCount,
    int availableSeats,
    BigDecimal occupancyPercentage,
    Instant lastUpdatedAt
) {
    /**
     * Factory method to create a response from a domain seat counts model.
     *
     * @param summary domain seat counts snapshot
     * @return presentation response DTO
     */
    public static WorkshopRegistrationSummaryResponse of(
        WorkshopRegistrationSeatCounts summary
    ) {
        return new WorkshopRegistrationSummaryResponse(
            summary.workshopId(),
            summary.capacity(),
            summary.confirmedCount(),
            summary.pendingCount(),
            summary.availableSeats(),
            summary.occupancyPercentage(),
            summary.lastUpdatedAt()
        );
    }
}
