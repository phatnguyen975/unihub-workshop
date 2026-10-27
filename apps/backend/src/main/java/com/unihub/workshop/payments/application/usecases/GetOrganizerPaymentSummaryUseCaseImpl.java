package com.unihub.workshop.payments.application.usecases;

import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentSummaryUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.domain.model.PaymentSummaryAggregate;
import com.unihub.workshop.payments.domain.model.PaymentSummaryBucket;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link GetOrganizerPaymentSummaryUseCase}.
 *
 * <p>Expands the persistence aggregate rows into the fixed four-status currency summary
 * contract. Missing statuses are represented by zero buckets, and currencies are ordered
 * deterministically.
 */
@Service
@RequiredArgsConstructor
public class GetOrganizerPaymentSummaryUseCaseImpl
    implements GetOrganizerPaymentSummaryUseCase
{

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyPaymentSummary> getSummary(UUID organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException(
                "organizationId must not be null"
            );
        }

        List<PaymentSummaryAggregate> aggregates =
            paymentRepository.summarizeByOrganizationId(organizationId);
        if (aggregates == null || aggregates.isEmpty()) {
            return List.of();
        }

        Map<
            String,
            Map<PaymentStatus, PaymentSummaryBucket>
        > bucketsByCurrency = new TreeMap<>();
        for (PaymentSummaryAggregate aggregate : aggregates) {
            Map<PaymentStatus, PaymentSummaryBucket> buckets =
                bucketsByCurrency.computeIfAbsent(
                    aggregate.currency(),
                    currency -> new EnumMap<>(PaymentStatus.class)
                );
            buckets.put(
                aggregate.status(),
                new PaymentSummaryBucket(aggregate.count(), aggregate.amount())
            );
        }

        return bucketsByCurrency
            .entrySet()
            .stream()
            .map(entry -> toCurrencySummary(entry.getKey(), entry.getValue()))
            .toList();
    }

    private CurrencyPaymentSummary toCurrencySummary(
        String currency,
        Map<PaymentStatus, PaymentSummaryBucket> buckets
    ) {
        return new CurrencyPaymentSummary(
            currency,
            bucket(buckets, PaymentStatus.PENDING),
            bucket(buckets, PaymentStatus.COMPLETED),
            bucket(buckets, PaymentStatus.FAILED),
            bucket(buckets, PaymentStatus.REFUNDED)
        );
    }

    private PaymentSummaryBucket bucket(
        Map<PaymentStatus, PaymentSummaryBucket> buckets,
        PaymentStatus status
    ) {
        return buckets.getOrDefault(status, PaymentSummaryBucket.empty());
    }
}
