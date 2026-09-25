package com.unihub.workshop.workshops.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SpeakerRequest(
    @NotBlank(message = "Speaker name is mandatory")
    @Size(max = 255, message = "Speaker name must not exceed 255 characters")
    String name,

    @NotBlank(message = "Speaker email is mandatory")
    @Email(message = "Speaker email must be a valid email address")
    @Size(max = 255, message = "Speaker email must not exceed 255 characters")
    String email,

    @Size(max = 2000, message = "Speaker bio must not exceed 2000 characters")
    String bio,

    @Size(max = 2048, message = "Speaker avatarUrl must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?://.*)?$",
        message = "Speaker avatarUrl must be a valid HTTP or HTTPS URL"
    )
    String avatarUrl,

    @Size(max = 2048, message = "Speaker linkedinUrl must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?://.*)?$",
        message = "Speaker linkedinUrl must be a valid HTTP or HTTPS URL"
    )
    String linkedinUrl
) {}
