package com.unihub.workshop.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisIdempotencyAdapterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IdempotencyKeyJpaRepository repository;
    private RedisIdempotencyAdapter adapter;

    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private final UUID userId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();
    private final String redisKey =
        "idempotency:" + userId + ":" + idempotencyKey;
    private final String requestHash = "hash123";

    record TestPayload(String message, int count) {}

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository = mock(IdempotencyKeyJpaRepository.class);
        adapter = new RedisIdempotencyAdapter(redisTemplate, repository);
    }

    @Test
    @DisplayName(
        "claim: L1 hit with COMPLETED status returns ClaimResult.Replayed with deserialized payload"
    )
    void claim_L1HitCompleted_ReturnsReplayed() throws Exception {
        TestPayload payload = new TestPayload("Success", 42);
        RedisIdempotencyAdapter.CachedIdempotencyEntry entry =
            new RedisIdempotencyAdapter.CachedIdempotencyEntry(
                "COMPLETED",
                requestHash,
                200,
                OBJECT_MAPPER.writeValueAsString(payload)
            );
        when(valueOperations.get(redisKey)).thenReturn(
            OBJECT_MAPPER.writeValueAsString(entry)
        );

        ClaimResult<TestPayload> result = adapter.claim(
            userId,
            idempotencyKey,
            "POST",
            "/api/v1/test",
            requestHash,
            TestPayload.class
        );

        assertThat(result).isInstanceOf(ClaimResult.Replayed.class);
        ClaimResult.Replayed<TestPayload> replayed =
            (ClaimResult.Replayed<TestPayload>) result;
        assertThat(replayed.response().message()).isEqualTo("Success");
        assertThat(replayed.response().count()).isEqualTo(42);
    }

    @Test
    @DisplayName(
        "claim: L1 hit with PENDING status throws IdempotencyConflictException with retry-after: 2"
    )
    void claim_L1HitPending_ThrowsIdempotencyConflictException()
        throws Exception {
        RedisIdempotencyAdapter.CachedIdempotencyEntry entry =
            new RedisIdempotencyAdapter.CachedIdempotencyEntry(
                "PENDING",
                requestHash,
                null,
                null
            );
        when(valueOperations.get(redisKey)).thenReturn(
            OBJECT_MAPPER.writeValueAsString(entry)
        );

        assertThatThrownBy(() ->
            adapter.claim(
                userId,
                idempotencyKey,
                "POST",
                "/api/v1/test",
                requestHash,
                TestPayload.class
            )
        )
            .isInstanceOf(IdempotencyConflictException.class)
            .satisfies(ex -> {
                IdempotencyConflictException ice =
                    (IdempotencyConflictException) ex;
                assertThat(ice.getRetryAfterSeconds()).isEqualTo(2);
            });
    }

    @Test
    @DisplayName(
        "claim: L1 hit with payload hash mismatch throws ConflictException"
    )
    void claim_L1HitHashMismatch_ThrowsConflictException() throws Exception {
        RedisIdempotencyAdapter.CachedIdempotencyEntry entry =
            new RedisIdempotencyAdapter.CachedIdempotencyEntry(
                "COMPLETED",
                "different-hash",
                200,
                "{}"
            );
        when(valueOperations.get(redisKey)).thenReturn(
            OBJECT_MAPPER.writeValueAsString(entry)
        );

        assertThatThrownBy(() ->
            adapter.claim(
                userId,
                idempotencyKey,
                "POST",
                "/api/v1/test",
                requestHash,
                TestPayload.class
            )
        )
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName(
        "claim: L1 miss and L2 insert succeeds (1) returns ClaimResult.Acquired and sets transient lock"
    )
    void claim_L1MissL2InsertSuccess_ReturnsAcquired() {
        when(valueOperations.get(redisKey)).thenReturn(null);
        when(
            repository.insertPendingIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(idempotencyKey),
                eq("POST"),
                eq("/api/v1/test"),
                eq(requestHash),
                any(Instant.class)
            )
        ).thenReturn(1);

        ClaimResult<TestPayload> result = adapter.claim(
            userId,
            idempotencyKey,
            "POST",
            "/api/v1/test",
            requestHash,
            TestPayload.class
        );

        assertThat(result).isInstanceOf(ClaimResult.Acquired.class);
        verify(valueOperations).set(
            eq(redisKey),
            anyString(),
            eq(Duration.ofSeconds(60))
        );
    }

    @Test
    @DisplayName(
        "claim: L1 miss, L2 conflict with PENDING in DB throws IdempotencyConflictException"
    )
    void claim_L1MissL2ConflictPending_ThrowsIdempotencyConflictException() {
        when(valueOperations.get(redisKey)).thenReturn(null);
        when(
            repository.insertPendingIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(idempotencyKey),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class)
            )
        ).thenReturn(0);

        IdempotencyKeyJpaEntity existing = IdempotencyKeyJpaEntity.builder()
            .userId(userId)
            .idempotencyKey(idempotencyKey)
            .requestHash(requestHash)
            .status("PENDING")
            .build();
        when(
            repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
        ).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            adapter.claim(
                userId,
                idempotencyKey,
                "POST",
                "/api/v1/test",
                requestHash,
                TestPayload.class
            )
        )
            .isInstanceOf(IdempotencyConflictException.class)
            .satisfies(ex -> {
                IdempotencyConflictException ice =
                    (IdempotencyConflictException) ex;
                assertThat(ice.getRetryAfterSeconds()).isEqualTo(2);
            });
    }

    @Test
    @DisplayName(
        "claim: L1 miss, L2 conflict with COMPLETED in DB backfills L1 and returns ClaimResult.Replayed"
    )
    void claim_L1MissL2ConflictCompleted_BackfillsAndReturnsReplayed()
        throws Exception {
        when(valueOperations.get(redisKey)).thenReturn(null);
        when(
            repository.insertPendingIfAbsent(
                any(UUID.class),
                eq(userId),
                eq(idempotencyKey),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class)
            )
        ).thenReturn(0);

        TestPayload payload = new TestPayload("Replayed Payload", 99);
        IdempotencyKeyJpaEntity existing = IdempotencyKeyJpaEntity.builder()
            .userId(userId)
            .idempotencyKey(idempotencyKey)
            .requestHash(requestHash)
            .status("COMPLETED")
            .responseCode(201)
            .responseBody(OBJECT_MAPPER.writeValueAsString(payload))
            .expiresAt(Instant.now().plus(Duration.ofHours(20)))
            .build();
        when(
            repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
        ).thenReturn(Optional.of(existing));

        ClaimResult<TestPayload> result = adapter.claim(
            userId,
            idempotencyKey,
            "POST",
            "/api/v1/test",
            requestHash,
            TestPayload.class
        );

        assertThat(result).isInstanceOf(ClaimResult.Replayed.class);
        ClaimResult.Replayed<TestPayload> replayed =
            (ClaimResult.Replayed<TestPayload>) result;
        assertThat(replayed.response().message()).isEqualTo("Replayed Payload");
        assertThat(replayed.response().count()).isEqualTo(99);
        verify(valueOperations).set(
            eq(redisKey),
            anyString(),
            any(Duration.class)
        );
    }

    @Test
    @DisplayName(
        "complete: updates DB entity to COMPLETED and sets 24h Redis L1 entry"
    )
    void complete_UpdatesDbAndSetsRedis() {
        UUID keyId = UUID.randomUUID();
        IdempotencyKeyJpaEntity entity = IdempotencyKeyJpaEntity.builder()
            .id(keyId)
            .userId(userId)
            .idempotencyKey(idempotencyKey)
            .status("PENDING")
            .build();
        when(repository.findById(keyId)).thenReturn(Optional.of(entity));

        TestPayload payload = new TestPayload("Completed", 100);

        adapter.complete(
            userId,
            keyId,
            idempotencyKey,
            requestHash,
            201,
            payload
        );

        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        assertThat(entity.getResponseCode()).isEqualTo(201);
        assertThat(entity.getResponseBody()).contains("Completed");
        verify(repository).save(entity);
        verify(valueOperations).set(
            eq(redisKey),
            anyString(),
            eq(Duration.ofHours(24))
        );
    }

    @Test
    @DisplayName("fail: deletes Redis L1 key and marks DB entity as FAILED")
    void fail_DeletesRedisAndMarksDbFailed() {
        IdempotencyKeyJpaEntity entity = IdempotencyKeyJpaEntity.builder()
            .userId(userId)
            .idempotencyKey(idempotencyKey)
            .status("PENDING")
            .build();
        when(
            repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
        ).thenReturn(Optional.of(entity));

        adapter.fail(userId, idempotencyKey);

        verify(redisTemplate).delete(redisKey);
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        verify(repository).save(entity);
    }
}
