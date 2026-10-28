# Implementation Plan: Workshop Registration Monitoring and Roster

> **Spec:** `specs/workshop-registration-monitoring-and-roster/SPEC.md`  
> **API Contract:** `specs/workshop-registration-monitoring-and-roster/API_CONTRACT.md`  
> **Status:** APPROVED

## 1. Overview

This plan defines the engineering tasks to implement Feature 4.4 — **Workshop Registration Monitoring and Roster** in the `registrations` module of Core Backend (`apps/backend/`). It provides organizer-facing capabilities to monitor workshop capacity and seat counts in real-time, inspect attendee rosters with keyset cursor pagination and status filtering, and guarantees that all registration status changes broadcast a full 6-field real-time event snapshot (`registration.updated`) over Redis Pub/Sub to connected SSE dashboards.

## 2. Architecture Decisions for This Plan

- **Dedicated Admin Controller:** Create `WorkshopRegistrationMonitoringController` in `registrations.presentation.controllers` under the URI path `/api/v1/admin/workshops/{workshopId}/registrations` instead of modifying `RegistrationController` (which is student-scoped under `/api/v1/registrations`).
- **Read Replica Isolation:** Mark all queries in use cases and controller methods with `@Transactional(readOnly = true)` to route queries to the Neon read replica per ADR-015.
- **Cross-Module Boundaries:** Call `WorkshopApi.getWorkshopForRegistration(workshopId)` to check workshop existence and verify tenant isolation (`workshop.organizationId == jwt.org`), and invoke `IamApi.getStudentProfilesByIds(studentUserIds)` for batch student resolution to eliminate N+1 queries per BR-06.
- **Existing Keyset Pagination Reuse:** Leverage `JpaKeysetPagination` and the existing database index `idx_registrations_workshop_created_at_id` on `(workshop_id, created_at DESC, id DESC)` for zero-migration performant roster paging.
- **Single Source of Truth for Seat Counts:** Consolidate the calculation of seat count snapshots (`confirmedCount`, `pendingCount`, `capacity`, `availableSeats`, `occupancyPercentage`, `lastUpdatedAt`) into a reusable domain/application component so both the REST summary endpoint and real-time event broadcasts compute identical numbers per FR-17.

## 3. Task List

### Phase 1: Core Dashboard Monitoring & Attendee Roster

- [ ] **T1 — Workshop Registration Summary Endpoint (`GET .../registrations/summary`)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/domain/model/WorkshopRegistrationSeatCounts.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetWorkshopRegistrationSummaryQuery.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetWorkshopRegistrationSummaryUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopRegistrationSummaryUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/RegistrationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/controllers/WorkshopRegistrationMonitoringController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/WorkshopRegistrationSummaryResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopRegistrationSummaryUseCaseTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/WorkshopRegistrationSummaryControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/admin/workshops/{workshopId}/registrations/summary` as an `ORGANIZER` matching the workshop's `organizationId` returns `200 OK` with `confirmedCount`, `pendingCount`, `capacity`, `availableSeats`, `occupancyPercentage`, and `lastUpdatedAt` (traced to FR-04, FR-05, FR-06, FR-07, FR-08, Scenario 1).
    - If `capacity = 0`, `occupancyPercentage` returns `0.00` and `availableSeats` returns `0` (traced to FR-06, FR-07, BR-08, BR-09, Scenario 2).
    - If no registrations exist for the workshop, `confirmedCount=0`, `pendingCount=0`, and `lastUpdatedAt` falls back to the workshop's `updatedAt` (traced to FR-08, Scenario 3).
    - Non-existent `workshopId` returns `404 Not Found` (traced to FR-02, Scenario 4).
    - Request from an organizer whose `jwt.org` does not match `workshop.organizationId` returns `403 Forbidden` (traced to FR-03, BR-01, Scenario 5).
    - Unauthenticated request returns `401 Unauthorized` and caller with `STUDENT`/`STAFF` role returns `403 Forbidden` (traced to FR-01, Scenario 6, Scenario 7).
    - Query executes under `@Transactional(readOnly = true)` ensuring read replica routing (traced to FR-09, BR-02).

- [ ] **T2 — Workshop Attendee Roster Endpoint with Keyset Pagination and Status Filter (`GET .../registrations`)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/domain/model/WorkshopAttendeeRosterItem.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetWorkshopAttendeeRosterQuery.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/GetWorkshopAttendeeRosterUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopAttendeeRosterUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/RegistrationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/controllers/WorkshopRegistrationMonitoringController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/WorkshopAttendeeRosterResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/GetWorkshopAttendeeRosterUseCaseTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/WorkshopAttendeeRosterControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/admin/workshops/{workshopId}/registrations` without a cursor returns `200 OK` with up to 20 entries sorted by `created_at DESC, id DESC`, each entry containing nested `student: { id, fullName, studentNumber, email }`, `status`, `price`, `currency`, `registeredAt`, and cursor metadata `nextCursor` and `hasMore` (traced to FR-10, FR-11, FR-13, FR-14, Scenario 8).
    - Supplying `nextCursor` from the previous page fetches the next page of results until `hasMore: false` and `nextCursor: null` (traced to FR-10, FR-13, Scenario 9).
    - Supplying optional query parameter `status` (e.g., `CONFIRMED`) filters results to only matching registrations; invalid status value returns `422 Unprocessable Entity` (traced to FR-12, Scenario 10, Scenario 15).
    - Request specifying `size > 100` or `size < 1` is rejected with `422 Unprocessable Entity` (traced to FR-13, BR-04, Scenario 11).
    - If a cursor has a `filterFingerprint` that mismatches the request's `status` filter, request is rejected with `400 Bad Request` (traced to BR-05, Scenario 12).
    - Student profiles are batch-resolved via `IamApi.getStudentProfilesByIds` without per-row queries (traced to FR-14, BR-06).
    - Access control rules match T1: non-existent workshop returns `404`, wrong organization returns `403`, non-organizer returns `403`, unauthenticated returns `401`.

