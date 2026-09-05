package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.User;

public record AuthResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        User user) implements AuthSessionResult {
}
