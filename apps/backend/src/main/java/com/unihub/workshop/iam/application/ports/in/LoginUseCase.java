package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;

public interface LoginUseCase {
    LoginResult login(String email, String password, String deviceInfo, String ipAddress);

    record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            User user) implements AuthSessionResult {
    }
}
