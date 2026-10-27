package com.unihub.workshop.payments.presentation.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.payments.application.ports.in.GetPaymentDetailUseCase;
import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryUseCase;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.presentation.mapper.PaymentHistoryPresentationMapperImpl;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.JwtAuthenticatedPrincipal;
import com.unihub.workshop.shared.web.GlobalExceptionHandler;
import com.unihub.workshop.shared.web.GlobalResponseBodyAdvice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentHistoryControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString(
        "0a1d51bc-6689-45c0-9e1d-734899f6e129"
    );
    private static final UUID REGISTRATION_ID = UUID.fromString(
        "fa4c10c2-7994-4c31-a586-a88e7795d899"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "5be67c0e-6495-4a35-846e-f1889d21ddbe"
    );
    private static final Instant CREATED_AT = Instant.parse(
        "2026-10-21T05:12:00Z"
    );
    private static final Instant STATUS_OCCURRED_AT = Instant.parse(
        "2026-10-21T05:14:00Z"
    );
    private static final String GATEWAY_REFERENCE = "pi_reference_a1b2";
    private static final String PROHIBITED_FAILURE_CODE = "card_declined";
    private static final String PROHIBITED_FAILURE_REASON =
        "Insufficient funds";

    private MockMvc mockMvc;
    private GetStudentPaymentHistoryUseCase useCase;
    private GetPaymentDetailUseCase detailUseCase;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = mock(GetStudentPaymentHistoryUseCase.class);
        detailUseCase = mock(GetPaymentDetailUseCase.class);

        PaymentHistoryController controller = new PaymentHistoryController(
            useCase,
            detailUseCase,
            new PaymentHistoryPresentationMapperImpl()
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    private TestingAuthenticationToken createAuth(UUID userId, String role) {
        return createAuth(userId, role, null);
    }

    private TestingAuthenticationToken createAuth(
        UUID userId,
        String role,
        String organizationId
    ) {
        JwtAuthenticatedPrincipal principal = new JwtAuthenticatedPrincipal(
            userId.toString(),
            organizationId,
            "jti-test",
            role,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new TestingAuthenticationToken(principal, null, "ROLE_" + role);
    }

    private PaymentHistoryItem historyItem(String providerPaymentId) {
        Payment payment = Payment.builder()
            .id(PAYMENT_ID)
            .registrationId(REGISTRATION_ID)
            .provider("STRIPE")
            .providerPaymentId(providerPaymentId)
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .completedAt(STATUS_OCCURRED_AT)
            .failureCode(PROHIBITED_FAILURE_CODE)
            .failureReason(PROHIBITED_FAILURE_REASON)
            .createdAt(CREATED_AT)
            .updatedAt(STATUS_OCCURRED_AT)
            .build();
        return new PaymentHistoryItem(payment, WORKSHOP_ID, "CONFIRMED");
    }

    private OrganizerPaymentHistoryItem organizerHistoryItem(
        String providerPaymentId
    ) {
        PaymentHistoryItem item = historyItem(providerPaymentId);
        return new OrganizerPaymentHistoryItem(
            item.payment(),
            item.workshopId(),
            item.registrationStatus(),
            "Nguyen Van A",
            "23123456"
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 200 OK with the student payment shape and final-four masking"
    )
    void getMyPaymentHistory_asStudent_returnsContractShape() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenReturn(
            new CursorPage<>(
                List.of(historyItem(GATEWAY_REFERENCE)),
                "next-cursor-value",
                true
            )
        );

        mockMvc
            .perform(
                get("/api/v1/payments/me").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Payment history page"))
            .andExpect(
                jsonPath("$.data.items[0].paymentId").value(
                    PAYMENT_ID.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].registration.id").value(
                    REGISTRATION_ID.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].registration.workshopId").value(
                    WORKSHOP_ID.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].registration.status").value(
                    "CONFIRMED"
                )
            )
            .andExpect(jsonPath("$.data.items[0].amount").value(50.0))
            .andExpect(jsonPath("$.data.items[0].currency").value("USD"))
            .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
            .andExpect(
                jsonPath("$.data.items[0].providerReferenceMasked").value(
                    "••••a1b2"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].statusOccurredAt").value(
                    STATUS_OCCURRED_AT.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].createdAt").value(
                    CREATED_AT.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].updatedAt").value(
                    STATUS_OCCURRED_AT.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-value")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: omits gateway identifiers and failure details from every item"
    )
    void getMyPaymentHistory_omitsProhibitedFields() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenReturn(
            new CursorPage<>(
                List.of(historyItem(GATEWAY_REFERENCE)),
                null,
                false
            )
        );

        mockMvc
            .perform(
                get("/api/v1/payments/me").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.items[0].providerPaymentId").doesNotExist()
            )
            .andExpect(
                jsonPath("$.data.items[0].providerRefundId").doesNotExist()
            )
            .andExpect(jsonPath("$.data.items[0].failureCode").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].failureReason").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].student").doesNotExist())
            .andExpect(content().string(not(containsString(GATEWAY_REFERENCE))))
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_CODE)))
            )
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_REASON)))
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: fully masks a gateway reference shorter than four characters"
    )
    void getMyPaymentHistory_withShortGatewayReference_fullyMasksIt()
        throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenReturn(
            new CursorPage<>(List.of(historyItem("abc")), null, false)
        );

        mockMvc
            .perform(
                get("/api/v1/payments/me").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.items[0].providerReferenceMasked").value(
                    "••••"
                )
            )
            .andExpect(content().string(not(containsString("abc"))));
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: forwards status, cursor, and size to the use case"
    )
    void getMyPaymentHistory_forwardsQueryParameters() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenReturn(CursorPage.empty());

        mockMvc
            .perform(
                get("/api/v1/payments/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("status", "COMPLETED")
                    .param("cursor", "cursor-value")
                    .param("size", "10")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.cursor.hasMore").value(false))
            .andExpect(jsonPath("$.data.cursor.nextCursor").doesNotExist());

        verify(useCase).getPaymentHistory(
            new GetStudentPaymentHistoryQuery(
                studentUserId,
                "COMPLETED",
                "cursor-value",
                "10"
            )
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 400 Bad Request for a rejected cursor"
    )
    void getMyPaymentHistory_withRejectedCursor_returns400() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenThrow(new BadRequestException("Invalid cursor format"));

        mockMvc
            .perform(
                get("/api/v1/payments/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("cursor", "malformed-cursor")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 422 for an unsupported status filter"
    )
    void getMyPaymentHistory_withInvalidStatus_returns422() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenThrow(
            new ValidationException("status", "Invalid payment status: SETTLED")
        );

        mockMvc
            .perform(
                get("/api/v1/payments/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("status", "SETTLED")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 422 for a size outside 1-100"
    )
    void getMyPaymentHistory_withInvalidSize_returns422() throws Exception {
        when(
            useCase.getPaymentHistory(any(GetStudentPaymentHistoryQuery.class))
        ).thenThrow(
            new ValidationException("size", "size must be between 1 and 100")
        );

        mockMvc
            .perform(
                get("/api/v1/payments/me")
                    .principal(createAuth(studentUserId, "STUDENT"))
                    .param("size", "500")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 403 Forbidden when the caller is not a STUDENT"
    )
    void getMyPaymentHistory_withoutStudentRole_returns403() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/payments/me").principal(
                    createAuth(studentUserId, "ORGANIZER")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/me: returns 401 Unauthorized without an authenticated caller"
    )
    void getMyPaymentHistory_withoutAuthentication_returns401()
        throws Exception {
        mockMvc
            .perform(get("/api/v1/payments/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns a student's own payment with the student-safe shape"
    )
    void getPaymentDetail_asStudentOwner_returnsContractShape()
        throws Exception {
        when(
            detailUseCase.getStudentPaymentDetail(PAYMENT_ID, studentUserId)
        ).thenReturn(historyItem(GATEWAY_REFERENCE));

        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Payment retrieved"))
            .andExpect(
                jsonPath("$.data.paymentId").value(PAYMENT_ID.toString())
            )
            .andExpect(
                jsonPath("$.data.registration.id").value(
                    REGISTRATION_ID.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.registration.workshopId").value(
                    WORKSHOP_ID.toString()
                )
            )
            .andExpect(
                jsonPath("$.data.registration.status").value("CONFIRMED")
            )
            .andExpect(jsonPath("$.data.amount").value(50.0))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(
                jsonPath("$.data.providerReferenceMasked").value("••••a1b2")
            )
            .andExpect(
                jsonPath("$.data.statusOccurredAt").value(
                    STATUS_OCCURRED_AT.toString()
                )
            )
            .andExpect(jsonPath("$.data.student").doesNotExist())
            .andExpect(jsonPath("$.data.providerPaymentId").doesNotExist())
            .andExpect(jsonPath("$.data.providerRefundId").doesNotExist())
            .andExpect(jsonPath("$.data.failureCode").doesNotExist())
            .andExpect(jsonPath("$.data.failureReason").doesNotExist())
            .andExpect(content().string(not(containsString(GATEWAY_REFERENCE))))
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_CODE)))
            )
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_REASON)))
            );

        verify(detailUseCase).getStudentPaymentDetail(
            PAYMENT_ID,
            studentUserId
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns the organizer shape with only the minimal student identity"
    )
    void getPaymentDetail_asOrganizer_returnsContractShape() throws Exception {
        when(
            detailUseCase.getOrganizerPaymentDetail(PAYMENT_ID, organizationId)
        ).thenReturn(organizerHistoryItem(GATEWAY_REFERENCE));

        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(
                        UUID.randomUUID(),
                        "ORGANIZER",
                        organizationId.toString()
                    )
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Payment retrieved"))
            .andExpect(
                jsonPath("$.data.paymentId").value(PAYMENT_ID.toString())
            )
            .andExpect(
                jsonPath("$.data.student.fullName").value("Nguyen Van A")
            )
            .andExpect(
                jsonPath("$.data.student.studentNumber").value("23123456")
            )
            .andExpect(jsonPath("$.data.student.email").doesNotExist())
            .andExpect(jsonPath("$.data.providerPaymentId").doesNotExist())
            .andExpect(jsonPath("$.data.providerRefundId").doesNotExist())
            .andExpect(jsonPath("$.data.failureCode").doesNotExist())
            .andExpect(jsonPath("$.data.failureReason").doesNotExist())
            .andExpect(
                content().string(not(containsString(GATEWAY_REFERENCE)))
            );

        verify(detailUseCase).getOrganizerPaymentDetail(
            PAYMENT_ID,
            organizationId
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: hides a cross-student payment behind the not-found response"
    )
    void getPaymentDetail_asStudent_withNonVisiblePayment_returns404()
        throws Exception {
        when(
            detailUseCase.getStudentPaymentDetail(PAYMENT_ID, studentUserId)
        ).thenThrow(new NotFoundException("Payment not found"));

        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: hides a cross-organization payment behind the not-found response"
    )
    void getPaymentDetail_asOrganizer_withNonVisiblePayment_returns404()
        throws Exception {
        when(
            detailUseCase.getOrganizerPaymentDetail(PAYMENT_ID, organizationId)
        ).thenThrow(new NotFoundException("Payment not found"));

        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(
                        UUID.randomUUID(),
                        "ORGANIZER",
                        organizationId.toString()
                    )
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns 400 for a non-UUID payment identifier"
    )
    void getPaymentDetail_withMalformedPaymentId_returns400() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/payments/not-a-uuid").principal(
                    createAuth(studentUserId, "STUDENT")
                )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );

        verifyNoInteractions(detailUseCase);
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns 403 for a STAFF caller"
    )
    void getPaymentDetail_asStaff_returns403() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(UUID.randomUUID(), "STAFF")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(detailUseCase);
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns 403 when an organizer has no organization scope"
    )
    void getPaymentDetail_asOrganizerWithoutOrganization_returns403()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/payments/{paymentId}", PAYMENT_ID).principal(
                    createAuth(UUID.randomUUID(), "ORGANIZER")
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(detailUseCase);
    }

    @Test
    @DisplayName(
        "GET /api/v1/payments/{paymentId}: returns 401 Unauthorized without an authenticated caller"
    )
    void getPaymentDetail_withoutAuthentication_returns401() throws Exception {
        mockMvc
            .perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }
}
