# Worked Example: A Complete TDD Cycle Across Four Layers

> [!NOTE]
>
> - This is an illustrative, domain-neutral example showing how to apply this skill. It walks through implementing one Functional Requirement — FR-02 from the "Resource Reservation With Capacity Limit" example used in the `spec-driven-development` and `api-contract-design` skills: _"The system SHALL reject a reservation request when the resource's remaining capacity is zero."_
> - Pseudocode is used deliberately instead of any specific language or test framework, so this example stays applicable regardless of your project's actual stack. Substitute your project's real testing framework, mocking library, and naming conventions.
> - This example covers the **Unit tier only** (all four architectural sub-layers) — it does not include an integration or end-to-end test, since the TDD Red-Green-Refactor cycle itself is practiced almost exclusively at the unit level. See `SKILL.md` for guidance on integration and end-to-end tests.

## Layer 1 — Domain

**Behavior under test:** A resource's capacity value object refuses to decrement below zero.

### RED

```
test "decrementing capacity below zero raises CapacityExhaustedError":
    capacity = Capacity.of(0)
    expect: capacity.decrement() raises CapacityExhaustedError
```

Run it. It fails — `Capacity` does not exist yet, or `decrement()` is not implemented. Confirmed failing for the expected reason.

### GREEN

```
class Capacity:
    constructor(value):
        this.value = value

    static of(value):
        return new Capacity(value)

    decrement():
        if this.value <= 0:
            raise CapacityExhaustedError()
        return new Capacity(this.value - 1)
```

Run the test. It passes. Run the full Domain test suite for this class — nothing else broke (Beyoncé Rule).

### REFACTOR

No duplication or unclear naming yet at this size. Nothing to refactor. Move to the next behavior (e.g. "decrementing a positive capacity reduces it by exactly one" — its own separate Red-Green-Refactor cycle, omitted here for brevity).

> **Note what this test does NOT do:** It does not touch a database, a cache, or any framework type. It is pure, fast, and deterministic — this is what makes it a Layer 1 test, with zero mocks required.

## Layer 2 — Application

**Behavior under test:** The "create reservation" use case rejects the request when the resource's remaining capacity is exhausted, without calling the repository to persist anything.

### RED

```
test "createReservation rejects when capacity is exhausted":
    mock resourceRepository.findById(resourceId) returns resourceWithZeroCapacity
    mock reservationRepository // should NOT be called

    useCase = CreateReservationUseCase(resourceRepository, reservationRepository)

    expect: useCase.execute(validCommand) raises CapacityExhaustedError
    verify: reservationRepository.save was never called
```

Run it. It fails — the use case doesn't exist yet, or doesn't check capacity before saving. Confirmed failing for the expected reason.

### GREEN

```
class CreateReservationUseCase:
    constructor(resourceRepository, reservationRepository):
        this.resourceRepository = resourceRepository
        this.reservationRepository = reservationRepository

    execute(command):
        resource = this.resourceRepository.findById(command.resourceId)
        resource.capacity.decrement() // raises CapacityExhaustedError if zero
        reservation = Reservation.createActive(command.userId, command.resourceId)
        this.reservationRepository.save(reservation)
        return reservation
```

Run the test. It passes — the exception from the Domain layer's `Capacity.decrement()` propagates up, and because it's raised before the `save()` call, the repository is never invoked. Run the full Application test suite for this use case — nothing else broke.

### REFACTOR

The use case currently has no persistence of the decremented capacity back to the resource — that's a behavior for a different FR/test cycle (not shown here). Structure is otherwise clear; nothing to refactor yet.

> **Note what this test mocks:** Only `resourceRepository` and `reservationRepository` — the two outbound ports this use case depends on. The `Resource` and `Capacity` domain objects flowing through the test are real, simply-constructed instances, not mocks. Per the mocking preference order, an interaction-verifying mock is the right choice here specifically because the point of _this_ test is to verify the use case calls `reservationRepository.save` — or doesn't — under the right conditions; that is an interaction the test is deliberately checking, not a side effect it's trying to avoid asserting on.

