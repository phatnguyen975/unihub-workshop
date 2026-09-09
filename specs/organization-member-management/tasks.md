# Implementation Plan: Organization Member Management

> **Spec:** `specs/organization-member-management/SPEC.md`  
> **API Contract:** `specs/organization-member-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan decomposes Feature 1.5 (`organization-member-management`) into four sequential, vertical-slice tasks organized across two logical phases:

- **Phase 1 (Read Operations):** Delivers the member listing query with cursor pagination and multi-attribute filtering (T1), followed by the individual member profile retrieval (T2), both routed to the PostgreSQL read replica.
- **Phase 2 (Lifecycle Mutations):** Delivers zero-trust account provisioning with temporary password generation and outbox notification (T3), followed by account status management (ban/unban) featuring fail-closed Redis session revocation and status change event publication (T4), both routed to the primary database.

Each task spans all required architectural layers (Domain $\rightarrow$ Application $\rightarrow$ Infrastructure $\rightarrow$ Presentation) and delivers an independently testable, externally observable capability.

## Architecture Decisions for This Plan

- **Vertical Slice Discipline:** Every task implements a complete caller-accessible capability across all layers. No horizontal layer-by-layer tasks.
- **DataSource Routing (ADR-015):** Read queries in Phase 1 (T1, T2) are routed to the PostgreSQL Read Replica via `@Transactional(readOnly = true)`. Write mutations in Phase 2 (T3, T4) are routed to the PostgreSQL Primary via `@Transactional`.
- **Fail-Closed Session Revocation (ADR-005, ADR-006):** In T4, banning a user executes Redis token blacklisting and session tracking removal before committing database state. If Redis fails, the entire ban transaction is aborted with `503 Service Unavailable` (`urn:unihub:error:service-unavailable`) without modifying PostgreSQL records.
- **Asynchronous Notification Delivery via Outbox (ADR-027, ADR-028):** Account provisioning (T3) and account status updates (T4) emit domain events (`UserProvisionedEvent` and `UserStatusChangedEvent`) through Spring Modulith's transactional outbox (`event_publication` table) externalized to RabbitMQ (`unihub.events::notifications`).
- **Natural Idempotency Enforcement:** Account provisioning relies on the database unique constraint on `users.email` to catch duplicate submissions (`409 Conflict`). Status update is an idempotent state assignment where re-applying the current status behaves as a no-op returning `200 OK`.
- **Keyset Cursor Pagination (BR-09):** Pagination in T1 uses composite keyset cursors `(created_at, id)` encoded as Base64 JSON, with `id DESC` ensuring stable ordering.

## Task List

### Phase 1: Member Roster Queries (Read Replica)

- [x] **T1 — List organization members with Keyset cursor pagination and multi-attribute filtering (`GET /api/v1/organizations/users`)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ListOrganizationMembersUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ListMembersQuery.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ListOrganizationMembersUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/MemberSummaryResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ListOrganizationMembersUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerListTest.java`
  - **Files (modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserRepository.java` (add member search/query method with cursor and filters)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaRepository.java` (add JPQL cursor pagination query)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserRepositoryAdapter.java` (implement cursor pagination and query execution)
  - **Acceptance Criteria:**
    - `GET /api/v1/organizations/users` with no filters returns `200 OK` containing members of the caller's organization across all roles (`ORGANIZER`, `STAFF`, `STUDENT`), strictly excluding the calling organizer (`user_id != currentUserId`). _(FR-01; SPEC Scenario 1)_
    - `GET /api/v1/organizations/users?role=STUDENT&isActive=true` filters members matching both conditions conjunctively. _(FR-02, FR-03, FR-05; SPEC Scenario 2)_
    - `GET /api/v1/organizations/users?search=keyword` performs a case-insensitive partial match across `full_name`, `email`, and `phone_number`. _(FR-04; SPEC Scenario 3)_
    - Keyset pagination with `limit=20` returns 20 items, `cursor.hasMore=true`, and an opaque Base64 `cursor.nextCursor`. Querying with `cursor=<nextCursor>` returns the next page. _(FR-06, FR-07; SPEC Scenario 4)_
    - `limit > 100` returns `400 Bad Request` (`urn:unihub:error:bad-request`). _(FR-07; SPEC Scenario 5)_
    - Unauthenticated requests return `401 Unauthorized` (`urn:unihub:error:unauthorized`). Non-organizer callers return `403 Forbidden` (`urn:unihub:error:forbidden`). _(FR-25, FR-26; SPEC Scenarios 16, 17)_

- [x] **T2 — View individual organization member profile (`GET /api/v1/organizations/users/{userId}`)**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files (new):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/GetOrganizationMemberUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/GetOrganizationMemberUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/MemberDetailResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/GetOrganizationMemberUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerDetailTest.java`
  - **Files (modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserRepository.java` (add `findByIdAndOrganizationId`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaRepository.java` (add finder method)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserRepositoryAdapter.java` (implement finder)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserController.java` (add endpoint method)
  - **Acceptance Criteria:**
    - `GET /api/v1/organizations/users/{userId}` for a member in the caller's organization returns `200 OK` with the complete member profile (`id`, `organizationId`, `email`, `fullName`, `role`, `phoneNumber`, `avatarUrl`, `isActive`, `isTemporaryPassword`, `studentRecordId`, `lastLoginAt`, `createdAt`, `updatedAt`). _(FR-09; SPEC Scenario 6)_
    - Requesting a `userId` that does not exist or belongs to another organization returns `404 Not Found` (`urn:unihub:error:not-found`) with uniform detail message to avoid leaking user existence across organizations. _(FR-10, FR-28; SPEC Scenario 7)_
    - Non-organizer caller returns `403 Forbidden`. Unauthenticated caller returns `401 Unauthorized`. _(FR-25, FR-26; SPEC Scenarios 16, 17)_

#### Checkpoint: Phase 1 Complete

- [x] List and detail member query capabilities are fully verified with unit tests.
- [x] Read replica routing (`@Transactional(readOnly = true)`) is verified for both endpoints.
- [x] Standard response envelopes and RFC 7807 error envelopes match `API_CONTRACT.md`.

### Phase 2: Member Lifecycle Mutations (Primary DB)

- [x] **T3 — Provision new `STAFF` or `ORGANIZER` member account (`POST /api/v1/organizations/users`)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ProvisionUserUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ProvisionUserCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ProvisionUserUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ProvisionUserRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ProvisionUserResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ProvisionUserUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerProvisionTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerTestBuilder.java`
  - **Files (modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserController.java` (add endpoint method)
  - **Acceptance Criteria:**
    - `POST /api/v1/organizations/users` with valid payload (`email`, `fullName`, `phoneNumber`, `role='STAFF'|'ORGANIZER'`) returns `201 Created` with member metadata. User record in database has `is_active=true`, `is_temporary_password=true`, and an Argon2id-hashed password generated via `PasswordGenerator`. _(FR-12, FR-15, FR-16; SPEC Scenario 8)_
    - Plaintext temporary password is NOT exposed in the HTTP response; a `UserProvisionedEvent` is published to the transactional outbox (`event_publication` table) destined for `unihub.events::notifications` containing the plaintext temporary password. _(FR-17; SPEC Scenario 8)_
    - Submitting `role='STUDENT'` returns `422 Unprocessable Content` (`urn:unihub:error:student-provisioning-forbidden`) and no record is created. _(FR-13; SPEC Scenario 9)_
    - Submitting an email that already exists globally in the system returns `409 Conflict` (`urn:unihub:error:conflict`). _(FR-14; SPEC Scenario 10)_
    - Malformed payload or validation error returns `422 Unprocessable Content` (`urn:unihub:error:validation`). _(API_CONTRACT §3.3)_
    - Non-organizer caller returns `403 Forbidden`. Unauthenticated caller returns `401 Unauthorized`. _(FR-25, FR-26; SPEC Scenarios 16, 17)_

- [x] **T4 — Update member active status (Ban/Unban) with fail-closed session invalidation and notification event (`PATCH /api/v1/organizations/users/{userId}/status`)**
  - **Size:** M
  - **Depends on:** T1, T2
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new):**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/UserStatusChangedEvent.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/UpdateMemberStatusUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/UpdateMemberStatusCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/UpdateMemberStatusUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/UpdateMemberStatusRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/UpdateMemberStatusResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/UpdateMemberStatusUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerStatusTest.java`
  - **Files (modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserRepository.java` (add `updateIsActive`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaRepository.java` (add status update query)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserRepositoryAdapter.java` (implement status update)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserController.java` (add endpoint method)
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/OrganizationUserControllerTestBuilder.java` (add status use case mock)
  - **Acceptance Criteria:**
    - `PATCH /api/v1/organizations/users/{userId}/status` with `{ "isActive": false }` adds all active user JTIs to the Redis blacklist, removes sessions from the Redis session tracker, revokes all active refresh tokens in PostgreSQL, updates `users.is_active = false`, and publishes `UserStatusChangedEvent` (`isActive = false`) to the transactional outbox, returning `200 OK`. _(FR-19, FR-21; SPEC Scenario 11)_
    - If Redis is unavailable during a ban attempt, the operation fails with `503 Service Unavailable` (`urn:unihub:error:service-unavailable`) with `Retry-After` header, leaving PostgreSQL tokens and user status unmodified (fail-closed). _(FR-21; SPEC Scenario 12)_
    - Attempting to modify caller's own active status returns `403 Forbidden` (`urn:unihub:error:self-status-modification-forbidden`). _(FR-20; SPEC Scenario 13)_
    - `PATCH` with `{ "isActive": true }` sets `users.is_active = true` in PostgreSQL and publishes `UserStatusChangedEvent` (`isActive = true`) to outbox without Redis session manipulation, returning `200 OK`. _(FR-19, FR-22; SPEC Scenario 14)_
    - Submitting a status that matches the member's current status behaves as an idempotent no-op, performing no Redis or database updates and returning `200 OK`. _(FR-23; SPEC Scenario 15)_
    - Target `userId` not found in caller's organization returns `404 Not Found` (`urn:unihub:error:not-found`). _(FR-10, FR-28)_
    - Non-organizer caller returns `403 Forbidden`. Unauthenticated caller returns `401 Unauthorized`. _(FR-25, FR-26; SPEC Scenarios 16, 17)_

#### Checkpoint: Feature Complete

- [x] All 4 tasks completed and all individual acceptance criteria pass.
- [x] Unit tests for all affected controllers, use cases, and repository adapters execute and pass.
- [x] Spring Modulith architectural boundaries verify cleanly (`ModulithArchitectureTest`).
- [x] Ready for `/review organization-member-management`.

## Risks and Mitigations

| Risk                                               | Impact | Mitigation                                                                                                                                                                                           |
| :------------------------------------------------- | :----: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Distributed State Desynchronization during Ban** |  High  | Strict execution order: Redis JTI blacklist and session removal execute first. If Redis throws an exception, the request fails with 503 before any database update or token revocation is performed. |
| **Cross-Tenant Information Leakage**               |  High  | Member lookup queries strictly filter by `organization_id = jwt.org`. Non-existent user IDs and user IDs from foreign organizations return identical `404 Not Found` responses.                      |
| **Email Collision on Concurrent Provisioning**     | Medium | Protected by the unique index on `users.email`. Concurrent provisioning calls result in a clean `409 Conflict` via `DataIntegrityViolationException` translation in `GlobalExceptionHandler`.        |
| **Pagination Ordering Drift**                      |  Low   | Composite cursor `(created_at, id)` with tie-breaking `id DESC` guarantees deterministic paging even when members are created at the exact same millisecond.                                         |

## Open Questions

None. All scope, error taxonomies, and architectural boundaries are fully settled in `SPEC.md` and `API_CONTRACT.md`.
