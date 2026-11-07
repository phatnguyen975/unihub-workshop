package com.unihub.workshop.attendances.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record AttendanceSummary(
    UUID workshopId,
    int capacity,
    long confirmedCount,
    long checkedInCount,
    BigDecimal checkInPercentage,
    List<Attendance> recentScans
) {
    public AttendanceSummary {
        recentScans = List.copyOf(recentScans);
    }

    public static AttendanceSummary of(
        UUID workshopId,
        int capacity,
        long confirmedCount,
        long checkedInCount,
        List<Attendance> recentScans
    ) {
        BigDecimal percentage =
            confirmedCount == 0
                ? new BigDecimal("0.00")
                : BigDecimal.valueOf(checkedInCount)
                      .multiply(BigDecimal.valueOf(100))
                      .divide(
                          BigDecimal.valueOf(confirmedCount),
                          2,
                          RoundingMode.HALF_UP
                      );
        return new AttendanceSummary(
            workshopId,
            capacity,
            confirmedCount,
            checkedInCount,
            percentage,
            recentScans
        );
    }
}
