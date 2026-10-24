package com.unihub.workshop.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Organization-scoped payment summary response.
 *
 * @param byCurrency one summary entry per currency with at least one payment
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentSummaryResponse(
    List<CurrencyPaymentSummaryResponse> byCurrency
) {}
