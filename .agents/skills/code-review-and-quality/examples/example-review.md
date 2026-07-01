# Code Review: Resource Reservation — Task T3 (CreateReservationUseCase)

> **Spec:** specs/resource-reservation-with-capacity-limit/SPEC.md  
> **API Contract:** specs/resource-reservation-with-capacity-limit/API_CONTRACT.md  
> **Reviewed change size:** ~85 lines (3 slices, per the `incremental-implementation` worked example)  
> **Status:** REVIEWED — awaiting human confirmation

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It reviews the `CreateReservationUseCase` implementation built across the three slices shown in the `incremental-implementation` skill's worked example, continuing the "resource reservation" example used across the other skill examples.

## Summary

| Severity   | Count |
| :--------- | :---: |
| Critical   |   0   |
| Should-fix |   2   |
| Nit        |   1   |
| FYI        |   1   |

Overall, this change is mostly in good shape and satisfies its stated acceptance criteria. Two Should-fix items should be addressed before merge: one around error-translation specificity in test coverage, and one around a query pattern that will degrade as reservation volume grows. The remaining observation is optional polish.

## Reviewability

- **Change size:** within ideal range — three small slices, ~85 lines total, fully reviewable in one pass.

## Axis-by-Axis Findings

### 1. Correctness

- No issues found. Verified against FR-02, FR-03, FR-04 in `SPEC.md`. The reject paths (duplicate reservation, capacity exhausted) and the success path (decrement-then-save-then-create-then-save, in order) all match the spec's stated requirements, and the order of operations matches what `API_CONTRACT.md`'s Side Effects section for this endpoint implies.

### 2. Design

- **[Nit]** The use case constructor takes two port interfaces positionally (`resourceRepository`, `reservationRepository`). As more ports are likely to be added later (e.g. the idempotency port from task T5, per `tasks.md`), consider whether this project's convention favors a single options/dependencies object for use cases with more than two or three collaborators, to avoid an increasingly fragile positional constructor signature. — `application/create-reservation.*`
- No module-boundary issues found — all dependencies are on the two declared port interfaces (`application/ports/`), consistent with `modular-boundary-enforcement`.

### 3. Security

- Not applicable — this use case takes no external input beyond already-validated, internally-typed command data; it makes no authorization decision of its own (authorization for this endpoint is enforced at the Presentation layer, reviewed separately when that slice is built in task T8).

### 4. Performance

- **[Should-fix]** The reservation listing path introduces a query inside a loop when loading related resource data. For a user with N reservations, this changes the database access pattern from a bounded number of queries into N+1 queries, causing response time and database load to grow linearly with the number of reservations returned. The related resources should be loaded through a batched query or the data layer's eager-loading mechanism instead of fetching each resource individually. — `application/list-reservations.*`
- No issues found otherwise — the use case does not introduce unbounded result processing, unnecessary repeated computation, or latency-sensitive external calls.

### 5. Test Coverage

- **[Should-fix]** The two reject-path tests (`shouldRejectWithDuplicateReservationError...`, `shouldRejectWithCapacityExhaustedError...`) each verify that the _expected_ exception is raised, but neither test explicitly verifies that the _other_ exception type isn't accidentally raised in its place — e.g. the capacity-exhausted test doesn't confirm `DuplicateReservationError` is what's _not_ thrown. In this specific case the distinction is already implicitly covered (the mocked duplicate-check returns `null` in that test, which only leaves one possible exception path), so this is unlikely to currently hide a real defect — but as more reject conditions are added to this use case over time, asserting on the _specific_ exception type/class (not just "an exception was thrown") becomes increasingly important to keep failures precisely diagnosable. Recommend tightening the assertions now while the use case is still small, rather than once more reject paths exist. — `application/create-reservation.test.*`
- No issues found otherwise — both reject-path tests and the success- path test live at the Application layer, mock exactly the two outbound ports per `test-driven-development`'s layer policy, and use real, simply-constructed `Resource`/`Reservation` domain objects as fixtures rather than mocking the domain model itself. The success-path test re-confirms the reject-path tests still pass after it was added (Beyoncé Rule was honored, per the worked example's own narration).

### 6. Style

- **[FYI]** The success path's variable name `decrementedResource` is clear and intention-revealing — calling this out as a positive example, not a finding requiring action, since it's a good naming pattern worth keeping consistent as the codebase grows (entities named for the state transition they just underwent, rather than a generic name like `updatedResource`).
- No issues found otherwise — naming, formatting, and comment usage are consistent with the rest of the example codebase shown in the prior skill examples.

## Critical Findings Requiring Immediate Attention

None.

## Human Disposition

| Finding                                                                      | Agent Severity | Human Decision | Notes |
| :--------------------------------------------------------------------------- | :------------- | :------------- | :---- |
| Constructor positional-args design (Design, Nit)                             | Nit            | _Pending_      |       |
| Reject-path exception-type assertion specificity (Test Coverage, Should-fix) | Should-fix     | _Pending_      |       |
| N+1 query pattern in reservation listing (Performance, Should-fix)           | Should-fix     | _Pending_      |       |
| Positive naming callout (Style, FYI)                                         | FYI            | _Pending_      |       |

**Additional findings identified by the human reviewer (not caught by this review):**

None.

## What This Example Demonstrates

- All six axes were walked deliberately, including two ("Security") marked explicitly Not Applicable with a stated reason, rather than silently omitted.
- The one Should-fix finding states a concrete, specific risk (test assertions becoming less diagnostic as more reject paths are added) rather than a vague "tests could be better."
- An FYI finding was used to record a positive observation, showing that this severity level isn't only for minor complaints — it's for anything worth noting that isn't a request for change.
- The review references the actual spec FR numbers and the actual `tasks.md` task IDs it's checking against, rather than a generic unsupported claim of correctness.
- No fix was applied — every finding is left for the human to confirm, dispute, or defer in the Human Disposition table.
- The Performance finding demonstrates that performance review focuses on scalability impact and execution behavior (for example query count growth), not subjective preferences about making code "faster."
