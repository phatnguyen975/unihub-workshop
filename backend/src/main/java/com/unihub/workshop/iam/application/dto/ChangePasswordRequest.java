package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
                @NotBlank String currentPassword,
                @NotBlank @Size(min = 8) String newPassword,
                @NotBlank String confirmPassword) {
}
