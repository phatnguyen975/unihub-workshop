# Implementation Plan: In-App Notification Management

> **Spec:** `specs/in-app-notification-management/SPEC.md`  
> **API Contract:** `specs/in-app-notification-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan outlines the vertical-slice implementation for Feature 7.2 (`in-app-notification-management`) in `apps/backend/`. The feature establishes a new Spring Modulith module (`com.unihub.workshop.notifications`) following Clean Architecture, providing four authenticated REST endpoints under `/api/v1/notifications`. The capabilities are divided into two phases: read path queries (navbar badge unread count and keyset-paginated notification inbox with read-state filtering) followed by state mutations (single mark-read and bulk mark-all-read with real-time SSE synchronization across active client sessions).

## Architecture Decisions for This Plan

- **Module boundary & dependencies:** The new `notifications` module declares `@ApplicationModule(displayName = "Notifications Module", allowedDependencies = {"shared"})` in `package-info.java`. It has zero direct dependencies on other business modules (`iam`, `workshops`, `registrations`).
- **Clean Architecture layering per slice:** Each task implements its own vertical slice across Domain (`model`, `repository`), Application (`port/in`, `service`, `dto`), Infrastructure (`persistence`, `realtime`), and Presentation (`controllers`, `responses`).
- **No new schema migrations:** The database table `in_app_notifications` and the composite index `idx_in_app_notifications_user_is_read (user_id, is_read)` already exist in `V1__init_schema.sql`.
- **Primary vs. Replica routing:** Read use cases (T1, T2) execute with `@Transactional(readOnly = true)` routing to the Neon read replica. Mutation use cases (T3, T4) execute with `@Transactional` routing to the Neon primary datasource. In mutation endpoints, the updated `unreadCount` is computed from the primary database to avoid replication lag inconsistency (BR-07).
- **Idempotency & SSE event suppression:** Mutations suppress real-time SSE events whenever no database state transition occurred (i.e., marking an already-read notification or calling `read-all` when unread count is already zero).
- **Routing disambiguation:** Route `@PatchMapping("/read-all")` is declared before `@PatchMapping("/{id}/read")` (with explicit UUID path regex matching) to prevent literal route collision.
- **Strict non-null JSON serialization:** All DTOs strictly adhere to `@JsonInclude(JsonInclude.Include.NON_NULL)` per `docs/api-standards.md`, dropping all `null` fields (`readAt` when unread, `actionUrl` when absent, `nextCursor` when no more pages, optional payload fields).

## Task List

### Phase 1: Read Path Capabilities (Query & Observability)

- [ ] **T1 — Dedicated Unread Count Endpoint (`GET /api/v1/notifications/unread-count`)**
  - **Size:** S
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/package-info.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/model/InAppNotification.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/repository/NotificationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/InAppNotificationJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/SpringDataNotificationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/NotificationRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/port/in/GetUnreadCountUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/service/GetUnreadCountService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/controllers/NotificationController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/responses/UnreadCountResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/domain/InAppNotificationTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/application/GetUnreadCountServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/infrastructure/NotificationRepositoryAdapterTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/presentation/NotificationControllerTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/notifications/unread-count` with valid JWT returns `200 OK` and `{ "success": true, "data": { "unreadCount": <count> }, "metadata": { "processedBy": "...", "traceId": "..." } }` (FR-06, Scenario 4).
    - Query uses index `idx_in_app_notifications_user_is_read` filtering `user_id = :userId AND is_read = false` on replica datasource (`readOnly = true`) (BR-05, BR-06).
    - Calling without `Authorization` header returns `401 Unauthorized` with RFC 7807 problem detail (BR-11, Scenario 11).
    - Module boundaries satisfy Spring Modulith architectural rules (`ModulithArchitectureTest` passes).

- [ ] **T2 — Paginated Notification Listing and Filtering (`GET /api/v1/notifications`)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/repository/NotificationRepository.java` (add keyset find method)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/NotificationRepositoryAdapter.java` (implement keyset query)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/dto/GetNotificationsQuery.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/port/in/GetNotificationsUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/service/GetNotificationsService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/responses/NotificationResponse.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/responses/NotificationPageData.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/controllers/NotificationController.java` (add `GET /`)
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/application/GetNotificationsServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/infrastructure/NotificationRepositoryAdapterKeysetTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/presentation/NotificationControllerListTest.java`
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/notifications` returns `200 OK` with items sorted by `createdAt DESC, id DESC`, `cursor: { nextCursor, hasMore }`, and total user `unreadCount` (FR-01, FR-03, FR-04, FR-05, Scenario 1).
    - Query parameter `isRead=false` returns only unread items; `isRead=true` returns only read items; omitted returns all items (FR-02, Scenario 2, Scenario 3).
    - Internal `idempotencyKey` in notification payload is stripped and never returned in the API response (BR-04, Scenario 14).
    - Null fields are omitted from JSON serialization (`readAt` absent when unread, `actionUrl` absent when null, `nextCursor` absent when `hasMore = false`) per BR-12.
    - Malformed cursor or sortField mismatch returns `400 Bad Request` (`urn:unihub:error:bad-request`) (BR-09, Scenario 12).
    - Query parameter `size > 100` or `size < 1` returns `422 Unprocessable Content` (`urn:unihub:error:validation`) (BR-10, Scenario 13).

#### Checkpoint: Phase 1 (Read Path Complete)

- [ ] All read capabilities (T1, T2) pass their acceptance criteria end-to-end.
- [ ] `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"` passes with zero boundary violations.
- [ ] Keyset pagination and unread counts execute cleanly against repository adapters.

