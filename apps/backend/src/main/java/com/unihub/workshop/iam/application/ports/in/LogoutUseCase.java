package com.unihub.workshop.iam.application.ports.in;

import java.time.Instant;
import java.util.UUID;

public interface LogoutUseCase {
    void logout(
        String jti,
        Instant expiresAt,
        UUID userId,
        String refreshTokenString
    );
}
