# Worked Example: Building One Task's Full Vertical Slice, Layer by Layer

> [!NOTE]
>
> - This is an illustrative, domain-neutral example showing how to apply this skill. It builds task **T1** from the worked `tasks.md` example in the `task-planning-and-breakdown` skill — _"A user can create a reservation for a resource with available capacity, and is correctly rejected when capacity or duplication rules are violated"_ — all the way through every layer it needs, so this example can be read as a continuation of the `spec-driven-development` → `api-contract-design` → `task-planning-and-breakdown` walkthrough. Pseudocode is used deliberately, with no specific language or framework, so this stays applicable regardless of your actual stack.
> - What this example deliberately does **not** do? It does not stop after the Domain or Application layer and call the task "done." All four layer steps below belong to the _same_ task, and the task is only complete once the capability is verifiable through the real HTTP endpoint, exactly as T1's acceptance criteria in `tasks.md` require.

## The Task, As Handed Off

From `tasks.md`:

> **T1 — A user can create a reservation for a resource with available capacity, and is correctly rejected when capacity or duplication rules are violated**  
> **Layers touched:** Domain, Application, Infrastructure, Presentation  
> **Acceptance Criteria:**
>
> - Calling `POST /api/v1/resources/{resourceId}/reservations` against a resource with available capacity returns `201` with an `ACTIVE` reservation, and remaining capacity is reduced by exactly one
> - Calling the same endpoint against zero remaining capacity returns `409 capacity-exhausted`, no reservation created
> - A duplicate active reservation for the same user/resource returns `409 duplicate-reservation`
> - Retrying with the same `Idempotency-Key` returns the original `201` without decrementing capacity again
> - Two concurrent requests for the last unit of capacity result in exactly one success and one `409`

## Step 1 — Confirm the Boundary

**In scope for this task:** Every layer needed to make the five acceptance criteria above true, verifiable through the real endpoint.

**Explicitly out of scope:** Task T2 (cancellation) — it depends on T1 being complete and is a separate task, not a layer of this one.

## Step 2 — Order the Layers by Dependency

```
Layer step 1: Domain          — Capacity (zero-floor invariant) and Reservation (ACTIVE state on creation)
Layer step 2: Application     — CreateReservationUseCase orchestrating the reject/success paths, plus the idempotency guard
Layer step 3: Infrastructure  — repository adapters and the schema migration
Layer step 4: Presentation    — the HTTP handler exposing it all
```

This is the same dependency order used inside the `test-driven-development` and `idempotency-and-exactly-once-design` worked examples — those examples already show the Red-Green-Refactor detail for layer steps 1, 2, and 3 individually. This example focuses on showing that **all four belong to one task** and walks through reaching layer step 4, where the task actually becomes complete.

## Layer Step 1 — Domain

Build `Capacity` (decrement/increment, zero-floor invariant) and `Reservation` (created in `ACTIVE` status). This is exactly the Domain layer already built and tested in the `test-driven-development` skill's worked example (`T1`/`T2` there, predating this task's numbering) — Red-Green-Refactor for each invariant, zero mocks, zero framework dependency.

**Verify:** Build succeeds, all Domain tests pass. **Stopping point — but not a task-completion point.** The capability "create a reservation" does not exist yet from any caller's perspective; only its domain rules exist, proven correct in isolation.

## Layer Step 2 — Application

Build `CreateReservationUseCase` (reject paths for duplication and capacity exhaustion, success path saving resource-then-reservation in order) and the idempotency guard in front of it. This is exactly the Application layer built in the `idempotency-and-exactly-once-design` skill's worked example, with all outbound ports mocked per `test-driven-development`'s layer policy.

**Verify:** Build succeeds, all Application-layer tests pass (including re-confirming the reject-path tests still pass after the success path was added — Beyoncé Rule). **Stopping point — still not a task-completion point.** The use case is fully proven against mocked ports, but nothing real persists anything yet, and no caller outside this codebase can reach it.

## Layer Step 3 — Infrastructure

