# Spec: In-App Notification Management

> **Status:** APPROVED  
> **Domain:** 7 — Notification System  
> **Owner/area:** `notifications` module — `apps/backend/`  
> **Last revised:** 2026-10-08 — first version

## 1. Overview

The In-App Notification Management feature provides authenticated users (students, staff, and organizers) with the ability to retrieve their in-app notifications and mark them as read — individually or all at once. Notification records are created exclusively by the Notification Delivery Pipeline (Feature 7.1 / Worker); this feature is solely responsible for reading and transitioning those records. It also exposes a lightweight unread-count endpoint so the UI can keep a notification badge current without loading the full list. Multi-tab and multi-device synchronization is maintained by publishing a real-time SSE event whenever a read-state change is actually persisted, allowing other active sessions of the same user to update their local UI state deterministically. This feature is owned by the new `notifications` Spring Modulith module within `apps/backend/`.

## 2. Actors & Preconditions

| Actor       | Precondition                                                                                                                                    |
| :---------- | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| `STUDENT`   | Holds a valid, non-revoked RS256 JWT with role `STUDENT`. At least zero notification records exist for the user (empty list is a valid result). |
| `STAFF`     | Holds a valid, non-revoked RS256 JWT with role `STAFF`. At least zero notification records exist for the user.                                  |
| `ORGANIZER` | Holds a valid, non-revoked RS256 JWT with role `ORGANIZER`. At least zero notification records exist for the user.                              |

**Shared preconditions for all actors:**

- The JWT is present in the `Authorization: Bearer <token>` header.
- The JWT has not been revoked (the `jti` claim is not on the Redis JTI blacklist).
- For mark-read operations: the target notification exists in `in_app_notifications` and was created for the authenticated user.

## 3. Functional Requirements

### 3.1 List Notifications

- **FR-01:** The system SHALL return a paginated list of notifications belonging to the authenticated user when the user requests their notification list.
- **FR-02:** The system SHALL support an optional boolean filter `isRead` that, when provided as `true`, returns only read notifications; when provided as `false`, returns only unread notifications; and when omitted, returns all notifications regardless of read state.
- **FR-03:** The system SHALL sort notifications in descending order of creation time (most recent first), with ties broken by notification ID descending.
- **FR-04:** The system SHALL use keyset (cursor-based) pagination, accepting an opaque cursor and a page size, with a default size of 20 and a maximum size of 100.
- **FR-05:** The system SHALL include the total unread count for the authenticated user in every paginated list response, regardless of the active `isRead` filter or the current page position, reflecting the true count of all unread notifications at the moment of the query.

### 3.2 Unread Count

- **FR-06:** The system SHALL return the total count of unread notifications for the authenticated user when the user requests only the unread count.

### 3.3 Mark Single Notification as Read

- **FR-07:** The system SHALL transition a single notification from unread to read and record the timestamp of the transition when the authenticated user marks a specific notification as read.
- **FR-08:** The system SHALL return `200 OK` with the updated notification (reflecting `isRead = true`, `readAt` set) and the new total unread count when a notification is successfully marked as read.
- **FR-09:** The system SHALL return `200 OK` with the current notification state (unchanged) and the current unread count, without performing any write, when the authenticated user marks a notification that is already read.
- **FR-10:** The system SHALL reject the request with a forbidden error when the authenticated user attempts to mark a notification that belongs to a different user.
- **FR-11:** The system SHALL reject the request with a not-found error when the authenticated user attempts to mark a notification that does not exist.

### 3.4 Mark All Notifications as Read

- **FR-12:** The system SHALL transition all unread notifications belonging to the authenticated user to read, recording the transition timestamp on each, when the user requests a bulk read operation.
- **FR-13:** The system SHALL return `200 OK` with the count of notifications actually updated and the new total unread count (zero) when at least one notification was transitioned.
- **FR-14:** The system SHALL return `200 OK` with an updated count of zero and the current unread count (zero) without performing any write when the authenticated user issues a bulk-read request but already has no unread notifications.

### 3.5 Real-Time Synchronization

- **FR-15:** The system SHALL publish a real-time SSE event of type `notification.read` to all active sessions of the authenticated user when a single notification is successfully transitioned from unread to read (i.e., a write actually occurred).
- **FR-16:** The system SHALL publish a real-time SSE event of type `notification.read` to all active sessions of the authenticated user when at least one notification is transitioned by a bulk-read operation (i.e., a write actually occurred).
- **FR-17:** The system SHALL NOT publish a real-time SSE event when a mark-single-read request is received for an already-read notification.
- **FR-18:** The system SHALL NOT publish a real-time SSE event when a bulk-read request is received and no notifications were actually modified (unread count was already zero).

