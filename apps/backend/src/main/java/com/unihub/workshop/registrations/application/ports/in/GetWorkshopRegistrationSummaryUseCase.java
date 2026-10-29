package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;

/**
 * Inbound port for retrieving workshop registration seat count summary.
 */
public interface GetWorkshopRegistrationSummaryUseCase {
    /**
     * Retrieves the seat count snapshot and occupancy percentage for a workshop.
     *
     * @param query the query containing workshopId and callerOrganizationId
     * @return current WorkshopRegistrationSeatCounts snapshot
     */
    WorkshopRegistrationSeatCounts getSummary(
        GetWorkshopRegistrationSummaryQuery query
    );
}
