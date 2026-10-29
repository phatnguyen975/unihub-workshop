# Implementation Plan: Mobile Offline Check-in — Workshop Data Provisioning

> **Spec:** `specs/mobile-offline-checkin/SPEC.md`  
> **API Contract:** `specs/mobile-offline-checkin/API_CONTRACT.md`  
> **Status:** APPROVED

## 1. Overview

This plan decomposes the backend data provisioning capability (`GET /api/v1/workshops/assigned`) for Feature 6.1 into two ordered vertical-slice tasks within the `workshops` Spring Modulith module (`apps/backend/`). Each task spans every architectural layer it touches (Domain, Application, Infrastructure/Persistence, and Presentation) and is end-to-end verifiable using unit and MockMvc slice tests. The first slice establishes the authenticated, keyset-paginated data retrieval baseline reading from the Primary DB; the second slice layers on structured filtering (`status`, `from`, `to`), keyword search (`q`), input validation error handling (RFC 7807 400), and pagination cursor fingerprint validation to safeguard data integrity against filter drift.

## 2. Architecture Decisions for This Plan

- **Single Primary DB Query with Inner Join:** Data retrieval joins `workshop_assignments`, `workshops`, and `organizations` in a single Criteria query in `WorkshopAssignmentRepositoryAdapter` to fetch `organizationName` directly without cross-module RPC or N+1 queries.
- **Dedicated Presentation Controller:** Introduce `StaffWorkshopController` mapped to `/api/v1/workshops/assigned` with `@PreAuthorize("hasRole('STAFF')")`, separating staff mobile provisioning from organizer-facing admin endpoints (`AdminWorkshopController`) and public catalog endpoints (`PublicWorkshopController`).
- **Keyset Cursor Tie-Breaking:** Keyset pagination uses primary sort `starts_at ASC` and deterministic tie-breaker `id ASC` via `JpaKeysetPagination` and `KeysetCursorCodec`, strictly aligned with `docs/api-standards.md §Pagination`.
- **Strict Validation Strategy:** Reject invalid query parameters (`size < 1 || size > 100`, `status` other than `PUBLISHED`/`COMPLETED`, `from > to`, `q.length > 200`) with HTTP 400 Bad Request using `BadRequestException`, producing `urn:unihub:error:bad-request` with a `validationErrors` list.

## 3. Task List

### Phase 1: Core Assigned Workshops Provisioning Flow

- [x] **T1 — Authenticated Keyset-Paginated Assigned Workshop Provisioning (Core Slice)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure (Persistence), Presentation (Web/REST)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/StaffAssignedWorkshop.java` (Domain entity/projection)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListStaffAssignedWorkshopsQuery.java` (Application input query)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListStaffAssignedWorkshopsUseCase.java` (Inbound use case port)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImpl.java` (Application use case service)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAssignmentRepository.java` (Outbound persistence port extension)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` (Criteria query implementation on Primary DB)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopController.java` (REST controller with RBAC)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/dto/StaffAssignedWorkshopResponse.java` (Wire DTO with `@JsonInclude(NON_NULL)`)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/mapper/StaffWorkshopPresentationMapper.java` (Domain-to-DTO mapper)
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImplTest.java` (Use case unit tests)
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopControllerTest.java` (Controller MockMvc test slice)
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/workshops/assigned` without token returns HTTP 401 with RFC 7807 error wrapped in `ApiResponse` (_Traces to: FR-01, Scenario 4_).
    - Calling `GET /api/v1/workshops/assigned` with non-STAFF role (e.g. `STUDENT`) returns HTTP 403 (_Traces to: FR-02, Scenario 5_).
    - Authenticated `STAFF` retrieves only workshops explicitly assigned to their `jwt.sub` in `workshop_assignments` (_Traces to: FR-03, FR-04, Scenario 1, Scenario 14_).
    - Any workshop with `status = CANCELED` is excluded from the returned payload even if assigned (_Traces to: FR-05, BR-02, Scenario 3_).
    - Staff with zero assignments receives HTTP 200 with empty `items: []` and `hasMore: false` (_Traces to: FR-04, Scenario 2_).
    - Query routes to Primary DB and uses keyset cursor sorted by `starts_at ASC, id ASC`, returning correct `items`, `nextCursor`, and `hasMore` across multiple pages (_Traces to: FR-06, FR-07, FR-08, FR-10, FR-11, Scenario 6_).
    - Request specifying `size > 100` or `size < 1` returns HTTP 400 Bad Request with `validationErrors` (_Traces to: FR-09, BR-04, Scenario 7_).
    - Response strictly matches `ApiResponse<PageResponse<StaffAssignedWorkshopResponse>>` with `metadata.processedBy` and `metadata.traceId`, omitting null `description`/`coverImageUrl` (_Traces to: FR-19, FR-20, FR-21_).

