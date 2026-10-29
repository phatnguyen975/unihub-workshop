package com.unihub.workshop.registrations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkshopRegistrationSeatCountsTest {

    private final UUID workshopId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @Test
    @DisplayName(
        "Should correctly calculate available seats and occupancy percentage for normal capacity"
    )
    void shouldCalculateNormalCapacity() {
        // 100 capacity, 72 confirmed, 5 pending => available: 23, occupancy: 72.00%
        WorkshopRegistrationSeatCounts seatCounts =
            WorkshopRegistrationSeatCounts.of(workshopId, 100, 72L, 5L, now);

        assertThat(seatCounts.workshopId()).isEqualTo(workshopId);
        assertThat(seatCounts.capacity()).isEqualTo(100);
        assertThat(seatCounts.confirmedCount()).isEqualTo(72L);
        assertThat(seatCounts.pendingCount()).isEqualTo(5L);
        assertThat(seatCounts.availableSeats()).isEqualTo(23);
        assertThat(seatCounts.occupancyPercentage()).isEqualTo(
            new BigDecimal("72.00")
        );
        assertThat(seatCounts.lastUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "Should return 0.00 occupancy and 0 available seats when capacity is zero (FR-07, BR-09)"
    )
    void shouldHandleZeroCapacityWithoutDivisionByZero() {
        WorkshopRegistrationSeatCounts seatCounts =
            WorkshopRegistrationSeatCounts.of(workshopId, 0, 0L, 0L, now);

        assertThat(seatCounts.capacity()).isEqualTo(0);
        assertThat(seatCounts.availableSeats()).isEqualTo(0);
        assertThat(seatCounts.occupancyPercentage()).isEqualTo(
            new BigDecimal("0.00")
        );
    }

    @Test
    @DisplayName(
        "Should floor available seats to zero when registrations exceed capacity (FR-06, BR-08)"
    )
    void shouldFloorAvailableSeatsToZeroWhenOverbooked() {
        WorkshopRegistrationSeatCounts seatCounts =
            WorkshopRegistrationSeatCounts.of(workshopId, 50, 45L, 10L, now);

        assertThat(seatCounts.availableSeats()).isEqualTo(0);
        assertThat(seatCounts.occupancyPercentage()).isEqualTo(
            new BigDecimal("90.00")
        );
    }

    @Test
    @DisplayName(
        "Should round occupancy percentage to two decimal places with HALF_UP"
    )
    void shouldRoundOccupancyToTwoDecimalPlaces() {
        // 3 capacity, 1 confirmed => 33.33%
        WorkshopRegistrationSeatCounts seatCounts =
            WorkshopRegistrationSeatCounts.of(workshopId, 3, 1L, 0L, now);

        assertThat(seatCounts.occupancyPercentage()).isEqualTo(
            new BigDecimal("33.33")
        );
    }
}
