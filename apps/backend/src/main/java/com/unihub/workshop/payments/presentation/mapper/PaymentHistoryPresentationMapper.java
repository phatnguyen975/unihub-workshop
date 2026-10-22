package com.unihub.workshop.payments.presentation.mapper;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.presentation.dto.PaymentHistoryItemResponse;
import com.unihub.workshop.payments.presentation.dto.PaymentHistoryListResponse;
import com.unihub.workshop.payments.presentation.dto.PaymentRegistrationContextResponse;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentHistoryPresentationMapper {
    String MASK_PREFIX = "••••";
    int MIN_UNMASKED_REFERENCE_LENGTH = 4;

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(
        target = "registration",
        expression = "java(toRegistrationContext(item))"
    )
    @Mapping(target = "amount", source = "payment.amount")
    @Mapping(target = "currency", source = "payment.currency")
    @Mapping(target = "status", source = "payment.status")
    @Mapping(
        target = "providerReferenceMasked",
        expression = "java(maskProviderReference(item.payment()))"
    )
    @Mapping(
        target = "statusOccurredAt",
        expression = "java(statusOccurredAt(item.payment()))"
    )
    @Mapping(target = "createdAt", source = "payment.createdAt")
    @Mapping(target = "updatedAt", source = "payment.updatedAt")
    PaymentHistoryItemResponse toItemResponse(PaymentHistoryItem item);

    List<PaymentHistoryItemResponse> toItemResponseList(
        List<PaymentHistoryItem> items
    );

    default PaymentHistoryListResponse toListResponse(
        CursorPage<PaymentHistoryItem> page
    ) {
        if (page == null) {
            return null;
        }
        return PaymentHistoryListResponse.of(
            toItemResponseList(page.items()),
            page.nextCursor(),
            page.hasMore()
        );
    }

    default PaymentRegistrationContextResponse toRegistrationContext(
        PaymentHistoryItem item
    ) {
        if (item == null || item.payment() == null) {
            return null;
        }
        return new PaymentRegistrationContextResponse(
            item.payment().registrationId(),
            item.workshopId(),
            item.registrationStatus()
        );
    }

    /**
     * Reveals only the final four characters of a gateway reference. A reference shorter
     * than four characters is masked completely, and an absent reference stays absent.
     */
    default String maskProviderReference(Payment payment) {
        if (payment == null) {
            return null;
        }
        String reference = payment.providerPaymentId();
        if (reference == null || reference.isBlank()) {
            return null;
        }
        if (reference.length() < MIN_UNMASKED_REFERENCE_LENGTH) {
            return MASK_PREFIX;
        }
        return (
            MASK_PREFIX +
            reference.substring(
                reference.length() - MIN_UNMASKED_REFERENCE_LENGTH
            )
        );
    }

    /**
     * Resolves the timestamp of the currently visible status: creation time while pending,
     * otherwise the terminal-status timestamp.
     */
    default Instant statusOccurredAt(Payment payment) {
        if (payment == null || payment.status() == null) {
            return null;
        }
        return switch (payment.status()) {
            case PENDING -> payment.createdAt();
            case COMPLETED -> payment.completedAt();
            case FAILED -> payment.failedAt();
            case REFUNDED -> payment.refundedAt();
        };
    }
}
