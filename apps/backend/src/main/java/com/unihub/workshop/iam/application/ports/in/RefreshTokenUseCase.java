package com.unihub.workshop.iam.application.ports.in;

import lombok.Builder;

public interface RefreshTokenUseCase {
    RefreshResult refresh(
        String refreshTokenString,
        String userAgent,
        String ipAddress
    );

    @Builder
    record RefreshResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
    ) implements TokenResult {}
}
