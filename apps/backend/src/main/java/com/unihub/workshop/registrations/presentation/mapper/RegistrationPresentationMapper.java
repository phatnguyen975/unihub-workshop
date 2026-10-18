package com.unihub.workshop.registrations.presentation.mapper;

import com.unihub.workshop.registrations.domain.model.CancelledRegistrationResult;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.registrations.presentation.dto.RegistrationDetailResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationListResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationResponse;
import com.unihub.workshop.registrations.presentation.dto.RegistrationSummaryItemResponse;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RegistrationPresentationMapper {
    @Mapping(target = "studentId", source = "studentUserId")
    @Mapping(target = "qrPayload", source = "qrTicket.qrPayload")
    @Mapping(target = "qrSignature", source = "qrTicket.qrSignature")
    @Mapping(target = "qrIssuedAt", source = "qrTicket.qrIssuedAt")
    @Mapping(target = "refund", ignore = true)
    RegistrationResponse toResponse(Registration registration);

    default RegistrationResponse toCancelResponse(
        CancelledRegistrationResult result
    ) {
        if (result == null || result.registration() == null) {
            return null;
        }
        Registration reg = result.registration();
        RegistrationResponse.RefundDto refund = null;
        if (result.refundAmount() != null) {
            refund = new RegistrationResponse.RefundDto(
                result.refundAmount(),
                result.refundCurrency()
            );
        }
        return new RegistrationResponse(
            reg.id(),
            reg.workshopId(),
            reg.studentUserId(),
            reg.status().name(),
            reg.priceAtRegistration(),
            reg.currency(),
            null,
            null,
            null,
            null,
            reg.createdAt(),
            reg.updatedAt(),
            refund
        );
    }

    RegistrationDetailResponse toDetailResponse(RegistrationDetail detail);

    RegistrationSummaryItemResponse toSummaryItemResponse(
        RegistrationSummary summary
    );

    List<RegistrationSummaryItemResponse> toSummaryItemResponseList(
        List<RegistrationSummary> summaries
    );

    default RegistrationListResponse toListResponse(
        CursorPage<RegistrationSummary> page
    ) {
        if (page == null) {
            return null;
        }
        List<RegistrationSummaryItemResponse> items = toSummaryItemResponseList(
            page.items()
        );
        return RegistrationListResponse.of(
            items,
            page.nextCursor(),
            page.hasMore()
        );
    }
}
