package com.unihub.workshop.iam.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.lastLoginAt = :time WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") UUID id, @Param("time") Instant time);

    @Modifying
    @Query("UPDATE UserJpaEntity u SET u.isActive = true, u.updatedAt = :now WHERE u.id = :id")
    void activateUser(@Param("id") UUID id, @Param("now") Instant now);
}
