package com.unihub.workshop.iam.application.ports.out;

import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.pagination.CursorPage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);

    void updateLastLoginAt(UUID userId, Instant lastLoginAt);

    void activate(UUID userId);

    boolean updateTemporaryPassword(
        UUID userId,
        String newPasswordHash,
        Instant loginTime
    );

    CursorPage<User> findMembersByOrganizationId(
        UUID organizationId,
        UUID callerId,
        UserRole role,
        Boolean isActive,
        String q,
        String cursor,
        int size
    );

    void updateIsActive(UUID userId, UUID organizationId, boolean isActive);
}
