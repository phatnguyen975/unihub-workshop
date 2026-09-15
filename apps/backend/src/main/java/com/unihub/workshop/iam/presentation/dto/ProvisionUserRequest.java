package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record ProvisionUserRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email,
    @NotBlank(message = "Full name must not be blank")
    @Size(max = 200, message = "Full name must not exceed 200 characters")
    String fullName,
    @Pattern(
        regexp = "^\\+[1-9]\\d{1,14}$",
        message = "Phone number must be in international E.164 format"
    )
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    String phoneNumber,
    @NotNull(message = "Role must not be null") String role
) {}
