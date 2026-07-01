# Worked Example: Idempotency Design for Reservation Creation

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It designs the full idempotency mechanism for `POST /api/v1/resources/{resourceId}/reservations` — the same endpoint defined in the `api-contract-design` skill's worked example, which already states "Idempotency Requirement: Required" for exactly the reason this example now works through in detail. Pseudocode is used deliberately, with no specific language, framework, or database syntax, so this stays applicable regardless of your actual stack.

## Step 1 — Confirm This Needs the Skill

This endpoint creates a reservation and decrements shared capacity — a mutating operation. A client (web or mobile) calling it over a real network has genuine retry risk: a timeout gives no information about whether the server actually processed the request before the connection dropped. This clearly needs explicit idempotency design.

## Step 2 — Define the Key's Scope

**Scope:** `(userId, idempotencyKey)`.

The key alone is not sufficient scope — two different users could coincidentally generate the same key value (especially if a buggy client reuses a key across sessions). Scoping by `(userId, idempotencyKey)` ensures one user's retry can never collide with a different user's unrelated request.

The key itself is the client-generated `Idempotency-Key` header value already specified in the endpoint's contract — a UUID, generated once per logical reservation attempt, reused on every retry of that same attempt.

## Step 3 — Design the State Machine

```
           ┌─────────────┐
new key ──►│   PENDING   │  (claimed, operation in progress)
           └──────┬──────┘
                  │
      ┌───────────┴───────────┐
      ▼                       ▼
┌─────────────┐         ┌─────────────┐
│  COMPLETED  │         │   FAILED    │
│  (terminal) │         │  (terminal) │
└─────────────┘         └─────────────┘
```

- **PENDING:** the key has been claimed; the operation is in progress. A retry that arrives while in this state gets the in-flight response (see Step 7 below) — never a duplicate execution, never a hang.
- **COMPLETED:** the operation succeeded; the stored response is returned identically to any retry.
- **FAILED:** the operation failed. Whether a retry replays the stored failure or is allowed to attempt fresh depends on the failure category (see Step 5).

## Step 4 — Design the Atomic Claim Mechanism

```
function handleCreateReservation(userId, idempotencyKey, resourceId):

    // Atomic claim: insert with a uniqueness constraint on
    // (userId, idempotencyKey). This is the ONLY place duplicate
    // detection happens — never a separate prior "check" call.
    claimResult = idempotencyStore.tryInsert(
        userId: userId,
        idempotencyKey: idempotencyKey,
        status: "PENDING"
    )

    if claimResult.alreadyExisted:
        existingRecord = claimResult.existingRecord
        return handleExistingRecord(existingRecord)

    // This is a genuinely new request — proceed with the operation.
    return processNewReservation(userId, resourceId, claimResult.recordId)
```

