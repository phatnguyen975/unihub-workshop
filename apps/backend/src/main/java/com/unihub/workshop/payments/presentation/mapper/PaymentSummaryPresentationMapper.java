package com.unihub.workshop.payments.presentation.mapper;

import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import com.unihub.workshop.payments.domain.model.PaymentSummaryBucket;
import com.unihub.workshop.payments.presentation.dto.CurrencyPaymentSummaryResponse;
import com.unihub.workshop.payments.presentation.dto.PaymentSummaryBucketResponse;
import com.unihub.workshop.payments.presentation.dto.PaymentSummaryResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentSummaryPresentationMapper {
    default PaymentSummaryResponse toResponse(
        List<CurrencyPaymentSummary> summaries
    ) {
        return new PaymentSummaryResponse(
            summaries == null ? List.of() : toCurrencyResponseList(summaries)
        );
    }

    List<CurrencyPaymentSummaryResponse> toCurrencyResponseList(
        List<CurrencyPaymentSummary> summaries
    );

    CurrencyPaymentSummaryResponse toCurrencyResponse(
        CurrencyPaymentSummary summary
    );

    PaymentSummaryBucketResponse toBucketResponse(PaymentSummaryBucket bucket);
}
