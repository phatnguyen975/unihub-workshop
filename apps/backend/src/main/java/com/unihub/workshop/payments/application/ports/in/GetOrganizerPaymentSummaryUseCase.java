package com.unihub.workshop.payments.application.ports.in;

import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import java.util.List;
import java.util.UUID;

public interface GetOrganizerPaymentSummaryUseCase {
    /**
     * Returns the organization-scoped payment summary grouped by currency, with every
     * status bucket present.
     *
     * @param organizationId authenticated organizer's organization scope
     * @return one summary entry per currency with at least one payment, ordered by currency
     */
    List<CurrencyPaymentSummary> getSummary(UUID organizationId);
}
