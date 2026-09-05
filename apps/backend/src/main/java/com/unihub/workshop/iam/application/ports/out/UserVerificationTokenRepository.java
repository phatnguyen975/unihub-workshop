package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.UserVerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface UserVerificationTokenRepository {

    UserVerificationToken save(UserVerificationToken token);

    Optional<UserVerificationToken> findActiveTokenByUserEmailAndType(String email, String tokenType);

    Optional<UserVerificationToken> findLatestByUserIdAndType(UUID userId, String tokenType);

    void markConsumed(UUID tokenId);

    void invalidateActiveTokens(UUID userId, String tokenType);
}
