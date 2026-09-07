package com.unihub.workshop.iam.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity r SET r.revokedAt = :revokedAt WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllActiveTokensForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    @Query("""
            SELECT r FROM RefreshTokenJpaEntity r
            WHERE r.userId = :userId
                AND r.revokedAt IS NULL
                AND r.expiresAt > :now
                AND (
                    :cursorTime IS NULL OR r.createdAt < :cursorTime
                    OR (r.createdAt = :cursorTime AND r.id < :cursorId)
                )
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<RefreshTokenJpaEntity> findActiveSessions(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);

    @Query("""
            SELECT r FROM RefreshTokenJpaEntity r
            WHERE r.id = :id
                AND r.userId = :userId
                AND r.revokedAt IS NULL
                AND r.expiresAt > :now
            """)
    Optional<RefreshTokenJpaEntity> findActiveSessionByIdAndUserId(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity r
            SET r.revokedAt = :revokedAt
            WHERE r.id = :id
                AND r.userId = :userId
                AND r.revokedAt IS NULL
                AND r.expiresAt > :now
            """)
    int revokeActiveSession(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("now") Instant now);

    @Query("""
            SELECT r FROM RefreshTokenJpaEntity r
            WHERE r.userId = :userId
                AND r.id != :currentSessionId
                AND r.revokedAt IS NULL
                AND r.expiresAt > :now
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<RefreshTokenJpaEntity> findActiveRemoteSessions(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId,
            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            UPDATE RefreshTokenJpaEntity r
            SET r.revokedAt = :revokedAt
            WHERE r.userId = :userId
                AND r.id != :currentSessionId
                AND r.revokedAt IS NULL
                AND r.expiresAt > :now
            """)
    int revokeAllOtherActiveSessions(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId,
            @Param("revokedAt") Instant revokedAt,
            @Param("now") Instant now);
}
