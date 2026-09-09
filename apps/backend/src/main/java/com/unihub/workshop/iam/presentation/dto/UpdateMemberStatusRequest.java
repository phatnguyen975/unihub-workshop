package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberStatusRequest(
        @NotNull(message = "isActive is required") Boolean isActive) {
}
