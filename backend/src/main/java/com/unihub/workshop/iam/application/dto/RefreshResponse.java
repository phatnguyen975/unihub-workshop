package com.unihub.workshop.iam.application.dto;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        int refreshExpiresInSeconds,
        boolean refreshTokenRotated) {
}
