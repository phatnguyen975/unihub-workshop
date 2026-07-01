# Worked Example: Investigating an Intermittent Double-Reservation Defect

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It investigates a defect reported against the `CreateReservationUseCase` built in the `incremental-implementation` skill's worked example, continuing the "resource reservation" example used across the other skill examples. Pseudocode is used deliberately, with no specific language, framework, or database syntax, so this stays applicable regardless of your actual stack.

## Symptom

A monitoring alert reports that resource `R-1042` ended up with `-1` remaining capacity — a value the domain layer's own invariant (`Capacity` cannot go negative, per the `spec-driven-development` worked example's FR-02) should make impossible. Two reservations exist for the same resource where only one unit of capacity was available.

## Step 1 — Reproduce

A single, sequential call to `CreateReservationUseCase.execute()` against a resource with one remaining unit of capacity behaves correctly every time — capacity goes to zero, exactly one reservation is created. The defect doesn't reproduce under sequential testing.

Per the **Timing-dependent** guidance: this points toward a concurrency issue. Running two calls to `execute()` _simultaneously_ against the same resource, repeated under a tight loop to widen the race window, reproduces the defect intermittently — roughly 1 in 50 attempts, both calls succeed and capacity ends at `-1`.

**Reproduces:** Intermittently, under genuine concurrency — confirmed reproducible, not abandoned as "couldn't reproduce."

## Step 2 — Localize

Tracing the boundary between Application and Domain layers: the Application-layer use case calls `resource.capacity.decrement()` (a pure Domain-layer operation, already unit-tested and known correct in isolation per the `test-driven-development` worked example) and then calls `resourceRepository.save(decrementedResource)`.

Checking what crosses the Application → Infrastructure boundary: the use case reads the resource (`findById`), decrements it _in memory_, then saves the new value. Two concurrent calls can both read the _same_ pre-decrement value before either one's `save()` has been written — each one independently computes "one less than what I read," and both write the same final answer, instead of the second one starting from the first one's already-decremented result.

This localizes the defect to the **read-then-write sequence in the Application/Infrastructure boundary**, not to the `Capacity` value object itself (which is correct in isolation) and not to the database layer's own constraint enforcement (no constraint was violated here — both writes succeeded, because nothing currently stops two independent read-modify-write sequences from interleaving).

## Step 3 — Reduce to the Smallest Reproducible Case

```
test "two concurrent reads of the same resource, each independently decremented and saved, both succeed":
    resource = Resource.withCapacity(1)
    repository.save(resource) // capacity = 1, stored

    runConcurrently(
        () => {
            r = repository.findById(resource.id)          // reads capacity = 1
            r2 = r.withCapacity(r.capacity.decrement())   // computes capacity = 0
            repository.save(r2)                           // writes capacity = 0
        },
        () => {
            r = repository.findById(resource.id)          // ALSO reads capacity = 1
            r2 = r.withCapacity(r.capacity.decrement())   // ALSO computes capacity = 0
            repository.save(r2)                           // writes capacity = 0 (again)
        }
    )

    // Both branches wrote "0" independently — capacity ends at 0,
    // but TWO reservations were created against ONE unit of capacity.
```

This isolates the mechanism entirely from the rest of the use case — no payment logic, no reservation creation, nothing but the read-decrement-save sequence — and reliably demonstrates the race when run under the same concurrent harness used in Step 1.

## Step 4 — Form and Test a Hypothesis

**Hypothesis:** The Application-layer use case performs an unsynchronized read-then-write against shared resource state. There is no mechanism preventing two concurrent executions from both reading the same pre-decrement value before either write is committed — the decrement is not atomic with respect to the read.

**Verification:** Add a deliberate artificial delay between the read and the write in a test harness, specifically to widen the race window, and confirm the defect becomes far more frequent (closer to reproducing every time rather than 1-in-50) — if the hypothesis is correct, widening the exact window between read and write should make the race nearly guaranteed, not just somewhat more likely.

**Result:** Confirmed. With an artificial 50ms delay inserted between the read and the write, the two-concurrent-call test fails (double decrement) on every run, not intermittently — strong confirmation that the unsynchronized read-write gap is the actual mechanism.