### Phase 2: Mutation & Synchronization Capabilities (State Transitions & SSE)

- [ ] **T3 — Mark Single Notification as Read with Real-Time SSE Sync (`PATCH /api/v1/notifications/{id}/read`)**
  - **Size:** M
  - **Depends on:** T1, T2
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/model/InAppNotification.java` (add `markAsRead(Instant)` business method)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/repository/NotificationRepository.java` (add findById and save/update methods)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/NotificationRepositoryAdapter.java` (implement findById & save)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/port/in/MarkNotificationReadUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/service/MarkNotificationReadService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/responses/MarkNotificationReadResponse.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/controllers/NotificationController.java` (add `PATCH /{id}/read`)
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/domain/InAppNotificationMarkReadTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/application/MarkNotificationReadServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/presentation/NotificationControllerMarkReadTest.java`
  - **Acceptance Criteria:**
    - Calling `PATCH /api/v1/notifications/{id}/read` for an unread notification owned by the caller transitions `is_read` to `true`, sets server `read_at = now()`, persists to primary database, publishes a `notification.read` event via `RealtimeEventPublisher`, and returns `200 OK` with updated notification and new `unreadCount` (FR-07, FR-08, FR-15, BR-02, BR-03, Scenario 5).
    - Calling `PATCH /api/v1/notifications/{id}/read` for an already-read notification returns `200 OK` with current state and unread count, executes zero database updates, and suppresses the SSE event (FR-09, FR-17, Scenario 6).
    - Calling for a notification owned by a different user returns `403 Forbidden` (`urn:unihub:error:forbidden`) without database write or SSE event (FR-10, BR-01, Scenario 7).
    - Calling with a non-existent notification ID returns `404 Not Found` (`urn:unihub:error:not-found`) (FR-11, Scenario 8).
    - Invalid UUID syntax for `{id}` returns `400 Bad Request` (`urn:unihub:error:bad-request`).
    - Transient SSE publisher failure logs a warning without rolling back the successful database commit.

- [ ] **T4 — Bulk Mark All Notifications as Read with Real-Time SSE Sync (`PATCH /api/v1/notifications/read-all`)**
  - **Size:** S
  - **Depends on:** T3
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/domain/repository/NotificationRepository.java` (add `markAllAsReadByUserId(UUID, Instant)`)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/SpringDataNotificationRepository.java` (add `@Modifying` bulk update query)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/infrastructure/persistence/NotificationRepositoryAdapter.java` (implement bulk update)
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/port/in/MarkAllNotificationsReadUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/application/service/MarkAllNotificationsReadService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/responses/MarkAllReadResponse.java`
    - `apps/backend/src/main/java/com/unihub/workshop/notifications/presentation/controllers/NotificationController.java` (add `PATCH /read-all` with route precedence)
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/application/MarkAllNotificationsReadServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/notifications/presentation/NotificationControllerMarkAllReadTest.java`
  - **Acceptance Criteria:**
    - Calling `PATCH /api/v1/notifications/read-all` when unread notifications exist updates all matching records to `is_read = true`, returns `200 OK` with `{ "updatedCount": N, "unreadCount": 0 }`, and publishes a `notification.read` SSE event with `{ "all": true, "unreadCount": 0, "readAt": "..." }` (FR-12, FR-13, FR-16, Scenario 9).
    - Calling `PATCH /api/v1/notifications/read-all` when unread count is already `0` returns `200 OK` with `{ "updatedCount": 0, "unreadCount": 0 }` and suppresses the SSE event (FR-14, FR-18, Scenario 10).
    - Route precedence guarantees `/read-all` matches the bulk endpoint and is never matched as a UUID path variable `{id}` of the single-read endpoint.

#### Checkpoint: Feature Complete

- [ ] All 4 tasks (T1, T2, T3, T4) are checked off.
- [ ] Every acceptance criterion across all 14 scenarios has been verified with failing-first unit tests (TDD).
- [ ] Full unit test suite passes: `./gradlew test --tests "com.unihub.workshop.notifications.*"`.
- [ ] Architecture regression test passes: `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`.
- [ ] Ready for `/review in-app-notification-management`.

## Risks and Mitigations

| Risk                                                                | Impact | Mitigation                                                                                                                                                  |
| :------------------------------------------------------------------ | :----: | :---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Spring MVC Route Collision** between `/read-all` and `/{id}/read` |  High  | Register `@PatchMapping("/read-all")` explicitly before `@PatchMapping("/{id:[a-fA-F0-9\\-]{36}}/read")` and use UUID regex constraint on path variable.    |
| **Read-After-Write Lag** on unread count in mutation responses      | Medium | Compute `unreadCount` inside the primary transaction or derive it directly from the primary mutation result (BR-07), rather than querying the read replica. |
| **Transient SSE Failure** failing HTTP response                     | Medium | Wrap `RealtimeEventPublisher.publishToUser` call in a try-catch block logging a warning; ensure database commit remains successful.                         |
| **JSONB Payload Deserialization & Internal Field Leakage**          |  Low   | Provide dedicated deserializer / mapper for JSONB payload that explicitly removes `idempotencyKey` before returning `NotificationResponse`.                 |

## Open Questions

None. All architectural and functional requirements have been confirmed in `SPEC.md` and `API_CONTRACT.md`.
