package com.unihub.workshop.registrations.presentation.mapper;

import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
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
    RegistrationResponse toResponse(Registration registration);

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
