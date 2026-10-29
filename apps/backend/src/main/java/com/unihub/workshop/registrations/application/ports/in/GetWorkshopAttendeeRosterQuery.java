package com.unihub.workshop.registrations.application.ports.in;

import java.util.UUID;

/**
 * Query for retrieving paginated attendee roster of a workshop.
 *
 * @param workshopId unique identifier of the workshop
 * @param callerOrganizationId organization UUID of calling organizer
 * @param status optional status filter (e.g. "CONFIRMED")
 * @param cursor optional opaque keyset cursor
 * @param size requested page size
 */
public record GetWorkshopAttendeeRosterQuery(
    UUID workshopId,
    UUID callerOrganizationId,
    String status,
    String cursor,
    Integer size
) {
    public GetWorkshopAttendeeRosterQuery {
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
