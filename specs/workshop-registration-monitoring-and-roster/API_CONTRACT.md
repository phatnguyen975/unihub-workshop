# API Contract: Workshop Registration Monitoring and Roster

> **Status:** APPROVED  
> **Spec:** `specs/workshop-registration-monitoring-and-roster/SPEC.md`  
> **Last revised:** 2026-10-28

## 1. Overview

This contract defines the two read-only REST endpoints exposed by the `registrations` module of the Core Backend (`apps/backend/`) for feature **4.4 — Workshop Registration Monitoring and Roster**. Both endpoints are accessible only by users with the `ORGANIZER` role and are scoped to workshops belonging to the caller's organization.

It also documents the `registration.updated` real-time event payload emitted to connected SSE clients, as a cross-reference to the existing SSE infrastructure contract — no new SSE endpoint is introduced.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/admin/...` — consistent with other organizer-facing admin endpoints in this system. All paths are prefixed with `/api/v1`.
- **Pagination convention:** Keyset cursor pagination. Responses for list endpoints include a `cursor` object with `nextCursor` (opaque base64-encoded token) and `hasMore` (boolean). Default page size: 20. Maximum page size: 100. _(Source: docs/api-standards.md — Pagination)_
- **Success response shape:** JSON envelope `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. _(Source: docs/api-standards.md — JSON Envelope)_
- **Error response format:** RFC 7807 Problem Details — `{ "type": "...", "title": "...", "status": <int>, "detail": "...", "instance": "..." }`. _(Source: docs/api-standards.md — Error Format)_
- **Idempotency-key convention:** Not applicable — both endpoints are `GET` (naturally idempotent, no idempotency key needed).
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256-signed JWT issued by the platform auth service. The JWT MUST contain `role` (= `ORGANIZER`) and `org` (organization UUID) claims.
- **Date/time formatting convention:** ISO 8601 UTC — `2026-10-28T14:00:00Z`. _(Source: docs/api-standards.md)_

## 3. Endpoints

### GET /api/v1/admin/workshops/{workshopId}/registrations/summary

**Description:** Returns the current seat count snapshot (confirmed, pending, available, occupancy %) and the last update timestamp for a specific workshop.  
**Required Role / Auth:** `ORGANIZER` — Bearer JWT RS256 with `role=ORGANIZER` and a valid `org` claim.  
**Idempotency Requirement:** Not Required — `GET` request; naturally idempotent by definition.

#### Request

**Headers:**

| Header          | Required | Description                                                          |
| :-------------- | :------- | :------------------------------------------------------------------- |
| `Authorization` | Required | `Bearer <JWT>` — RS256-signed platform JWT                           |
| `X-Trace-Id`    | Optional | Client-supplied trace identifier for distributed tracing correlation |

**Path Parameters:**

