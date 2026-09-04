package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);

    void updateLastLoginAt(UUID userId, Instant lastLoginAt);

    void activate(UUID userId);
}