## 4. Business Rules

- **BR-01:** Each notification belongs exclusively to one user (`user_id` FK). A user may only read or list their own notifications. Cross-user access is always rejected with a forbidden error, never a not-found, to prevent user enumeration.
- **BR-02:** The `is_read` state transition is monotonically increasing: a notification may only move from `false` to `true`. There is no "unread" or "unmark" operation. This is a confirmed system constraint from the Notification Delivery Pipeline spec (Feature 7.1, BR-02).
- **BR-03:** The `readAt` timestamp is set at the moment the transition is persisted to the database; it reflects server time, not client-supplied time.
- **BR-04:** The `payload` field returned in a notification response is the deserialized JSONB object stored in `in_app_notifications.payload`, with the internal `idempotencyKey` field excluded from the API response. Other payload fields (e.g., `workshopId`, `workshopTitle`, `startsAt`, `endsAt`, `location`, `actionUrl`, `reason`) are passed through as-is.
- **BR-05:** All read (SELECT) operations route to the Neon Read Replica datasource. All write (UPDATE) operations route to the Neon Primary datasource. This rule applies even within the same request (e.g., mark-single-read performs its existence/ownership check via a primary write that returns the updated record, not a separate replica read followed by a primary write).
- **BR-06:** The existing composite index `idx_in_app_notifications_user_is_read ON in_app_notifications (user_id, is_read)` must be utilized by all queries filtering on `(user_id, is_read)`. No full table scan on `in_app_notifications` is acceptable.
- **BR-07:** The `unreadCount` value in list and mark-read responses is computed from the authoritative primary database (or the result of a primary write), not from the replica, to avoid read-after-write inconsistency on a mutation response.
- **BR-08:** The SSE `notification.read` event payload must include: `id` (the specific notification ID, or absent on bulk-read), `all` (boolean: `false` for single-read, `true` for bulk-read), `unreadCount` (the new total, always `0` for bulk-read), and `readAt` (ISO 8601 UTC timestamp of the transition).
- **BR-09:** Pagination cursors are opaque Base64-encoded tokens. A cursor that is malformed, expired (references a sort position no longer valid), or was generated for a different sort order must be rejected with a client error.
- **BR-10:** The maximum allowed page size is 100. A request specifying `size > 100` must be rejected with a client error.
- **BR-11:** Access to this feature requires a valid, non-expired, non-revoked JWT (RS256). Requests without a valid token are rejected with an unauthorized error before any business logic executes.
- **BR-12:** Conforming to `@JsonInclude(JsonInclude.Include.NON_NULL)` and `docs/api-standards.md`, all API responses strictly omit fields with `null` values. Nullable fields such as `readAt` (when `isRead = false`), `actionUrl` (when absent), `cursor.nextCursor` (when `hasMore = false`), and optional `payload` attributes are dropped from the JSON output and never serialized as `"field": null`.

## 5. State Transitions

The `is_read` field on `in_app_notifications` is the sole lifecycle state managed by this feature.

| From State      | Event/Trigger                                       | To State                   |       Valid?        | Rejection Behavior (if invalid)                                                                                                                 |
| :-------------- | :-------------------------------------------------- | :------------------------- | :-----------------: | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| `is_read=false` | User marks single notification as read              | `is_read=true`             |       ✅ Yes        | —                                                                                                                                               |
| `is_read=true`  | User marks single already-read notification as read | `is_read=true` (no change) | ✅ Yes (idempotent) | Returns `200 OK` with current state; no write; no SSE event published                                                                           |
| `is_read=false` | User marks all notifications as read                | `is_read=true`             |       ✅ Yes        | —                                                                                                                                               |
| `is_read=true`  | User marks all (no unread exist) as read            | `is_read=true` (no change) | ✅ Yes (idempotent) | Returns `200 OK` with `updatedCount=0, unreadCount=0`; no write; no SSE event published                                                         |
| `is_read=true`  | User attempts to un-read a notification             | `is_read=false`            |        ❌ No        | This operation does not exist in the API surface; no endpoint supports it. Requests for it are rejected as `404 Not Found` (no matching route). |

## 6. Non-Functional Requirements

