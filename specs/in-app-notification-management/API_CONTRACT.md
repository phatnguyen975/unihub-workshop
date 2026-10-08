# API Contract: In-App Notification Management

> **Status:** APPROVED  
> **Spec:** `specs/in-app-notification-management/SPEC.md`  
> **Last revised:** 2026-10-09 — align response envelope metadata and non-null field serialization with `docs/api-standards.md`

## 1. Overview

This contract specifies the HTTP REST interface for the **In-App Notification Management** feature (Domain 7: Communications & Notifications, Feature 7.2) owned by the `notifications` module in `apps/backend/`. It defines four authenticated endpoints under `/api/v1/notifications` that enable any authenticated user (`STUDENT`, `STAFF`, or `ORGANIZER`) to query their notification inbox with optional read-state filtering, retrieve a lightweight unread count for UI badges, mark an individual notification as read, and bulk-mark all notifications as read. All endpoints conform strictly to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** URI path versioning with prefix `/api/v1/...` and resource name `/notifications` (plural, lowercase, kebab-case), conforming to `docs/api-standards.md §Versioning Strategy`.
- **Pagination convention:** Keyset (cursor-based) pagination conforming to `docs/api-standards.md §Pagination, Search & Filtering`. Clients pass `?cursor=` (opaque Base64-encoded string representing `{ id, sortField, primaryValue, secondaryValue }`) and optional `?size=` (integer, default `20`, max `100`). The response container uses `PageResponse<T>` containing `items` and `cursor: { nextCursor, hasMore }`.
- **Success response shape:** Unified `ApiResponse<T>` envelope conforming to `docs/api-standards.md §Standard JSON Payloads`:
  ```json
  {
    "success": true,
    "message": "Human-readable summary message",
    "data": { ... },
    "metadata": {
      "processedBy": "unihub-app-1",
      "traceId": "7f6f7c9b2d0b4d8c"
    }
  }
  ```
  All responses strictly omit `null` fields via `@JsonInclude(JsonInclude.Include.NON_NULL)`.
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope (`Content-Type: application/problem+json`) with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, optional `validationErrors`), and `metadata` (`processedBy`, `traceId`). Field `data` is omitted when null per `docs/api-standards.md §Error Response (4xx, 5xx)`.
- **Field nullability & non-null serialization:** Conforming to `@JsonInclude(JsonInclude.Include.NON_NULL)`, any field whose value is `null` is completely omitted from the JSON output. Specifically:
  - `readAt`: omitted from notification items when `isRead = false` (or when `readAt` is null).
  - `actionUrl`: omitted when the notification has no deep-link action URL.
  - Inside `payload`: any optional property whose value is null is omitted; the internal `idempotencyKey` attribute is stripped prior to serialization.
  - `cursor.nextCursor`: omitted when `hasMore = false` and no subsequent cursor exists.
