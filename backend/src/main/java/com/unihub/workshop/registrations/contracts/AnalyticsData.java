package com.unihub.workshop.registrations.contracts;

/**
 * Data Transfer Object for workshop analytics metrics.
 * Provides aggregated data on capacity utilization, registrations, and
 * attendance.
 */
public record AnalyticsData(
        int totalCapacity,
        int totalRegistered,
        int totalConfirmed,
        int totalCanceled,
        int totalCheckedIn,
        int totalNoShow,
        double utilizationPercentage,
        double confirmationRate) {
}