Build `ResourceRepository` and `ReservationRepository` adapters (query/mapping logic, with the underlying database client mocked in their own tests per `test-driven-development`'s Infrastructure-layer policy), and the schema migration creating the `resources` and `reservations` tables with the partial unique constraint that backs FR-03's duplicate rejection at the database level (see the `database-schema-evolution` skill for the migration's own design discipline).

**Verify:** Build succeeds, all Infrastructure-layer tests pass — including the use case's tests from Layer Step 2, which still pass unchanged since the ports they mock haven't changed shape. **Stopping point — still not a task-completion point.** The persistence logic is proven correct in isolation against a mocked client, but it has never yet been wired to the use case in a real, running request path, and there is still no way for an actual HTTP client to trigger any of this.

## Layer Step 4 — Presentation

Build the HTTP handler wiring everything above together into a real, callable endpoint:

```
handler POST /api/v1/resources/{resourceId}/reservations:
    command = buildCommandFromRequest(request)
    try:
        result = idempotencyGuard.execute(command)   // wraps CreateReservationUseCase
        return success(201, result)
    catch DuplicateReservationError:
        return errorResponse(409, "duplicate-reservation")
    catch CapacityExhaustedError:
        return errorResponse(409, "capacity-exhausted")
    catch MissingIdempotencyKeyError:
        return errorResponse(422, "missing-idempotency-key")
```

**Test (Presentation layer, use case mocked, per `test-driven-development`):**

```
test "POST /reservations returns 201 with the created reservation on success":
    mock idempotencyGuard.execute(anyCommand) returns activeReservation
    response = httpClient.post("/api/v1/resources/{id}/reservations", validBody, asUser)
    expect: response.statusCode == 201
    expect: response.body.data.status == "ACTIVE"

test "POST /reservations returns 409 capacity-exhausted when the use case rejects for capacity":
    mock idempotencyGuard.execute(anyCommand) raises CapacityExhaustedError
    response = httpClient.post("/api/v1/resources/{id}/reservations", validBody, asUser)
    expect: response.statusCode == 409
    expect: response.body.errorIdentifier == "capacity-exhausted"

// ... and similarly for duplicate-reservation and missing-idempotency-key,
// each previously proven at the layers below; this layer's tests verify
// only that this layer correctly TRANSLATES those outcomes into the
// contract's defined HTTP responses, per test-driven-development's
// Presentation-layer scope.
```

**Verify:** Build succeeds, all Presentation-layer tests pass, and — critically, since this is the layer step that completes the task — the **full Layer Step 1 through 4 test suite together** is run once more and confirmed green.

## Step 7 — Final Task-Level Verification

This is the point where T1 actually becomes complete, and it's verified the way its acceptance criteria require: through the real endpoint, not only through internal unit tests at any single layer. First ask the human reviewer whether it's safe to do so in the current environment (e.g. a development environment, not production) before proceeding these API calls.

```
// An end-to-end check exercising the real, wired-together stack —
// this is what proves the task is genuinely done, not just that each
// layer passes its own isolated unit tests.
verify: POST /api/v1/resources/{a-real-resource-with-capacity}/reservations
        → 201, capacity reduced by exactly one

verify: POST /api/v1/resources/{a-real-resource-with-zero-capacity}/reservations
        → 409 capacity-exhausted, no reservation created

verify: same request twice (same user, same resource)
        → second call returns 409 duplicate-reservation

verify: same request retried with the same Idempotency-Key
        → original 201 response returned, capacity not decremented again

verify: two concurrent requests against a resource with one remaining unit
        → exactly one 201, exactly one 409 capacity-exhausted
```

Every acceptance criterion from T1's entry in `tasks.md` is now demonstrably true — not because each layer's isolated tests passed (they did, and that was necessary), but because the _capability itself_, end to end, behaves correctly when exercised the way a real caller would exercise it.

## What This Example Demonstrates

- All four layer steps were built in dependency order, each one verified before the next began — but none of the first three was mistaken for the task being "done."
- The task became genuinely complete only at the end of Layer Step 4, when it could be verified the way its acceptance criteria actually describe (through the real endpoint).
- No separate task was started for "the API layer" or "the persistence layer" — all four layer steps belong to T1, and T2 (cancellation) remains untouched and unstarted, exactly per Step 1's scope boundary.
- Each layer step's verification re-confirmed the layers below it still passed (Beyoncé Rule), so a regression introduced while building a later layer would have been caught at that layer step, not discovered only once everything was wired together.
