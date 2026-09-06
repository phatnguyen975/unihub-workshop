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

    @Modifying
    @Query("UPDATE UserVerificationTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markConsumed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserVerificationTokenJpaEntity t SET t.usedAt = :now, t.updatedAt = :now " +
            "WHERE t.userId = :userId AND t.tokenType = :tokenType AND t.usedAt IS NULL")
    void invalidateActiveTokens(@Param("userId") UUID userId, @Param("tokenType") String tokenType,
            @Param("now") Instant now);
}
