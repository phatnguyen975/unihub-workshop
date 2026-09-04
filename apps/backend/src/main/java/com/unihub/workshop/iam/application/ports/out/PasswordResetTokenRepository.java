package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findActiveTokenByUserEmail(String email);

    Optional<PasswordResetToken> findLatestByUserId(UUID userId);

    void markConsumed(UUID tokenId);
}
