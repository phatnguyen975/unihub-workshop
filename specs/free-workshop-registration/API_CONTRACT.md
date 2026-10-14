# API Contract: Free Workshop Registration

> **Status:** APPROVED  
> **Spec:** `specs/free-workshop-registration/SPEC.md`  
> **Approved date:** 2026-10-13

## 1. Overview

This contract defines the three REST endpoints exposed by the `registrations` module for feature 4.1 (free-workshop-registration): the registration mutation, the student's own paginated registration list, and the single registration ticket detail. All endpoints are under `/api/v1/registrations` and are exclusively accessible to the `STUDENT` role. The notification worker delivery (email, in-app, SSE) is an async side effect triggered by the mutation and is documented in the Side Effects section of that endpoint; it has no dedicated REST surface of its own.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/registrations/**` — URI path versioning per `docs/api-standards.md §Versioning Strategy`.
- **Pagination convention:** Keyset cursor pagination with `?cursor=` (opaque Base64-encoded JSON) and `?size=` query parameters. Default size: 20, maximum: 50. Response shape: `{ items: [...], cursor: { nextCursor: string|null, hasMore: boolean } }` nested under `data`.
- **Success response shape:** Standard JSON envelope: `{ success: true, message: string, data: object, metadata: { processedBy: string, traceId: string } }`. Null fields omitted via `@JsonInclude(NON_NULL)`.
- **Error response format:** RFC 7807 Problem Details wrapped in the standard envelope: `{ success: false, error: { type: "urn:unihub:error:*", title, status, detail, instance, [validationErrors] }, metadata: { processedBy, traceId } }`. Content-Type: `application/problem+json`.
- **Idempotency-key convention:** Required on the `POST /api/v1/registrations` mutation. UUIDv4 string, max 64 characters, sent in the `Idempotency-Key` request header. Not required on GET endpoints.
- **Auth header convention:** `Authorization: Bearer <jwt>` (RS256). All three endpoints require an authenticated JWT.
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z` (e.g., `2026-10-13T15:00:00Z`).
- **Correlation:** `X-Correlation-ID` header accepted on all requests for cross-boundary tracing.
- **Rate-limit headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` returned on Bucket4j-protected endpoints.

## 3. Endpoints

### POST /api/v1/registrations

**Description:** Register the authenticated student for a free (zero-price) workshop. Produces a `CONFIRMED` registration with a signed QR ticket and publishes a `WorkshopRegisteredEvent` for async notification delivery.  
**Required Role / Auth:** `STUDENT` — valid RS256 JWT with role claim `STUDENT`.  
**Idempotency Requirement:** **Required** — any POST to this endpoint with retry risk. The `Idempotency-Key` header (UUIDv4, ≤64 chars) is mandatory. The key lifecycle (L1 Redis + L2 DB `idempotency_keys`) provides exactly-once semantics across retries and server restarts. See §5 for the full lifecycle.

#### Request

**Headers:**

