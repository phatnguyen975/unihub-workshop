package com.unihub.workshop.shared.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyKeyJpaRepository
    extends JpaRepository<IdempotencyKeyJpaEntity, UUID>
{
    Optional<IdempotencyKeyJpaEntity> findByUserIdAndIdempotencyKey(
        UUID userId,
        String idempotencyKey
    );

    @Modifying
    @Query(
        value = """
        INSERT INTO idempotency_keys (id, user_id, idempotency_key, request_method, request_path, request_hash, status, expires_at, created_at, updated_at)
        VALUES (:id, :userId, :idempotencyKey, :requestMethod, :requestPath, :requestHash, 'PENDING', :expiresAt, NOW(), NOW())
        ON CONFLICT (user_id, idempotency_key) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertPendingIfAbsent(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("requestMethod") String requestMethod,
        @Param("requestPath") String requestPath,
        @Param("requestHash") String requestHash,
        @Param("expiresAt") Instant expiresAt
    );
}
