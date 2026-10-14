package com.unihub.workshop.registrations.application.ports.in;

import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.shared.pagination.CursorPage;

public interface GetMyRegistrationsUseCase {
    /**
     * Executes the query to fetch keyset cursor-paginated registrations.
     *
     * @param query query parameters including student ID, status filter, cursor, and size
     * @return cursor-paginated page of RegistrationSummary items
     */
    CursorPage<RegistrationSummary> getMyRegistrations(
        GetMyRegistrationsQuery query
    );
}
