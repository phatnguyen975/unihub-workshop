package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254)
    String email,
    @NotBlank(message = "Password is required") @Size(max = 255) String password
) {}
