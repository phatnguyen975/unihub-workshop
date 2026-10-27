package com.unihub.workshop.payments.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetPaymentDetailUseCaseImplTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
        "0a1d51bc-6689-45c0-9e1d-734899f6e129"
    );
    private static final UUID REGISTRATION_ID = UUID.fromString(
        "fa4c10c2-7994-4c31-a586-a88e7795d899"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "5be67c0e-6495-4a35-846e-f1889d21ddbe"
    );
    private static final UUID STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final UUID OTHER_STUDENT_USER_ID = UUID.fromString(
        "a0000002-0000-4000-a000-000000000002"
    );
    private static final UUID ORGANIZATION_ID = UUID.fromString(
        "b0000001-0000-4000-a000-000000000001"
    );
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString(
        "b0000002-0000-4000-a000-000000000002"
    );
    private static final Instant NOW = Instant.parse("2026-10-21T05:14:00Z");

    private PaymentRepository paymentRepository;
    private RegistrationApi registrationApi;
    private WorkshopApi workshopApi;
    private IamApi iamApi;
    private GetPaymentDetailUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        registrationApi = mock(RegistrationApi.class);
        workshopApi = mock(WorkshopApi.class);
        iamApi = mock(IamApi.class);
        useCase = new GetPaymentDetailUseCaseImpl(
            paymentRepository,
            registrationApi,
            workshopApi,
            iamApi
        );
    }

    @Test
    @DisplayName(
        "Returns a student's own payment with its registration context"
    )
    void getStudentPaymentDetail_asOwner_returnsPaymentAndContext() {
        Payment payment = payment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment)
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, WORKSHOP_ID))
        );

        PaymentHistoryItem result = useCase.getStudentPaymentDetail(
            PAYMENT_ID,
            STUDENT_USER_ID
        );

        assertThat(result.payment()).isEqualTo(payment);
        assertThat(result.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(result.registrationStatus()).isEqualTo("CONFIRMED");

        verify(paymentRepository).findById(PAYMENT_ID);
        verify(registrationApi).getPaymentContexts(Set.of(REGISTRATION_ID));
        verifyNoInteractions(workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Hides a payment owned by another student behind the same not-found response"
    )
    void getStudentPaymentDetail_forAnotherStudent_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(OTHER_STUDENT_USER_ID, WORKSHOP_ID))
        );

        assertThatThrownBy(() ->
            useCase.getStudentPaymentDetail(PAYMENT_ID, STUDENT_USER_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Returns not found for an absent payment without a registration lookup"
    )
    void getStudentPaymentDetail_whenPaymentMissing_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.getStudentPaymentDetail(PAYMENT_ID, STUDENT_USER_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(registrationApi, workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Returns not found when the linked registration context is unavailable"
    )
    void getStudentPaymentDetail_whenRegistrationContextMissing_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(Map.of());

        assertThatThrownBy(() ->
            useCase.getStudentPaymentDetail(PAYMENT_ID, STUDENT_USER_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Returns an organization-scoped payment with only the minimal student identity"
    )
    void getOrganizerPaymentDetail_forOrganizationWorkshop_returnsItem() {
        Payment payment = payment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment)
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, WORKSHOP_ID))
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(ORGANIZATION_ID))
        );
        when(
            iamApi.getStudentProfilesByIds(Set.of(STUDENT_USER_ID))
        ).thenReturn(
            Map.of(STUDENT_USER_ID, studentProfile("Nguyen Van A", "23123456"))
        );

        OrganizerPaymentHistoryItem result = useCase.getOrganizerPaymentDetail(
            PAYMENT_ID,
            ORGANIZATION_ID
        );

        assertThat(result.payment()).isEqualTo(payment);
        assertThat(result.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(result.registrationStatus()).isEqualTo("CONFIRMED");
        assertThat(result.studentFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.studentNumber()).isEqualTo("23123456");

        verify(workshopApi).getWorkshopForRegistration(WORKSHOP_ID);
        verify(iamApi).getStudentProfilesByIds(Set.of(STUDENT_USER_ID));
    }

    @Test
    @DisplayName(
        "Hides a payment from another organization behind the same not-found response"
    )
    void getOrganizerPaymentDetail_forAnotherOrganization_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, WORKSHOP_ID))
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(OTHER_ORGANIZATION_ID))
        );

        assertThatThrownBy(() ->
            useCase.getOrganizerPaymentDetail(PAYMENT_ID, ORGANIZATION_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(iamApi);
    }

    @Test
    @DisplayName("Returns not found when the linked workshop no longer exists")
    void getOrganizerPaymentDetail_whenWorkshopMissing_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, WORKSHOP_ID))
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            useCase.getOrganizerPaymentDetail(PAYMENT_ID, ORGANIZATION_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(iamApi);
    }

    @Test
    @DisplayName(
        "Returns not found without a workshop lookup when the context has no workshop"
    )
    void getOrganizerPaymentDetail_withoutWorkshopContext_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, null)));

        assertThatThrownBy(() ->
            useCase.getOrganizerPaymentDetail(PAYMENT_ID, ORGANIZATION_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Returns not found without a workshop lookup when the registration context is unavailable"
    )
    void getOrganizerPaymentDetail_whenRegistrationContextMissing_throwsNotFound() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(Map.of());

        assertThatThrownBy(() ->
            useCase.getOrganizerPaymentDetail(PAYMENT_ID, ORGANIZATION_ID)
        )
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Payment not found");

        verifyNoInteractions(workshopApi, iamApi);
    }

    @Test
    @DisplayName(
        "Keeps the payment visible with a null identity when IAM has no matching student record"
    )
    void getOrganizerPaymentDetail_withoutStudentProfile_returnsNullIdentity() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(
            Optional.of(payment())
        );
        when(
            registrationApi.getPaymentContexts(Set.of(REGISTRATION_ID))
        ).thenReturn(
            Map.of(REGISTRATION_ID, context(STUDENT_USER_ID, WORKSHOP_ID))
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop(ORGANIZATION_ID))
        );
        when(
            iamApi.getStudentProfilesByIds(Set.of(STUDENT_USER_ID))
        ).thenReturn(Map.of());

        OrganizerPaymentHistoryItem result = useCase.getOrganizerPaymentDetail(
            PAYMENT_ID,
            ORGANIZATION_ID
        );

        assertThat(result.studentFullName()).isNull();
        assertThat(result.studentNumber()).isNull();
        assertThat(result.workshopId()).isEqualTo(WORKSHOP_ID);
    }

    private Payment payment() {
        return Payment.builder()
            .id(PAYMENT_ID)
            .registrationId(REGISTRATION_ID)
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

    private RegistrationPaymentContextDto context(
        UUID studentUserId,
        UUID workshopId
    ) {
        return new RegistrationPaymentContextDto(
            REGISTRATION_ID,
            workshopId,
            studentUserId,
            "CONFIRMED"
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

    private StudentProfileDto studentProfile(
        String fullName,
        String studentNumber
    ) {
        return new StudentProfileDto(
            STUDENT_USER_ID,
            UUID.randomUUID(),
            fullName,
            "student@university.edu",
            studentNumber
        );
    }
}
