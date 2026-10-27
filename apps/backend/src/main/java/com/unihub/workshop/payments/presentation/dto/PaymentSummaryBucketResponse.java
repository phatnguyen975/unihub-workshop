package com.unihub.workshop.payments.presentation.dto;

import java.math.BigDecimal;

/**
 * Count and amount for one payment status inside one currency.
 *
 * @param count number of payments in the bucket
 * @param amount sum of the bucket's payment amounts
 */
public record PaymentSummaryBucketResponse(long count, BigDecimal amount) {}
