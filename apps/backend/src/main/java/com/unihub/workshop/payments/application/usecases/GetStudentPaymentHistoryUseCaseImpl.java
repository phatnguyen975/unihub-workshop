package com.unihub.workshop.payments.application.usecases;

import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link GetStudentPaymentHistoryUseCase}.
 * Resolves the payment page from this module and enriches it in one batch call through
 * {@link RegistrationApi}.
 */
@Service
@RequiredArgsConstructor
public class GetStudentPaymentHistoryUseCaseImpl
    implements GetStudentPaymentHistoryUseCase
{

    private final PaymentRepository paymentRepository;
    private final RegistrationApi registrationApi;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<PaymentHistoryItem> getPaymentHistory(
        GetStudentPaymentHistoryQuery query
    ) {
        PaymentStatus status = PaymentHistoryQueryParser.parseStatus(
            query.status()
        );
        int size = PaymentHistoryQueryParser.parseSize(query.size());

        CursorPage<Payment> page = paymentRepository.findByStudentUserId(
            query.studentUserId(),
            status,
            query.cursor(),
            size
        );

        if (page == null || page.items().isEmpty()) {
            return CursorPage.empty();
        }

        Set<UUID> registrationIds = page
            .items()
            .stream()
            .map(Payment::registrationId)
            .collect(Collectors.toSet());

        Map<UUID, RegistrationPaymentContextDto> contexts =
            registrationApi.getPaymentContexts(registrationIds);

        List<PaymentHistoryItem> items = page
            .items()
            .stream()
            .map(payment -> toHistoryItem(payment, contexts))
            .toList();

        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private PaymentHistoryItem toHistoryItem(
        Payment payment,
        Map<UUID, RegistrationPaymentContextDto> contexts
    ) {
        RegistrationPaymentContextDto context = contexts.get(
            payment.registrationId()
        );
        return new PaymentHistoryItem(
            payment,
            context != null ? context.workshopId() : null,
            context != null ? context.status() : null
        );
    }
}
