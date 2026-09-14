package com.unihub.workshop.iam.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
    @Query("UPDATE UserJpaEntity u SET u.lastLoginAt = :time WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") UUID id, @Param("time") Instant time);

    @Modifying
    @Query(
        "UPDATE UserJpaEntity u SET u.isActive = true, u.updatedAt = :now WHERE u.id = :id"
    )
    void activateUser(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query(
        "UPDATE UserJpaEntity u SET u.isActive = :isActive, u.updatedAt = :now WHERE u.id = :id AND u.organizationId = :organizationId"
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

    @Query(
        """
        SELECT u FROM UserJpaEntity u
        WHERE u.organizationId = :organizationId
            AND u.id != :callerId
            AND (:role IS NULL OR u.role = :role)
            AND (:isActive IS NULL OR u.isActive = :isActive)
            AND (
                :search IS NULL OR :search = ''
                OR LOWER(u.fullName) LIKE CONCAT('%', LOWER(:search), '%') ESCAPE '\\'
                OR LOWER(u.email) LIKE CONCAT('%', LOWER(:search), '%') ESCAPE '\\'
                OR (u.phoneNumber IS NOT NULL AND LOWER(u.phoneNumber) LIKE CONCAT('%', LOWER(:search), '%') ESCAPE '\\')
            )
            AND (
                :cursorTime IS NULL
                OR u.createdAt < :cursorTime
                OR (u.createdAt = :cursorTime AND u.id < :cursorId)
            )
        ORDER BY u.createdAt DESC, u.id DESC
        """
    )
    List<UserJpaEntity> findMembers(
        @Param("organizationId") UUID organizationId,
        @Param("callerId") UUID callerId,
        @Param("role") String role,
        @Param("isActive") Boolean isActive,
        @Param("search") String search,
        @Param("cursorTime") Instant cursorTime,
        @Param("cursorId") UUID cursorId,
        Pageable pageable
    );
}
