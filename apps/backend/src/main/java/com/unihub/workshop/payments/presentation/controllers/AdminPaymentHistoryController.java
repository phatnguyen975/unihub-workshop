package com.unihub.workshop.payments.presentation.controllers;

import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryUseCase;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentSummaryUseCase;
import com.unihub.workshop.payments.domain.model.CurrencyPaymentSummary;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.presentation.dto.PaymentHistoryListResponse;
import com.unihub.workshop.payments.presentation.dto.PaymentSummaryResponse;
import com.unihub.workshop.payments.presentation.mapper.PaymentHistoryPresentationMapper;
import com.unihub.workshop.payments.presentation.mapper.PaymentSummaryPresentationMapper;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer-only payment operations endpoints.
 *
 * <p>Every route is scoped to the organization claim in the caller's JWT; student-facing
 * endpoints stay in {@link PaymentHistoryController}.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentHistoryController {

    private final GetOrganizerPaymentHistoryUseCase getOrganizerPaymentHistoryUseCase;
    private final GetOrganizerPaymentSummaryUseCase getOrganizerPaymentSummaryUseCase;
    private final PaymentHistoryPresentationMapper paymentHistoryMapper;
    private final PaymentSummaryPresentationMapper paymentSummaryMapper;

    /**
     * Returns payment history for workshops in the authenticated organizer's organization,
     * newest first.
     *
     * @param authentication current security context
     * @param workshopId optional workshop filter, validated by the application layer
     * @param status optional payment status filter
     * @param cursor optional opaque keyset cursor issued for the same filter set
     * @param size optional page size, validated by the application layer
     * @return 200 OK with the paginated organization-scoped payment history
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<PaymentHistoryListResponse> getOrganizationPaymentHistory(
        Authentication authentication,
        @RequestParam(value = "workshopId", required = false) String workshopId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) String size
    ) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        UUID organizationId = SecurityContextUtils.extractOrganizationId(
            authentication
        );

        GetOrganizerPaymentHistoryQuery query =
            new GetOrganizerPaymentHistoryQuery(
                organizationId,
                workshopId,
                status,
                cursor,
                size
            );

        CursorPage<OrganizerPaymentHistoryItem> page =
            getOrganizerPaymentHistoryUseCase.getPaymentHistory(query);

        PaymentHistoryListResponse response =
            paymentHistoryMapper.toOrganizerListResponse(page);

        return ApiResponse.success(response, "Payment history page");
    }

    /**
     * Returns the organization-scoped payment summary grouped by currency.
     *
     * @param authentication current security context
     * @return 200 OK with one summary entry per currency that has at least one payment
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ApiResponse<PaymentSummaryResponse> getOrganizationPaymentSummary(
        Authentication authentication
    ) {
        SecurityContextUtils.verifyRole(authentication, "ORGANIZER");
        UUID organizationId = SecurityContextUtils.extractOrganizationId(
            authentication
        );

        List<CurrencyPaymentSummary> summary =
            getOrganizerPaymentSummaryUseCase.getSummary(organizationId);

        PaymentSummaryResponse response = paymentSummaryMapper.toResponse(
            summary
        );

        return ApiResponse.success(response, "Payment summary");
    }
}
