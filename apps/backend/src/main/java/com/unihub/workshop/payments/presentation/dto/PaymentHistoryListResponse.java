package com.unihub.workshop.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.shared.pagination.CursorResponse;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentHistoryListResponse(
    List<PaymentHistoryItemResponse> items,
    CursorResponse cursor
) {
    public static PaymentHistoryListResponse of(
        List<PaymentHistoryItemResponse> items,
        String nextCursor,
        boolean hasMore
    ) {
        return new PaymentHistoryListResponse(
            items,
            CursorResponse.of(nextCursor, hasMore)
        );
    }
}
