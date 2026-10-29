package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.shared.security.SecurityContextUtils;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsUseCase;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.presentation.dto.StaffAssignedWorkshopResponse;
import com.unihub.workshop.workshops.presentation.mapper.StaffWorkshopPresentationMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops/assigned")
@PreAuthorize("hasRole('STAFF')")
@RequiredArgsConstructor
public class StaffWorkshopController {

    private final ListStaffAssignedWorkshopsUseCase listStaffAssignedWorkshopsUseCase;
    private final StaffWorkshopPresentationMapper presentationMapper;

    @GetMapping
    public ApiResponse<
        PageResponse<StaffAssignedWorkshopResponse>
    > getAssignedWorkshops(
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "size", required = false) Integer size,
        Authentication authentication
    ) {
        SecurityContextUtils.verifyRole(authentication, "STAFF");
        UUID staffUserId = SecurityContextUtils.extractUserId(authentication);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, cursor, size);

        CursorPage<StaffAssignedWorkshop> page =
            listStaffAssignedWorkshopsUseCase.execute(query);
        PageResponse<StaffAssignedWorkshopResponse> response =
            presentationMapper.toPageResponse(page);

        return ApiResponse.success(
            response,
            "Assigned workshops retrieved successfully"
        );
    }
}