The uniqueness constraint on `(userId, idempotencyKey)` at the storage layer is what makes this atomic — `tryInsert` either succeeds (this is the first time this key has been seen) or fails because the row already exists (it's a duplicate), and there is no window between "checking" and "claiming" for two concurrent attempts to both think they're first.

## Step 5 — Decide What's Persisted

**On success (→ `COMPLETED`):**

```
idempotencyStore.update(recordId,
    status: "COMPLETED",
    responseStatusCode: 201,
    responseBody: serializedReservation
)
```

The full response — status code and body — is stored, not just a "succeeded" flag. A retry against this record returns exactly this, unchanged.

**On failure (→ `FAILED`), with explicit per-category handling:**

```
function handleFailure(recordId, error):
    if error is CapacityExhaustedError or error is DuplicateReservationError:
        // Permanent, deterministic failures: retrying won't change the
        // outcome unless something else in the system changes first.
        // Store the failure and replay it identically on retry.
        idempotencyStore.update(recordId,
            status: "FAILED",
            responseStatusCode: 409,
            responseBody: serializedError(error),
            retryable: false
        )
    else if error is TransientDownstreamTimeoutError:
        // Transient failure: this specific attempt failed for a reason
        // unrelated to the request's validity. Mark the record so a
        // retry is allowed to attempt fresh, rather than replaying a
        // stale transient failure forever.
        idempotencyStore.delete(recordId) // release the claim entirely
```

This distinction matters: a `CapacityExhaustedError` is a true fact about the world at the time of the request — replaying it on retry is correct. A downstream timeout is not a fact about the request's validity — it would be wrong to permanently condemn this idempotency key to always fail just because one attempt hit a transient blip.

## Step 6 — Define the TTL

**TTL: 24 hours**, justified as follows: This endpoint is called from mobile clients, which per realistic mobile-network failure data can experience extended connectivity gaps (a user losing signal, switching networks, or closing and reopening the app hours later before retrying a failed action they remember attempting). 24 hours comfortably covers this without holding idempotency records indefinitely. A pure server-to-server integration with much tighter, more predictable retry windows would justify a shorter TTL — the number is chosen for this specific client population, not copied from an unrelated default.

A scheduled cleanup job removes records past their TTL, consistent with how this project already handles other TTL-bound tables.

## Step 7 — Handle Every State on Retry

```
function handleExistingRecord(record):
    if record.status == "COMPLETED":
        return response(record.responseStatusCode, record.responseBody)

    if record.status == "FAILED":
        // Only reached for non-retryable failures — retryable failures
        // were deleted in Step 5, so a fresh attempt is already
        // underway by the time a retry would land here.
        return response(record.responseStatusCode, record.responseBody)

    if record.status == "PENDING":
        // The original attempt is still in flight. Do NOT re-execute.
        // Do NOT hang indefinitely. Tell the caller to wait and retry.
        return response(409, {
            error: "request-in-progress",
            retryAfterSeconds: 2
        })
```

## Step 8 — Identify Other Async Boundaries

This endpoint's success path, per the earlier `api-contract-design` example, also publishes a `reservation-created` domain event consumed by a separate notification-handling component (see the `modular-boundary-enforcement` example, where exactly this event is used). That downstream consumer is a **separate idempotency domain**:

- If the message broker delivering this event provides at-least-once delivery (the common default), the notification consumer must perform its own deduplication — keyed on the event's own unique ID, not on anything from this endpoint's `Idempotency-Key`.
- This endpoint's idempotency design protects against the _client_ retrying the HTTP request. It does nothing to protect against the _broker_ redelivering the resulting event. Both need their own treatment — designing one does not cover the other.

## Step 9 — The Concurrent-Retry Test Scenario

```
test "two concurrent requests with the same idempotency key result in exactly one reservation":
    idempotencyKey = generateUuid()

    // Simulate two requests arriving at effectively the same time
    result1, result2 = runConcurrently(
        () => handleCreateReservation(userId, idempotencyKey, resourceId),
        () => handleCreateReservation(userId, idempotencyKey, resourceId)
    )

    // Exactly one of them performed the real work; the other received
    // the stored result (or the in-flight response, depending on timing)
    expect: countReservationsFor(userId, resourceId) == 1
    expect: resourceCapacityDecrementedExactlyOnce(resourceId)

test "a retry after successful completion returns the original response, unchanged":
    idempotencyKey = generateUuid()

    firstResult = handleCreateReservation(userId, idempotencyKey, resourceId)
    secondResult = handleCreateReservation(userId, idempotencyKey, resourceId) // retry

    expect: secondResult.statusCode == firstResult.statusCode
    expect: secondResult.body == firstResult.body
    expect: countReservationsFor(userId, resourceId) == 1 // not 2
```

## What This Example Demonstrates

- The key's scope (`userId` + client key) prevents cross-user collision, not just same-user duplicate detection.
- The atomic claim mechanism (`tryInsert` with a uniqueness constraint) is the only place duplication is decided — there is no separate check-then-act sequence anywhere in the flow.
- Permanent failures (capacity exhausted) and transient failures (downstream timeout) are handled with explicitly different retry semantics, instead of one blanket rule for "failure."
- The in-flight (`PENDING`) case has its own defined response, distinct from both success and failure.
- The downstream notification consumer was explicitly called out as its own, separate idempotency domain — not assumed to be covered by this endpoint's design.
- Both required test scenarios (concurrent race, post-completion retry) are defined concretely, each asserting on the actual side effect count — not just on the HTTP response shape.
