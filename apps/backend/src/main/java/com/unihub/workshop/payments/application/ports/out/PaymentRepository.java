package com.unihub.workshop.payments.application.ports.out;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.domain.model.PaymentSummaryAggregate;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByRegistrationId(UUID registrationId);

    /**
     * Finds one payment by its own identifier.
     *
     * <p>This lookup evaluates no ownership or tenant scope; callers must enforce
     * visibility before returning any data.
     *
     * @param paymentId payment identifier
     * @return the payment when it exists
     */
    Optional<Payment> findById(UUID paymentId);

    boolean existsByProviderPaymentId(String providerPaymentId);

    /**
     * Finds keyset cursor-paginated payments linked to the student's registrations.
     *
     * <p>Ownership is evaluated in the same statement as the ordering window so the page
     * size, continuation cursor, and {@code hasMore} flag stay consistent.
     *
     * @param studentUserId owner student user ID
     * @param status optional payment status filter
     * @param cursor optional opaque keyset cursor issued for the same filter set
     * @param size page window size
     * @return page of payments ordered by creation time descending, then id descending
     */
    CursorPage<Payment> findByStudentUserId(
        UUID studentUserId,
        PaymentStatus status,
        String cursor,
        int size
    );

    /**
     * Finds keyset cursor-paginated payments for workshops owned by an organization.
     *
     * <p>Tenant isolation and the optional workshop filter are evaluated in the same
     * statement as the ordering window so the page size, continuation cursor, and
     * {@code hasMore} flag stay consistent.
     *
     * @param organizationId caller's organization scope
     * @param workshopId optional workshop filter already verified as accessible
     * @param status optional payment status filter
     * @param cursor optional opaque keyset cursor issued for the same filter set
     * @param size page window size
     * @return page of payments ordered by creation time descending, then id descending
     */
    CursorPage<Payment> findByOrganizationId(
        UUID organizationId,
        UUID workshopId,
        PaymentStatus status,
        String cursor,
        int size
    );

    /**
     * Aggregates payments for workshops owned by an organization by currency and status.
     *
     * <p>The tenant visibility predicate and aggregation are evaluated in the same
     * read-only statement so a row can never aggregate another organization's payments.
     *
     * @param organizationId caller's organization scope
     * @return one aggregate row per currency and status with at least one payment,
     *     ordered by currency then status
     */
    List<PaymentSummaryAggregate> summarizeByOrganizationId(
        UUID organizationId
    );
}
