package com.unihub.workshop.iam.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);

    Optional<UserJpaEntity> findByIdAndOrganizationId(
        UUID id,
        UUID organizationId
    );

    boolean existsByEmail(String email);

    @Modifying
    @Query(
        """
        UPDATE UserJpaEntity u
        SET u.lastLoginAt = :time
        WHERE u.id = :id
        """
    )
    void updateLastLoginAt(@Param("id") UUID id, @Param("time") Instant time);

    @Modifying
    @Query(
        """
        UPDATE UserJpaEntity u
        SET u.isActive = true, u.updatedAt = :now
        WHERE u.id = :id
        """
    )
    void activateUser(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query(
        """
        UPDATE UserJpaEntity u
        SET u.isActive = :isActive, u.updatedAt = :now
        WHERE u.id = :id AND u.organizationId = :organizationId
        """
    )
    int updateIsActive(
        @Param("id") UUID id,
        @Param("organizationId") UUID organizationId,
        @Param("isActive") boolean isActive,
        @Param("now") Instant now
    );

    @Modifying
    @Query(
        """
        UPDATE UserJpaEntity u
        SET u.passwordHash = :newPasswordHash,
            u.isTemporaryPassword = false,
            u.lastLoginAt = :now,
            u.updatedAt = :now
        WHERE u.id = :id AND u.isTemporaryPassword = true
        """
    )
    int updateTemporaryPassword(
        @Param("id") UUID id,
        @Param("newPasswordHash") String newPasswordHash,
        @Param("now") Instant now
    );
}
