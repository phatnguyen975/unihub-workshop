package com.unihub.workshop.workshops.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchAssignResponse(
    UUID workshopId,
    List<AssignedStaffItemResponse> assignedStaffs
) {}
