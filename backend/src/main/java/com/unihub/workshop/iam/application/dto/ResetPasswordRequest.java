package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
                @NotBlank String resetToken,
                @NotBlank @Size(min = 8) String newPassword,
                @NotBlank String confirmPassword) {
}