- **Idempotency-key convention:** Not required for endpoints in this contract (see §5 Idempotency Behavior for rationale).
- **Auth header convention:** Mandatory `Authorization: Bearer <RS256 JWT>` on all endpoints. JWT claims (`sub`, `role`, `jti`, `exp`) are validated by the global security filter chain prior to endpoint invocation.
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z` (e.g., `2026-10-08T15:30:00Z`), per `docs/api-standards.md §Date/Time & Time Zone`.

## 3. Endpoints

### GET /api/v1/notifications

**Description:** Returns a paginated, keyset cursor-based list of notifications belonging to the authenticated caller, optionally filtered by read state (`isRead`). Always includes the caller's total unread notification count.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — read-only operation; safe and idempotent.

#### Request

**Headers:**

| Header             | Required | Description                                                                                  |
| :----------------- | :------: | :------------------------------------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT access token containing authenticated user identity (`sub`, `jti`, `role`).       |
| `X-Correlation-ID` |    No    | Client-generated UUID for distributed request tracing. Passed through to `metadata.traceId`. |
| `Accept-Language`  |    No    | Preferred locale for localized error messages (default: `en`).                               |

**Path Parameters:** None.

**Query Parameters:**

| Parameter | Type    | Required | Default | Description                                                                                                             |
| :-------- | :------ | :------: | :-----: | :---------------------------------------------------------------------------------------------------------------------- |
| `cursor`  | string  |    No    |    —    | Opaque Base64 keyset cursor from a prior page's `data.cursor.nextCursor`. Omit on the initial request.                  |
| `size`    | integer |    No    |  `20`   | Number of items to return. Must be between `1` and `100` inclusive.                                                     |
| `isRead`  | boolean |    No    |    —    | Filter by read status: `true` = only read; `false` = only unread; omitted = all notifications regardless of read state. |

**Request Body:** None.

**Validation Rules:**

- `cursor` — optional; if present, must be a valid Base64-encoded keyset cursor JSON matching sort attribute `createdAt`. An undecodable cursor or a cursor issued for another sort field returns `400 Bad Request` (`urn:unihub:error:bad-request`).
- `size` — optional; if present, must be an integer between 1 and 100 inclusive. Values `< 1` or `> 100` return `422 Unprocessable Content` (`urn:unihub:error:validation`).
- `isRead` — optional; if present, must parse to a boolean (`true` or `false`). Unparseable values return `400 Bad Request` (`urn:unihub:error:bad-request`).

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Notifications retrieved successfully",
  "data": {
    "items": [
      {
        "id": "e4b1c2d3-1111-4aaa-8bbb-000000000001",
        "eventKey": "WORKSHOP_RESCHEDULED",
        "payload": {
          "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
          "workshopTitle": "Career Sprint 101",
          "startsAt": "2026-10-15T09:00:00Z",
          "endsAt": "2026-10-15T11:00:00Z",
          "location": "Hall A",
          "actionUrl": "https://unihub.edu.vn/workshops/career-sprint-101",
          "reason": "Speaker schedule adjustment"
        },
        "isRead": false,
        "actionUrl": "https://unihub.edu.vn/workshops/career-sprint-101",
        "createdAt": "2026-10-08T10:00:00Z"
      },
      {
        "id": "e4b1c2d3-2222-4aaa-8bbb-000000000002",
        "eventKey": "USER_STATUS_CHANGED",
        "payload": {
          "reason": "Profile identity verified"
        },
        "isRead": true,
        "readAt": "2026-10-08T09:15:00Z",
        "createdAt": "2026-10-08T08:00:00Z"
      }
    ],
    "unreadCount": 1,
    "cursor": {
      "nextCursor": "eyJpZCI6ImU0YjFjMmQzLTIyMjItNGFhYS04YmJiLTAwMDAwMDAwMDAwMiIsInNvcnRGaWVsZCI6ImNyZWF0ZWRBdCIsInByaW1hcnlWYWx1ZSI6IjIwMjYtMTAtMDhUMDg6MDA6MDBaIn0=",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> [!IMPORTANT]
> **Null Field Omission in Examples:**
>
> - In Item 1 (`isRead = false`), `readAt` is `null` and therefore **omitted** from the JSON payload.
> - In Item 2, there is no action URL, so `actionUrl` is `null` and therefore **omitted** from the JSON payload.
> - When `hasMore = false` and no next page exists, `cursor.nextCursor` is `null` and therefore **omitted**, leaving only `"cursor": { "hasMore": false }`.
> - The `unreadCount` reflects the total unread notifications for the caller at query time, independent of the active `isRead` filter parameter or cursor position.

**Field Descriptions (`data`):**

| Field               | Type              | Nullable | Description                                                                                                  |
| :------------------ | :---------------- | :------: | :----------------------------------------------------------------------------------------------------------- |
| `items`             | Array             |    No    | Ordered array of notification objects (sorted by `createdAt DESC, id DESC`). Empty array `[]` when no items. |
| `items[].id`        | UUID (string)     |    No    | Unique notification record identifier.                                                                       |
| `items[].eventKey`  | String            |    No    | Business event identifier (e.g., `WORKSHOP_RESCHEDULED`, `STAFF_ASSIGNED_TO_WORKSHOP`).                      |
| `items[].payload`   | Object            |    No    | Deserialized event-specific JSON payload. Internal `idempotencyKey` stripped; null attributes omitted.       |
| `items[].isRead`    | Boolean           |    No    | Current read state: `false` if unread, `true` if read.                                                       |
| `items[].readAt`    | String (ISO 8601) |   Yes    | Timestamp when marked as read. **Omitted when `null` (unread).**                                             |
| `items[].actionUrl` | String (URL)      |   Yes    | Optional deep link for user navigation. **Omitted when `null`.**                                             |
| `items[].createdAt` | String (ISO 8601) |    No    | Timestamp when notification record was created.                                                              |
| `unreadCount`       | Integer           |    No    | Authoritative total count of unread notifications for the user (`≥ 0`).                                      |
| `cursor.nextCursor` | String (Base64)   |   Yes    | Keyset cursor token for subsequent page. **Omitted when `null` (`hasMore = false`).**                        |
| `cursor.hasMore`    | Boolean           |    No    | `true` if more items exist past the current window; `false` otherwise.                                       |

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                                   |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------ |
| `400`  | `urn:unihub:error:bad-request`  | Bad Request           | Malformed Base64 cursor, cursor sortField mismatch, or invalid boolean syntax for `isRead`. |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing Bearer token, expired JWT, invalid signature, or revoked `jti` in Redis.            |
| `422`  | `urn:unihub:error:validation`   | Unprocessable Content | Validation failure (`size < 1` or `size > 100`). Contains `validationErrors`.               |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error.                                                                    |

**Error Response Example (400 Bad Request — Malformed Cursor):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:bad-request",
    "title": "Bad Request",
    "status": 400,
    "detail": "Cursor is invalid or could not be decoded.",
    "instance": "/api/v1/notifications"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Size Exceeds Max):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Validation failed for query parameters.",
    "instance": "/api/v1/notifications",
    "validationErrors": ["size: must be less than or equal to 100"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. This is a read-only endpoint served from the read replica.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, BR-01, BR-04, BR-05, BR-06, BR-07, BR-09, BR-10, BR-11
- **Scenarios:** Scenario 1, Scenario 2, Scenario 3, Scenario 11, Scenario 12, Scenario 13, Scenario 14

### GET /api/v1/notifications/unread-count

**Description:** Returns only the authoritative unread notification count for the authenticated user. Dedicated lightweight endpoint optimized for the UI navbar bell icon badge; returns no item records.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — read-only operation.

#### Request

**Headers:**

| Header             | Required | Description                                                                                  |
| :----------------- | :------: | :------------------------------------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT access token containing authenticated user identity (`sub`, `jti`, `role`).       |
| `X-Correlation-ID` |    No    | Client-generated UUID for distributed request tracing. Passed through to `metadata.traceId`. |
| `Accept-Language`  |    No    | Preferred locale for localized error messages (default: `en`).                               |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None.

**Validation Rules:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Unread count retrieved successfully",
  "data": {
    "unreadCount": 7
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Descriptions (`data`):**

| Field         | Type    | Nullable | Description                                                             |
| :------------ | :------ | :------: | :---------------------------------------------------------------------- |
| `unreadCount` | Integer |    No    | Total count of notifications for caller with `is_read = false` (`≥ 0`). |

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                        |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------- |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing Bearer token, expired JWT, invalid signature, or revoked `jti` in Redis. |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error.                                                         |

#### Side Effects

None. This is a read-only endpoint served from the read replica via index `idx_in_app_notifications_user_is_read`.

#### Spec Traceability

- **Satisfies:** FR-06, BR-01, BR-05, BR-06, BR-11
- **Scenarios:** Scenario 4, Scenario 11

### PATCH /api/v1/notifications/{id}/read

**Description:** Transitions a single notification belonging to the authenticated caller from `is_read = false` to `is_read = true` and records server `read_at`. If the notification is already read, returns the current state idempotently without database modification. Publishes a real-time SSE event `notification.read` only when an actual database state transition occurs.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`) — must be the owner of the notification  
**Idempotency Requirement:** Not Required — operation is naturally idempotent due to monotonic lifecycle transition (`is_read` can only transition `false` → `true`).

