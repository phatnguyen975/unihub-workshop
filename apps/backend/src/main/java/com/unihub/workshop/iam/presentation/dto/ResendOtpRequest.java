package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record ResendOtpRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email
) {}
