package com.unihub.workshop.iam.application.ports.in;

public interface LoginUseCase {
    LoginResult login(String email, String password, String deviceInfo, String ipAddress);

    record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
    }
}
