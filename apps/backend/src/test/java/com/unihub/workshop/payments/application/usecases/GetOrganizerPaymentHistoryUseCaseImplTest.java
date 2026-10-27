package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetOrganizerPaymentHistoryUseCaseImplTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
        "b0000001-0000-4000-a000-000000000001"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID FIRST_STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final UUID SECOND_STUDENT_USER_ID = UUID.fromString(
        "a0000002-0000-4000-a000-000000000002"
    );
    private static final UUID FIRST_REGISTRATION_ID = UUID.fromString(
        "e0000001-0000-4000-a000-000000000001"
    );
    private static final UUID SECOND_REGISTRATION_ID = UUID.fromString(
        "e0000002-0000-4000-a000-000000000002"
    );
    private static final Instant NOW = Instant.parse("2026-10-21T05:14:00Z");

    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private IamApi iamApi;
    private WorkshopApi workshopApi;
    private GetOrganizerPaymentHistoryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        registrationApi = mock(RegistrationApi.class);
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        useCase = new GetOrganizerPaymentHistoryUseCaseImpl(
            paymentRepository,
            registrationApi,
            iamApi,
            workshopApi
        );
    }

    @Test
    @DisplayName(
        "Returns the tenant page enriched with one batch registration lookup and one batch student lookup"
    )
    void getPaymentHistory_withResults_returnsEnrichedPage() {
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();
        Payment first = payment(firstPaymentId, FIRST_REGISTRATION_ID);
        Payment second = payment(secondPaymentId, SECOND_REGISTRATION_ID);

        when(
            paymentRepository.findByOrganizationId(
                ORGANIZATION_ID,
                WORKSHOP_ID,
                PaymentStatus.COMPLETED,
                "cursor-1",
                10
            )
        ).thenReturn(
            new CursorPage<>(List.of(first, second), "cursor-2", true)
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(ORGANIZATION_ID))
        );

        Set<UUID> registrationIds = Set.of(
            FIRST_REGISTRATION_ID,
            SECOND_REGISTRATION_ID
        );
        Set<UUID> studentUserIds = Set.of(
            FIRST_STUDENT_USER_ID,
            SECOND_STUDENT_USER_ID
        );
        when(registrationApi.getPaymentContexts(registrationIds)).thenReturn(
            Map.of(
                FIRST_REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    FIRST_REGISTRATION_ID,
                    WORKSHOP_ID,
                    FIRST_STUDENT_USER_ID,
                    "CONFIRMED"
                ),
                SECOND_REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    SECOND_REGISTRATION_ID,
                    WORKSHOP_ID,
                    SECOND_STUDENT_USER_ID,
                    "CONFIRMED"
                )
            )
        );
        when(iamApi.getStudentProfilesByIds(studentUserIds)).thenReturn(
            Map.of(
                FIRST_STUDENT_USER_ID,
                student(FIRST_STUDENT_USER_ID, "Alice Smith", "23123456"),
                SECOND_STUDENT_USER_ID,
                student(SECOND_STUDENT_USER_ID, "Bob Tran", "23999999")
            )
        );

        CursorPage<OrganizerPaymentHistoryItem> result =
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    WORKSHOP_ID.toString(),
                    "completed",
                    "cursor-1",
                    "10"
                )
            );

        assertThat(result.items()).hasSize(2);
        assertThat(result.nextCursor()).isEqualTo("cursor-2");
        assertThat(result.hasMore()).isTrue();

        OrganizerPaymentHistoryItem firstItem = result.items().get(0);
        assertThat(firstItem.payment()).isEqualTo(first);
        assertThat(firstItem.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(firstItem.registrationStatus()).isEqualTo("CONFIRMED");
        assertThat(firstItem.studentFullName()).isEqualTo("Alice Smith");
        assertThat(firstItem.studentNumber()).isEqualTo("23123456");

        OrganizerPaymentHistoryItem secondItem = result.items().get(1);
        assertThat(secondItem.studentFullName()).isEqualTo("Bob Tran");
        assertThat(secondItem.studentNumber()).isEqualTo("23999999");

        verify(registrationApi).getPaymentContexts(registrationIds);
        verify(iamApi).getStudentProfilesByIds(studentUserIds);
        verify(registrationApi, times(1)).getPaymentContexts(registrationIds);
        verify(iamApi, times(1)).getStudentProfilesByIds(studentUserIds);
    }

    @Test
    @DisplayName(
        "Uses the default page size and omits workshop and status filters when absent"
    )
    void getPaymentHistory_withoutOptionalFilters_usesDefaults() {
        when(
            paymentRepository.findByOrganizationId(
                ORGANIZATION_ID,
                null,
                null,
                null,
                PaymentHistoryQueryParser.DEFAULT_SIZE
            )
        ).thenReturn(CursorPage.empty());

        CursorPage<OrganizerPaymentHistoryItem> result =
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    null,
                    null,
                    null,
                    null
                )
            );

        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();

        verifyNoInteractions(registrationApi);
        verifyNoInteractions(iamApi);
        verifyNoInteractions(workshopApi);
    }

    @Test
    @DisplayName(
        "Rejects a workshop outside the caller's organization as not found without querying payments"
    )
    void getPaymentHistory_withInaccessibleWorkshop_throwsNotFound() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(UUID.randomUUID()))
        );

        assertThatThrownBy(() ->
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    WORKSHOP_ID.toString(),
                    null,
                    null,
                    null
                )
            )
        ).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(registrationApi);
        verifyNoInteractions(iamApi);
    }

    @Test
    @DisplayName(
        "Rejects a nonexistent workshop as not found without querying payments"
    )
    void getPaymentHistory_withMissingWorkshop_throwsNotFound() {
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    WORKSHOP_ID.toString(),
                    null,
                    null,
                    null
                )
            )
        ).isInstanceOf(NotFoundException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Rejects a malformed or blank workshopId as a bad request")
    void getPaymentHistory_withMalformedWorkshopId_throwsBadRequest() {
        for (String invalidWorkshopId : List.of("not-a-uuid", "", "   ")) {
            assertThatThrownBy(() ->
                useCase.getPaymentHistory(
                    new GetOrganizerPaymentHistoryQuery(
                        ORGANIZATION_ID,
                        invalidWorkshopId,
                        null,
                        null,
                        null
                    )
                )
            )
                .as("workshopId=%s", invalidWorkshopId)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("workshopId");
        }

        verifyNoInteractions(workshopApi);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Rejects an unsupported status filter with a validation error")
    void getPaymentHistory_withInvalidStatus_throwsValidationException() {
        assertThatThrownBy(() ->
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    null,
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
    @DisplayName("Rejects sizes outside 1-100 and non-integer sizes")
    void getPaymentHistory_withInvalidSize_throwsValidationException() {
        for (String invalidSize : List.of("0", "101", "-1", "abc", "")) {
            assertThatThrownBy(() ->
                useCase.getPaymentHistory(
                    new GetOrganizerPaymentHistoryQuery(
                        ORGANIZATION_ID,
                        null,
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
        "Keeps a payment visible with a null identity when IAM has no matching student record"
    )
    void getPaymentHistory_withoutStudentProfile_returnsNullIdentity() {
        Payment payment = payment(UUID.randomUUID(), FIRST_REGISTRATION_ID);
        when(
            paymentRepository.findByOrganizationId(
                ORGANIZATION_ID,
                null,
                null,
                null,
                20
            )
        ).thenReturn(new CursorPage<>(List.of(payment), null, false));
        when(
            registrationApi.getPaymentContexts(Set.of(FIRST_REGISTRATION_ID))
        ).thenReturn(
            Map.of(
                FIRST_REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    FIRST_REGISTRATION_ID,
                    WORKSHOP_ID,
                    FIRST_STUDENT_USER_ID,
                    "CONFIRMED"
                )
            )
        );
        when(
            iamApi.getStudentProfilesByIds(Set.of(FIRST_STUDENT_USER_ID))
        ).thenReturn(Map.of());

        CursorPage<OrganizerPaymentHistoryItem> result =
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    null,
                    null,
                    null,
                    null
                )
            );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).studentFullName()).isNull();
        assertThat(result.items().get(0).studentNumber()).isNull();
        assertThat(result.items().get(0).workshopId()).isEqualTo(WORKSHOP_ID);
    }

    @Test
    @DisplayName(
        "Returns an empty page without registration or student lookups when nothing matches"
    )
    void getPaymentHistory_withNoPayments_returnsEmptyPage() {
        when(
            paymentRepository.findByOrganizationId(
                ORGANIZATION_ID,
                WORKSHOP_ID,
                PaymentStatus.PENDING,
                null,
                20
            )
        ).thenReturn(CursorPage.empty());
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(ORGANIZATION_ID))
        );

        CursorPage<OrganizerPaymentHistoryItem> result =
            useCase.getPaymentHistory(
                new GetOrganizerPaymentHistoryQuery(
                    ORGANIZATION_ID,
                    WORKSHOP_ID.toString(),
                    "PENDING",
                    null,
                    null
                )
            );

        assertThat(result.items()).isEmpty();
        verify(registrationApi, never()).getPaymentContexts(any());
        verify(iamApi, never()).getStudentProfilesByIds(any());
    }

    @Test
    @DisplayName("Requires an organization scope on the query")
    void getPaymentHistory_withoutOrganization_throwsIllegalArgument() {
        assertThatThrownBy(() ->
            new GetOrganizerPaymentHistoryQuery(null, null, null, null, null)
        ).isInstanceOf(IllegalArgumentException.class);
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

    private StudentProfileDto student(
        UUID userId,
        String fullName,
        String studentNumber
    ) {
        return new StudentProfileDto(
            userId,
            UUID.randomUUID(),
            fullName,
            "student@university.edu",
            studentNumber
        );
    }

    private WorkshopRegistrationDetailsDto workshop(UUID organizationId) {
        return new WorkshopRegistrationDetailsDto(
            WORKSHOP_ID,
            organizationId,
            "Advanced Java 25",
            "advanced-java-25",
            "PUBLISHED",
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            new BigDecimal("50.00"),
            100,
            "Hall A"
        );
    }
}
