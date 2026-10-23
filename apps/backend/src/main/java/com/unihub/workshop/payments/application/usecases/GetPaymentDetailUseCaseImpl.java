package com.unihub.workshop.payments.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.payments.application.ports.in.GetPaymentDetailUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link GetPaymentDetailUseCase}.
 *
 * <p>Loads the payment from this module's own store, resolves its registration context
 * through the registrations facade, and then enforces the caller's ownership or tenant
 * scope. A payment outside that scope is reported exactly like an absent payment.
 */
@Service
@RequiredArgsConstructor
public class GetPaymentDetailUseCaseImpl implements GetPaymentDetailUseCase {

    private static final String PAYMENT_NOT_FOUND = "Payment not found";

    private final PaymentRepository paymentRepository;
    private final RegistrationApi registrationApi;
    private final WorkshopApi workshopApi;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryItem getStudentPaymentDetail(
        UUID paymentId,
        UUID studentUserId
    ) {
        Payment payment = requirePayment(paymentId);
        RegistrationPaymentContextDto context = requireRegistrationContext(
            payment
        );

        if (!studentUserId.equals(context.studentUserId())) {
            throw new NotFoundException(PAYMENT_NOT_FOUND);
        }

        return new PaymentHistoryItem(
            payment,
            context.workshopId(),
            context.status()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerPaymentHistoryItem getOrganizerPaymentDetail(
        UUID paymentId,
        UUID organizationId
    ) {
        Payment payment = requirePayment(paymentId);
        RegistrationPaymentContextDto context = requireRegistrationContext(
            payment
        );

        if (!isVisibleToOrganization(context.workshopId(), organizationId)) {
            throw new NotFoundException(PAYMENT_NOT_FOUND);
        }

        StudentProfileDto profile = resolveStudentProfile(
            context.studentUserId()
        );

        return new OrganizerPaymentHistoryItem(
            payment,
            context.workshopId(),
            context.status(),
            profile != null ? profile.fullName() : null,
            profile != null ? profile.studentNumber() : null
        );
    }

    private Payment requirePayment(UUID paymentId) {
        return paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new NotFoundException(PAYMENT_NOT_FOUND));
    }

    private RegistrationPaymentContextDto requireRegistrationContext(
        Payment payment
    ) {
        RegistrationPaymentContextDto context = registrationApi
            .getPaymentContexts(Set.of(payment.registrationId()))
            .get(payment.registrationId());
        if (context == null) {
            throw new NotFoundException(PAYMENT_NOT_FOUND);
        }
        return context;
    }

    private boolean isVisibleToOrganization(
        UUID workshopId,
        UUID organizationId
    ) {
        if (workshopId == null) {
            return false;
        }
        return workshopApi
            .getWorkshopForRegistration(workshopId)
            .filter(workshop ->
                organizationId.equals(workshop.organizationId())
            )
            .isPresent();
    }

    private StudentProfileDto resolveStudentProfile(UUID studentUserId) {
        if (studentUserId == null) {
            return null;
        }
        return iamApi
            .getStudentProfilesByIds(Set.of(studentUserId))
            .get(studentUserId);
    }
}
