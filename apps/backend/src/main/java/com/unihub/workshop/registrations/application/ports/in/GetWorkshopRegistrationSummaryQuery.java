package com.unihub.workshop.registrations.application.ports.in;

import java.util.UUID;

/**
 * Query for retrieving registration summary of a workshop.
 *
 * @param workshopId unique identifier of target workshop
 * @param callerOrganizationId organization UUID of the calling organizer from JWT claims
 */
public record GetWorkshopRegistrationSummaryQuery(
    UUID workshopId,
    UUID callerOrganizationId
) {
    public GetWorkshopRegistrationSummaryQuery {
        if (workshopId == null) {
            throw new IllegalArgumentException("workshopId must not be null");
        }
        if (callerOrganizationId == null) {
            throw new IllegalArgumentException(
                "callerOrganizationId must not be null"
            );
        }
    }
}
