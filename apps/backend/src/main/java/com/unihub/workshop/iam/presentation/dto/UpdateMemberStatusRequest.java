package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UpdateMemberStatusRequest(
    @NotNull(message = "isActive is required") Boolean isActive
) {}
