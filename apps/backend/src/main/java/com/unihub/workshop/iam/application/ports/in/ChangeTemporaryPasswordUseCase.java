package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;

public interface ChangeTemporaryPasswordUseCase {
    ChangeTemporaryPasswordResult changeTemporaryPassword(
        String email,
        String currentPassword,
        String newPassword,
        String deviceInfo,
        String ipAddress
    );

    record ChangeTemporaryPasswordResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user
    ) implements AuthSessionResult {}
}