#### Checkpoint: Core Provisioning Baseline

- [x] Task T1 passes all unit and MockMvc slice tests.
- [x] Spring Modulith architecture test passes: `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`.

### Phase 2: Filtering & Cursor Integrity

- [x] **T2 — Filtered Provisioning with Fingerprint Integrity Verification (Filtered Slice)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure (Persistence), Presentation (Web/REST)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/domain/model/StaffWorkshopFilter.java` (Domain filter value object & validation)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ListStaffAssignedWorkshopsQuery.java` (Extended with filter attributes)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImpl.java` (Filter validation & fingerprint verification)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopAssignmentRepositoryAdapter.java` (Dynamic Criteria predicates for `status`, `from`, `to`, `q`)
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopController.java` (Binding `status`, `from`, `to`, `q` query params)
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ListStaffAssignedWorkshopsUseCaseImplTest.java` (Filter & fingerprint unit tests)
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/presentation/controllers/StaffWorkshopControllerTest.java` (Filter validation MockMvc tests)
  - **Acceptance Criteria:**
    - Passing `status=COMPLETED` or `status=PUBLISHED` returns only workshops matching that status (_Traces to: FR-12, Scenario 8_).
    - Passing any invalid `status` (such as `CANCELED`, `DRAFT`, or unknown string) returns HTTP 400 Bad Request with field error on `status` (_Traces to: FR-16, BR-06, Scenario 9_).
    - Passing `from` and/or `to` ISO-8601 UTC timestamps filters workshops where `starts_at >= from` and/or `starts_at <= to` (_Traces to: FR-13, FR-14, Scenario 10_).
    - Passing malformed timestamps or `from > to` returns HTTP 400 Bad Request (_Traces to: FR-17, BR-07, Scenario 11_).
    - Passing `q` performs case-insensitive substring match against `title`, `slug`, and `location` (_Traces to: FR-15, Scenario 12_).
    - Passing `q` with length > 200 characters returns HTTP 400 Bad Request (_Traces to: API_CONTRACT.md Validation Rules_).
    - When filters are active, the returned `nextCursor` embeds a SHA-256 filter fingerprint; replaying that cursor with altered filters returns HTTP 400 Bad Request with `CURSOR_FILTER_MISMATCH` (_Traces to: FR-10, FR-18, BR-05, Scenario 13_).

#### Checkpoint: Feature Complete

- [x] All tasks (T1, T2) are checked off.
- [x] Every acceptance criterion across Scenarios 1 through 14 has been verified.
- [x] Spring Modulith module boundaries verified via `ModulithArchitectureTest`.
- [x] Full backend test suite passes: `./gradlew test`.
- [x] Ready for `/review mobile-offline-checkin`.

## 4. Risks and Mitigations

| Risk                                                                                                                                                                                                  | Impact | Mitigation                                                                                                                                                                                                  |
| :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Primary DB Join Overhead:** Joining `workshop_assignments`, `workshops`, and `organizations` under high concurrency could increase latency on the Primary DB.                                       | Medium | Filter by `staff_user_id` first using the existing index on `workshop_assignments(staff_user_id)`. The joined result set per staff member is small (typically <50 workshops), avoiding heavy nested loops.  |
| **Filter Fingerprint Drift:** Client altering filter parameters (e.g. changing `status` or `q`) while continuing to paginate with a previous cursor could return duplicate or corrupted data windows. | High   | Embed a deterministic filter fingerprint in `KeysetCursor` on the server side and reject requests with HTTP 400 if the replayed cursor's fingerprint does not match the active query's fingerprint (BR-05). |
| **Timezone Inconsistencies in Date Filtering:** Staff mobile client sending non-UTC or offset timestamps in `from`/`to`.                                                                              | Low    | Enforce ISO-8601 UTC parsing via `Instant.parse(...)`. Reject invalid string formats early at the controller layer with HTTP 400.                                                                           |

## 5. Open Questions

None. All technical decisions (database routing, response envelope formatting, validation status codes, pagination keyset fields) have been agreed and approved in `SPEC.md` and `API_CONTRACT.md`.
