package com.unihub.workshop.payments.application.ports.in;

import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import java.util.UUID;

/**
 * Retrieves one payment for an eligible student owner or organizer.
 *
 * <p>Both operations intentionally report every absent or non-visible payment with the same
 * not-found outcome so a caller cannot distinguish another student's or organization's
 * payment from one that does not exist.
 */
public interface GetPaymentDetailUseCase {
    /**
     * Returns one payment linked to a registration owned by the student.
     *
     * @param paymentId payment identifier
     * @param studentUserId authenticated student identifier
     * @return the payment with its registration context
     * @throws com.unihub.workshop.shared.exception.NotFoundException when the payment is
     *     absent or not owned by the student
     */
    PaymentHistoryItem getStudentPaymentDetail(
        UUID paymentId,
        UUID studentUserId
    );

    /**
     * Returns one payment linked to a workshop in the organizer's organization.
     *
     * @param paymentId payment identifier
     * @param organizationId authenticated organizer's organization scope
     * @return the payment with its registration context and minimal student identity
     * @throws com.unihub.workshop.shared.exception.NotFoundException when the payment is
     *     absent or outside the organization scope
     */
    OrganizerPaymentHistoryItem getOrganizerPaymentDetail(
        UUID paymentId,
        UUID organizationId
    );
}
