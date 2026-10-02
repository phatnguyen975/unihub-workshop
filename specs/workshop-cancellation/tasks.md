# Implementation Plan: Workshop Cancellation

> **Spec:** `specs/workshop-cancellation/SPEC.md`  
> **API Contract:** `specs/workshop-cancellation/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the single `PATCH /api/v1/admin/workshops/{id}/cancel` endpoint for Feature 2.4, decomposed into three vertical-slice tasks ordered by dependency and risk. Task 1 establishes the core domain behavior — the new `Workshop.cancel()` method and the `WorkshopCanceledEvent` shared-kernel record that all downstream logic depends on. Task 2 builds the full use-case orchestration — all guard clauses, optimistic-lock validation, prohibited-field rejection, and the atomic transactional commit with event emission. Task 3 wires the HTTP presentation layer: request DTO, controller handler, `WorkshopResponse.canceledAt` addition, and controller-level tests. No task is scoped to a single architectural layer; each one delivers a callable, testable capability.

## Architecture Decisions for This Plan

- **T1 before T2:** `Workshop.cancel()` domain method and `WorkshopCanceledEvent` must exist and have passing unit tests before the use case (T2) can call them with confidence. The domain method is the most compact thing to get right first; any misunderstanding of the state/temporal guards surfaces cheaply in unit tests at T1 rather than propagating into the use-case layer.
- **T2 before T3:** The use case carries all the complex guard logic (6 guard clauses across FR-05 through FR-11 plus optimistic-locking semantics). Proving it correct with unit tests before wiring the controller means the HTTP layer (T3) only needs to verify routing, request-to-command mapping, and response serialization — not re-test business rules.
- **`WorkshopCanceledEvent` belongs in T1:** The event record is shared groundwork, but its only direct consumer is the use case implemented in T2. Per the skill's "first capability that needs it" guidance, it is created as part of T1's slice (which owns the domain model changes) rather than as a separate horizontal task.
- **`WorkshopResponse.canceledAt` belongs in T3:** The DTO amendment is strictly presentational and has no effect on domain or use-case correctness. Adding it to T3 keeps the presentation-layer change co-located with the controller test that verifies the new field appears in the serialized response.
- **Separate controller test class for cancel:** Following the established pattern (`AdminWorkshopControllerRescheduleTest`, `AdminWorkshopControllerPublishTest`), the cancel endpoint gets its own focused test class so no existing test class is touched.
- **No migration task:** The `canceled_at` column already exists in the `workshops` table (confirmed in `docs/database-schema.md` line 385). No schema migration is needed, so there is no separate migration task.

## Task List

### Phase 1: Domain Behavior & Event Record

- [x] **T1 — `Workshop` aggregate gains a `cancel()` domain method, and `WorkshopCanceledEvent` is defined in the shared kernel**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Shared Kernel (event record)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/Workshop.java` _(modify — add `cancel(UUID updatedBy)` method)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/WorkshopCanceledEvent.java` _(create — `@Externalized("unihub.events::workshop.canceled")`)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/domain/model/WorkshopTest.java` _(modify — add cancel unit tests)_
  - **Acceptance Criteria:**
    - Calling `workshop.cancel(callerId)` on a `PUBLISHED` workshop whose `endsAt` is in the future returns a new `Workshop` with `status = CANCELED`, non-null `canceledAt`, updated `updatedAt`, and `updatedBy = callerId`. `version` is NOT incremented by the domain method itself (JPA `@Version` handles that at save time). _(FR-12 — domain step)_
    - Calling `workshop.cancel(callerId)` on a `DRAFT` workshop throws a domain validation exception with a message directing the caller to the draft deletion endpoint. _(FR-08)_
    - Calling `workshop.cancel(callerId)` on a `COMPLETED` or `CANCELED` workshop throws a domain validation exception indicating a terminal state. _(FR-09)_
    - Calling `workshop.cancel(callerId)` on a `PUBLISHED` workshop whose `endsAt <= now()` throws a domain validation exception indicating the workshop has already concluded. _(FR-10)_
    - `WorkshopCanceledEvent` compiles with `@Externalized("unihub.events::workshop.canceled")` and carries all required fields: `workshopId`, `organizationId`, `title`, `slug`, `startsAt`, `endsAt`, `canceledAt`, `reason` (nullable), `occurredAt`. _(FR-13)_

#### Checkpoint: Phase 1

