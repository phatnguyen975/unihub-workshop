package com.unihub.workshop.iam.application.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        Integer refreshExpiresInSeconds,
        LoginUserResponse user) {
}
