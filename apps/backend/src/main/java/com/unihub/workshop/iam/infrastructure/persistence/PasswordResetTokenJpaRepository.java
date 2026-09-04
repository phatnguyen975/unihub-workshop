package com.unihub.workshop.iam.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    Optional<PasswordResetTokenJpaEntity> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT t FROM PasswordResetTokenJpaEntity t, UserJpaEntity u WHERE t.userId = u.id AND u.email = :email AND t.usedAt IS NULL AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    List<PasswordResetTokenJpaEntity> findActiveTokensByEmail(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now WHERE t.id = :id")
    void markConsumed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    void invalidateAllActiveTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
