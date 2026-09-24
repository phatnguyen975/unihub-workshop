package com.unihub.workshop.iam.presentation.dto;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
    @Size(max = 255, message = "Refresh token must not exceed 255 characters")
    String refreshToken
) {
    public LogoutRequest {
        refreshToken =
            refreshToken != null && !refreshToken.isBlank()
                ? refreshToken.trim()
                : null;
    }
}
