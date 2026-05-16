package com.unihub.workshop.registrations.contracts.impl;

import com.unihub.workshop.registrations.contracts.AnalyticsData;
import com.unihub.workshop.registrations.contracts.RegistrationWorkshopFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Dummy implementation of RegistrationWorkshopFacade for development and
 * testing.
 * Returns hardcoded zero values and default analytics. Replace with real
 * implementation when registrations module is fully developed.
 */
@Service
@RequiredArgsConstructor
public class DummyRegistrationWorkshopFacadeImpl implements RegistrationWorkshopFacade {

    @Override
    public AnalyticsData getWorkshopAnalytics(UUID workshopId) {
        // Dummy: return zero-initialized analytics
        return new AnalyticsData(
                0, // totalCapacity
                0, // totalRegistered
                0, // totalConfirmed
                0, // totalCanceled
                0, // totalCheckedIn
                0, // totalNoShow
                0.0, // utilizationPercentage
                0.0 // confirmationRate
        );
    }

    @Override
    public int getConfirmedRegistrationCount(UUID workshopId) {
        // Dummy: return 0 confirmed registrations
        return 0;
    }

    @Override
    public int getPendingRegistrationCount(UUID workshopId) {
        // Dummy: return 0 pending registrations
        return 0;
    }

    @Override
    public com.unihub.workshop.registrations.contracts.AttendeePage listAttendees(UUID workshopId, int page, int size, String status) {
        var pagination = new com.unihub.workshop.shared.web.PaginationMetadata(page, size, 0L, 0, false, false);
        return new com.unihub.workshop.registrations.contracts.AttendeePage(java.util.List.of(), pagination);
    }
}
