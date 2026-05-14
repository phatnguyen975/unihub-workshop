package com.unihub.workshop.iam.domain.repository;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;
import java.util.Optional;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findActiveByTokenHash(String tokenHash);
}
