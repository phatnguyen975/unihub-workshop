package com.unihub.workshop.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-tier idempotency adapter combining Redis L1 cache for sub-millisecond deduplication
 * and PostgreSQL L2 durable idempotency keys table for guaranteed exactly-once processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotencyAdapter implements IdempotencyPort {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    public record CachedIdempotencyEntry(
        String status,
        String hash,
        Integer responseCode,
        String responseBody
    ) {}

    @Override
    @Transactional
    public <T> ClaimResult<T> claim(
        UUID userId,
        String idempotencyKey,
        String requestMethod,
        String requestPath,
        String requestHash,
        Class<T> responseType
    ) {
        String redisKey = formatKey(userId, idempotencyKey);

        // Step 1: L1 Redis Check (Cache-Aside Read)
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                CachedIdempotencyEntry entry = OBJECT_MAPPER.readValue(
                    cachedJson,
                    CachedIdempotencyEntry.class
                );

                if (entry.hash() != null && !entry.hash().equals(requestHash)) {
                    throw new ConflictException(
                        "Idempotency key was previously used with a different request payload"
                    );
                }

                if ("PENDING".equalsIgnoreCase(entry.status())) {
                    throw new IdempotencyConflictException(
                        "Request is currently in progress",
                        2
                    );
                }

                if ("COMPLETED".equalsIgnoreCase(entry.status())) {
                    T replayed = deserializePayload(
                        entry.responseBody(),
                        responseType
                    );
                    if (replayed != null) {
                        log.info(
                            "L1 Redis hit for completed idempotency key: {}",
                            redisKey
                        );
                        return new ClaimResult.Replayed<>(replayed);
                    }
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                "Redis L1 read failed for idempotency key '{}', falling back to primary DB: {}",
                redisKey,
                e.getMessage()
            );
        }

        // Step 2: L1 Miss -> L2 DB Atomic Claim
        UUID newRecordId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
        int inserted = idempotencyKeyJpaRepository.insertPendingIfAbsent(
            newRecordId,
            userId,
            idempotencyKey,
            requestMethod,
            requestPath,
            requestHash,
            expiresAt
        );

        if (inserted == 1) {
            // Genuinely new request claimed
            setTransientPendingLock(redisKey, requestHash);
            return new ClaimResult.Acquired<>(newRecordId);
        }

        // Conflict Detected in L2: fetch existing DB record
        IdempotencyKeyJpaEntity existing = idempotencyKeyJpaRepository
            .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .orElseThrow(() ->
                new ConflictException("Concurrent conflict on idempotency key")
            );

        if (!requestHash.equals(existing.getRequestHash())) {
            throw new ConflictException(
                "Idempotency key was previously used with a different request payload"
            );
        }

        if ("PENDING".equalsIgnoreCase(existing.getStatus())) {
            throw new IdempotencyConflictException(
                "Request is currently in progress",
                2
            );
        }

        if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
            T replayed = deserializePayload(
                existing.getResponseBody(),
                responseType
            );
            if (replayed != null) {
                backfillL1Cache(
                    redisKey,
                    requestHash,
                    existing.getResponseCode(),
                    existing.getResponseBody(),
                    existing.getExpiresAt()
                );
                return new ClaimResult.Replayed<>(replayed);
            }
        }

        if ("FAILED".equalsIgnoreCase(existing.getStatus())) {
            throw new ConflictException(
                "Idempotency key previously failed; please generate a new idempotency key"
            );
        }

        throw new ConflictException("Idempotency key conflict");
    }

    @Override
    @Transactional
    public <T> void complete(
        UUID userId,
        UUID idempotencyKeyId,
        String idempotencyKey,
        String requestHash,
        int responseCode,
        T responsePayload
    ) {
        String redisKey = formatKey(userId, idempotencyKey);
        String responseBodyJson = null;
        if (responsePayload != null) {
            try {
                responseBodyJson = OBJECT_MAPPER.writeValueAsString(
                    responsePayload
                );
            } catch (Exception e) {
                log.error(
                    "Failed to serialize response payload for idempotency completion",
                    e
                );
            }
        }

        // Update DB L2 record
        if (idempotencyKeyId != null) {
            final String finalResponseBodyJson = responseBodyJson;
            idempotencyKeyJpaRepository
                .findById(idempotencyKeyId)
                .ifPresent(entity -> {
                    entity.setStatus("COMPLETED");
                    entity.setResponseCode(responseCode);
                    entity.setResponseBody(finalResponseBodyJson);
                    idempotencyKeyJpaRepository.save(entity);
                });
        }

        // Populate Redis L1 Cache (24h TTL)
        try {
            CachedIdempotencyEntry entry = new CachedIdempotencyEntry(
                "COMPLETED",
                requestHash,
                responseCode,
                responseBodyJson
            );
            redisTemplate
                .opsForValue()
                .set(
                    redisKey,
                    OBJECT_MAPPER.writeValueAsString(entry),
                    Duration.ofHours(24)
                );
        } catch (Exception e) {
            log.warn(
                "Failed to populate Redis L1 cache on completion: {}",
                e.getMessage()
            );
        }
    }

    @Override
    @Transactional
    public void fail(UUID userId, String idempotencyKey) {
        String redisKey = formatKey(userId, idempotencyKey);

        // Invalidate Redis L1
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn(
                "Failed to delete Redis L1 key on failure: {}",
                e.getMessage()
            );
        }

        // Mark DB L2 record as FAILED
        idempotencyKeyJpaRepository
            .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
            .ifPresent(entity -> {
                entity.setStatus("FAILED");
                idempotencyKeyJpaRepository.save(entity);
            });
    }

    private void setTransientPendingLock(String redisKey, String requestHash) {
        try {
            CachedIdempotencyEntry entry = new CachedIdempotencyEntry(
                "PENDING",
                requestHash,
                null,
                null
            );
            redisTemplate
                .opsForValue()
                .set(
                    redisKey,
                    OBJECT_MAPPER.writeValueAsString(entry),
                    Duration.ofSeconds(60)
                );
        } catch (Exception e) {
            log.warn(
                "Failed to write transient PENDING lock to Redis: {}",
                e.getMessage()
            );
        }
    }

    private void backfillL1Cache(
        String redisKey,
        String requestHash,
        Integer responseCode,
        String responseBodyJson,
        Instant expiresAt
    ) {
        try {
            long remainingSeconds = Duration.between(
                Instant.now(),
                expiresAt
            ).getSeconds();
            if (remainingSeconds > 0) {
                CachedIdempotencyEntry entry = new CachedIdempotencyEntry(
                    "COMPLETED",
                    requestHash,
                    responseCode,
                    responseBodyJson
                );
                redisTemplate
                    .opsForValue()
                    .set(
                        redisKey,
                        OBJECT_MAPPER.writeValueAsString(entry),
                        Duration.ofSeconds(remainingSeconds)
                    );
            }
        } catch (Exception e) {
            log.warn("Failed to backfill Redis L1 cache from L2 DB record", e);
        }
    }

    private <T> T deserializePayload(
        String responseBodyJson,
        Class<T> responseType
    ) {
        if (
            responseBodyJson == null ||
            responseBodyJson.isBlank() ||
            responseType == null
        ) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(responseBodyJson, responseType);
        } catch (Exception e) {
            log.error(
                "Failed to deserialize response body from JSON for type {}",
                responseType.getName(),
                e
            );
            return null;
        }
    }

    private String formatKey(UUID userId, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + idempotencyKey;
    }
}