#### Request

**Headers:**

| Header             | Required | Description                                                                                  |
| :----------------- | :------: | :------------------------------------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT access token containing authenticated user identity (`sub`, `jti`, `role`).       |
| `X-Correlation-ID` |    No    | Client-generated UUID for distributed request tracing. Passed through to `metadata.traceId`. |

**Path Parameters:**

| Parameter | Type | Required | Description                                     |
| :-------- | :--- | :------: | :---------------------------------------------- |
| `id`      | UUID |   Yes    | Unique record identifier of target notification |

**Query Parameters:** None.

**Request Body:** None.

**Validation Rules:**

- `id` — must be a well-formed UUID string. Malformed format returns `400 Bad Request` (`urn:unihub:error:bad-request`).

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Notification marked as read",
  "data": {
    "notification": {
      "id": "e4b1c2d3-1111-4aaa-8bbb-000000000001",
      "eventKey": "WORKSHOP_RESCHEDULED",
      "payload": {
        "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
        "workshopTitle": "Career Sprint 101",
        "startsAt": "2026-10-15T09:00:00Z",
        "endsAt": "2026-10-15T11:00:00Z",
        "location": "Hall A",
        "actionUrl": "https://unihub.edu.vn/workshops/career-sprint-101",
        "reason": "Speaker schedule adjustment"
      },
      "isRead": true,
      "readAt": "2026-10-08T15:30:00Z",
      "actionUrl": "https://unihub.edu.vn/workshops/career-sprint-101",
      "createdAt": "2026-10-08T10:00:00Z"
    },
    "unreadCount": 6
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> [!NOTE]
> **Idempotent Already-Read Case:** When the notification was already marked as read prior to the request, the identical `200 OK` JSON shape is returned with existing `readAt` timestamp and current `unreadCount`. No database write occurs, and no SSE event is emitted.
>
> **Null Field Omission:** If `actionUrl` is `null`, it is omitted. Any `null` property in `payload` is omitted. The internal `idempotencyKey` field is always excluded.

**Field Descriptions (`data`):**

| Field                    | Type              | Nullable | Description                                                                                    |
| :----------------------- | :---------------- | :------: | :--------------------------------------------------------------------------------------------- |
| `notification.id`        | UUID (string)     |    No    | Identifier of the notification marked as read.                                                 |
| `notification.eventKey`  | String            |    No    | Event key (e.g., `WORKSHOP_RESCHEDULED`).                                                      |
| `notification.payload`   | Object            |    No    | Deserialized event payload (internal `idempotencyKey` excluded; null values omitted).          |
| `notification.isRead`    | Boolean           |    No    | Always `true` upon successful mark-read.                                                       |
| `notification.readAt`    | String (ISO 8601) |    No    | Authoritative server timestamp of the read transition (or original timestamp if already read). |
| `notification.actionUrl` | String (URL)      |   Yes    | Direct navigation link if applicable. **Omitted when `null`.**                                 |
| `notification.createdAt` | String (ISO 8601) |    No    | Creation timestamp of the notification record.                                                 |
| `unreadCount`            | Integer           |    No    | Authoritative new unread count for caller after the update (`≥ 0`).                            |

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                             |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------ |
| `400`  | `urn:unihub:error:bad-request`  | Bad Request           | Path parameter `id` is not a valid UUID format.                                       |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing Bearer token, expired JWT, invalid signature, or revoked `jti` in Redis.      |
| `403`  | `urn:unihub:error:forbidden`    | Forbidden             | Target notification exists in database but belongs to another user (BR-01 isolation). |
| `404`  | `urn:unihub:error:not-found`    | Not Found             | No notification record with the given `id` exists in the system.                      |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error.                                                              |

**Error Response Example (403 Forbidden — Cross-User Access Guard):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "You do not have permission to access this notification.",
    "instance": "/api/v1/notifications/e4b1c2d3-1111-4aaa-8bbb-000000000001"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (404 Not Found — Record Does Not Exist):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Notification not found.",
    "instance": "/api/v1/notifications/99999999-9999-9999-9999-999999999999"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- **On state transition (`is_read` updated `false` → `true`):**
  Publishes an event of type `RealtimeEventType.NOTIFICATION_READ` (`notification.read`) to the Redis Pub/Sub channel for the authenticated user (`unihub:stream:user:{userId}`). Fans out to active client SSE sessions to sync unread state across open tabs and mobile devices.
  ```json
  {
    "type": "notification.read",
    "payload": {
      "id": "e4b1c2d3-1111-4aaa-8bbb-000000000001",
      "all": false,
      "unreadCount": 6,
      "readAt": "2026-10-08T15:30:00Z"
    }
  }
  ```
- **On idempotent execution (already read):**
  Database write is skipped. Real-time SSE publish is **suppressed** (no redundant events emitted).
- **Failure tolerance:**
  SSE event publishing is non-blocking and best-effort; a Redis delivery failure logs a warning but does not fail the HTTP transaction.

#### Spec Traceability

- **Satisfies:** FR-07, FR-08, FR-09, FR-10, FR-11, FR-15, FR-17, BR-01, BR-02, BR-03, BR-04, BR-05, BR-07, BR-08, BR-11
- **Scenarios:** Scenario 5, Scenario 6, Scenario 7, Scenario 8, Scenario 11

### PATCH /api/v1/notifications/read-all

**Description:** Transitions all unread notifications belonging to the authenticated caller to `is_read = true` in an atomic database update. If the caller has zero unread notifications, returns `updatedCount = 0` idempotently with no database modification. Publishes a real-time SSE event `notification.read` only when at least one notification was transitioned.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — bulk update predicate `WHERE user_id = ? AND is_read = false` is naturally idempotent.

#### Request

**Headers:**

| Header             | Required | Description                                                                                  |
| :----------------- | :------: | :------------------------------------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT access token containing authenticated user identity (`sub`, `jti`, `role`).       |
| `X-Correlation-ID` |    No    | Client-generated UUID for distributed request tracing. Passed through to `metadata.traceId`. |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None.

**Validation Rules:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "All notifications marked as read",
  "data": {
    "updatedCount": 5,
    "unreadCount": 0
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> [!NOTE]
> **Idempotent No-Op Case:** When the user has zero unread notifications (`unreadCount = 0`), the identical `200 OK` structure is returned with `updatedCount = 0` and `unreadCount = 0`. No database write occurs, and no SSE event is emitted.

**Field Descriptions (`data`):**

| Field          | Type    | Nullable | Description                                                                |
| :------------- | :------ | :------: | :------------------------------------------------------------------------- |
| `updatedCount` | Integer |    No    | Number of notification records actually updated from `unread` to `read`.   |
| `unreadCount`  | Integer |    No    | Remaining unread count for user immediately following the operation (`0`). |

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                        |
| :----: | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------- |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing Bearer token, expired JWT, invalid signature, or revoked `jti` in Redis. |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server error.                                                         |

#### Side Effects

- **On state transition (`updatedCount > 0`):**
  Publishes an event of type `RealtimeEventType.NOTIFICATION_READ` (`notification.read`) to Redis Pub/Sub channel `unihub:stream:user:{userId}`:
  ```json
  {
    "type": "notification.read",
    "payload": {
      "all": true,
      "unreadCount": 0,
      "readAt": "2026-10-08T15:30:00Z"
    }
  }
  ```
  Note: `id` is omitted when `all = true`, instructing clients to reset all unread indicators.
- **On idempotent execution (`updatedCount == 0`):**
  No database write occurs. Real-time SSE publish is **suppressed**.
- **Failure tolerance:**
  SSE publish is non-blocking and best-effort; Redis transient failure logs a warning without rolling back the database transaction.

#### Spec Traceability

- **Satisfies:** FR-12, FR-13, FR-14, FR-16, FR-18, BR-01, BR-02, BR-03, BR-05, BR-07, BR-08, BR-11
- **Scenarios:** Scenario 9, Scenario 10, Scenario 11

## 4. Rate Limiting Behavior

All four endpoints in this contract are protected by the project-wide Redis Token Bucket rate limiting infrastructure (Bucket4j), scoped per authenticated user (`X-RateLimit-*` response headers returned by the server). No custom rate limit rules or endpoint-specific limits are introduced.

## 5. Idempotency Behavior

Mutations within this feature are inherently idempotent without requiring client-generated `Idempotency-Key` headers:

- `PATCH /api/v1/notifications/{id}/read` performs a monotonic state change (`is_read` can only transition `false` → `true`). Repeated requests for an already-read notification return `200 OK` with existing state and suppress redundant SSE events.
- `PATCH /api/v1/notifications/read-all` updates records matching `WHERE user_id = ? AND is_read = false`. Repeating the request when all notifications are already read yields `updatedCount = 0` and suppresses redundant SSE events.
- Both `GET` endpoints are side-effect-free, safe reads.

## 6. Cross-Endpoint Consistency & Serialization Notes

- **`NotificationResponse` model consistency:** The notification entity representation returned in `GET /api/v1/notifications` (`data.items[]`) and in `PATCH /api/v1/notifications/{id}/read` (`data.notification`) uses identical fields and types: `id`, `eventKey`, `payload`, `isRead`, `readAt`, `actionUrl`, `createdAt`.
- **Strict Non-Null Serialization:** Conforming to `@JsonInclude(JsonInclude.Include.NON_NULL)` and `docs/api-standards.md`, any attribute holding a `null` value is dropped during Jackson serialization. Under no circumstances should `"readAt": null`, `"actionUrl": null`, or `"metadata": null` appear in serialized wire JSON.
- **Envelope metadata consistency:** All `2xx`, `4xx`, and `5xx` responses include the transport metadata container populated automatically by `GlobalResponseBodyAdvice`:
  ```json
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
  ```
- **Unified Event Catalog conformance:** The `notification.read` real-time SSE event emitted on both mutation endpoints strictly matches the Unified Event Catalog schema in `docs/api-standards.md §Unified Event Catalog`.
- **Spring MVC Routing Precedence:** In the controller implementation, the static sub-route `/read-all` (`@PatchMapping("/read-all")`) must take precedence over the parametric route `/{id}/read` (`@PatchMapping("/{id}/read")`) to ensure the literal string `"read-all"` is never evaluated as a UUID path variable.
