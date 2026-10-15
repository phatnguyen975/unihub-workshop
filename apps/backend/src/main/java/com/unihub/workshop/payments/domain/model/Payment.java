package com.unihub.workshop.payments.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record Payment(
    UUID id,
    UUID registrationId,
    String provider,
    String providerPaymentId,
    PaymentStatus status,
    BigDecimal amount,
    String currency,
    Instant completedAt,
    Instant failedAt,
    String failureCode,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
        "BIF",
        "CLP",
        "DJF",
        "GNF",
        "JPY",
        "KMF",
        "KRW",
        "MGA",
        "PYG",
        "RWF",
        "UGX",
        "VND",
        "VUV",
        "XAF",
        "XOF",
        "XPF"
    );

    public static Payment createCompleted(
        UUID id,
        UUID registrationId,
        String provider,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        Instant completedAt
    ) {
        Instant now = completedAt != null ? completedAt : Instant.now();
        return Payment.builder()
            .id(id != null ? id : UUID.randomUUID())
            .registrationId(registrationId)
            .provider(provider != null ? provider : "STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.COMPLETED)
            .amount(amount != null ? amount : BigDecimal.ZERO)
            .currency(
                currency != null ? currency.toUpperCase(Locale.ROOT) : "USD"
            )
            .completedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static Payment createFailed(
        UUID id,
        UUID registrationId,
        String provider,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        String failureCode,
        String failureReason,
        Instant failedAt
    ) {
        Instant now = failedAt != null ? failedAt : Instant.now();
        return Payment.builder()
            .id(id != null ? id : UUID.randomUUID())
            .registrationId(registrationId)
            .provider(provider != null ? provider : "STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.FAILED)
            .amount(amount != null ? amount : BigDecimal.ZERO)
            .currency(
                currency != null ? currency.toUpperCase(Locale.ROOT) : "USD"
            )
            .failureCode(failureCode)
            .failureReason(failureReason)
            .failedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static Payment createPending(
        UUID id,
        UUID registrationId,
        String provider,
        String providerPaymentId,
        BigDecimal amount,
        String currency
    ) {
        Instant now = Instant.now();
        return Payment.builder()
            .id(id != null ? id : UUID.randomUUID())
            .registrationId(registrationId)
            .provider(provider != null ? provider : "STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.PENDING)
            .amount(amount != null ? amount : BigDecimal.ZERO)
            .currency(
                currency != null ? currency.toUpperCase(Locale.ROOT) : "USD"
            )
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static BigDecimal convertFromSmallestUnit(
        long smallestUnitAmount,
        String currency
    ) {
        String normalizedCurrency =
            currency != null && !currency.isBlank()
                ? currency.trim().toUpperCase(Locale.ROOT)
                : "USD";

        if (ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)) {
            return BigDecimal.valueOf(smallestUnitAmount).setScale(
                2,
                RoundingMode.HALF_UP
            );
        }

        return BigDecimal.valueOf(smallestUnitAmount).divide(
            BigDecimal.valueOf(100L),
            2,
            RoundingMode.HALF_UP
        );
    }
}
