package com.unihub.workshop.attendances.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttendanceSummaryResponse(
    UUID workshopId,
    int capacity,
    long confirmedCount,
    long checkedInCount,
    BigDecimal checkInPercentage,
    List<RecentScan> recentScans
) {
    public record RecentScan(
        UUID id,
        UUID registrationId,
        Instant checkedInAt,
        Instant createdAt
    ) {}
}