- **Performance:** All queries against `in_app_notifications` must use the existing `idx_in_app_notifications_user_is_read (user_id, is_read)` index (BR-06). The list query must use keyset pagination (no `COUNT(*)` on the full result set). Under the expected load of 12,000+ concurrent users, the unread-count endpoint must be servable from the replica with only an indexed `COUNT` query, completing in single-digit milliseconds.
- **Concurrency:** Concurrent mark-all-read calls from the same user (e.g., two browser tabs) must be safe. Because `UPDATE … WHERE user_id = ? AND is_read = false` is idempotent on the result set, concurrent execution at the database level does not corrupt data. Exactly one of the concurrent requests will observe `updatedCount > 0` and publish an SSE event; the other will observe `updatedCount = 0` and suppress the event. This is an acceptable outcome — no locking beyond row-level database atomicity is required.
- **Idempotency:** Mark-single-read is idempotent by definition (BR-02 — monotonic transition). Mark-all-read is idempotent by design (BR-14). No client-supplied idempotency key is required for either operation.
- **Rate-limiting:** Applied at the Redis Bucket4j token-bucket layer (existing infrastructure), per authenticated user, consistent with the project-wide rate-limiting strategy. No endpoint-specific custom limits are introduced by this feature.
- **Security:** All endpoints require a valid JWT (BR-11). Ownership isolation is enforced at the query level: every query is scoped to `user_id` extracted from the JWT, never from a client-supplied parameter (BR-01). The `idempotencyKey` internal field is stripped from the `payload` before returning it to the client (BR-04).
- **Reliability / Failure modes:** If the SSE publish step fails (e.g., Redis Pub/Sub unavailable), the HTTP response is still returned with `200 OK` — SSE delivery is best-effort and non-blocking. A log warning is emitted if the publish fails. The notification state has already been committed to the database at that point.
- **Observability:** All four endpoints must emit standard Micrometer metrics (request count, latency histogram) tagged by endpoint and HTTP status code, consistent with existing controllers in the project.

## 7. Acceptance Criteria

### Scenario 1: List all notifications — default (no filter)

```gherkin
Given an authenticated STUDENT user with 5 notifications (3 unread, 2 read)
When the user calls GET /api/v1/notifications (no filter, no cursor)
Then the response is 200 OK
And the response body contains items sorted by createdAt descending
And the response body contains exactly 5 items (all notifications)
And the response body contains unreadCount = 3
And the cursor field reflects the next page state
```

_Traces to: FR-01, FR-03, FR-04, FR-05_

### Scenario 2: List notifications filtered by isRead=false

```gherkin
Given an authenticated STAFF user with 4 unread and 2 read notifications
When the user calls GET /api/v1/notifications?isRead=false
Then the response is 200 OK
And the response body contains exactly 4 items, all with isRead=false
And the response body contains unreadCount = 4
```

_Traces to: FR-01, FR-02, FR-05_

### Scenario 3: List notifications filtered by isRead=true

```gherkin
Given an authenticated ORGANIZER user with 1 unread and 3 read notifications
When the user calls GET /api/v1/notifications?isRead=true
Then the response is 200 OK
And the response body contains exactly 3 items, all with isRead=true
And the response body contains unreadCount = 1
```

_Traces to: FR-01, FR-02, FR-05_

### Scenario 4: Get unread count only

```gherkin
Given an authenticated STUDENT user with 7 unread notifications
When the user calls GET /api/v1/notifications/unread-count
Then the response is 200 OK
And the response body contains unreadCount = 7
And no notification list items are returned
```

_Traces to: FR-06_

### Scenario 5: Mark single unread notification as read — success

```gherkin
Given an authenticated STUDENT user
And the user has an unread notification with id = "notif-123"
When the user calls PATCH /api/v1/notifications/notif-123/read
Then the response is 200 OK
And the response body contains the updated notification with isRead=true and readAt set
And the response body contains the new unreadCount (original count minus 1)
And a notification.read SSE event is published to the user's active sessions
And the SSE event payload contains id="notif-123", all=false, unreadCount=(new count), readAt=(timestamp)
```

_Traces to: FR-07, FR-08, FR-15_

### Scenario 6: Mark single notification as read — already read (idempotent)

```gherkin
Given an authenticated STUDENT user
And the user has an already-read notification with id = "notif-456"
When the user calls PATCH /api/v1/notifications/notif-456/read
Then the response is 200 OK
And the response body contains the notification with isRead=true and its original readAt value
And the response body contains the current unreadCount (unchanged)
And no notification.read SSE event is published
```

_Traces to: FR-09, FR-17_

### Scenario 7: Mark single notification as read — belongs to another user

```gherkin
Given an authenticated STUDENT user A
And a notification with id = "notif-789" that belongs to user B
When user A calls PATCH /api/v1/notifications/notif-789/read
Then the response is 403 Forbidden
And no database write occurs
And no SSE event is published
```

_Traces to: FR-10, BR-01_

### Scenario 8: Mark single notification as read — does not exist