| Parameter    | Type          | Validation                     | Description                                  |
| :----------- | :------------ | :----------------------------- | :------------------------------------------- |
| `workshopId` | UUID (string) | Must be a valid UUID v4 format | The unique identifier of the target workshop |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Registration summary retrieved",
  "data": {
    "workshopId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "capacity": 100,
    "confirmedCount": 72,
    "pendingCount": 5,
    "availableSeats": 23,
    "occupancyPercentage": 72.0,
    "lastUpdatedAt": "2026-10-28T14:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
  }
}
```

**Response Field Definitions:**

| Field                 | Type            | Description                                                                                                               |
| :-------------------- | :-------------- | :------------------------------------------------------------------------------------------------------------------------ |
| `workshopId`          | UUID string     | Echo of the path parameter                                                                                                |
| `capacity`            | integer ≥ 0     | Maximum seats configured for the workshop                                                                                 |
| `confirmedCount`      | integer ≥ 0     | Number of registrations in `CONFIRMED` status                                                                             |
| `pendingCount`        | integer ≥ 0     | Number of registrations in `PENDING` status                                                                               |
| `availableSeats`      | integer ≥ 0     | `max(0, capacity − confirmedCount − pendingCount)` — never negative                                                       |
| `occupancyPercentage` | decimal, 2 d.p. | `(confirmedCount × 100.0) / capacity`; `0.00` when `capacity = 0`                                                         |
| `lastUpdatedAt`       | ISO 8601 UTC    | Timestamp of the most recently updated registration; falls back to workshop's own `updatedAt` when no registrations exist |

**Error Responses:**

| Status                      | Error Type                | Condition                                                                                                                                 |
| :-------------------------- | :------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `validation-error`        | `workshopId` path parameter is not a valid UUID format                                                                                    |
| `401 Unauthorized`          | `authentication-required` | No `Authorization` header, expired JWT, invalid JWT signature, or JWT cannot be parsed                                                    |
| `403 Forbidden`             | `access-denied`           | Caller holds `STUDENT` or `STAFF` role (not `ORGANIZER`), OR the calling organizer's `org` claim does not match `workshop.organizationId` |
| `404 Not Found`             | `workshop-not-found`      | No workshop with the given `workshopId` exists                                                                                            |
| `429 Too Many Requests`     | `rate-limit-exceeded`     | Caller has exceeded the `default-tier` token bucket: 100 requests per 60 seconds per `jwt.sub`                                            |
| `500 Internal Server Error` | `internal-error`          | Unhandled infrastructure failure (database, circuit breaker open, etc.)                                                                   |

#### Side Effects

None. This endpoint is read-only. No events are published. No records are created or modified.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03 (access control); FR-04, FR-05, FR-06, FR-07, FR-08 (summary computation); FR-09 (replica routing); BR-01, BR-02, BR-07, BR-08, BR-09
- **Acceptance Criteria:** Scenario 1, Scenario 2, Scenario 3, Scenario 4, Scenario 5, Scenario 6, Scenario 7, Scenario 16

### GET /api/v1/admin/workshops/{workshopId}/registrations

**Description:** Returns a cursor-paginated attendee roster for a specific workshop, with optional filtering by registration status, ordered by registration creation time descending.  
**Required Role / Auth:** `ORGANIZER` — Bearer JWT RS256 with `role=ORGANIZER` and a valid `org` claim.  
**Idempotency Requirement:** Not Required — `GET` request; naturally idempotent by definition.

#### Request

**Headers:**

| Header          | Required | Description                                                          |
| :-------------- | :------- | :------------------------------------------------------------------- |
| `Authorization` | Required | `Bearer <JWT>` — RS256-signed platform JWT                           |
| `X-Trace-Id`    | Optional | Client-supplied trace identifier for distributed tracing correlation |

**Path Parameters:**

| Parameter    | Type          | Validation                     | Description                                  |
| :----------- | :------------ | :----------------------------- | :------------------------------------------- |
| `workshopId` | UUID (string) | Must be a valid UUID v4 format | The unique identifier of the target workshop |

**Query Parameters:**

| Parameter | Type    | Required | Default               | Validation                                                                                                             | Description                                                      |
| :-------- | :------ | :------- | :-------------------- | :--------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------- |
| `cursor`  | string  | No       | (absent = first page) | Opaque base64 token; must have been issued by this endpoint with matching filter. Mismatched filter fingerprint → 400. | Pagination cursor from a previous response's `cursor.nextCursor` |
| `size`    | integer | No       | `20`                  | Integer in range `[1, 100]`. Values < 1 → 422. Values > 100 → 422.                                                     | Number of entries to return per page                             |
| `status`  | string  | No       | (absent = no filter)  | One of: `PENDING`, `CONFIRMED`, `CANCELED`, `FAILED` (case-sensitive). Any other value → 422.                          | Filter roster to registrations with this exact status            |

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Attendee roster retrieved",
  "data": {
    "items": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "student": {
          "id": "2c624232-cfe8-4b93-9f74-95e1f3f24df9",
          "fullName": "Nguyen Van A",
          "studentNumber": "23123456",
          "email": "23123456@student.hcmus.edu.vn"
        },
        "status": "CONFIRMED",
        "price": 0.0,
        "currency": "USD",
        "registeredAt": "2026-10-15T09:00:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6IjdjOWU2Njc5LTc0MjUtNDBkZS05NDRiLWUwN2ZjMWY5MGFlNyIsInNvcnRGaWVsZCI6ImNyZWF0ZWRBdCIsInByaW1hcnlWYWx1ZSI6IjIwMjYtMTAtMTVUMDk6MDA6MDBaIiwic2Vjb25kYXJ5VmFsdWUiOm51bGwsImZpbHRlckZpbmdlcnByaW50IjoibnVsbCJ9",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
  }
}
```

**Response Field Definitions — `items[]` entry:**

