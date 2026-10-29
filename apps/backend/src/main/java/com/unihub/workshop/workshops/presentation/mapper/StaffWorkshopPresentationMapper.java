package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.presentation.dto.StaffAssignedWorkshopResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StaffWorkshopPresentationMapper {
    @Mapping(
        target = "status",
        expression = "java(workshop.status() != null ? workshop.status().name() : null)"
    )
    StaffAssignedWorkshopResponse toResponse(StaffAssignedWorkshop workshop);

    default PageResponse<StaffAssignedWorkshopResponse> toPageResponse(
        CursorPage<StaffAssignedWorkshop> page
    ) {
        if (page == null) {
            return null;
        }
        return PageResponse.from(page, this::toResponse);
    }
}
