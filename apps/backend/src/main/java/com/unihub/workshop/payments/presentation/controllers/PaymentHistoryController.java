package com.unihub.workshop.payments.presentation.controllers;

import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetStudentPaymentHistoryUseCase;
import com.unihub.workshop.payments.domain.model.PaymentHistoryItem;
import com.unihub.workshop.payments.presentation.dto.PaymentHistoryListResponse;
import com.unihub.workshop.payments.presentation.mapper.PaymentHistoryPresentationMapper;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment endpoints available to students and to both student and organizer roles.
 *
 * <p>Organizer-only endpoints live in {@link AdminPaymentHistoryController} so each
 * controller maps only the routes its role policy covers.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentHistoryController {

    private final GetStudentPaymentHistoryUseCase getStudentPaymentHistoryUseCase;
    private final PaymentHistoryPresentationMapper paymentHistoryMapper;

    /**
     * Returns the authenticated student's own payment history, newest first.
     *
     * @param authentication current security context
     * @param status optional payment status filter
     * @param cursor optional opaque keyset cursor issued for the same filter set
     * @param size optional page size, validated by the application layer
     * @return 200 OK with the paginated payment history
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PaymentHistoryListResponse> getMyPaymentHistory(
        Authentication authentication,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) String size
    ) {
        SecurityContextUtils.verifyRole(authentication, "STUDENT");
        UUID studentUserId = SecurityContextUtils.extractUserId(authentication);

        GetStudentPaymentHistoryQuery query = new GetStudentPaymentHistoryQuery(
            studentUserId,
            status,
            cursor,
            size
        );

        CursorPage<PaymentHistoryItem> page =
            getStudentPaymentHistoryUseCase.getPaymentHistory(query);

        PaymentHistoryListResponse response =
            paymentHistoryMapper.toListResponse(page);

        return ApiResponse.success(response, "Payment history page");
    }
}
