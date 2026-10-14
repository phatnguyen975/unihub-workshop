package com.unihub.workshop.shared.idempotency;

import java.util.UUID;

/**
 * Port defining cross-cutting two-tier idempotency coordination.
 */
public interface IdempotencyPort {
    /**
     * Attempts to claim the idempotency key for the authenticated user and request payload.
     *
     * @param userId authenticated user ID
     * @param idempotencyKey client-supplied UUID string
     * @param requestMethod HTTP method (e.g., "POST")
     * @param requestPath HTTP path (e.g., "/api/v1/registrations")
     * @param requestHash SHA-256 digest of method + path + payload
     * @param responseType target class to deserialize if a completed replay is found
     * @param <T> type of response payload
     * @return {@link ClaimResult.Acquired} if key is newly claimed (status PENDING),
     *         or {@link ClaimResult.Replayed} if key was previously COMPLETED
     */
    <T> ClaimResult<T> claim(
        UUID userId,
        String idempotencyKey,
        String requestMethod,
        String requestPath,
        String requestHash,
        Class<T> responseType
    );

    /**
     * Marks the idempotency key as COMPLETED with the resulting response payload,
     * updating PostgreSQL and populating L1 Redis with a 24-hour TTL.
     *
     * @param userId user ID
     * @param idempotencyKeyId DB primary key of the idempotency record
     * @param idempotencyKey client idempotency key string
     * @param requestHash SHA-256 digest
     * @param responseCode HTTP response code (e.g., 200, 201)
     * @param responsePayload completed response payload object
     * @param <T> type of response payload
     */
    <T> void complete(
        UUID userId,
        UUID idempotencyKeyId,
        String idempotencyKey,
        String requestHash,
        int responseCode,
        T responsePayload
    );

    /**
     * Overload for completing with default 200 OK response code.
     */
    default <T> void complete(
        UUID userId,
        UUID idempotencyKeyId,
        String idempotencyKey,
        String requestHash,
        T responsePayload
    ) {
        complete(
            userId,
            idempotencyKeyId,
            idempotencyKey,
            requestHash,
            200,
            responsePayload
        );
    }

    /**
     * Releases or marks the idempotency key as FAILED upon mutation failure,
     * invalidating the Redis L1 cache entry so the client may retry.
     *
     * @param userId user ID
     * @param idempotencyKey client idempotency key string
     */
    void fail(UUID userId, String idempotencyKey);
}
