package com.unihub.workshop.iam.presentation.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {
}