| Field                   | Type         | Description                                                                                                                   |
| :---------------------- | :----------- | :---------------------------------------------------------------------------------------------------------------------------- |
| `id`                    | UUID string  | The registration's unique identifier                                                                                          |
| `student.id`            | UUID string  | The student's user account UUID                                                                                               |
| `student.fullName`      | string       | The student's full name as stored in the IAM module                                                                           |
| `student.studentNumber` | string       | The student's institutional student number                                                                                    |
| `student.email`         | string       | The student's institutional email address                                                                                     |
| `status`                | enum string  | Registration status: `PENDING` \| `CONFIRMED` \| `CANCELED` \| `FAILED`                                                       |
| `price`                 | decimal      | The price charged at the time of registration (`price_at_registration`), in the currency specified. `0.00` for free workshops |
| `currency`              | string       | ISO 4217 currency code (e.g., `"USD"`)                                                                                        |
| `registeredAt`          | ISO 8601 UTC | The timestamp when the registration was created (`created_at`)                                                                |

**Response Field Definitions — `cursor` object:**

| Field        | Type           | Description                                                                                           |
| :----------- | :------------- | :---------------------------------------------------------------------------------------------------- |
| `nextCursor` | string \| null | Opaque base64-encoded token to pass as `cursor` on the next request. `null` when `hasMore` is `false` |
| `hasMore`    | boolean        | `true` if there are additional pages beyond the current one                                           |

**Cursor internal structure** (informational — not part of the wire contract, do not parse client-side):

The `nextCursor` token encodes the following JSON, base64-encoded:

```json
{
  "id": "<last-item-registration-uuid>",
  "sortField": "createdAt",
  "primaryValue": "<last-item-created_at as ISO 8601>",
  "secondaryValue": null,
  "filterFingerprint": "status=CONFIRMED"
}
```

`filterFingerprint` is `null` when no `status` filter was applied. It is `"status=<VALUE>"` when a status filter was active. A cursor must only be used with the same filter that produced it.

**Last page — `hasMore: false`:**

```json
{
  "success": true,
  "message": "Attendee roster retrieved",
  "data": {
    "items": [/* 0 to size entries */],
    "cursor": {
      "nextCursor": null,
      "hasMore": false
    }
  },
  "metadata": { "processedBy": "...", "traceId": "..." }
}
```

**Error Responses:**

| Status                      | Error Type                | Condition                                                                                                                                                                    |
| :-------------------------- | :------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `invalid-cursor`          | The `cursor` token cannot be decoded (malformed base64 or corrupted JSON structure) OR the cursor's `filterFingerprint` does not match the current request's `status` filter |
| `400 Bad Request`           | `validation-error`        | `workshopId` path parameter is not a valid UUID format                                                                                                                       |
| `401 Unauthorized`          | `authentication-required` | No `Authorization` header, expired JWT, invalid JWT signature, or JWT cannot be parsed                                                                                       |
| `403 Forbidden`             | `access-denied`           | Caller holds `STUDENT` or `STAFF` role (not `ORGANIZER`), OR the calling organizer's `org` claim does not match `workshop.organizationId`                                    |
| `404 Not Found`             | `workshop-not-found`      | No workshop with the given `workshopId` exists                                                                                                                               |
| `422 Unprocessable Entity`  | `validation-error`        | `size` is less than 1 or greater than 100, OR `status` contains a value not in `{PENDING, CONFIRMED, CANCELED, FAILED}`                                                      |
| `429 Too Many Requests`     | `rate-limit-exceeded`     | Caller has exceeded the `default-tier` token bucket: 100 requests per 60 seconds per `jwt.sub`                                                                               |
| `500 Internal Server Error` | `internal-error`          | Unhandled infrastructure failure (database, circuit breaker open, IAM profile resolution unavailable, etc.)                                                                  |

#### Side Effects

None. This endpoint is read-only. No events are published. No records are created or modified.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03 (access control); FR-10, FR-11, FR-12, FR-13, FR-14 (roster and pagination); FR-09 (replica routing); BR-01, BR-02, BR-03, BR-04, BR-05, BR-06, BR-07
- **Acceptance Criteria:** Scenario 8, Scenario 9, Scenario 10, Scenario 11, Scenario 12, Scenario 15, Scenario 16, and the authorization scenarios (Scenario 4, Scenario 5, Scenario 6, Scenario 7)

## 4. Rate Limiting Behavior

Both endpoints are subject to the **`default-tier`** token bucket rate limit:

- **Limit:** 100 requests per 60 seconds per authenticated user, keyed by `jwt.sub`.
- **Mechanism:** Redis Bucket4j Token Bucket.
- **Fail-open:** If Redis is unavailable, the rate-limit check is skipped and the request proceeds normally. Rate-limiting errors are never surfaced to the caller when the limiter itself is down.
- **Response on limit exceeded:** `429 Too Many Requests` with `Retry-After` header indicating seconds until the bucket refills.

Neither endpoint uses the `registration-tier` bucket (which is reserved for registration write operations).

## 5. Idempotency Behavior

Not applicable. Both endpoints are `GET` requests and are inherently idempotent — the same request can be repeated any number of times without side effects. No idempotency key is needed or accepted.

## 6. SSE Event Contract — `registration.updated` (Cross-Reference)

> **Note:** This section documents the **required** real-time event payload shape for the existing `registration.updated` event type, which is consumed by organizer dashboards subscribed to a workshop's SSE channel. **No new SSE endpoint is introduced by this feature.** The SSE stream itself is defined in `specs/real-time-event-streaming-hub/API_CONTRACT.md`. This section exists solely to formalize the event payload shape that the current implementation must be corrected to produce (gap identified during context analysis; fix deferred to `/build`).

**Event channel:** `unihub:stream:workshop:{workshopId}` (Redis Pub-Sub key, broadcasted to all SSE clients subscribed to that workshop via the SSE hub).

**Event type:** `registration.updated`

**Triggers (per FR-15, FR-16):**

- A registration transitions to `CONFIRMED` status (free workshop auto-confirm or payment webhook success).
- A registration transitions to `CANCELED` status (student cancels or sweeper times out).

**Wire format (as received by the SSE client):**

```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "eventType": "registration.updated",
  "timestamp": "2026-10-28T14:00:00Z",
  "payload": {
    "workshopId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "confirmedCount": 72,
    "pendingCount": 4,
    "capacity": 100,
    "availableSeats": 24,
    "lastUpdatedAt": "2026-10-28T14:00:00Z"
  }
}
```

**Payload Field Definitions:**

| Field                    | Type         | Description                                                                   |
| :----------------------- | :----------- | :---------------------------------------------------------------------------- |
| `eventId`                | UUID string  | Unique identifier for this event instance                                     |
| `eventType`              | string       | Always `"registration.updated"` for this event type                           |
| `timestamp`              | ISO 8601 UTC | When the event was emitted                                                    |
| `payload.workshopId`     | UUID string  | The workshop whose seat counts changed                                        |
| `payload.confirmedCount` | integer ≥ 0  | Current total of `CONFIRMED` registrations after the transition               |
| `payload.pendingCount`   | integer ≥ 0  | Current total of `PENDING` registrations after the transition                 |
| `payload.capacity`       | integer ≥ 0  | The workshop's maximum seat capacity (unchanged by the transition)            |
| `payload.availableSeats` | integer ≥ 0  | `max(0, capacity − confirmedCount − pendingCount)` at the moment of the event |
| `payload.lastUpdatedAt`  | ISO 8601 UTC | Timestamp of the registration state change that triggered this event          |

**Implementation gap note:** The current implementation emits only `{ workshopId, status }`. This contract specifies the full 6-field payload. The discrepancy will be corrected as part of the `/build` tasks for this feature. Until then, SSE clients receiving this event may see a truncated payload.

**Spec Traceability:** FR-15, FR-16, FR-17, Scenario 13, Scenario 14.

## 7. Cross-Endpoint Consistency Notes

- **`workshopId` UUID format** is used identically across both endpoints and in the SSE event payload. Clients and implementers should treat these as the same type throughout.
- **Error response shape** follows the same RFC 7807 Problem Details format established in `specs/free-workshop-registration/API_CONTRACT.md` and `specs/payment-history-and-status/API_CONTRACT.md`. No endpoint-specific error format is introduced.
- **Cursor format** follows the identical keyset cursor structure used in `specs/free-workshop-registration/API_CONTRACT.md` (`id`, `sortField`, `primaryValue`, `secondaryValue`, `filterFingerprint`). The only new addition is the `filterFingerprint` field encoding the `status` filter — this field is already present in the existing cursor schema but was previously always `null`.
- **Nested `student: {}` structure** follows the pattern established in `specs/payment-history-and-status/API_CONTRACT.md` for organizer-facing registration views, ensuring frontend clients can reuse the same parsing logic.
- **ISO 8601 UTC timestamps** (`registeredAt`, `lastUpdatedAt`, `timestamp`) use the same format as all other timestamp fields across the platform.
