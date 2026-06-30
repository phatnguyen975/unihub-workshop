# Implementation Plan: Resource Reservation With Capacity Limit

> Spec: specs/resource-reservation-with-capacity-limit/SPEC.md  
> API Contract: specs/resource-reservation-with-capacity-limit/API_CONTRACT.md  
> Status: APPROVED

> [!NOTE]
>
> - This is an illustrative, domain-neutral example showing how to apply this skill. It decomposes the same "resource reservation with capacity limit" feature used as the worked example in the `spec-driven-development` and `api-contract-design` skills, so all three examples can be read together as one consistent walkthrough. It does not belong to any specific project, language, or framework — substitute your own project's actual layer names and file paths.
> - Each task below is a **complete vertical slice** — it touches every layer the capability needs (commonly Domain, Application, Infrastructure, and Presentation, or this project's equivalent names) and is independently end-to-end testable the moment it's done. No task is scoped to a single layer.

## Overview

This plan implements reservation creation and cancellation against a capacity-limited resource, per the approved spec and API contract. It is organized into two capabilities, ordered by dependency: creating a reservation must exist and work before cancellation can be meaningfully built and tested against it.

## Architecture Decisions for This Plan

- Both tasks below implement their own thin path through every layer they need, rather than being split into separate "layer" tasks — this keeps each task independently demonstrable and testable as soon as it's complete, instead of requiring both tasks to finish before anything can be verified end-to-end.
- The idempotency mechanism required by the contract (Idempotency-Key header) for reservation creation is built as part of Task 1's own vertical slice, since it's an inseparable part of what "create a reservation" must guarantee — not split out as a separate task, since it has no caller-observable behavior of its own apart from creation.

## Task List

### Phase 1: Core Reservation Flow

- [ ] **T1 — A user can create a reservation for a resource with available capacity, and is correctly rejected when capacity or duplication rules are violated**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:** `domain/capacity.*`, `domain/reservation.*`, `application/create-reservation.*`, `application/ports/resource-repository.*`, `application/ports/reservation-repository.*`, `application/idempotency-guard.*`, `infrastructure/resource-repository.*`, `infrastructure/reservation-repository.*`, `migrations/<timestamp>_create_resources_and_reservations.*`, `presentation/reservations-controller.*` (plus a `.test.*` file alongside each of the above)
  - **Acceptance Criteria:**
    - Calling `POST /api/v1/resources/{resourceId}/reservations` against a resource with available capacity returns `201` with an `ACTIVE` reservation, and the resource's remaining capacity is reduced by exactly one (FR-02, FR-04; Scenario 1)
    - Calling the same endpoint against a resource with zero remaining capacity returns `409 capacity-exhausted`, and no reservation is created (FR-02; Scenario 2)
    - Calling the same endpoint a second time for a user who already holds an active reservation for that resource returns `409 duplicate-reservation` (FR-03; Scenario 3)
    - Retrying the same request with the same `Idempotency-Key` returns the original `201` response without decrementing capacity a second time (FR-05; Scenario 4)
    - Two concurrent requests for the last unit of capacity result in exactly one success and one `409 capacity-exhausted` (Scenario 5)

#### Checkpoint: Core Reservation Flow

- [ ] T1 passes all of its acceptance criteria when exercised through the actual HTTP endpoint (not only through internal unit tests) against a representative set of resources and capacities
- [ ] Build is clean; full unit test suite for this slice passes

### Phase 2: Cancellation

- [ ] **T2 — A user can cancel their own active reservation, and is correctly rejected from canceling someone else's or an already-canceled one**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:** `domain/reservation.*` (extends T1's file — adds the cancellation state transition), `application/cancel-reservation.*`, `infrastructure/reservation-repository.*` (extends T1's file), `presentation/reservations-controller.*` (extends T1's file) (plus a `.test.*` file alongside each new/extended file)
  - **Acceptance Criteria:**
    - Calling `DELETE /api/v1/reservations/{reservationId}` as the reservation's owner returns `204`, the reservation transitions to `CANCELED`, and the resource's remaining capacity increases by exactly one (FR-06; Spec §5 state table)
    - Calling the same endpoint as a different user returns `403 forbidden`, and the reservation remains unchanged (FR-07)
    - Calling the same endpoint a second time against an already `CANCELED` reservation returns `409 already-canceled` (Spec §5 state table)

#### Checkpoint: Feature Complete

- [ ] T1 and T2 both pass all of their acceptance criteria when exercised through the actual HTTP endpoints.
- [ ] Full test suite passes (unit tests only, per this project's testing policy).
- [ ] Ready for `/review`.

## Risks and Mitigations

| Risk                                                                                                                                                                                          | Impact | Mitigation                                                                                                                                                                                                                                                                                                                         |
| :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Concurrent requests for the last unit of capacity could both pass an application-layer check before either persists, if the persistence layer's constraint isn't the actual enforcement point | High   | T1's acceptance criteria explicitly require the concurrent-request scenario (Scenario 5) to be verified end-to-end, not only at the application layer in isolation — the database-level partial unique constraint from the migration is the authoritative guard, with the application-layer check as a fast-path optimization only |
| Building T1 as a single vertical slice (rather than splitting persistence and API into separate tasks) makes T1 larger than a typical "S" task                                                | Medium | Sized as "M" deliberately rather than artificially split into per-layer pieces, which would make the slice no longer independently end-to-end testable — see this skill's Vertical vs. Horizontal Slicing guidance                                                                                                                 |

## Open Questions

- None — all ambiguities surfaced during spec review were resolved before this plan was approved.
