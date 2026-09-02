package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    void updateLastLoginAt(UUID userId, Instant lastLoginAt);
}
