package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
                @Email @NotBlank String email) {
}
