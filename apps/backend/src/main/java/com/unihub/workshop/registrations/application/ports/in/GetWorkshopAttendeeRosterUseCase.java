package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.shared.pagination.CursorPage;

/**
 * Inbound port for retrieving workshop attendee roster.
 */
public interface GetWorkshopAttendeeRosterUseCase {
    /**
     * Retrieves a keyset cursor-paginated roster of attendees for the given workshop.
     *
     * @param query query parameters
     * @return cursor page of WorkshopAttendeeRosterItem
     */
    CursorPage<WorkshopAttendeeRosterItem> getRoster(
        GetWorkshopAttendeeRosterQuery query
    );
}
