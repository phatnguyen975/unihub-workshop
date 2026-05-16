package com.unihub.workshop.workshops.application.dto;

public record WorkshopAnalyticsResponse(
        int totalCapacity,
        int totalRegistered,
        int totalConfirmed,
        int totalCanceled,
        int totalCheckedIn,
        int totalNoShow,
        double utilizationPercentage,
        double confirmationRate,
        int confirmedCount,
        int pendingCount) {
}