- [x] All Phase 1 unit tests pass (`WorkshopTest` cancel cases — all 4 scenarios above).
- [x] `WorkshopCanceledEvent` compiles with `@Externalized("unihub.events::workshop.canceled")`.
- [x] No existing tests in `WorkshopTest` (reschedule, publish, update cases) are broken.

### Phase 2: Use-Case Orchestration & Outbox Event

- [x] **T2 — `CancelWorkshopUseCaseImpl` orchestrates all guard clauses, validates prohibited fields, and atomically commits the cancellation with `WorkshopCanceledEvent` emission**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Application (inbound port command, use-case interface, use-case impl)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/CancelWorkshopCommand.java` _(create)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/CancelWorkshopUseCase.java` _(create)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/CancelWorkshopUseCaseImpl.java` _(create)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/CancelWorkshopUseCaseImplTest.java` _(create)_
  - **Acceptance Criteria:**
    - Given a valid `CancelWorkshopCommand` (non-null `version`, optional `reason ≤ 1000 chars`) targeting a `PUBLISHED` workshop whose `endsAt > now()` with a matching `version` — the use case saves the canceled workshop and publishes a `WorkshopCanceledEvent` carrying `workshopId`, `organizationId`, `title`, `slug`, `startsAt`, `endsAt`, `canceledAt`, `reason` (null if not supplied), and `occurredAt`. _(FR-12, FR-13, FR-14, Scenarios 1–3)_
    - When `command.version()` is null, the use case throws a validation exception before any repository call. _(FR-05, Scenario 9)_
    - When any prohibited field is non-null in the command, the use case throws a validation exception with field-level messages identifying the prohibited field(s). _(FR-07, Scenario 10)_
    - When the workshop does not exist, the use case throws a not-found exception (maps to `404`). _(FR-04, Scenario 13)_
    - When the caller's `organizationId` does not match the workshop's `organization_id`, the use case throws an access-denied exception (maps to `403`). _(FR-03, Scenario 12)_
    - When the workshop is in `DRAFT` status, the use case propagates the domain validation exception (maps to `422`). _(FR-08, Scenario 5)_
    - When the workshop is in `COMPLETED` or `CANCELED` status, the use case propagates the domain validation exception (maps to `422`). _(FR-09, Scenarios 6–7)_
    - When the workshop's `endsAt <= now()`, the use case propagates the domain validation exception (maps to `422`). _(FR-10, Scenario 4)_
    - When the supplied `version` does not match the stored workshop version, the use case throws a conflict exception (maps to `409`). _(FR-11, Scenario 8)_
    - When `reason` is a blank or whitespace-only string, the use case normalizes it to `null` before including it in the event payload. _(FR-06, BR-03, Scenario 14)_
    - `reason` is NOT passed to the repository save — only the status, `canceledAt`, `updatedAt`, `updatedBy`, and `version` columns are written to the database. _(BR-03)_

#### Checkpoint: Phase 2

- [x] All Phase 2 unit tests pass (`CancelWorkshopUseCaseImplTest` — all 11 criteria above verified).
- [x] No existing use-case tests (`RescheduleWorkshopUseCaseImplTest`, `PublishWorkshopUseCaseImplTest`, `UpdateWorkshopUseCaseImplTest`) are broken.
- [x] `ModulithArchitectureTest` passes with the new application-layer classes.

### Phase 3: HTTP Presentation Layer

