# API Contract: Attendance Sync Reconciliation

> **Status:** APPROVED  
> **Spec:** `specs/attendance-sync-reconciliation/SPEC.md`  
> **Last revised:** 2026-10-31

## 1. Overview

This contract defines the single REST endpoint exposed by the `attendances` module in `apps/backend/` for Feature 6.2 (`attendance-sync-reconciliation`). The endpoint accepts the batch of QR scans a Check-in Staff device queued while offline and returns one reconciliation outcome per submitted entry, so the mobile client can deterministically update its local SQLite queue. The full behavioral specification is in `specs/attendance-sync-reconciliation/SPEC.md`; this document specifies the exact wire format, error semantics, and side effects.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/` prefix, resource noun in plural form, action segment `sync` under the `attendances` sub-resource, following `docs/api-standards.md §Versioning`.
- **Pagination convention:** Not applicable — this endpoint accepts a bounded write batch and returns a non-paginated per-entry outcome array. `shared/pagination` (keyset cursors) applies only to collection `GET` endpoints.
- **Success response shape:** Standard `ApiResponse<T>` envelope — `{ "success": true, "message": string, "data": <T>, "metadata": { "processedBy": string, "traceId": string } }`, with `207 Multi-Status` as the success status because each entry carries its own outcome. All null values are omitted via `@JsonInclude(JsonInclude.Include.NON_NULL)`.
- **Error response format:** RFC 7807 Problem Details wrapped in the standard `ApiResponse` error envelope — `{ "success": false, "error": { "type": "urn:unihub:error:*", "title": string, "status": int, "detail": string, "instance": string, "validationErrors"?: string[] }, "metadata": { "processedBy": string, "traceId": string } }`. Response uses `Content-Type: application/problem+json`.
- **Idempotency-key convention:** Required — `Idempotency-Key` header, UUIDv4 string, max 64 characters, mandatory on this business-critical `POST` per `docs/api-standards.md §Mandatory Headers`. It is the request-level (2-tier Redis L1 + `idempotency_keys` L2) deduplication mechanism, complementary to the per-entry `syncId` (see §5).
- **Auth header convention:** `Authorization: Bearer <JWT>` (RS256, asymmetric). JWT validated by the shared security filter chain. JTI revocation checked against Redis.
- **Date/time formatting convention:** ISO-8601 UTC — `yyyy-MM-dd'T'HH:mm:ss'Z'` (e.g. `2026-11-15T09:00:00Z`).
- **Database routing:** Every read and write of this endpoint is pinned to the **primary** PostgreSQL instance (ADR-015). The endpoint never reads from a replica.

## 3. Endpoints

### POST /api/v1/workshops/{workshopId}/attendances/sync

**Description:** Reconciles a batch of offline check-in scans for one workshop and returns one outcome per submitted entry.  
**Required Role / Auth:** `AUTHENTICATED | STAFF` — valid Bearer JWT with `role = STAFF`, **and** an existing `workshop_assignments` record for (`workshopId`, `jwt.sub`). Any other role, or an unassigned staff member, is rejected.  
**Idempotency Requirement:** Required — a retried batch must not re-execute reconciliation or duplicate attendance records.

#### Request

**Headers:**

| Header             | Required | Description                                                                                                               |
| :----------------- | :------- | :------------------------------------------------------------------------------------------------------------------------ |
| `Authorization`    | Yes      | `Bearer <JWT>` — RS256-signed JWT with `role = STAFF`                                                                     |
| `Idempotency-Key`  | Yes      | UUIDv4 string, max 64 characters — request-level deduplication key for this batch                                         |
| `Content-Type`     | Yes      | `application/json`                                                                                                        |
| `X-Correlation-ID` | Yes      | Client-generated correlation identifier, surfaced back as `metadata.traceId` (`docs/api-standards.md §Mandatory Headers`) |
| `Accept-Language`  | Yes      | Preferred language for localized RFC 7807 messages (`docs/api-standards.md §Mandatory Headers`)                           |

**Path Parameters:**

| Parameter    | Type          | Description                                                                      |
| :----------- | :------------ | :------------------------------------------------------------------------------- |
| `workshopId` | string (UUID) | Workshop the batch belongs to. Every read and write is scoped to this identifier |

**Query Parameters:** None. This endpoint accepts no filtering, sorting, or pagination parameters.

**Request Body:**

```json
{
  "deviceId": "android-8f3c2b1a-0042",
  "entries": [
    {
      "syncId": "7f8b0a5c-1aa0-44c6-9b6c-0a19b6c4b1f1",
      "registrationId": "e0000001-0000-0000-0000-000000000001",
      "scannedAt": "2026-11-15T09:03:11Z"
    },
    {
      "syncId": "3c3d7b2f-8d6d-4f52-bfd9-3f2a7b4b1e20",
      "registrationId": "e0000001-0000-0000-0000-000000000002",
      "scannedAt": "2026-11-15T09:04:02Z"
    }
  ]
}
```

**Root Body Fields:**

| Field      | Type   | Required | Constraints        | Description                                                                                  |
| :--------- | :----- | :------- | :----------------- | :------------------------------------------------------------------------------------------- |
| `deviceId` | string | No       | Max 100 characters | Optional identifier of the device that produced the batch; persisted on every created record |
| `entries`  | array  | Yes      | 1 ≤ size ≤ 500     | One element per queued scan, in the order the client expects outcomes back                   |

**Entry Object Fields:**

| Field            | Type                  | Required | Constraints                  | Description                                                                                                |
| :--------------- | :-------------------- | :------- | :--------------------------- | :--------------------------------------------------------------------------------------------------------- |
| `syncId`         | string (UUID)         | Yes      | Unique within the batch      | Device-generated identifier for this single scan. Stable across retries of the same scan                   |
| `registrationId` | string (UUID)         | Yes      | Unique within the batch      | Registration the scanned QR code resolves to                                                               |
| `scannedAt`      | string (ISO-8601 UTC) | Yes      | Valid ISO-8601 UTC timestamp | Device clock time at which the scan occurred; clamped server-side before persistence (see §3 Side Effects) |

**Validation Rules:**

- `Authorization` — If absent, malformed, expired, signature-invalid, or carrying a revoked `jti`, the request is rejected with `401 Unauthorized` (FR-01).
- `jwt.role` — If the authenticated caller's role is not `STAFF`, the request is rejected with `403 Forbidden` (FR-02).
- `Idempotency-Key` — If absent, not a UUIDv4 string, or longer than 64 characters, the request is rejected with `400 Bad Request` (FR-06).
- `workshopId` — If not a valid UUID, the request is rejected with `400 Bad Request` as an invalid raw path parameter.
- Request body syntax — If the body is not parseable JSON, the request is rejected with `400 Bad Request`.
- `entries` — If absent, `null`, or empty, the request is rejected with `422 Unprocessable Content` (FR-07).
- `entries` — If it contains more than 500 elements, the request is rejected with `422 Unprocessable Content` (FR-08).
- `entries[].syncId` — If two or more entries share the same value, the request is rejected with `422 Unprocessable Content` before any database work (FR-09).
- `entries[].registrationId` — If two or more entries share the same value, the request is rejected with `422 Unprocessable Content` before any database work (FR-10).
- `entries[].syncId`, `entries[].registrationId`, `entries[].scannedAt` — If any is missing or fails its format check (UUID / ISO-8601 UTC), the request is rejected with `422 Unprocessable Content` (FR-11).
- `deviceId` — If present and longer than 100 characters, the request is rejected with `422 Unprocessable Content`; when omitted, records are persisted with `device_id = NULL` (FR-12).
- Workshop existence — If no workshop exists for `workshopId`, the request is rejected with `404 Not Found` (FR-03).
- Staff assignment — If the workshop exists but no `workshop_assignments` record exists for (`workshopId`, `jwt.sub`), the request is rejected with `403 Forbidden` (FR-04).
- Workshop status — If the workshop is `DRAFT` or `CANCELED`, the request is rejected with `422 Unprocessable Content` (FR-13). `PUBLISHED` and `COMPLETED` are accepted with no time-window restriction, including batches that arrive after the workshop ended (FR-14).

**Evaluation Order (first failing check wins):**

| #   | Check                                                     | Failure Response       |
| :-- | :-------------------------------------------------------- | :--------------------- |
| 1   | JWT authentication (signature, expiry, JTI revocation)    | `401`                  |
| 2   | Caller role is `STAFF`                                    | `403`                  |
| 3   | Header / path / JSON-syntax validation                    | `400`                  |
| 4   | Body validation (batch bounds, duplicates, field formats) | `422`                  |
| 5   | Request-level idempotency (replay or conflict)            | `409` / replayed `207` |
| 6   | Workshop existence                                        | `404`                  |
| 7   | Staff assignment for (`workshopId`, `jwt.sub`)            | `403`                  |
| 8   | Workshop status is `PUBLISHED` or `COMPLETED`             | `422`                  |
| 9   | Per-entry reconciliation                                  | `207`                  |

Idempotency is evaluated before the business reads so that a replayed batch never touches the workshop, assignment, or attendance tables at all.

#### Response

**Success — 207 Multi-Status:**

```json
{
  "success": true,
  "message": "Attendance sync processed",
  "data": [
    {
      "syncId": "7f8b0a5c-1aa0-44c6-9b6c-0a19b6c4b1f1",
      "status": "SUCCESS"
    },
    {
      "syncId": "3c3d7b2f-8d6d-4f52-bfd9-3f2a7b4b1e20",
      "status": "ALREADY_USED"
    },
    {
      "syncId": "b1f6a1c4-5e2d-4a3b-8c7d-9e0f1a2b3c4d",
      "status": "INVALID_TICKET"
    }
  ],
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Outcome Object Fields (`data[]`):**

| Field    | Type          | Nullable | Description                                                                                           |
| :------- | :------------ | :------- | :---------------------------------------------------------------------------------------------------- |
| `syncId` | string (UUID) | No       | Echo of the `syncId` submitted for that entry, so the client can match the outcome to its local queue |
| `status` | string        | No       | One of `SUCCESS`, `ALREADY_USED`, `INVALID_TICKET` (FR-40)                                            |

**Outcome Semantics:**

| `status`         | Meaning                                                                                                                                   | Client Action                                          |
| :--------------- | :---------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------- |
| `SUCCESS`        | New check-in persisted, or a legitimate retry of a scan already recorded under the same `syncId` (FR-25, FR-26)                           | Mark the local queue entry `SUCCESS` and stop retrying |
| `ALREADY_USED`   | Registration already checked in under a different `syncId`, already `INVALIDATED`, or lost a concurrent race (FR-27, FR-28, FR-29, FR-31) | Mark `ALREADY_USED` and stop retrying                  |
| `INVALID_TICKET` | Registration absent, not `CONFIRMED`, or bound to another workshop (FR-21, FR-22, FR-23)                                                  | Mark `INVALID_TICKET` and stop retrying                |

**Response Contract Guarantees:**

- The status is `207` whenever authentication, request validation, and the workshop preconditions pass, **including when every entry is `INVALID_TICKET` or `ALREADY_USED`** (FR-37).
- `data` contains exactly one outcome per submitted entry, in the same order as the request (FR-38).
- No outcome object carries anything beyond `syncId` and `status` — no messages, counts, or record identifiers (FR-39).
- A per-entry classification failure never aborts the batch and never rolls back entries already classified as `SUCCESS` (FR-41).

**Error Responses:**

| Status                       | Error `type` (URN)                        | Title                  | Condition                                                                                                                                                             |
| :--------------------------- | :---------------------------------------- | :--------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`            | `urn:unihub:error:bad-request`            | Bad Request            | `Idempotency-Key` header is absent.                                                                                                                                   |
| `400 Bad Request`            | `urn:unihub:error:bad-request`            | Bad Request            | `Idempotency-Key` is not a UUIDv4 string, or exceeds 64 characters.                                                                                                   |
| `400 Bad Request`            | `urn:unihub:error:bad-request`            | Bad Request            | Request body is not parseable JSON.                                                                                                                                   |
| `400 Bad Request`            | `urn:unihub:error:bad-request`            | Bad Request            | `{workshopId}` path segment is not a valid UUID.                                                                                                                      |
| `401 Unauthorized`           | `urn:unihub:error:unauthorized`           | Unauthorized           | Missing `Authorization` header, invalid token format, expired JWT, invalid signature, or JTI revoked in Redis.                                                        |
| `403 Forbidden`              | `urn:unihub:error:forbidden`              | Forbidden              | Authenticated caller's role is not `STAFF`.                                                                                                                           |
| `403 Forbidden`              | `urn:unihub:error:forbidden`              | Forbidden              | Caller is `STAFF` but has no `workshop_assignments` record for `{workshopId}`, regardless of organization membership.                                                 |
| `404 Not Found`              | `urn:unihub:error:not-found`              | Not Found              | No workshop exists for `{workshopId}`.                                                                                                                                |
| `409 Conflict`               | `urn:unihub:error:conflict`               | Conflict               | `Idempotency-Key` was already completed for this caller with a different request payload (body or `workshopId`).                                                      |
| `409 Conflict`               | `urn:unihub:error:conflict`               | Conflict               | Another request with the same `Idempotency-Key` for this caller is still in flight. Response carries `Retry-After: 2`.                                                |
| `415 Unsupported Media Type` | `urn:unihub:error:unsupported-media-type` | Unsupported Media Type | Request `Content-Type` is not `application/json` (framework-level rejection per `docs/api-standards.md`).                                                             |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | `entries` is absent, `null`, or empty.                                                                                                                                |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | `entries` contains more than 500 elements.                                                                                                                            |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | Two or more entries share the same `syncId`.                                                                                                                          |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | Two or more entries share the same `registrationId`.                                                                                                                  |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | An entry is missing `syncId`, `registrationId`, or `scannedAt`, or a value fails its UUID / ISO-8601 UTC format check.                                                |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | `deviceId` exceeds 100 characters.                                                                                                                                    |
| `422 Unprocessable Content`  | `urn:unihub:error:validation`             | Unprocessable Content  | Workshop status is `DRAFT` or `CANCELED`.                                                                                                                             |
| `429 Too Many Requests`      | `urn:unihub:error:rate-limited`           | Too Many Requests      | Caller has exceeded the 100 requests / 60 s token-bucket quota keyed on `jwt.sub`. Includes `Retry-After` header.                                                     |
| `500 Internal Server Error`  | `urn:unihub:error:internal`               | Internal Server Error  | Unexpected server-side failure. Technical stack trace is suppressed.                                                                                                  |
| `503 Service Unavailable`    | `urn:unihub:error:service-unavailable`    | Service Unavailable    | Primary PostgreSQL is unreachable or the bulk write failed at the infrastructure level. The endpoint does NOT fall back to a replica (BR-02). Includes `Retry-After`. |

**Error Response Examples:**

_1. Missing Idempotency-Key (400 Bad Request):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:bad-request",
    "title": "Bad Request",
    "status": 400,
    "detail": "The 'Idempotency-Key' header is required and must be a UUIDv4 string of at most 64 characters.",
    "instance": "/api/v1/workshops/f0000001-0000-0000-0000-000000000001/attendances/sync"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_2. Duplicate Identifier Inside the Batch (422 Unprocessable Content):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "The sync batch is invalid.",
    "instance": "/api/v1/workshops/f0000001-0000-0000-0000-000000000001/attendances/sync",
    "validationErrors": [
      "entries[3].registrationId: duplicate value 'e0000001-0000-0000-0000-000000000001' already present at entries[1]"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_3. Unassigned Staff Member (403 Forbidden):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "You are not assigned to this workshop.",
    "instance": "/api/v1/workshops/f0000001-0000-0000-0000-000000000001/attendances/sync"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_4. Idempotency-Key Reused With a Different Payload (409 Conflict):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "This Idempotency-Key was already used with a different request payload.",
    "instance": "/api/v1/workshops/f0000001-0000-0000-0000-000000000001/attendances/sync"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

_5. Canceled Workshop (422 Unprocessable Content):_

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Attendance cannot be synchronized for a workshop in CANCELED status.",
    "instance": "/api/v1/workshops/f0000001-0000-0000-0000-000000000001/attendances/sync"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

On a `207` response, the endpoint:

1. **Creates attendance records** for every entry classified `SUCCESS` through a new insert, in a single bulk write. Each record carries `workshop_id = {workshopId}`, the entry's `registration_id`, `checked_in_by_user_id = jwt.sub`, the entry's `sync_id`, the request's `device_id` (or `NULL`), `check_in_method = 'OFFLINE_SYNC'`, `status = 'VALID'`, and `created_by` / `updated_by = jwt.sub`. `checked_in_at` is the entry's `scannedAt` clamped to `[workshop.starts_at, server now()]`; `created_at` and `updated_at` are the server's receipt time (FR-33, FR-34, FR-35, BR-09).
2. **Writes the request-level idempotency record** — the `idempotency_keys` row transitions `PENDING` → `COMPLETED` with the serialized `207` response, and the Redis L1 key is populated with a 24-hour TTL (FR-19, BR-06).
3. **Publishes exactly one `attendance.updated` event** after the transaction commits, but only when at least one new record was persisted (FR-42, FR-43, FR-44). The event is written to the workshop channel `unihub:stream:workshop:{workshopId}` through `shared/realtime` and is delivered to subscribers over the multiplexed SSE stream `GET /api/v1/stream/events` (FR-46). Payload:

```json
{
  "workshopId": "f0000001-0000-0000-0000-000000000001",
  "checkedInCount": 42,
  "capacity": 100,
  "lastSyncedAt": "2026-11-15T10:00:00Z"
}
```

- `checkedInCount` is the current count of `VALID` attendance records for the workshop, computed once after the write (BR-04).
- `lastSyncedAt` is the server time of the sync that produced the event, not `max(checked_in_at)` of the batch (FR-45).

4. **Emits observability data** — request count, latency, per-classification counters, bulk-write failure count as Micrometer metrics, plus a structured log line with `user_id`, `workshopId`, entry count, per-classification counts, latency, and trace identifier.

The endpoint does **not**: modify the workshop, modify a registration, delete any attendance record, write to `event_publication` or RabbitMQ, update Elasticsearch, touch MinIO, or send notifications.

On any `4xx`/`5xx` response, no attendance record is created or modified. A transient failure releases the idempotency claim so the client may retry with the same key (§5).

#### Spec Traceability

- **Satisfies:** FR-01 – FR-46; BR-01 – BR-15
- **Acceptance Criteria:** Scenarios 1 – 24

## 4. Rate Limiting Behavior

The endpoint is subject to the **default tier** token-bucket policy, enforced by Bucket4j backed by Redis (ADR-010; `docs/system-design.md §Rate Limiting Tiers`):

- **Quota:** 100 tokens per 60-second rolling window.
- **Key:** `jwt.sub` (authenticated user ID) — never the IP address, because multiple staff devices may share a venue NAT.
- **Response on limit exceeded:** `429 Too Many Requests` with `urn:unihub:error:rate-limited`, a `Retry-After` header indicating seconds until the next token, and `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Retry-After` headers.
- **Redis unavailability:** the limiter fails **open** — the request proceeds and rate limiting is skipped. A staff member flushing a legitimate offline queue must not be blocked by an infrastructure failure (BR-15).

## 5. Idempotency Behavior

This endpoint has **two independent idempotency layers**; both are mandatory and neither replaces the other (BR-06).

### 5.1 Request-level `Idempotency-Key`

Follows the platform-wide 2-tier cache-aside protocol (ADR-014; `docs/system-design.md §Transaction Idempotency`).

- **Scope:** the pair (`jwt.sub`, `Idempotency-Key`), backed by the unique constraint `(user_id, idempotency_key)` on `idempotency_keys`. Two different staff members using the same key value never collide (FR-18).
- **Request identity:** `request_hash = SHA-256(method + request_path + body)`. Because `request_path` contains `{workshopId}`, replaying the same key against a different workshop is treated as a different request and returns `409 Conflict`.
- **L1 (Redis):** key `idempotency:<user_id>:<key>`; `PENDING` claim with a 60-second TTL protects against cross-node microsecond races; `COMPLETED` entries carry the serialized response and live for 24 hours.
- **L2 (PostgreSQL primary):** atomic claim `INSERT ... ON CONFLICT (user_id, idempotency_key) DO NOTHING RETURNING *`, so concurrent requests from different backend nodes resolve to exactly one executor.
- **Lifecycle:**

| State observed                           | Behavior                                                                                  |
| :--------------------------------------- | :---------------------------------------------------------------------------------------- |
| Key absent (L1 miss, L2 insert succeeds) | Row created as `PENDING`, Redis claim set, reconciliation executes                        |
| `PENDING` (same key, in flight)          | `409 Conflict` with `Retry-After: 2`, without touching PostgreSQL business tables (FR-17) |
| `COMPLETED` + matching hash              | Stored `207` response replayed verbatim; reconciliation is not re-executed (FR-15)        |
| `COMPLETED` + different hash             | `409 Conflict` (FR-16)                                                                    |
| `FAILED`                                 | Treated as a new attempt: the row is re-claimed for a fresh execution                     |

- **Only successful `207` responses are stored as `COMPLETED`.** Deterministic `4xx` failures are recorded as `FAILED` and never replayed as success; transient `5xx` failures delete the `PENDING` row and the Redis claim so the client can retry immediately (FR-19).
- **TTL:** `expires_at = now() + 24 hours`, purged by the scheduled TTL cleanup job every 6 hours.

### 5.2 Per-entry `syncId`

- `syncId` is generated by the device **per scan**, before the entry enters the offline queue, and is stable across every retry of that scan (including retries under a fresh `Idempotency-Key`).
- The durable guarantee is the unique constraint `(workshop_id, sync_id)` on `attendances`, complemented by `(workshop_id, registration_id)`.
- Re-submitting an entry whose `syncId` already exists for the same workshop and registration returns `SUCCESS` without modifying the stored record (FR-26).
- Re-submitting an entry whose `syncId` is already bound to a **different** registration of the same workshop returns `ALREADY_USED` (FR-31).
- The two layers are complementary: the header makes a whole-batch retry a cached replay; `syncId` keeps an individual scan safe when the client re-batches its queue under a different header value.

## 6. Cross-Endpoint Consistency Notes

- **207 outcome vocabulary:** `SUCCESS`, `ALREADY_USED`, and `INVALID_TICKET` are exactly the values the mobile `mobile_sync_queue.sync_status` column accepts (`docs/database-schema.md`), so the client can persist the outcome without translation. `FAILED` exists only client-side for transport failures and is never returned by this endpoint.
- **`syncId` naming:** the JSON field is `syncId` (camelCase), matching `docs/api-standards.md` and every other endpoint's field-naming convention; the persisted column is `attendances.sync_id`. `registrationId` / `deviceId` follow the same rule.
- **`attendance.updated` payload:** the `{ workshopId, checkedInCount, capacity, lastSyncedAt }` shape is the one declared in the `docs/api-standards.md §Unified Event Catalog` and required by F-6.3 (`live-attendance-monitoring`), which owns the summary read model and only consumes this event. No additional field is introduced here.
- **Envelope and error shape:** identical to the `mobile-offline-checkin` contract (F-6.1) and to every other endpoint, produced by `ApiResponse`, `GlobalResponseBodyAdvice`, and `GlobalExceptionHandler`. `207` is preserved as-is by the response advice, not downgraded to `200`.
- **Status-code split for this endpoint:** `400` is reserved for protocol-level problems (missing/invalid `Idempotency-Key`, malformed JSON, malformed path parameter), while `422` covers body validation and state-machine violations (`DRAFT`/`CANCELED` workshop), exactly as `docs/api-standards.md §HTTP Status Codes` prescribes.
- **`404` vs `403` split:** a non-existent workshop yields `404` because the identifier is not a resource; an existing workshop the caller is not assigned to yields `403` because the resource exists but access is denied. Workshop identifiers are already public through the catalog and F-6.1's assigned-workshops list, so this reveals nothing new.
- **Primary-only reads:** like every check-then-act mutation in this system, this endpoint never reads the replica — consistent with the routing rules in `docs/database-schema.md §Replication Lag & Read-After-Write Consistency` and with F-6.1's deliberately different replica-backed read path.
