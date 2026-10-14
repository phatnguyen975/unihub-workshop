package com.unihub.workshop.shared.idempotency;

import java.util.UUID;

/**
 * Result of claiming an idempotency key.
 *
 * @param <T> the type of the cached or returned response payload
 */
public sealed interface ClaimResult<T> permits
    ClaimResult.Acquired,
    ClaimResult.Replayed {
    /**
     * Key was successfully claimed as PENDING for execution.
     *
     * @param idempotencyKeyId the database primary key of the idempotency record
     */
    record Acquired<T>(UUID idempotencyKeyId) implements ClaimResult<T> {}

    /**
     * Key was previously COMPLETED, returning the cached response payload.
     *
     * @param response the deserialized previous response payload
     */
    record Replayed<T>(T response) implements ClaimResult<T> {}
}
