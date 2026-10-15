package com.unihub.workshop.payments.infrastructure.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Converts monetary amounts to their respective smallest currency units
 * conforming to Stripe API standards (e.g. cents for USD, integer units for VND/JPY).
 */
@Component
public class CurrencyUnitConverter {

    /**
     * Currencies with 0 decimal places according to Stripe specifications.
     */
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

    /**
     * Currencies with 3 decimal places according to Stripe specifications.
     */
    private static final Set<String> THREE_DECIMAL_CURRENCIES = Set.of(
        "BHD",
        "JOD",
        "KWD",
        "OMR",
        "TND"
    );

    /**
     * Converts a given amount to the smallest currency unit.
     *
     * @param amount monetary value (must not be null or negative)
     * @param currency ISO-4217 currency code (null/blank defaults to USD)
     * @return amount in smallest currency unit
     */
    public long toSmallestUnit(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }

        String normalizedCurrency =
            currency == null || currency.isBlank()
                ? "USD"
                : currency.trim().toUpperCase(Locale.ROOT);

        if (ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)) {
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }

        if (THREE_DECIMAL_CURRENCIES.contains(normalizedCurrency)) {
            return amount
                .multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        }

        // Standard 2-decimal currencies (USD, EUR, GBP, SGD, etc.)
        return amount
            .multiply(BigDecimal.valueOf(100L))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
    }
}
