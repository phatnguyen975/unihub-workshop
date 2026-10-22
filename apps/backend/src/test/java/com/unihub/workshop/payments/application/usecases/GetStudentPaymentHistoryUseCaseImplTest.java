package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetStudentPaymentHistoryUseCaseImplTest {

    private static final UUID STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final UUID FIRST_REGISTRATION_ID = UUID.fromString(
        "e0000001-0000-4000-a000-000000000001"
    );
    private static final UUID SECOND_REGISTRATION_ID = UUID.fromString(
        "e0000002-0000-4000-a000-000000000002"
    );
    private static final UUID FIRST_WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID SECOND_WORKSHOP_ID = UUID.fromString(
        "f0000002-0000-4000-a000-000000000002"
    );
    private static final Instant NOW = Instant.parse("2026-10-21T05:14:00Z");

    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private GetStudentPaymentHistoryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        registrationApi = mock(RegistrationApi.class);
        useCase = new GetStudentPaymentHistoryUseCaseImpl(
            paymentRepository,
            registrationApi
        );
    }

    private Payment payment(UUID id, UUID registrationId) {
        return Payment.builder()
            .id(id)
            .registrationId(registrationId)
            .provider("STRIPE")
            .providerPaymentId("pi_test_a1b2")
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .completedAt(NOW)
            .createdAt(NOW.minusSeconds(120))
            .updatedAt(NOW)
            .build();
    }

    @Test
    @DisplayName(
        "Returns the payment page enriched from one batch registration lookup"
    )
    void getPaymentHistory_withResults_returnsEnrichedPageFromSingleBatchLookup() {
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();
        Payment first = payment(firstPaymentId, FIRST_REGISTRATION_ID);
        Payment second = payment(secondPaymentId, SECOND_REGISTRATION_ID);

        when(
            paymentRepository.findByStudentUserId(
                STUDENT_USER_ID,
                PaymentStatus.COMPLETED,
                "cursor-1",
                10
            )
        ).thenReturn(
            new CursorPage<>(List.of(first, second), "cursor-2", true)
        );

        Set<UUID> registrationIds = Set.of(
            FIRST_REGISTRATION_ID,
            SECOND_REGISTRATION_ID
        );
        when(registrationApi.getPaymentContexts(registrationIds)).thenReturn(
            Map.of(
                FIRST_REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    FIRST_REGISTRATION_ID,
                    FIRST_WORKSHOP_ID,
                    "CONFIRMED"
                ),
                SECOND_REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    SECOND_REGISTRATION_ID,
                    SECOND_WORKSHOP_ID,
                    "CANCELED"
                )
            )
        );

        CursorPage<PaymentHistoryItem> result = useCase.getPaymentHistory(
            new GetStudentPaymentHistoryQuery(
                STUDENT_USER_ID,
                "completed",
                "cursor-1",
                "10"
            )
        );

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isEqualTo("cursor-2");
        assertThat(result.hasMore()).isTrue();

        PaymentHistoryItem firstItem = result.items().get(0);
        assertThat(firstItem.payment()).isEqualTo(first);
        assertThat(firstItem.workshopId()).isEqualTo(FIRST_WORKSHOP_ID);
        assertThat(firstItem.registrationStatus()).isEqualTo("CONFIRMED");

        PaymentHistoryItem secondItem = result.items().get(1);
        assertThat(secondItem.payment()).isEqualTo(second);
        assertThat(secondItem.workshopId()).isEqualTo(SECOND_WORKSHOP_ID);
        assertThat(secondItem.registrationStatus()).isEqualTo("CANCELED");

        verify(registrationApi).getPaymentContexts(registrationIds);
        verify(registrationApi, times(1)).getPaymentContexts(registrationIds);
    }

    @Test
    @DisplayName("Uses the default page size when size is absent")
    void getPaymentHistory_withoutSize_usesDefaultPageSize() {
        when(
            paymentRepository.findByStudentUserId(
                STUDENT_USER_ID,
                null,
                null,
                GetStudentPaymentHistoryQuery.DEFAULT_SIZE
            )
        ).thenReturn(CursorPage.empty());

        CursorPage<PaymentHistoryItem> result = useCase.getPaymentHistory(
            new GetStudentPaymentHistoryQuery(STUDENT_USER_ID, null, null, null)
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();
        verifyNoInteractions(registrationApi);
    }

    @Test
    @DisplayName("Accepts the inclusive size bounds 1 and 100")
    void getPaymentHistory_withBoundarySizes_acceptsThem() {
        when(
            paymentRepository.findByStudentUserId(
                STUDENT_USER_ID,
                null,
                null,
                1
            )
        ).thenReturn(CursorPage.empty());
        when(
            paymentRepository.findByStudentUserId(
                STUDENT_USER_ID,
                null,
                null,
                100
            )
        ).thenReturn(CursorPage.empty());

        useCase.getPaymentHistory(
            new GetStudentPaymentHistoryQuery(STUDENT_USER_ID, null, null, "1")
        );
        useCase.getPaymentHistory(
            new GetStudentPaymentHistoryQuery(
                STUDENT_USER_ID,
                null,
                null,
                "100"
            )
        );

        verify(paymentRepository).findByStudentUserId(
            STUDENT_USER_ID,
            null,
            null,
            1
        );
        verify(paymentRepository).findByStudentUserId(
            STUDENT_USER_ID,
            null,
            null,
            100
        );
    }

    @Test
    @DisplayName("Rejects an unsupported status filter with a validation error")
    void getPaymentHistory_withInvalidStatus_throwsValidationException() {
        assertThatThrownBy(() ->
            useCase.getPaymentHistory(
                new GetStudentPaymentHistoryQuery(
                    STUDENT_USER_ID,
                    "SETTLED",
                    null,
                    null
                )
            )
        )
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("SETTLED");

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName(
        "Rejects sizes outside 1-100 and non-integer sizes without clamping"
    )
    void getPaymentHistory_withInvalidSize_throwsValidationException() {
        for (String invalidSize : List.of("0", "101", "-1", "abc", "")) {
            assertThatThrownBy(() ->
                useCase.getPaymentHistory(
                    new GetStudentPaymentHistoryQuery(
                        STUDENT_USER_ID,
                        null,
                        null,
                        invalidSize
                    )
                )
            )
                .as("size=%s", invalidSize)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("size");
        }

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName(
        "Returns an empty page without a registration lookup when nothing matches"
    )
    void getPaymentHistory_withNoPayments_returnsEmptyPage() {
        when(
            paymentRepository.findByStudentUserId(
                STUDENT_USER_ID,
                PaymentStatus.PENDING,
                null,
                20
            )
        ).thenReturn(CursorPage.empty());

        CursorPage<PaymentHistoryItem> result = useCase.getPaymentHistory(
            new GetStudentPaymentHistoryQuery(
                STUDENT_USER_ID,
                "PENDING",
                null,
                null
            )
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();
        verifyNoInteractions(registrationApi);
    }
}
