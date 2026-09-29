# Implementation Plan: Workshop Staff Assignment

> **Spec:** `specs/workshop-staff-assignment/SPEC.md`  
> **API Contract:** `specs/workshop-staff-assignment/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds Feature 1.6 — Workshop Staff Assignment — into the `workshops` module of the Core Backend. It adds four endpoints under `GET|POST|DELETE /api/v1/admin/workshops/{id}/staff[/available][/{userId}]`, a new `workshop_assignments` Flyway migration, the `IamApi` facade on the `iam` module (needed to resolve STAFF user profiles cross-module), two `@Externalized` outbox integration events, and all connecting domain/application/infrastructure/presentation layers. The tasks are ordered so each one delivers an independently callable and testable HTTP capability end-to-end before the next one builds on it.

## Architecture Decisions for This Plan

- **IamApi facade first, inside T1** — `workshops` has no cross-module query path to IAM today (`allowedDependencies = { "shared" }`). Before any staff capability can resolve user profiles, the `iam` module must expose an `IamApi` interface + `iam/api/` named-interface package + `workshops/package-info.java` must declare `"iam::api"` as an allowed dependency. Because every subsequent task depends on this, it is folded into T1 (List Assigned Staff), the first capability that calls into IAM.
- **Standard Keyset Pagination Reuse** — Collection read endpoints (`GET .../staff` and `GET .../staff/available`) directly reuse canonical platform types `com.unihub.workshop.shared.pagination.PageResponse<T>` and `CursorPage<T>` with `KeysetCursorCodec`, identical to `AdminWorkshopController` and `AuthController`. No custom page response wrapper DTOs are created.
- **Flyway migration in T1** — the `workshop_assignments` table is the shared foundation every capability reads from or writes to. It is introduced in the Flyway migration included in T1; later tasks depend on T1 being done and the table existing.
- **Schema-level guard, not application-level lock** — The DB `UNIQUE (workshop_id, staff_user_id)` constraint is the final concurrency guard. The application layer pre-checks run inside the same write transaction on the Primary DB (check-then-act), so the schema constraint only fires in a narrow race window.
- **Read capabilities before write capabilities** — T1 (List Assigned Staff) and T2 (List Available Staff) are placed before T3 (Batch Assign) and T4 (Revoke) because the assign/revoke use cases' acceptance tests need a working GET to verify their outcomes.
- **Outbox events per assignment in T3/T4** — `StaffAssignedToWorkshopEvent` (one per assigned staff member) and `StaffRevokedFromWorkshopEvent` (one per revocation) follow the `shared/events/` pattern using `@Externalized("unihub.events::notifications")`, consistent with `UserProvisionedEvent`.

## Task List

### Phase 1: Read Capabilities & Shared Foundation

- [x] **T1 — Organizer can list all staff currently assigned to a workshop (across any workshop status)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application (port + use case), Infrastructure (migration, JPA entity, repository adapter, IamApi facade), Presentation (controller, DTOs, mapper)
  - **Files:**
    - `apps/backend/src/main/resources/db/migration/V1__init_schema.sql` _(updated with keyset pagination index)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/IamApi.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/package-info.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/dto/package-info.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/dto/StaffUserDto.java` _(new — userId, organizationId, role, isActive, fullName, email, avatarUrl, phoneNumber)_
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/facade/IamFacade.java` _(new — implements IamApi)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/package-info.java` _(update allowedDependencies to add `"iam::api"`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/WorkshopAssignment.java` _(new domain record)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/AssignedStaff.java` _(new domain projection record)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListAssignedStaffQuery.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListAssignedStaffUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAssignmentRepository.java` _(new out-port)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListAssignedStaffUseCaseImpl.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentJpaEntity.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentJpaRepository.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` _(new — implements WorkshopAssignmentRepository)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/mapper/WorkshopAssignmentPersistenceMapper.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(update — add GET /{id}/staff handler)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/AssignedStaffItemResponse.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/mapper/WorkshopPresentationMapper.java` _(update — add assigned staff mapping)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListAssignedStaffUseCaseImplTest.java` _(new)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopControllerStaffListTest.java` _(new)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/workshops/{id}/staff` with a valid ORGANIZER JWT returns HTTP 200 with `ApiResponse<PageResponse<AssignedStaffItemResponse>>` containing `items: []` and `cursor.hasMore: false` when no staff are assigned. _(FR-04, FR-05, FR-07, Scenario 4)_
    - The same endpoint with 2 staff assigned returns 200 with items containing `userId`, `fullName`, `email`, `avatarUrl` (nullable), `phoneNumber` (nullable), `assignedAt`, and a keyset `cursor`. _(FR-08, Scenario 4)_
    - Workshop status `DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED` all return 200 (no restriction on GET assigned). _(FR-05, BR-08, Scenario 4)_
    - Requesting a non-existent workshop ID returns HTTP 404 in standard `ApiResponse` envelope. _(FR-06, Scenario 5)_
    - Requesting a workshop belonging to a different organization returns HTTP 403. _(FR-03, BR-01, Scenario 3)_
    - Missing/invalid JWT returns HTTP 401; wrong role (STUDENT/STAFF) returns HTTP 403. _(FR-01, FR-02, Scenarios 1–2)_
    - `ModulithArchitectureTest` passes (no forbidden cross-module access after adding `iam::api` allowed dependency). _(BR boundary enforcement)_

- [x] **T2 — Organizer can list available staff eligible for assignment to a PUBLISHED workshop before it starts**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application (port + use case), Infrastructure (JPA query + adapter method), Presentation (DTO, mapper method, controller endpoint)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/AvailableStaff.java` _(new domain projection record)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListAvailableStaffQuery.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListAvailableStaffUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListAvailableStaffUseCaseImpl.java` _(new — calls IamApi for STAFF list, queries workshop_assignments for conflicts)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAssignmentRepository.java` _(update — add findConflictingStaffUserIds, countActiveAssignmentsForUsers)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` _(update — implement new port methods)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/AvailableStaffItemResponse.java` _(new — userId, fullName, email, avatarUrl, phoneNumber, activeAssignmentsCount)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/mapper/WorkshopPresentationMapper.java` _(update — add available staff mapping)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(update — add GET /{id}/staff/available handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListAvailableStaffUseCaseImplTest.java` _(new)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/workshops/{id}/staff/available` with a PUBLISHED workshop (`starts_at` in the future) returns HTTP 200 with `ApiResponse<PageResponse<AvailableStaffItemResponse>>` containing paginated `items` with `userId`, `fullName`, `email`, `avatarUrl`, `phoneNumber`, `activeAssignmentsCount`. _(FR-09, FR-12, FR-15, Scenario 9)_
    - A staff member already assigned to the target workshop does NOT appear in the list. _(FR-14-c)_
    - A staff member whose schedule overlaps the target workshop (non-CANCELED other workshop) does NOT appear in the list. _(FR-14-d, BR-05, Scenario 10)_
    - A staff member assigned to a CANCELED workshop (same time) DOES appear in the available list. _(BR-05, Scenario 11)_
    - Keyword filter `?q=alice` returns only staff whose `fullName` or `email` matches case-insensitively. _(FR-13, Scenario 12)_
    - Workshop in `DRAFT`, `COMPLETED`, or `CANCELED` status returns HTTP 409 Conflict. _(FR-10, BR-03, Scenarios 6–7)_
    - PUBLISHED workshop where `now() >= starts_at` returns HTTP 422 Unprocessable Content. _(FR-11, BR-04, Scenario 8)_

#### Checkpoint: Phase 1

- [x] Both `GET .../staff` and `GET .../staff/available` endpoints pass all their acceptance criteria end-to-end.
- [x] `ListAssignedStaffUseCaseImplTest` and `ListAvailableStaffUseCaseImplTest` are green.
- [x] `ModulithArchitectureTest` passes — `workshops` only accesses `iam` via `iam::api` named interface.
- [x] Build is clean (no compilation errors or failing tests).

### Phase 2: Write Capabilities & Event Publishing

- [ ] **T3 — Organizer can batch-assign one or more STAFF members to a PUBLISHED workshop before it starts (atomic, all-or-nothing)**
  - **Size:** M
  - **Depends on:** T1, T2
  - **Layers touched:** Domain (validation logic), Application (command + use case + IamApi call), Infrastructure (repository write + outbox event publish), Presentation (request DTO, response DTO, controller endpoint)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/StaffAssignedToWorkshopEvent.java` _(new @Externalized outbox event)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/AssignStaffCommand.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/AssignStaffUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/AssignStaffUseCaseImpl.java` _(new — all-or-nothing batch: validate, check conflict, insert, publish events)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAssignmentRepository.java` _(update — add saveAll, findAlreadyAssignedUserIds, findScheduleConflictingUserIds)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` _(update — implement new port methods)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/AssignStaffRequest.java` _(new — staffUserIds: List<UUID>)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/BatchAssignResponse.java` _(new — workshopId, assignedStaffs: List<AssignedStaffItemResponse>)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/mapper/WorkshopPresentationMapper.java` _(update — add batch assign mapping)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(update — add POST /{id}/staff handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/AssignStaffUseCaseImplTest.java` _(new)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/{id}/staff` with valid STAFF user IDs for a PUBLISHED future workshop returns HTTP 201 with `ApiResponse<data>` containing `workshopId` and `assignedStaffs` (array with `userId`, `fullName`, `email`, `avatarUrl`, `phoneNumber`, `assignedAt`). _(FR-16, FR-22, FR-23, Scenario 13)_
    - The response `assignedStaffs` array length equals the number of UUIDs submitted. No `totalAssigned` field is present. _(API contract)_
    - Rows are persisted in `workshop_assignments`; one `StaffAssignedToWorkshopEvent` is published per assigned staff member via the Transactional Outbox. _(FR-23, BR-11)_
    - Workshop in `DRAFT`, `COMPLETED`, or `CANCELED` status returns HTTP 409 before any DB write. _(FR-17, Scenario 14)_
    - PUBLISHED workshop where `now() >= starts_at` returns HTTP 422 with `validationErrors`. _(FR-18, Scenario 15)_
    - Any staff user ID in the batch already assigned to this workshop returns HTTP 409 (entire batch rejected, no partial writes). _(FR-19, FR-22, BR-06, Scenario 16)_
    - Any staff user ID with an overlapping schedule with another non-CANCELED workshop returns HTTP 409 (all-or-nothing rollback). _(FR-20, FR-22, BR-06, Scenario 17)_
    - Any user ID not existing, not holding `STAFF` role, in a different org, or inactive returns HTTP 422 with `validationErrors`. _(FR-21, FR-22, BR-02, Scenario 18)_
    - Concurrent duplicate requests: the DB `UNIQUE (workshop_id, staff_user_id)` constraint catches any race, returning HTTP 409. _(BR-07, Scenario 23)_

- [ ] **T4 — Organizer can revoke a single staff member's assignment from a PUBLISHED workshop before it starts**
  - **Size:** S
  - **Depends on:** T3
  - **Layers touched:** Application (command + use case), Infrastructure (repository delete + outbox event publish), Presentation (controller endpoint)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/StaffRevokedFromWorkshopEvent.java` _(new @Externalized outbox event)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/RevokeStaffCommand.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/RevokeStaffUseCase.java` _(new)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/RevokeStaffUseCaseImpl.java` _(new — validate workshop status + timing, delete row, publish event)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAssignmentRepository.java` _(update — add deleteByWorkshopIdAndStaffUserId)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` _(update — implement delete method)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/AdminWorkshopController.java` _(update — add DELETE /{id}/staff/{userId} handler)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/RevokeStaffUseCaseImplTest.java` _(new)_
  - **Acceptance Criteria:**
    - `DELETE /api/v1/admin/workshops/{id}/staff/{userId}` for a valid PUBLISHED future workshop with an assigned staff member returns HTTP 204 No Content with no body. _(FR-24, FR-28, Scenario 19)_
    - The `workshop_assignments` row is deleted and one `StaffRevokedFromWorkshopEvent` is published via the Transactional Outbox. _(FR-28, BR-11)_
    - Workshop in `DRAFT`, `COMPLETED`, or `CANCELED` status returns HTTP 409. _(FR-25, BR-03, Scenario 20)_
    - PUBLISHED workshop where `now() >= starts_at` returns HTTP 422 with `validationErrors`. _(FR-26, BR-04, Scenario 21)_
    - Staff member not currently assigned to the workshop returns HTTP 404. _(FR-27, Scenario 22)_

#### Checkpoint: Feature Complete

- [ ] All four tasks (T1–T4) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end.
- [ ] `ModulithArchitectureTest` passes (`./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`).
- [ ] Full unit test suite for affected test classes is green (`ListAssignedStaffUseCaseImplTest`, `ListAvailableStaffUseCaseImplTest`, `AssignStaffUseCaseImplTest`, `RevokeStaffUseCaseImplTest`).
- [ ] Ready for `/review workshop-staff-assignment`.

## Risks and Mitigations

| Risk                                                                                                                                                                               | Impact | Mitigation                                                                                                                                                                                                                                                 |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `IamApi` facade does not yet exist — need to add `iam::api` named interface and update `workshops/package-info.java`; if done wrong, `ModulithArchitectureTest` breaks immediately | High   | T1 includes the facade creation, boundary update, and `ModulithArchitectureTest` execution together; boundary is verified from day 1                                                                                                                       |
| Cross-module query performance (avoiding N+1 and keeping boundary pure)                                                                                                            | High   | Use two-phase ID batching: `workshops` queries conflicting IDs in 1 indexed query, passes `excludedIds` to `IamApi` to paginate `users` in 1 query, and batch-counts assignments in 1 query. Total 2–3 indexed queries, 0 N+1, zero cross-module SQL joins |
| Keyset pagination on `workshop_assignments(assignedAt DESC, id DESC)` requires an index — without it queries will full-scan under load                                             | Medium | Migration `V3` must include `CREATE INDEX` on `workshop_assignments(workshop_id, assigned_at DESC, id DESC)` so pagination is always index-backed                                                                                                          |
| Concurrent batch assignment race: two requests assign the same staff member simultaneously and both pass application-level pre-checks before either commits                        | Medium | DB `UNIQUE (workshop_id, staff_user_id)` constraint is the final guard; `DataIntegrityViolationException` is caught and translated to HTTP 409 in the shared exception handler                                                                             |

## Open Questions

None. All design decisions were resolved during the spec and contract review phases.
