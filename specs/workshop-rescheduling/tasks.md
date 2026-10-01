# Implementation Plan: Workshop Rescheduling

> **Spec:** `specs/workshop-rescheduling/SPEC.md`  
> **API Contract:** `specs/workshop-rescheduling/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the single `PATCH /api/v1/admin/workshops/{id}/reschedule` endpoint for Feature 2.3, decomposed into three vertical-slice tasks ordered by dependency and risk. Task 1 lays the core domain behavior and persistence query that all other logic depends on; Task 2 builds the full use-case orchestration (status guard, prohibited-field validation, speaker conflict, location collision, atomic commit, and event emission); Task 3 wires the HTTP presentation layer and closes the end-to-end path. No task is scoped to a single architectural layer — each one delivers a callable, testable capability.

## Architecture Decisions for This Plan

- **T1 before T2:** The new `Workshop.reschedule()` domain method and the new `findCollidingWorkshopAtLocation()` port method must exist and have passing unit tests before the use case (T2) can call them with confidence.
- **T2 before T3:** The use case is the most complex piece (6 guard clauses, 2 conflict checks, transactional commit + event). Proving it correct with unit tests before wiring the controller means the HTTP layer (T3) only needs to verify routing, request mapping, and response serialization — not re-test business logic.
- **`WorkshopRescheduledEvent` belongs in T2:** The shared-kernel event record is genuinely shared groundwork, but its only caller is the use case implemented in T2, so it is created as part of T2's slice per the skill's "first capability that needs it" guidance.
- **Separate controller test class for reschedule:** Following the established pattern (`AdminWorkshopControllerPublishTest`, `AdminWorkshopControllerUpdatePublishedTest`), the reschedule endpoint gets its own focused test class so the existing publish/update tests are never touched.

## Task List

### Phase 1: Domain Behavior & Persistence Query

- [x] **T1 — `Workshop` aggregate gains a `reschedule()` domain method, and the `WorkshopRepository` outbound port gains a campus-wide location collision query**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application (outbound port), Infrastructure (persistence adapter + JPA repository)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/Workshop.java` _(modify — add `reschedule()` method)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopRepository.java` _(modify — add `findCollidingWorkshopAtLocation(...)` method)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopJpaRepository.java` _(modify — add `@Query` for location collision)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(modify — implement `findCollidingWorkshopAtLocation`)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/domain/model/WorkshopTest.java` _(modify — add reschedule unit tests)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapterLocationTest.java` _(create — unit tests for location collision query)_
  - **Acceptance Criteria:**
    - Calling `workshop.reschedule(newStartsAt, newEndsAt, callerId)` on a `PUBLISHED` workshop returns an updated `Workshop` with the new schedule and incremented `version`. _(FR-14 — domain step)_
    - Calling `workshop.reschedule(...)` on a `DRAFT`, `COMPLETED`, or `CANCELED` workshop throws an `IllegalStateException` or domain-specific exception. _(FR-11)_
    - The `WorkshopRepository.findCollidingWorkshopAtLocation(location, startsAt, endsAt, excludeWorkshopId)` port method, when its adapter is called with a location that overlaps another `PUBLISHED` workshop's window (excluding the current workshop itself), returns that workshop's ID. _(FR-13, BR-05)_
    - When no other active workshop occupies the location in the window, the method returns an empty `Optional`. _(FR-13)_
    - Location matching uses case-insensitive, trimmed comparison. _(BR-05)_

#### Checkpoint: Phase 1

- [x] All Phase 1 unit tests pass (`WorkshopTest` reschedule cases, `WorkshopRepositoryAdapterLocationTest`).
- [x] `WorkshopRepository` port interface compiles with the new method signature.
- [x] No existing tests in `WorkshopTest` or `WorkshopRepositoryAdapterSpeakerTest` are broken.

### Phase 2: Use-Case Orchestration & Outbox Event

