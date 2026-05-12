package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResendOtpRequest(
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = "REGISTER|RESET_PASSWORD", message = "must be REGISTER or RESET_PASSWORD") String intent) {
}
