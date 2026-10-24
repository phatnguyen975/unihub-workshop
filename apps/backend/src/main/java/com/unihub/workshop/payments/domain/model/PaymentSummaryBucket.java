package com.unihub.workshop.payments.domain.model;

import java.math.BigDecimal;

/**
 * Aggregated count and amount for one payment status inside one currency.
 *
 * @param count number of payment records in the bucket
 * @param amount sum of those records' amounts, in the bucket's currency
 */
public record PaymentSummaryBucket(long count, BigDecimal amount) {
    private static final PaymentSummaryBucket EMPTY = new PaymentSummaryBucket(
        0L,
        BigDecimal.ZERO.setScale(2)
    );

    /**
     * Returns the zero bucket used when a currency has no records for a status.
     *
     * @return an immutable bucket with count zero and amount zero
     */
    public static PaymentSummaryBucket empty() {
        return EMPTY;
    }
}
