package com.unihub.workshop.iam.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateProfileRequest(
        @Pattern(regexp = "^(?!\\s*$).+", message = "Full name must not be blank") @Size(max = 200, message = "Full name must not exceed 200 characters") String fullName,
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone number must match international format ^\\+?[0-9]{8,15}$") @Size(max = 20, message = "Phone number must not exceed 20 characters") String phoneNumber,
        @Pattern(regexp = "^(http|https)://.*$", message = "Avatar URL must be a well-formed absolute URL") @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters") String avatarUrl) {
}