```gherkin
Given an authenticated STUDENT user
And no notification with id = "notif-999" exists in the system
When the user calls PATCH /api/v1/notifications/notif-999/read
Then the response is 404 Not Found
```

_Traces to: FR-11_

### Scenario 9: Mark all notifications as read — success

```gherkin
Given an authenticated STUDENT user with 5 unread notifications
When the user calls PATCH /api/v1/notifications/read-all
Then the response is 200 OK
And the response body contains updatedCount = 5
And the response body contains unreadCount = 0
And a notification.read SSE event is published to the user's active sessions
And the SSE event payload contains all=true, unreadCount=0, readAt=(timestamp)
```

_Traces to: FR-12, FR-13, FR-16_

### Scenario 10: Mark all notifications as read — no unread exist (idempotent)

```gherkin
Given an authenticated STUDENT user with 0 unread notifications
When the user calls PATCH /api/v1/notifications/read-all
Then the response is 200 OK
And the response body contains updatedCount = 0
And the response body contains unreadCount = 0
And no notification.read SSE event is published
```

_Traces to: FR-14, FR-18_

### Scenario 11: Unauthenticated request is rejected

```gherkin
Given a request with no Authorization header (or an expired/revoked JWT)
When the request is made to any notification endpoint
Then the response is 401 Unauthorized
```

_Traces to: BR-11_

### Scenario 12: Pagination cursor is malformed

```gherkin
Given an authenticated STUDENT user
When the user calls GET /api/v1/notifications?cursor=not-a-valid-base64-cursor
Then the response is 400 Bad Request
And no database query is executed
```

_Traces to: BR-09_

### Scenario 13: Page size exceeds maximum

```gherkin
Given an authenticated STUDENT user
When the user calls GET /api/v1/notifications?size=200
Then the response is 400 Bad Request
```

_Traces to: BR-10_

### Scenario 14: payload field idempotencyKey is excluded from response

```gherkin
Given an authenticated STUDENT user
And a notification whose payload JSONB contains an "idempotencyKey" field
When the user calls GET /api/v1/notifications
Then the response notification items do not include the "idempotencyKey" field in their payload
```

_Traces to: BR-04_

## 8. Out of Scope

- **Notification creation** — Records in `in_app_notifications` are created solely by the Notification Delivery Pipeline (Feature 7.1 / Worker). This feature never writes new notification rows.
- **Push notifications and SMS** — Only in-app (web/mobile app inbox) notifications are addressed here. Out-of-band delivery channels are out of scope.
- **Notification preferences or opt-in/opt-out settings** — There is no per-user or per-event-type muting in this feature.
- **Notification deletion** — Users cannot delete notifications. Records persist until a future data-retention policy (not yet designed) handles it.
- **Notification categories or grouping** — All notifications appear in a single flat list; no category tabs or group-by-event-type logic.
- **Admin/organizer broadcasting** — Sending notifications from the UI is not part of this feature.
- **SSE connection establishment** — The SSE connection setup endpoint and subscription lifecycle are owned by Feature 10.5 (Real-Time Infrastructure). This feature only publishes events to an existing publisher interface.
- **`notification.new` SSE events** — Publishing SSE events for newly created notifications is the responsibility of the Notification Delivery Pipeline (Feature 7.1 / Worker), not this feature.
- **Read replica lag compensation** — The list and unread-count endpoints read from the replica and accept that very recently created notifications may not yet be visible. This is an acceptable eventual-consistency trade-off per the project's replication routing rules.

## 9. Linked References

- `docs/feature-catalog.md` — Feature 7.2 (`in-app-notification-management`), status `READY`; Feature 7.1 (`notification-delivery-pipeline`), status `DONE` (dependency)
- `docs/system-design.md` — §"Notification System" flow; §"Offline-First Check-In & Sync" (SSE reconnect / fallback polling); §"Real-Time Infrastructure" SSE event catalog
- `docs/database-schema.md` — `in_app_notifications` table definition (columns, constraints, indexes); Replication Lag & Read-After-Write Consistency routing rules
- `docs/api-standards.md` — Pagination convention (keyset/cursor); `ApiResponse<T>` envelope; RFC 7807 error format; HTTP status code table; timestamp formatting
- `docs/adrs.md` — ADR-XXX (Spring Modulith modular monolith); ADR confirming Redis Pub/Sub for SSE fan-out
- `docs/project-structure.md` — `apps/backend/` Clean Architecture layout; module boundary rules; `shared/` infrastructure components
- `specs/notification-delivery-pipeline/SPEC.md` — BR-02 (is_read monotonic constraint); worker payload structure (idempotencyKey field)
