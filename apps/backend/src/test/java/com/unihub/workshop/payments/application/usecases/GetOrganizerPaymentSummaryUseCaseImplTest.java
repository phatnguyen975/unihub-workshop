package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.domain.model.PaymentSummaryAggregate;
import com.unihub.workshop.payments.domain.model.PaymentSummaryBucket;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetOrganizerPaymentSummaryUseCaseImplTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
        "b0000001-0000-4000-a000-000000000001"
    );

    private PaymentRepository paymentRepository;
    private GetOrganizerPaymentSummaryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        useCase = new GetOrganizerPaymentSummaryUseCaseImpl(paymentRepository);
    }

    @Test
    @DisplayName(
        "Groups aggregate rows by currency and fills every missing status bucket with zero"
    )
    void getSummary_withRows_groupsByCurrencyAndFillsMissingBuckets() {
        when(
            paymentRepository.summarizeByOrganizationId(ORGANIZATION_ID)
        ).thenReturn(
            List.of(
                aggregate("USD", PaymentStatus.REFUNDED, 3, "150.00"),
                aggregate("USD", PaymentStatus.PENDING, 2, "100.00"),
                aggregate("EUR", PaymentStatus.COMPLETED, 1, "20.00"),
                aggregate("USD", PaymentStatus.COMPLETED, 18, "900.00"),
                aggregate("USD", PaymentStatus.FAILED, 1, "50.00")
            )
        );

        List<CurrencyPaymentSummary> result = useCase.getSummary(
            ORGANIZATION_ID
        );

        assertThat(result)
            .extracting(CurrencyPaymentSummary::currency)
            .containsExactly("EUR", "USD");

        CurrencyPaymentSummary eur = result.get(0);
        assertThat(eur.completed().count()).isEqualTo(1);
        assertThat(eur.completed().amount()).isEqualByComparingTo("20.00");
        assertEmpty(eur.pending());
        assertEmpty(eur.failed());
        assertEmpty(eur.refunded());

        CurrencyPaymentSummary usd = result.get(1);
        assertBucket(usd.pending(), 2, "100.00");
        assertBucket(usd.completed(), 18, "900.00");
        assertBucket(usd.failed(), 1, "50.00");
        assertBucket(usd.refunded(), 3, "150.00");

        verify(paymentRepository).summarizeByOrganizationId(ORGANIZATION_ID);
    }

    @Test
    @DisplayName(
        "Returns an empty summary when the organization has no payments"
    )
    void getSummary_withNoPayments_returnsEmptyList() {
        when(
            paymentRepository.summarizeByOrganizationId(ORGANIZATION_ID)
        ).thenReturn(List.of());

        List<CurrencyPaymentSummary> result = useCase.getSummary(
            ORGANIZATION_ID
        );

        assertThat(result).isEmpty();
        verify(paymentRepository).summarizeByOrganizationId(ORGANIZATION_ID);
    }

    @Test
    @DisplayName("Returns an empty summary when the repository returns null")
    void getSummary_withNullRepositoryResult_returnsEmptyList() {
        when(
            paymentRepository.summarizeByOrganizationId(ORGANIZATION_ID)
        ).thenReturn(null);

        List<CurrencyPaymentSummary> result = useCase.getSummary(
            ORGANIZATION_ID
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
        "Rejects a missing organization scope before querying payments"
    )
    void getSummary_withoutOrganization_throwsIllegalArgument() {
        assertThatThrownBy(() -> useCase.getSummary(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("organizationId");

        verifyNoInteractions(paymentRepository);
    }

    private PaymentSummaryAggregate aggregate(
        String currency,
        PaymentStatus status,
        long count,
        String amount
    ) {
        return new PaymentSummaryAggregate(
            currency,
            status,
            count,
            new BigDecimal(amount)
        );
    }

    private void assertBucket(
        PaymentSummaryBucket bucket,
        long count,
        String amount
    ) {
        assertThat(bucket.count()).isEqualTo(count);
        assertThat(bucket.amount()).isEqualByComparingTo(amount);
    }

    private void assertEmpty(PaymentSummaryBucket bucket) {
        assertBucket(bucket, 0, "0.00");
    }
}
