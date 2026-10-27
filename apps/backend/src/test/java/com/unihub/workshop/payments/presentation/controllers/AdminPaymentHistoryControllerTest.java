package com.unihub.workshop.payments.presentation.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryUseCase;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentSummaryUseCase;
import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.domain.model.PaymentSummaryBucket;
import com.unihub.workshop.payments.presentation.mapper.PaymentHistoryPresentationMapperImpl;
import com.unihub.workshop.payments.presentation.mapper.PaymentSummaryPresentationMapperImpl;
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

class AdminPaymentHistoryControllerTest {

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
    private GetOrganizerPaymentHistoryUseCase organizerUseCase;
    private GetOrganizerPaymentSummaryUseCase summaryUseCase;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        organizerUseCase = mock(GetOrganizerPaymentHistoryUseCase.class);
        summaryUseCase = mock(GetOrganizerPaymentSummaryUseCase.class);

        AdminPaymentHistoryController controller =
            new AdminPaymentHistoryController(
                organizerUseCase,
                summaryUseCase,
                new PaymentHistoryPresentationMapperImpl(),
                new PaymentSummaryPresentationMapperImpl()
            );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(),
                new GlobalResponseBodyAdvice()
            )
            .build();
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

    private OrganizerPaymentHistoryItem organizerHistoryItem(
        String providerPaymentId
    ) {
        Payment payment = Payment.builder()
            .id(PAYMENT_ID)
            .registrationId(REGISTRATION_ID)
            .provider("STRIPE")
            .providerPaymentId(providerPaymentId)
            .providerRefundId("re_prohibited")
            .status(PaymentStatus.COMPLETED)
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .completedAt(STATUS_OCCURRED_AT)
            .failureCode(PROHIBITED_FAILURE_CODE)
            .failureReason(PROHIBITED_FAILURE_REASON)
            .createdAt(CREATED_AT)
            .updatedAt(STATUS_OCCURRED_AT)
            .build();
        return new OrganizerPaymentHistoryItem(
            payment,
            WORKSHOP_ID,
            "CONFIRMED",
            "Nguyen Van A",
            "23123456"
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 200 OK with organization-scoped payment fields and minimal student identity"
    )
    void getOrganizationPaymentHistory_asOrganizer_returnsContractShape()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenReturn(
            new CursorPage<>(
                List.of(organizerHistoryItem(GATEWAY_REFERENCE)),
                "next-cursor-value",
                true
            )
        );

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("workshopId", WORKSHOP_ID.toString())
                    .param("status", "COMPLETED")
                    .param("cursor", "cursor-value")
                    .param("size", "10")
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
            .andExpect(jsonPath("$.data.items[0].amount").value(50.0))
            .andExpect(jsonPath("$.data.items[0].currency").value("USD"))
            .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
            .andExpect(
                jsonPath("$.data.items[0].providerReferenceMasked").value(
                    "••••a1b2"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].student.fullName").value(
                    "Nguyen Van A"
                )
            )
            .andExpect(
                jsonPath("$.data.items[0].student.studentNumber").value(
                    "23123456"
                )
            )
            .andExpect(
                jsonPath("$.data.cursor.nextCursor").value("next-cursor-value")
            )
            .andExpect(jsonPath("$.data.cursor.hasMore").value(true));

        verify(organizerUseCase).getPaymentHistory(
            new GetOrganizerPaymentHistoryQuery(
                organizationId,
                WORKSHOP_ID.toString(),
                "COMPLETED",
                "cursor-value",
                "10"
            )
        );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: omits gateway identifiers, refunds, and failure details"
    )
    void getOrganizationPaymentHistory_omitsProhibitedFields()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenReturn(
            new CursorPage<>(
                List.of(organizerHistoryItem(GATEWAY_REFERENCE)),
                null,
                false
            )
        );

        mockMvc
            .perform(
                get("/api/v1/admin/payments").principal(
                    createAuth(
                        UUID.randomUUID(),
                        "ORGANIZER",
                        organizationId.toString()
                    )
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
            .andExpect(content().string(not(containsString(GATEWAY_REFERENCE))))
            .andExpect(content().string(not(containsString("re_prohibited"))))
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_CODE)))
            )
            .andExpect(
                content().string(not(containsString(PROHIBITED_FAILURE_REASON)))
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 404 when the workshop filter is inaccessible"
    )
    void getOrganizationPaymentHistory_withInaccessibleWorkshop_returns404()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenThrow(new NotFoundException("Workshop not found"));

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("workshopId", WORKSHOP_ID.toString())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:not-found")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 400 for a malformed workshopId"
    )
    void getOrganizationPaymentHistory_withMalformedWorkshopId_returns400()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenThrow(new BadRequestException("Invalid 'workshopId' value"));

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("workshopId", "not-a-uuid")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 400 for a rejected cursor"
    )
    void getOrganizationPaymentHistory_withRejectedCursor_returns400()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenThrow(new BadRequestException("Invalid cursor format"));

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("cursor", "malformed-cursor")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:bad-request")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 422 for an unsupported status filter"
    )
    void getOrganizationPaymentHistory_withInvalidStatus_returns422()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenThrow(
            new ValidationException("status", "Invalid payment status: SETTLED")
        );

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("status", "SETTLED")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 422 for a size outside 1-100"
    )
    void getOrganizationPaymentHistory_withInvalidSize_returns422()
        throws Exception {
        when(
            organizerUseCase.getPaymentHistory(
                any(GetOrganizerPaymentHistoryQuery.class)
            )
        ).thenThrow(
            new ValidationException("size", "size must be between 1 and 100")
        );

        mockMvc
            .perform(
                get("/api/v1/admin/payments")
                    .principal(
                        createAuth(
                            UUID.randomUUID(),
                            "ORGANIZER",
                            organizationId.toString()
                        )
                    )
                    .param("size", "500")
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            );
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 403 for STUDENT and STAFF callers"
    )
    void getOrganizationPaymentHistory_withoutOrganizerRole_returns403()
        throws Exception {
        for (String role : List.of("STUDENT", "STAFF")) {
            mockMvc
                .perform(
                    get("/api/v1/admin/payments").principal(
                        createAuth(
                            UUID.randomUUID(),
                            role,
                            organizationId.toString()
                        )
                    )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 403 when the organizer has no organization scope"
    )
    void getOrganizationPaymentHistory_withoutOrganizationClaim_returns403()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/payments").principal(
                    createAuth(UUID.randomUUID(), "ORGANIZER", null)
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments: returns 401 Unauthorized without an authenticated caller"
    )
    void getOrganizationPaymentHistory_withoutAuthentication_returns401()
        throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/payments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments/summary: returns 200 with currency-separated status buckets"
    )
    void getOrganizationPaymentSummary_asOrganizer_returnsContractShape()
        throws Exception {
        when(summaryUseCase.getSummary(organizationId)).thenReturn(
            List.of(
                new CurrencyPaymentSummary(
                    "USD",
                    new PaymentSummaryBucket(2, new BigDecimal("100.00")),
                    new PaymentSummaryBucket(18, new BigDecimal("900.00")),
                    new PaymentSummaryBucket(1, new BigDecimal("50.00")),
                    new PaymentSummaryBucket(3, new BigDecimal("150.00"))
                ),
                new CurrencyPaymentSummary(
                    "VND",
                    PaymentSummaryBucket.empty(),
                    new PaymentSummaryBucket(1, new BigDecimal("250000.00")),
                    PaymentSummaryBucket.empty(),
                    PaymentSummaryBucket.empty()
                )
            )
        );

        mockMvc
            .perform(
                get("/api/v1/admin/payments/summary").principal(
                    createAuth(
                        UUID.randomUUID(),
                        "ORGANIZER",
                        organizationId.toString()
                    )
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Payment summary"))
            .andExpect(jsonPath("$.data.byCurrency[0].currency").value("USD"))
            .andExpect(jsonPath("$.data.byCurrency[0].pending.count").value(2))
            .andExpect(
                jsonPath("$.data.byCurrency[0].pending.amount").value(100.0)
            )
            .andExpect(
                jsonPath("$.data.byCurrency[0].completed.count").value(18)
            )
            .andExpect(
                jsonPath("$.data.byCurrency[0].completed.amount").value(900.0)
            )
            .andExpect(jsonPath("$.data.byCurrency[0].failed.count").value(1))
            .andExpect(
                jsonPath("$.data.byCurrency[0].failed.amount").value(50.0)
            )
            .andExpect(jsonPath("$.data.byCurrency[0].refunded.count").value(3))
            .andExpect(
                jsonPath("$.data.byCurrency[0].refunded.amount").value(150.0)
            )
            .andExpect(jsonPath("$.data.byCurrency[1].currency").value("VND"))
            .andExpect(jsonPath("$.data.byCurrency[1].pending.count").value(0))
            .andExpect(
                jsonPath("$.data.byCurrency[1].pending.amount").value(0.0)
            )
            .andExpect(
                jsonPath("$.data.byCurrency[1].completed.count").value(1)
            )
            .andExpect(
                jsonPath("$.data.byCurrency[1].completed.amount").value(
                    250000.0
                )
            )
            .andExpect(jsonPath("$.data.total").doesNotExist());

        verify(summaryUseCase).getSummary(organizationId);
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments/summary: returns an empty byCurrency collection when the organization has no payments"
    )
    void getOrganizationPaymentSummary_withNoPayments_returnsEmptyCollection()
        throws Exception {
        when(summaryUseCase.getSummary(organizationId)).thenReturn(List.of());

        mockMvc
            .perform(
                get("/api/v1/admin/payments/summary").principal(
                    createAuth(
                        UUID.randomUUID(),
                        "ORGANIZER",
                        organizationId.toString()
                    )
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.byCurrency").isArray())
            .andExpect(jsonPath("$.data.byCurrency").isEmpty())
            .andExpect(jsonPath("$.data.total").doesNotExist());

        verify(summaryUseCase).getSummary(organizationId);
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments/summary: returns 403 for STUDENT and STAFF callers"
    )
    void getOrganizationPaymentSummary_withoutOrganizerRole_returns403()
        throws Exception {
        for (String role : List.of("STUDENT", "STAFF")) {
            mockMvc
                .perform(
                    get("/api/v1/admin/payments/summary").principal(
                        createAuth(
                            UUID.randomUUID(),
                            role,
                            organizationId.toString()
                        )
                    )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments/summary: returns 403 when the organizer has no organization scope"
    )
    void getOrganizationPaymentSummary_withoutOrganizationClaim_returns403()
        throws Exception {
        mockMvc
            .perform(
                get("/api/v1/admin/payments/summary").principal(
                    createAuth(UUID.randomUUID(), "ORGANIZER", null)
                )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName(
        "GET /api/v1/admin/payments/summary: returns 401 Unauthorized without an authenticated caller"
    )
    void getOrganizationPaymentSummary_withoutAuthentication_returns401()
        throws Exception {
        mockMvc
            .perform(get("/api/v1/admin/payments/summary"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }
}
