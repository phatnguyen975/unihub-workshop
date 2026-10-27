package com.unihub.workshop.payments.application.ports.in;

import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.shared.pagination.CursorPage;

public interface GetStudentPaymentHistoryUseCase {
    /**
     * Returns one keyset-paginated page of payments linked to the student's registrations.
     *
     * @param query student ownership, optional status filter, cursor, and page size
     * @return page of payment history items ordered newest first
     */
    CursorPage<PaymentHistoryItem> getPaymentHistory(
        GetStudentPaymentHistoryQuery query
    );
}
