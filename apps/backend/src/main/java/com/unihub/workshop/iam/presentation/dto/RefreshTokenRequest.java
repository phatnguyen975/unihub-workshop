package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
    @NotBlank(message = "refreshToken must not be blank")
    @Size(max = 255, message = "refreshToken must not exceed 255 characters")
    String refreshToken
) {
    public RefreshTokenRequest {
        refreshToken = refreshToken != null ? refreshToken.trim() : null;
    }
}
