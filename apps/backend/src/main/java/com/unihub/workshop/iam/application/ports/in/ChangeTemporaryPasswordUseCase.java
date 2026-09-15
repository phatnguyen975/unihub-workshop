package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;
import lombok.Builder;

public interface ChangeTemporaryPasswordUseCase {
    ChangeTemporaryPasswordResult changeTemporaryPassword(
        String email,
        String currentPassword,
        String newPassword,
        String deviceInfo,
        String ipAddress
    );

    @Builder
    record ChangeTemporaryPasswordResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user
    ) implements AuthSessionResult {}
}
