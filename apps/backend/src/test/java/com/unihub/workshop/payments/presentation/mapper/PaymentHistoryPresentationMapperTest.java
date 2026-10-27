package com.unihub.workshop.payments.presentation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentHistoryPresentationMapperTest {

    private static final Instant CREATED_AT = Instant.parse(
        "2026-10-21T05:12:00Z"
    );
    private static final Instant COMPLETED_AT = Instant.parse(
        "2026-10-21T05:13:00Z"
    );
    private static final Instant FAILED_AT = Instant.parse(
        "2026-10-21T05:14:00Z"
    );
    private static final Instant REFUNDED_AT = Instant.parse(
        "2026-10-21T05:15:00Z"
    );

    private final PaymentHistoryPresentationMapper mapper =
        new PaymentHistoryPresentationMapperImpl();

    @ParameterizedTest(name = "{0} uses {1}")
    @MethodSource("statusTimestampCases")
    @DisplayName(
        "statusOccurredAt resolves the timestamp of the currently visible status"
    )
    void statusOccurredAt_resolvesTimestampForStatus(
        PaymentStatus status,
        Instant expected
    ) {
        Payment payment = payment(status)
            .completedAt(COMPLETED_AT)
            .failedAt(FAILED_AT)
            .refundedAt(REFUNDED_AT)
            .build();

        assertThat(mapper.statusOccurredAt(payment)).isEqualTo(expected);
    }

    @Test
    @DisplayName(
        "statusOccurredAt omits the timestamp when the terminal timestamp is absent"
    )
    void statusOccurredAt_returnsNullWhenTerminalTimestampIsMissing() {
        Payment payment = payment(PaymentStatus.COMPLETED)
            .completedAt(null)
            .build();

        assertThat(mapper.statusOccurredAt(payment)).isNull();
    }

    private static Stream<Arguments> statusTimestampCases() {
        return Stream.of(
            Arguments.of(PaymentStatus.PENDING, CREATED_AT),
            Arguments.of(PaymentStatus.COMPLETED, COMPLETED_AT),
            Arguments.of(PaymentStatus.FAILED, FAILED_AT),
            Arguments.of(PaymentStatus.REFUNDED, REFUNDED_AT)
        );
    }

    private Payment.PaymentBuilder payment(PaymentStatus status) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .registrationId(UUID.randomUUID())
            .provider("STRIPE")
            .providerPaymentId("pi_test")
            .status(status)
            .createdAt(CREATED_AT)
            .updatedAt(CREATED_AT);
    }
}