| Header             | Required    | Description                                                                                     |
| :----------------- | :---------- | :---------------------------------------------------------------------------------------------- |
| `Authorization`    | Yes         | `Bearer <jwt>` — RS256 JWT with role `STUDENT`                                                  |
| `Idempotency-Key`  | Yes         | UUIDv4 string, max 64 characters. Identifies the logical registration intent for deduplication. |
| `X-Correlation-ID` | Recommended | Client-generated UUID for cross-boundary request tracing.                                       |
| `Content-Type`     | Yes         | `application/json`                                                                              |
| `Accept-Language`  | No          | BCP 47 language tag for localized error messages (e.g., `en`, `vi`).                            |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10"
}
```

**Validation Rules:**

- `workshopId` — **Required**. Must be a valid UUIDv4 string. Rejected with `422` if absent or malformed.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "Registration confirmed",
  "data": {
    "id": "a1b2c3d4-0000-4000-a000-000000000001",
    "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "studentId": "f7a8b9c0-0000-4000-a000-000000000002",
    "status": "CONFIRMED",
    "createdAt": "2026-10-13T08:00:00Z",
    "qrPayload": "{\"registrationId\":\"a1b2c3d4-0000-4000-a000-000000000001\",\"workshopId\":\"8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10\",\"studentId\":\"f7a8b9c0-0000-4000-a000-000000000002\",\"issuedAt\":\"2026-10-13T08:00:00Z\"}",
    "qrSignature": "Base64-encoded-RSA-SHA256-signature",
    "qrIssuedAt": "2026-10-13T08:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Field descriptions:**
>
> - `id` — UUID of the newly created registration record.
> - `workshopId` — UUID of the workshop the student registered for.
> - `studentId` — UUID of the authenticated student's user account (`jwt.sub`).
> - `status` — Always `CONFIRMED` for a successful free registration response.
> - `createdAt` — ISO 8601 UTC timestamp of registration creation.
> - `qrPayload` — Canonical JSON string: `{"registrationId": "...", "workshopId": "...", "studentId": "...", "issuedAt": "..."}`. Field order is deterministic.
> - `qrSignature` — Base64-encoded `SHA256withRSA` signature over the raw UTF-8 bytes of `qrPayload`.
> - `qrIssuedAt` — ISO 8601 UTC timestamp of when the QR was signed (equal to the DB-stored `qr_issued_at`).

**Error Responses:**

| Status                      | Error Type                             | Condition                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| :-------------------------- | :------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Request body is malformed JSON or `workshopId` is not a valid UUID format.                                                                                                                                                                                                                                                                                                                                                                                                   |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | JWT is absent, expired, invalid signature, or revoked (JTI blacklist).                                                                                                                                                                                                                                                                                                                                                                                                       |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | Authenticated user's role is not `STUDENT`, or the student's `student_record_id` is `NULL` (unlinked profile).                                                                                                                                                                                                                                                                                                                                                               |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No published workshop exists with the given `workshopId`.                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `409 Conflict`              | `urn:unihub:error:conflict`            | One of three conditions: (a) `Idempotency-Key` is currently in-flight (PENDING — response includes `Retry-After: 2`); (b) student already has an active (`PENDING` or `CONFIRMED`) registration for this workshop; (c) workshop has zero remaining capacity. The `detail` field distinguishes the sub-condition.                                                                                                                                                             |
| `422 Unprocessable Entity`  | `urn:unihub:error:validation`          | One of four conditions: (a) `Idempotency-Key` header is absent or not a valid UUIDv4 / exceeds 64 chars; (b) `workshopId` field is missing from body; (c) workshop is not in `PUBLISHED` status, or has already started, or is in a terminal state; (d) workshop `price > 0` (not a free workshop); (e) student has an active registration for a workshop with a time slot that overlaps the requested workshop. The `validationErrors` array provides per-condition detail. |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Bucket4j `registration` tier exceeded (5 requests / 10s / IP). Includes `X-RateLimit-Retry-After` header.                                                                                                                                                                                                                                                                                                                                                                    |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected server-side failure. No registration record is created in this case.                                                                                                                                                                                                                                                                                                                                                                                              |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Redis or RabbitMQ is unreachable, or the Resilience4j circuit breaker is OPEN. Includes `Retry-After` header.                                                                                                                                                                                                                                                                                                                                                                |

> **Note on `409` disambiguation:** The `detail` field in the RFC 7807 payload specifies which sub-condition was triggered:
>
> - `"detail": "A request with this Idempotency-Key is already in progress. Retry after 2 seconds."` + `Retry-After: 2` header.
> - `"detail": "Student is already registered for this workshop."`
> - `"detail": "No seats remaining for this workshop."`

#### Side Effects

On `201 Created`:

1. **Outbox event published (async):** A `WorkshopRegisteredEvent` is persisted to the Spring Modulith `event_publication` outbox table within the same DB transaction and forwarded to RabbitMQ after commit (routing key `workshop.registered`, exchange `unihub.events`, queue `q.notifications`). This triggers the notification worker to deliver:
   - A confirmation email with an embedded PNG QR code (CID: `ticket-qr`) to the student's registered email address.
   - An in-app notification record linking to `/workshops/{slug}`.
   - An SSE push event to the student's personal stream channel (`unihub:stream:user:{studentId}`) with event type `notification.new`.
2. **Workshop seat counter decremented:** The `workshop:{workshopId}:capacity` Redis key is decremented atomically (DECR). The `registered_count` column in the `workshops` table is incremented within the DB transaction.
3. **Redis Pub/Sub broadcast:** A `registration.updated` message is published to `unihub:stream:workshop:{workshopId}` after commit, delivering a real-time seat count update to any connected Organizer dashboard or student clients watching that workshop's capacity. Payload: `{ workshopId, confirmedCount, pendingCount, capacity, availableSeats, lastUpdatedAt }`.
4. **L1 idempotency cache set:** The idempotency key is cached in Redis with TTL 24 hours and status `COMPLETED`.

On idempotent replay (`201 Created`, key already `COMPLETED`): no side effects are re-executed — the cached response is returned as-is.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 17, 18, 19

### GET /api/v1/registrations/me

**Description:** Returns the authenticated student's own registrations as a paginated list, ordered by `created_at DESC`. Optionally filtered by status. Read replica routed.  
**Required Role / Auth:** `STUDENT` — valid RS256 JWT with role claim `STUDENT`.  
**Idempotency Requirement:** Not Required — read-only, safe to repeat.

#### Request

**Headers:**

| Header             | Required    | Description                                               |
| :----------------- | :---------- | :-------------------------------------------------------- |
| `Authorization`    | Yes         | `Bearer <jwt>` — RS256 JWT with role `STUDENT`            |
| `X-Correlation-ID` | Recommended | Client-generated UUID for cross-boundary request tracing. |
| `Accept-Language`  | No          | BCP 47 language tag for localized error messages.         |

**Path Parameters:** None.

**Query Parameters:**

| Parameter | Type            | Required | Description                                                                                                                                                                                       |
| :-------- | :-------------- | :------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `cursor`  | string (opaque) | No       | Keyset cursor from a previous response's `data.cursor.nextCursor`. Omit for the first page.                                                                                                       |
| `size`    | integer         | No       | Number of items per page. Default: `20`. Maximum: `50`. Values outside this range are clamped to the maximum or reset to the default.                                                             |
| `status`  | string (enum)   | No       | Filter by registration status. Allowed values: `PENDING`, `CONFIRMED`, `CANCELED`, `FAILED`. Multiple values are **not** supported; pass a single value. When omitted, all statuses are returned. |

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Registrations retrieved",
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-0000-4000-a000-000000000001",
        "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
        "workshopTitle": "Career Sprint 101",
        "workshopStartsAt": "2026-11-01T09:00:00Z",
        "workshopEndsAt": "2026-11-01T11:00:00Z",
        "status": "CONFIRMED",
        "createdAt": "2026-10-13T08:00:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6ImExYjJjM2Q0LTAwMDAtNDAwMC1hMDAwLTAwMDAwMDAwMDAwMSIsInNvcnRGaWVsZCI6ImNyZWF0ZWRBdCIsInByaW1hcnlWYWx1ZSI6IjIwMjYtMTAtMTNUMDg6MDA6MDBaIiwic2Vjb25kYXJ5VmFsdWUiOm51bGx9",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-2",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Field descriptions (`items[]`):**
>
> - `id` — UUID of the registration record.
> - `workshopId` — UUID of the workshop.
> - `workshopTitle` — Display title of the workshop (denormalized for list display, sourced from `workshops` table at query time).
> - `workshopStartsAt` / `workshopEndsAt` — ISO 8601 UTC timestamps of the workshop schedule (denormalized for list display).
> - `status` — Registration status: `PENDING` | `CONFIRMED` | `CANCELED` | `FAILED`.
> - `createdAt` — ISO 8601 UTC timestamp of when the registration was created.
>
> **Note:** QR fields (`qrPayload`, `qrSignature`, `qrIssuedAt`) are **not** returned in the list endpoint — they are available only via `GET /api/v1/registrations/{id}`.
>
> **Cursor format:** The `cursor.nextCursor` is an opaque Base64-encoded keyset cursor encoding `{ id, sortField: "createdAt", primaryValue: <created_at ISO>, secondaryValue: null }`. It is `null` and `hasMore` is `false` when no more pages exist. An empty result set returns `items: []`, `hasMore: false`, `nextCursor: null`.

**Error Responses:**

| Status                      | Error Type                      | Condition                                                                                                                              |
| :-------------------------- | :------------------------------ | :------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | `cursor` value is not a valid Base64-encoded keyset cursor, or the cursor's `sortField` does not match the current sort (`createdAt`). |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | JWT is absent, expired, invalid signature, or revoked.                                                                                 |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Authenticated user's role is not `STUDENT`.                                                                                            |
| `422 Unprocessable Entity`  | `urn:unihub:error:validation`   | `status` query parameter value is not one of the allowed enum values.                                                                  |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Unexpected server-side failure.                                                                                                        |

#### Side Effects

None. This is a read-only endpoint.

#### Spec Traceability

- **Satisfies:** FR-16, FR-17, FR-18, FR-19, FR-20
- **Scenarios:** 11, 12

### GET /api/v1/registrations/{id}

**Description:** Returns the full detail of a single registration ticket belonging to the authenticated student, including QR ticket fields when the registration is `CONFIRMED`. Read replica routed with primary fallback for read-after-write.  
**Required Role / Auth:** `STUDENT` — valid RS256 JWT with role claim `STUDENT`. The authenticated user must own the registration (i.e., their `jwt.sub` must match the registration's `student_user_id`).  
**Idempotency Requirement:** Not Required — read-only, safe to repeat.

#### Request

**Headers:**

| Header             | Required    | Description                                               |
| :----------------- | :---------- | :-------------------------------------------------------- |
| `Authorization`    | Yes         | `Bearer <jwt>` — RS256 JWT with role `STUDENT`            |
| `X-Correlation-ID` | Recommended | Client-generated UUID for cross-boundary request tracing. |
| `Accept-Language`  | No          | BCP 47 language tag for localized error messages.         |

**Path Parameters:**

| Parameter | Type          | Description                                                          |
| :-------- | :------------ | :------------------------------------------------------------------- |
| `id`      | UUID (string) | The registration's unique identifier. Must be a valid UUIDv4 string. |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 200 OK (CONFIRMED registration):**

```json
{
  "success": true,
  "message": "Registration retrieved",
  "data": {
    "id": "a1b2c3d4-0000-4000-a000-000000000001",
    "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "workshopTitle": "Career Sprint 101",
    "workshopStartsAt": "2026-11-01T09:00:00Z",
    "workshopEndsAt": "2026-11-01T11:00:00Z",
    "workshopLocation": "Room B4-101",
    "studentId": "f7a8b9c0-0000-4000-a000-000000000002",
    "status": "CONFIRMED",
    "createdAt": "2026-10-13T08:00:00Z",
    "qrPayload": "{\"registrationId\":\"a1b2c3d4-0000-4000-a000-000000000001\",\"workshopId\":\"8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10\",\"studentId\":\"f7a8b9c0-0000-4000-a000-000000000002\",\"issuedAt\":\"2026-10-13T08:00:00Z\"}",
    "qrSignature": "Base64-encoded-RSA-SHA256-signature",
    "qrIssuedAt": "2026-10-13T08:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Success — 200 OK (non-CONFIRMED registration — e.g., CANCELED or FAILED):**

```json
{
  "success": true,
  "message": "Registration retrieved",
  "data": {
    "id": "a1b2c3d4-0000-4000-a000-000000000001",
    "workshopId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "workshopTitle": "Career Sprint 101",
    "workshopStartsAt": "2026-11-01T09:00:00Z",
    "workshopEndsAt": "2026-11-01T11:00:00Z",
    "workshopLocation": "Room B4-101",
    "studentId": "f7a8b9c0-0000-4000-a000-000000000002",
    "status": "CANCELED",
    "createdAt": "2026-10-13T08:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Field descriptions:**
>
> - `id` — UUID of the registration.
> - `workshopId` — UUID of the workshop.
> - `workshopTitle` — Display title of the workshop.
> - `workshopStartsAt` / `workshopEndsAt` — ISO 8601 UTC timestamps of the workshop schedule.
> - `workshopLocation` — Venue or room name of the workshop (present in detail but not in the list endpoint).
> - `studentId` — UUID of the registered student.
> - `status` — `PENDING` | `CONFIRMED` | `CANCELED` | `FAILED`.
> - `createdAt` — ISO 8601 UTC timestamp of registration creation.
> - `qrPayload` — **Present only when `status == CONFIRMED`.** Canonical JSON string of the QR payload.
> - `qrSignature` — **Present only when `status == CONFIRMED`.** Base64-encoded RSA-SHA256 signature.
> - `qrIssuedAt` — **Present only when `status == CONFIRMED`.** ISO 8601 UTC timestamp of QR signing.

**Error Responses:**

| Status                      | Error Type                      | Condition                                                                                                                                 |
| :-------------------------- | :------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | `id` path parameter is not a valid UUID format.                                                                                           |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | JWT is absent, expired, invalid signature, or revoked.                                                                                    |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Authenticated user's role is not `STUDENT`, or the registration's `student_user_id` does not match the JWT subject (ownership violation). |
| `404 Not Found`             | `urn:unihub:error:not-found`    | No registration exists with the given `id`.                                                                                               |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Unexpected server-side failure.                                                                                                           |

#### Side Effects

None. This is a read-only endpoint.

#### Spec Traceability

- **Satisfies:** FR-21, FR-22, FR-23, FR-24
- **Scenarios:** 13, 14, 15

## 4. Rate Limiting Behavior

Only `POST /api/v1/registrations` is subject to the `registration` Bucket4j Token Bucket tier:

- **Tier:** `registration`
- **Limit:** 5 tokens per 10-second window, per client IP address.
- **Algorithm:** Token Bucket (Bucket4j) — enforced at the application layer (not at Nginx).
- **Response on exhaustion:** `429 Too Many Requests` with:
  - Body: RFC 7807 error `urn:unihub:error:rate-limited`.
  - Headers: `X-RateLimit-Limit: 5`, `X-RateLimit-Remaining: 0`, `X-RateLimit-Retry-After: <seconds>`.
- **Enforcement order:** Rate limiting is the outermost guard — applied before JWT validation, idempotency lookup, or any business logic.

`GET /api/v1/registrations/me` and `GET /api/v1/registrations/{id}` are not subject to the `registration` Bucket4j tier. They may fall under a general read-path tier if one is configured globally, but that is not defined by this feature.

## 5. Idempotency Behavior

Applies to: `POST /api/v1/registrations` only.

**Key:** The `Idempotency-Key` header value (UUIDv4, ≤64 characters) serves as the deduplication key, scoped to the authenticated student's user ID (`jwt.sub`) to prevent cross-user key collisions.

**Lifecycle:**

```
Client sends POST with Idempotency-Key "K"
    │
    ▼
L1: Redis lookup for "K" scoped to studentId
    │
    ├─ Key not found → acquire Redis lock (status: PENDING, TTL: 30s)
    │       │
    │       ▼
    │   L2: DB lookup in idempotency_keys table
    │       │
    │       ├─ Row exists + status COMPLETED → return cached response (201 replay)
    │       │
    │       └─ Row not found → proceed with business logic
    │               │
    │               ├─ Business logic fails (4xx) → release Redis lock; DO NOT write idempotency_keys row
    │               │
    │               └─ DB transaction commits (201) →
    │                       ├─ idempotency_keys row inserted with status COMPLETED + response snapshot
    │                       └─ Redis key set to COMPLETED with TTL 24h
    │
    ├─ Key found, status PENDING → return 409 Conflict + Retry-After: 2
    │
    └─ Key found, status COMPLETED → return 201 Created (cached response body replay)
```

**Scope:** The key is scoped to `(studentId, idempotencyKey)` composite to prevent student A from accidentally replaying student B's key if both happen to use the same UUIDv4 (extremely unlikely, but explicitly enforced).

**Durability:** After Redis eviction (post-TTL-24h), the L2 `idempotency_keys` DB table is the authoritative fallback. Keys in the DB are retained for auditing and are not deleted by this feature.

**Non-idempotent failure cases:** Business errors (4xx responses except the idempotency-specific `409`) do NOT create an `idempotency_keys` record. The client may retry with the same `Idempotency-Key` after correcting the request (e.g., correcting `workshopId`). Only `201 Created` outcomes are stored as COMPLETED.

## 6. Cross-Endpoint Consistency Notes

- **`studentId` field name:** Used consistently across all three endpoints to refer to the authenticated student's user UUID. Matches the JWT `sub` claim. Named `studentId` (not `userId` or `sub`) to be semantically clear in the registration context.
- **`workshopId` field name:** Used consistently across all three endpoints.
- **`status` enum values:** `PENDING`, `CONFIRMED`, `CANCELED`, `FAILED` — consistent across the POST response, GET list, and GET detail endpoints, and consistent with the `registrations.status` column in the DB schema.
- **`createdAt` field name:** Present in all three responses (POST 201, GET list items, GET detail). Represents the registration creation timestamp in all cases.
- **QR fields (`qrPayload`, `qrSignature`, `qrIssuedAt`):** Present in the POST `201 Created` response (always, as a new CONFIRMED registration always has QR fields) and in `GET /{id}` when `status == CONFIRMED`. Deliberately absent from the `GET /me` list response to minimize payload size for list rendering — clients that need the QR must call `GET /{id}`.
- **`workshopTitle`, `workshopStartsAt`, `workshopEndsAt`:** Denormalized from the `workshops` table into both read responses (list and detail) for client display convenience. These are the same fields in both endpoints. `workshopLocation` is additionally present in the detail endpoint only.
- **Error shape:** All error responses use the same RFC 7807-wrapped envelope throughout this contract. No endpoint has a one-off error shape.
- **Cursor `sortField` value:** Fixed as `"createdAt"` for `GET /me`. If a client submits a cursor issued for a different `sortField`, the endpoint returns `400 Bad Request` per the universal keyset cursor contract in `docs/api-standards.md`.
- **Extensibility & Forward Compatibility for Paid Registration (Feature 4.2):** `POST /api/v1/registrations` is designed so that when Feature 4.2 introduces paid workshop registration:
  - Free registrations (`price == 0`) retain this exact contract and behavior (`201 Created`, `status: "CONFIRMED"`, full QR data).
  - Paid registrations (`price > 0`) will use the same endpoint but dispatch to a `PaidRegistrationStrategy`, returning `status: "PENDING"` with Stripe checkout details (`clientSecret`), keeping the API surface unified and eliminating breaking changes for clients.
