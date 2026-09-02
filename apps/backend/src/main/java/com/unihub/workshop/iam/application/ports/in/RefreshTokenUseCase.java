package com.unihub.workshop.iam.application.ports.in;

public interface RefreshTokenUseCase {
    LoginUseCase.LoginResult refresh(String refreshTokenString, String userAgent, String ipAddress);
}