#### Checkpoint: Core Dashboard Monitoring & Attendee Roster Complete

- [ ] All T1 and T2 acceptance criteria pass in unit and WebMvc tests.
- [ ] Keyset pagination verifies correct ordering and cursor decoding without N+1 cross-module queries.
- [ ] Build is clean and `./gradlew test --tests "com.unihub.workshop.registrations.*"` passes.

### Phase 2: Live Seat Count Broadcasting Synchronization

- [ ] **T3 — Real-Time 6-Field Seat Count Snapshot Broadcasting on Registration State Transitions**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Application, Infrastructure
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/WorkshopRegistrationBroadcaster.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/WorkshopRegistrationBroadcasterImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/strategy/FreeRegistrationStrategy.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/CancelRegistrationUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/RegistrationReconciliationSweeper.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookUseCaseImpl.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/WorkshopRegistrationBroadcasterTest.java`
  - **Acceptance Criteria:**
    - When a registration transitions to `CONFIRMED` via free registration, payment webhook, or sweeper reconciliation, a `registration.updated` event containing the full 6-field snapshot `{ workshopId, confirmedCount, pendingCount, capacity, availableSeats, lastUpdatedAt }` is published to workshop channel `unihub:stream:workshop:{workshopId}` (traced to FR-15, FR-17, Scenario 13).
    - When a registration transitions to `CANCELED` via student cancellation or sweeper expiration, a `registration.updated` event containing the full 6-field snapshot is published to the workshop channel (traced to FR-16, FR-17, Scenario 14).
    - Snapshot values in the event payload match the exact formulas specified in FR-04 through FR-08 (traced to FR-17).
    - Failures during event publishing are logged and do not cause database transaction rollbacks.

#### Checkpoint: Feature Complete

- [ ] All tasks above (T1, T2, T3) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified.
- [ ] Architecture tests pass: `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`.
- [ ] Full backend test suite passes: `./gradlew test`.
- [ ] Ready for `/review`.

## 4. Risks and Mitigations

| Risk                                                     | Impact | Mitigation                                                                                                                                                                                   |
| :------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **High concurrency registration count lag**              | Low    | Read replica lag is < 100ms on Neon; dashboard queries accept point-in-time eventual consistency per NFR; transactions and live event broadcaster query primary or count adapter accurately. |
| **Batch student profile resolution failure**             | Medium | If `IamApi` profile resolution fails or is partially missing, propagate clean `500 Internal Server Error` per NFR / BR-06 rather than returning broken or inconsistent roster rows.          |
| **Cursor filter tampering**                              | Medium | Keyset cursor encodes `filterFingerprint` (`status=<VALUE>`). Mismatches are rejected with `400 Bad Request` per BR-05 to guarantee result set integrity.                                    |
| **Event broadcasting overhead during bulk sweeper runs** | Low    | Event publishing to Redis Pub/Sub is non-blocking with asynchronous I/O and fail-safe exception handling so sweeper execution is never blocked.                                              |

## 5. Open Questions

None. All 5 architectural and API design decisions were resolved and approved in the spec and contract pre-flight phase.
