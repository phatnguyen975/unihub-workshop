package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateStaffStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|LOCKED)$", message = "status must be ACTIVE or LOCKED") String status) {
}