- [x] **T3 — `PATCH /api/v1/admin/workshops/{id}/cancel` endpoint wired in `AdminWorkshopController`, `WorkshopResponse` gains `canceledAt`, returning `200 OK` with the full updated workshop**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Presentation (request DTO, response DTO amendment, controller handler, controller test)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/CancelWorkshopRequest.java` _(create — `version: Integer`, `reason: String`, prohibited fields declared as `Object`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/WorkshopResponse.java` _(modify — add `Instant canceledAt` field)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(modify — add `@PatchMapping("/{id}/cancel")` handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerCancelTest.java` _(create)_
  - **Acceptance Criteria:**
    - `PATCH /api/v1/admin/workshops/{id}/cancel` with a valid `ORGANIZER` JWT (matching `org`) and body `{"version": 2, "reason": "..."}` returns `200 OK` with an `ApiResponse<WorkshopResponse>` envelope where `data.status = "CANCELED"`, `data.canceledAt` is a non-null ISO 8601 timestamp, and `data.version` is incremented by 1. _(FR-15, Scenarios 1–3)_
    - `PATCH .../cancel` with a body containing a prohibited field (e.g. `"title": "New Title"`) returns `422 Unprocessable Content` with `validationErrors` identifying the prohibited field. _(FR-07, Scenario 10)_
    - `PATCH .../cancel` with missing `Authorization` header returns `401 Unauthorized`. _(FR-01)_
    - `PATCH .../cancel` with a `STAFF` or `STUDENT` JWT returns `403 Forbidden`. _(FR-02)_
    - `PATCH .../cancel` targeting a workshop owned by a different organization returns `403 Forbidden`. _(FR-03, Scenario 12)_
    - `PATCH .../cancel` targeting a non-existent workshop ID returns `404 Not Found`. _(FR-04, Scenario 13)_
    - `PATCH .../cancel` with a mismatched `version` returns `409 Conflict` with error type `urn:unihub:error:conflict`. _(FR-11, Scenario 8)_
    - `PATCH .../cancel` with null `version` returns `422 Unprocessable Content` identifying `version` as required. _(FR-05, Scenario 9)_
    - `PATCH .../cancel` with `reason` exceeding 1000 characters (after trimming) returns `422 Unprocessable Content` identifying `reason` as too long. _(FR-06, Scenario 11)_
    - `PATCH .../cancel` on a `DRAFT` workshop returns `422 Unprocessable Content` with a message directing the caller to `DELETE /api/v1/admin/workshops/{id}`. _(FR-08, Scenario 5)_
    - `PATCH .../cancel` on an already-concluded workshop (`endsAt <= now()`) returns `422 Unprocessable Content` indicating the workshop has already concluded. _(FR-10, Scenario 4)_
    - The `WorkshopResponse` shape is unchanged for all other endpoints — `canceledAt` is a new additive field, null-omitted via `@JsonInclude(NON_NULL)` for non-canceled workshops. _(API Contract §6 Note 1)_
    - Error responses use RFC 7807 Problem Details format with `Content-Type: application/problem+json`, consistent with all other endpoints in `AdminWorkshopController`. _(API Contract §2)_

#### Checkpoint: Feature Complete

- [x] All tasks T1–T3 are checked off.
- [x] All acceptance criteria across T1, T2, and T3 have been verified by their respective unit tests.
- [x] `ModulithArchitectureTest` passes (`./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`).
- [x] No regression in any existing test class in the `workshops` module.
- [x] Ready for `/review workshop-cancellation`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                        | Impact | Mitigation                                                                                                                                                                                                                                                                                                                                                                                           |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Temporal guard race condition**: `endsAt > now()` is evaluated in the domain method using `Instant.now()`. Under extreme clock skew or a workshop that ends within milliseconds of the request, the check could pass in the domain but the workshop may have concluded by DB commit time. | Low    | This is the same inherent race that exists in all time-based guards in the codebase (e.g. rescheduling's `startsAt > now()` check). The window is milliseconds-wide and the business impact is negligible (a workshop that just concluded is effectively the same as one that's still technically running). Accepted by design; document in the unit test as a known boundary condition.             |
| **`reason` normalization (blank → null)**: If normalization happens in the controller DTO instead of the use-case, and a future refactor introduces a second entry point (e.g. an admin CLI), the normalization may be skipped.                                                             | Low    | Apply normalization inside `CancelWorkshopUseCaseImpl` (application layer), not in the request DTO or controller. This ensures all callers get consistent behavior regardless of entry point. Mirror the trimming pattern from `RescheduleWorkshopUseCaseImpl`.                                                                                                                                      |
| **`WorkshopResponse.canceledAt` addition breaks existing controller test snapshots**: Some test assertions may do full-body equality checks that would now fail because `canceledAt` appears in the response for canceled workshops.                                                        | Medium | Check existing controller test classes (`AdminWorkshopControllerPublishTest`, `AdminWorkshopControllerRescheduleTest`, etc.) for exact-body assertions during T3. Update any affected assertion to either use field-by-field checks or exclude the new field explicitly. The field is `null`/omitted for non-canceled workshops, so tests exercising PUBLISHED/DRAFT responses should be unaffected. |
| **Concurrent optimistic-lock failure path**: A `ObjectOptimisticLockingFailureException` thrown by JPA after the `workshop.cancel()` call must be translated to a `409 Conflict` response. If the exception translation is not wired, callers receive a `500`.                              | Medium | Mirror the exception-translation pattern already in place for `RescheduleWorkshopUseCaseImpl` (which handles the same JPA exception). Verify the `CancelWorkshopUseCaseImplTest` explicitly tests the path where the repository's `save()` call throws `ObjectOptimisticLockingFailureException`.                                                                                                    |

## Open Questions

None. All design decisions and business rules were confirmed during spec review.