## Layer 3 — Infrastructure

**Behavior under test:** The repository adapter correctly translates a capacity-check database constraint violation into the application-level `CapacityExhaustedError`, without this skill assuming any particular persistence technology.

### RED

```
test "save() translates a unique-constraint violation into CapacityExhaustedError":
    mock databaseClient.execute(anyQuery) raises UniqueConstraintViolation("active_reservation_per_user")

    adapter = ReservationRepositoryAdapter(databaseClient)

    expect: adapter.save(someReservation) raises CapacityExhaustedError
```

Run it. It fails — the adapter doesn't yet catch and translate this specific underlying error. Confirmed failing for the expected reason.

### GREEN

```
class ReservationRepositoryAdapter:
    constructor(databaseClient):
        this.databaseClient = databaseClient

    save(reservation):
        try:
            query = buildInsertQuery(reservation)
            this.databaseClient.execute(query)
        catch UniqueConstraintViolation as e:
            if e.constraintName == "active_reservation_per_user":
                raise DuplicateReservationError()
            raise CapacityExhaustedError() // simplified for this example
```

Run the test. It passes. Run the full Infrastructure test suite for this adapter — nothing else broke.

### REFACTOR

The simplified `catch` block above conflates two distinct constraint names into a too-broad fallback. A real refactor pass would tighten this to map each named constraint explicitly, with its own test for each mapping — left as an exercise, since the point here is to illustrate the layer's scope, not to produce a production-ready adapter.

> **Note what this test does NOT do:** It does not run against a real database. `databaseClient` is mocked to simulate the exact failure condition being translated — the test verifies the adapter's _mapping logic_, not whether a real database actually enforces this constraint (that would be a property of the schema/migration, verified by a different mechanism — e.g. a schema-evolution skill or migration review — not by this test).

## Layer 4 — Presentation

**Behavior under test:** The HTTP handler returns the project's defined "conflict" response when the use case raises `CapacityExhaustedError`.

### RED

```
test "POST /reservations returns conflict response when capacity is exhausted":
    mock createReservationUseCase.execute(anyCommand) raises CapacityExhaustedError

    response = httpClient.post("/api/v1/resources/{id}/reservations", validBody, asUser)

    expect: response.status == 409
    expect: response.body.errorIdentifier == "capacity-exhausted"
```

Run it. It fails — the handler doesn't yet catch this exception and map it to the defined response. Confirmed failing for the expected reason.

### GREEN

```
handler POST /api/v1/resources/{resourceId}/reservations:
    try:
        result = createReservationUseCase.execute(buildCommand(request))
        return success(201, result)
    catch CapacityExhaustedError:
        return errorResponse(409, "capacity-exhausted")
```

Run the test. It passes. Run the full Presentation test suite for this handler — nothing else broke, including the previously-passing "happy path returns 201" test for the same handler.

### REFACTOR

If multiple handlers across the codebase repeat the same exception-to-response mapping pattern, this is a candidate for a shared error-mapping mechanism — but that refactor is only taken if it's genuinely improving clarity, not introduced speculatively ahead of having two or three real repeated cases to generalize from.

> **Note what this test mocks:** Only `createReservationUseCase` — the single collaborator this layer depends on. The test does not re-verify _why_ capacity might be exhausted (that's the Domain layer's job) or _how_ the use case orchestrates the check (that's the Application layer's job) — it verifies only that this layer correctly translates a known application-level outcome into the correct HTTP-level response.

## What This Example Demonstrates

- Each layer's test mocks exactly one boundary: The layer immediately below it, never more, never the layer's own internal logic.
- The same underlying business rule (capacity cannot go negative) is tested **once**, at the Domain layer. Every layer above it tests its own orchestration/translation responsibility around that rule — not the rule itself again.
- Every Red step failed for a real, expected reason before any implementation existed.
- Every Green step ran the surrounding suite, not just the new test.
- No layer required a real database, real HTTP server, or real network call — consistent with a unit-tests-only policy.
