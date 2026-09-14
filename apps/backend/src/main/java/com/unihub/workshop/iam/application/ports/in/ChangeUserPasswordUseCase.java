package com.unihub.workshop.iam.application.ports.in;

import java.util.UUID;

public interface ChangeUserPasswordUseCase {
    void changePassword(
        UUID userId,
        UUID currentSessionId,
        String currentPassword,
        String newPassword
    );
}
