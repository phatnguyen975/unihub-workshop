package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.PasswordResetToken;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
}