## Root Cause

The Application-layer use case's read-decrement-save sequence has no atomicity guarantee. Two concurrent executions can both read the pre-decrement state before either one's write is durably recorded, producing two independent "correct" decrements from the same starting value instead of one decrement building on the other.

This is **not** a defect in `Capacity.decrement()` itself (correctly rejects a decrement from zero, in isolation) and **not** a missing database constraint in the traditional sense (no uniqueness or check constraint was violated — both writes were individually valid; the defect is in the absence of contention control around the read-modify- write sequence, not in any single value).

## Fix

Per this project's own architectural decisions for handling this exact class of contention (see the project's own `adrs.md`-equivalent decision record for capacity/seat-allocation concurrency, if one exists), the correct fix at this layer is to make the decrement atomic at the point of contention — for example, by performing the decrement-and-save as a single atomic operation at the persistence layer (an atomic decrement-with-floor-check operation, or an optimistic concurrency check that rejects the second writer's stale read) rather than a read-then-compute-then-write sequence that assumes no concurrent writer exists in between.

```
// Corrected: the decrement and the write happen as a single atomic
// operation, instead of being computed in application-layer memory
// from a separately-read snapshot.
function decrementResourceCapacityAtomically(resourceId):
    result = resourceRepository.atomicDecrementIfPositive(resourceId)
    if result.rejected:
        raise CapacityExhaustedError()
    return result.newCapacity
```

The exact mechanism (a database-level atomic operation, an external coordination primitive, an optimistic-locking retry loop) depends on this project's own infrastructure choices — the root-cause fix is making the operation atomic with respect to concurrent callers, regardless of which specific mechanism implements that atomicity.

## Regression Guard

```
test "two concurrent decrement attempts against capacity of one result in exactly one success":
    resource = Resource.withCapacity(1)
    repository.save(resource)

    result1, result2 = runConcurrently(
        () => decrementResourceCapacityAtomically(resource.id),
        () => decrementResourceCapacityAtomically(resource.id)
    )

    successes = [result1, result2].filter(r => not r.rejected)
    expect: successes.length == 1
    expect: finalCapacity(resource.id) == 0 // never negative, never double-decremented
```

**Confirmed fails before fix:** Yes — run against the original read-then-write implementation, this test fails intermittently (and reliably with the artificial delay from Step 4), consistent with the originally reported symptom.

**Confirmed passes after fix:** Yes — run against the corrected atomic implementation, repeated under the same concurrent harness used in Steps 1 and 3, the test passes consistently across repeated runs (not just once), since this defect's nature requires repeated runs under contention to gain confidence, not a single passing run.

## Verification

- Regression test passes, repeatedly, under concurrent execution.
- The original `CreateReservationUseCase` test suite (per the `incremental-implementation` worked example) still passes — confirming the fix didn't change the use case's correctness for the already-tested sequential and reject-path scenarios.
- The broader Application-layer suite passes (Beyoncé Rule).

## Follow-Up

- The same read-then-write pattern should be audited for any other Application-layer use case that mutates shared, contended state — this specific defect was found in reservation creation, but the pattern itself may exist elsewhere in the codebase and is worth a deliberate sweep, tracked as separate, explicitly scoped work rather than expanded into this fix.

## What This Example Demonstrates

- The defect was genuinely intermittent and required the timing-dependent reproduction guidance from Step 1 — a naive single attempt at reproduction would have falsely concluded "cannot reproduce."
- Localization correctly distinguished the defect from the already-correct `Capacity` domain object — the bug was in the _orchestration_ of read-and-write, not in the business rule itself.
- The hypothesis was tested with a deliberate, falsifiable experiment (the artificial delay) before any fix was written — not assumed correct because it sounded plausible.
- The regression test specifically exercises concurrent contention (not just a single sequential call), because a test that didn't actually exercise the race could pass against either the buggy or the fixed implementation without proving anything.
- A related but out-of-scope concern (auditing other use cases for the same pattern) was recorded as a follow-up, not silently expanded into this fix's scope.
