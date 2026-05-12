package com.unihub.workshop.iam.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName,
        @Pattern(regexp = "^\\+?[0-9\\- ]{7,20}$", message = "must be a valid phone format") String phone) {
}
