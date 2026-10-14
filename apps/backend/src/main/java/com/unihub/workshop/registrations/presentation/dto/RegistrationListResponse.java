package com.unihub.workshop.registrations.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unihub.workshop.shared.pagination.CursorResponse;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationListResponse(
    List<RegistrationSummaryItemResponse> items,
    CursorResponse cursor
) {
    public static RegistrationListResponse of(
        List<RegistrationSummaryItemResponse> items,
        String nextCursor,
        boolean hasMore
    ) {
        return new RegistrationListResponse(
            items,
            CursorResponse.of(nextCursor, hasMore)
        );
    }
}
