package com.unihub.workshop.iam.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserVerificationTokenJpaRepository extends JpaRepository<UserVerificationTokenJpaEntity, UUID> {

    Optional<UserVerificationTokenJpaEntity> findFirstByUserIdAndTokenTypeOrderByCreatedAtDesc(UUID userId,
            String tokenType);

    @Query("SELECT t FROM UserVerificationTokenJpaEntity t, UserJpaEntity u " +
            "WHERE t.userId = u.id AND u.email = :email AND t.tokenType = :tokenType " +
            "AND t.usedAt IS NULL AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    Optional<UserVerificationTokenJpaEntity> findActiveTokenByEmailAndType(
            @Param("email") String email,
            @Param("tokenType") String tokenType,
            @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserVerificationTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now WHERE t.id = :id")
    void markConsumed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserVerificationTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now " +
            "WHERE t.userId = :userId AND t.tokenType = :tokenType AND t.usedAt IS NULL")
    void invalidateActiveTokens(@Param("userId") UUID userId, @Param("tokenType") String tokenType,
            @Param("now") Instant now);
}