- [x] **T2 — `RescheduleWorkshopUseCaseImpl` orchestrates all guard clauses, conflict checks, and atomic commit with `WorkshopRescheduledEvent` emission**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Application (in-port command + use-case interface + use-case impl), Shared Kernel (new domain event)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/RescheduleWorkshopCommand.java` _(create)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/RescheduleWorkshopUseCase.java` _(create)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/RescheduleWorkshopUseCaseImpl.java` _(create)_
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/WorkshopRescheduledEvent.java` _(create — `@Externalized("unihub.events::workshop.rescheduled")`)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/RescheduleWorkshopUseCaseImplTest.java` _(create)_
  - **Acceptance Criteria:**
    - Given a valid `RescheduleWorkshopCommand` targeting a `PUBLISHED` workshop with matching `version`, no speaker conflicts, and no location conflicts in the new window — the use case saves the updated workshop and publishes a `WorkshopRescheduledEvent` carrying `previousStartsAt`, `previousEndsAt`, `newStartsAt`, `newEndsAt`, and all other required payload fields. _(FR-14, FR-15, FR-16, Scenario 1)_
    - When the command's `version` does not match the stored workshop version, the use case throws a conflict exception (maps to `409 Conflict`). _(FR-06, Scenario 2)_
    - When the workshop is in `DRAFT` status, the use case throws a validation exception with a message directing the caller to the draft endpoint. _(FR-11, Scenario 3)_
    - When the workshop is in `COMPLETED` or `CANCELED` status, the use case throws a validation exception indicating terminal state. _(FR-11, Scenario 4)_
    - When `startsAt` is in the past or present, the use case throws a validation exception. _(FR-09, Scenario 5)_
    - When `endsAt` is not strictly after `startsAt`, the use case throws a validation exception. _(FR-08, Scenario 6)_
    - When a speaker conflict is detected for the new time window, the use case throws a validation exception identifying the conflicting speaker email(s). _(FR-12, Scenario 7)_
    - When a location collision is detected for the new time window, the use case throws a validation exception. _(FR-13, Scenario 8)_
    - When any prohibited field is non-null in the command, the use case throws a validation exception with field-level messages identifying the prohibited field(s). _(FR-10, Scenario 9)_
    - `WorkshopRescheduledEvent` carries: `workshopId`, `organizationId`, `title`, `slug`, `location`, `timezone`, `previousStartsAt`, `previousEndsAt`, `newStartsAt`, `newEndsAt`, `updatedAt`, `occurredAt`. _(FR-15)_

#### Checkpoint: Phase 2

- [x] All Phase 2 unit tests pass (`RescheduleWorkshopUseCaseImplTest` — all 9 scenarios above).
- [x] `WorkshopRescheduledEvent` compiles with `@Externalized("unihub.events::workshop.rescheduled")`.
- [x] No existing use-case tests (`PublishWorkshopUseCaseImplTest`, `UpdateWorkshopUseCaseImplTest`) are broken.

### Phase 3: HTTP Presentation Layer

- [x] **T3 — `PATCH /api/v1/admin/workshops/{id}/reschedule` endpoint wired in `AdminWorkshopController`, returning `200 OK` with full `WorkshopResponse`**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Presentation (request DTO, controller handler, controller test)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/RescheduleWorkshopRequest.java` _(create — prohibited fields declared as `Object`, schedule fields as `Instant`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(modify — add `@PatchMapping("/{id}/reschedule")` handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerRescheduleTest.java` _(create)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/{id}/reschedule` with a valid JWT (`ORGANIZER`, matching `org`) and valid body `{"version":2,"startsAt":"...","endsAt":"..."}` returns `200 OK` with a `WorkshopResponse` containing the new `startsAt`, `endsAt`, and incremented `version`. _(FR-17, Scenario 1)_
    - Request with a prohibited field (e.g. `"location": "New Room"`) in the body returns `422 Unprocessable Content` with `validationErrors` identifying the prohibited field. _(FR-10, Scenario 9)_
    - Request with missing `Authorization` header returns `401 Unauthorized`. _(FR-01, Scenario — auth)_
    - Request with a `STAFF` or `STUDENT` JWT returns `403 Forbidden`. _(FR-02)_
    - Request targeting a workshop owned by a different organization returns `403 Forbidden`. _(FR-03, Scenario 10)_
    - Request targeting a non-existent workshop ID returns `404 Not Found`. _(FR-04)_
    - Request with a mismatched `version` returns `409 Conflict`. _(FR-06, Scenario 2)_
    - Request with missing `startsAt` returns `422 Unprocessable Content` identifying `startsAt` as required. _(FR-07, Scenario 11)_
    - Error responses use RFC 7807 Problem Details format with `Content-Type: application/problem+json`, consistent with all other endpoints in `AdminWorkshopController`. _(API contract §2, §3 Error Responses)_

#### Checkpoint: Feature Complete

- [x] All tasks T1–T3 are checked off.
- [x] All acceptance criteria across T1, T2, and T3 have been verified by their respective unit tests.
- [x] `ModulithArchitectureTest` passes (`./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`).
- [x] No regression in any existing test class in the `workshops` module.
- [x] Ready for `/review workshop-rescheduling`.

## Risks and Mitigations

| Risk                                                                                                                                                                               | Impact | Mitigation                                                                                                                                                                                                                                                            |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Location collision JPQL query uses `LOWER(TRIM(...))` — some JPA providers may not support `TRIM` inside JPQL `LOWER()` correctly                                                  | Medium | Test the query with `WorkshopRepositoryAdapterLocationTest` (mock-based unit test against the adapter) in T1; if JPQL fails, fall back to a native `@Query(nativeQuery=true)` with identical semantics — this is an established fallback already used in the codebase |
| `RescheduleWorkshopRequest` has prohibited fields declared as `Object` — Jackson deserializing unexpected types (e.g. an array) into `Object` may behave differently than expected | Low    | Mirror the exact pattern from `UpdateWorkshopRequest.java` (F-2.2), which already handles this correctly; add a controller test case with a numeric `location` field value to confirm rejection                                                                       |
| Concurrent reschedule requests with the same `version` — JPA optimistic locking must prevent both from committing                                                                  | Medium | Covered by `@Version` already present on `WorkshopJpaEntity`; explicitly test optimistic-locking rejection path in `RescheduleWorkshopUseCaseImplTest` (T2) using a mocked repository that throws `ObjectOptimisticLockingFailureException`                           |

## Open Questions

None.
