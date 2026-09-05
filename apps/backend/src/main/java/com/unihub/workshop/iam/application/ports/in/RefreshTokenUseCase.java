package com.unihub.workshop.iam.application.ports.in;

public interface RefreshTokenUseCase {
    RefreshResult refresh(String refreshTokenString, String userAgent, String ipAddress);

    record RefreshResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
    }
}
