package com.unihub.workshop.payments.application.ports.in;

import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.shared.pagination.CursorPage;

public interface GetOrganizerPaymentHistoryUseCase {
    /**
     * Returns one keyset-paginated page of payments for workshops owned by the caller's
     * organization.
     *
     * @param query organization scope, optional filters, cursor, and page size
     * @return page of organizer payment history items ordered newest first
     */
    CursorPage<OrganizerPaymentHistoryItem> getPaymentHistory(
        GetOrganizerPaymentHistoryQuery query
    );
}
