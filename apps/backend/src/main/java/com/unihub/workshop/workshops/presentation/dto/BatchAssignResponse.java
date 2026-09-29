package com.unihub.workshop.workshops.presentation.dto;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BatchAssignResponse(
    UUID workshopId,
    List<AssignedStaffItemResponse> assignedStaffs
) {}
