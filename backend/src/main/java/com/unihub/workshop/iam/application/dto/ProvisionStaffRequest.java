package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvisionStaffRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(max = 120) String fullName) {
}
