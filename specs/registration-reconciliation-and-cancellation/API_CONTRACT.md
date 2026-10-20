# API Contract: Registration Reconciliation & Cancellation

> **Status:** APPROVED  
> **Spec:** `specs/registration-reconciliation-and-cancellation/SPEC.md`  
> **Last revised:** 2026-10-18

## 1. Overview

This contract covers the only network-facing surface of Feature 4.3: a single unified action endpoint, `POST /api/v1/registrations/{id}/cancel`, owned by the `registrations` module in `apps/backend/` and callable by both `STUDENT` and `ORGANIZER`. The Dual-Case Reconciliation Sweeper (SPEC §3.1) and the `notifications` worker handler (SPEC §3.6) expose no endpoint and are therefore not part of this contract; their externally observable effects (events, real-time messages) are listed under Side Effects.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/registrations/{id}/cancel` — URI path versioning per `docs/api-standards.md`; sub-resource action consistent with `PATCH /api/v1/admin/workshops/{id}/cancel` and `PATCH /api/v1/notifications/{id}/read`. `POST` chosen (human-confirmed) because the call is an action with side effects and an optional body.
- **Pagination convention:** N/A — no collection endpoints.
- **Success response shape:** `{ success: true, message, data, metadata: { processedBy, traceId } }`; null fields omitted (`NON_NULL`).
- **Error response format:** RFC 7807 problem details in the standard error envelope `{ success: false, error: { type, title, status, detail, instance, [validationErrors] }, metadata: { processedBy, traceId } }`, `Content-Type: application/problem+json`, same shape as `specs/paid-workshop-registration/API_CONTRACT.md`.
- **Idempotency-key convention:** `Idempotency-Key` header (UUIDv4, max 64 chars) — required here (SPEC FR-27, BR-05; mandatory on money-moving mutations per `docs/api-standards.md`).
- **Auth header convention:** `Authorization: Bearer <jwt>` (RS256).
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z`.
- **Correlation:** `X-Correlation-ID` accepted.
- **Rate-limit headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` on `429`.

## 3. Endpoints

### POST /api/v1/registrations/{id}/cancel

**Description:** Cancels a `CONFIRMED` registration before its workshop starts, automatically refunding 100% of a paid registration, returning the seat, and triggering asynchronous student notification.  
**Required Role / Auth:** `STUDENT` **or** `ORGANIZER` — valid RS256 JWT. A `STUDENT` must own the registration (`jwt.sub == registration.studentId`). An `ORGANIZER` must belong to the organization that owns the registration's workshop (`jwt.org == workshop.organizationId`). Any other role (e.g. `STAFF`) is rejected. Authorization is evaluated before state/time validation.  
**Idempotency Requirement:** **Required** — money-moving, retry-prone mutation. The key is scoped per caller; a replay of a completed key returns the original response; an in-flight key returns `409`. The gateway refund is additionally deduplicated per registration so retries (even with a new key after a partial failure) never refund twice.

#### Request

**Headers:**

| Header             | Required    | Description                                           |
| :----------------- | :---------- | :---------------------------------------------------- |
| `Authorization`    | Yes         | `Bearer <jwt>` — role `STUDENT` or `ORGANIZER`.       |
| `Idempotency-Key`  | Yes         | UUIDv4 string, max 64 characters.                     |
| `Content-Type`     | If body     | `application/json`. The body may be omitted entirely. |
| `X-Correlation-ID` | Recommended | Client-generated UUID for tracing.                    |
| `Accept-Language`  | No          | BCP 47 tag for localized error messages.              |

**Path Parameters:**

| Parameter | Type | Description                               |
| :-------- | :--- | :---------------------------------------- |
| `id`      | UUID | Identifier of the registration to cancel. |

**Query Parameters:** None.

**Request Body (optional):**

```json
{
  "reason": "string — optional free-text cancellation reason"
}
```

**Validation Rules:**

- `id` — must be a valid UUID; otherwise `400`.
- `reason` — optional; trimmed; max 1000 characters after trimming (`422` if longer); blank/whitespace-only is treated as absent. Not persisted; delivered only in the notification event.
- `Idempotency-Key` — required, valid UUIDv4, ≤ 64 chars; otherwise `422`.
- Any other body field is ignored.

#### Response

**Success — 200 OK (free registration):**

```json
{
  "success": true,
  "message": "Registration canceled",
  "data": {
    "id": "e0000001-0000-4000-a000-000000000001",
    "workshopId": "f0000001-0000-4000-a000-000000000001",
    "studentId": "b0000001-0000-4000-a000-000000000001",
    "status": "CANCELED",
    "priceAtRegistration": 0.0,
    "currency": "USD",
    "createdAt": "2026-10-15T08:00:00Z",
    "updatedAt": "2026-10-18T09:30:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Success — 200 OK (paid registration):** identical, plus `refund`:

```json
{
  "success": true,
  "message": "Registration canceled",
  "data": {
    "id": "e0000001-0000-4000-a000-000000000002",
    "workshopId": "f0000001-0000-4000-a000-000000000002",
    "studentId": "b0000001-0000-4000-a000-000000000002",
    "status": "CANCELED",
    "priceAtRegistration": 50.0,
    "currency": "USD",
    "refund": { "amount": 50.0, "currency": "USD" },
    "createdAt": "2026-10-15T08:00:00Z",
    "updatedAt": "2026-10-18T09:30:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8d"
  }
}
```

> **Field notes:** `status` is always `CANCELED`. `updatedAt` is the cancellation time. `refund` is present only for paid registrations and reflects the amount refunded (100% of `priceAtRegistration`). QR fields (`qrPayload`, `qrSignature`, `qrIssuedAt`) and `paymentClientSecret` are deliberately never returned. The gateway payment/refund identifiers are deliberately not exposed to clients (SPEC FR-39, §6 Security).

**Error Responses:**

| Status                      | Error Identifier                       | Condition                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| :-------------------------- | :------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Path `id` is not a valid UUID, or the body is present but malformed JSON.                                                                                                                                                                                                                                                                                                                                                                                                          |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | JWT absent, expired, invalid signature, or revoked.                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | One of: (a) role is neither `STUDENT` nor `ORGANIZER`; (b) caller is a `STUDENT` who does not own the registration; (c) caller is an `ORGANIZER` whose organization does not own the registration's workshop.                                                                                                                                                                                                                                                                      |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No registration exists with the given `id` (or its workshop no longer resolves).                                                                                                                                                                                                                                                                                                                                                                                                   |
| `409 Conflict`              | `urn:unihub:error:conflict`            | A request with the same `Idempotency-Key` is currently in flight. Includes `Retry-After: 2`.                                                                                                                                                                                                                                                                                                                                                                                       |
| `422 Unprocessable Content` | `urn:unihub:error:validation`          | One of (`detail` distinguishes): (a) `Idempotency-Key` missing/invalid; (b) `reason` > 1000 chars; (c) registration is `CANCELED` — "Registration is already canceled"; (d) registration is `FAILED` — "Cannot cancel a failed registration"; (e) registration is `PENDING` — "Only CONFIRMED registrations can be canceled"; (f) workshop not `PUBLISHED`; (g) workshop already started or ended — "Cannot cancel registration for a workshop that has already started or ended". |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Registration-tier token bucket exceeded (5 / 10 s / IP). Includes `X-RateLimit-*` headers.                                                                                                                                                                                                                                                                                                                                                                                         |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected failure, including a paid `CONFIRMED` registration with no `COMPLETED` payment record (SPEC FR-34). No state is changed.                                                                                                                                                                                                                                                                                                                                                |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | One of: (a) payment gateway refund failed / timed out / circuit breaker open; (b) Redis unavailable for idempotency claim. Includes `Retry-After`. No state is changed; the idempotency key is released for retry.                                                                                                                                                                                                                                                                 |

#### Side Effects

On `200 OK` (first execution only; replays repeat none of these):

1. **Gateway refund (paid only):** a 100% refund is created at the payment gateway before any local change, outside any DB transaction, deduplicated per registration.
2. **Database (single transaction, primary DB):** registration → `CANCELED` (actor recorded as `updated_by`); payment → `REFUNDED` (paid only); idempotency key → `COMPLETED` with response snapshot; registration-canceled event inserted into the transactional outbox.
3. **Outbox event:** registration-canceled event externalized to exchange `unihub.events`, routing key `registration.canceled`, consumed by the `notifications` worker from `q.notifications`. Payload: registration, workshop and student identifiers and snapshots (student email/name, workshop title/slug/schedule/location), optional `reason`, canceling role (`STUDENT` | `ORGANIZER`), refunded amount and currency (paid only).
4. **After commit — Redis:** `INCR` on the workshop capacity counter (one seat). Failure here does not affect the response.
5. **After commit — real-time:** `registration.updated` on `unihub:stream:workshop:{workshopId}` with `{ workshopId, confirmedCount, pendingCount, capacity, availableSeats, lastUpdatedAt }`.
6. **Asynchronous (worker):** in-app notification, cancellation email (reason, actor, refund details), and `notification.new` on the student's personal stream.

On any error response: none of the above occurs.

#### Spec Traceability

- **Satisfies:** FR-16 – FR-40 (and FR-41 / FR-42 via the event it publishes).
- **Scenarios:** 10 – 26, 29 (27 – 28 via the published event).

## 4. Rate Limiting Behavior

`POST /api/v1/registrations/{id}/cancel` falls under the `POST /api/v1/registrations/**` Registration Tier token bucket: 5 tokens, refill 5 per 10 seconds, per client IP, enforced in the backend filter layer before authentication-dependent business logic. Exhaustion returns `429` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After`.

## 5. Idempotency Behavior

- Key scope: the authenticated caller plus the key, with method `POST` and the request path (which includes the registration id) and a hash of the body.
- L1 Redis in-flight lock → L2 `idempotency_keys` row (`PENDING` → `COMPLETED` with the `200` response snapshot, or `FAILED`).
- In-flight duplicate → `409` + `Retry-After: 2`. Completed replay → cached `200`, no refund call, no event, no capacity change.
- `503`/`5xx` outcomes mark the key `FAILED` so the client may retry with the same key.
- 4xx business rejections (`403`, `404`, `422`) do not leave a `COMPLETED` record.
- Gateway-level: the refund request carries a deterministic deduplication key derived from the registration, so even a retry after "refund succeeded but local commit failed" (possibly under a new `Idempotency-Key`) results in one refund and one `CANCELED` transition.
- The sweeper has no client-facing idempotency; its transitions are guarded by the "still `PENDING`" check (SPEC FR-14).

## 6. Cross-Endpoint Consistency Notes

- Response fields (`id`, `workshopId`, `studentId`, `status`, `priceAtRegistration`, `currency`, `createdAt`) reuse names/types from `POST /api/v1/registrations` (4.1/4.2); `updatedAt` is added, and `refund` is new and only appears here.
- `status` enum gains no new value: `CANCELED` already exists in the registration status set; clients of `GET /api/v1/registrations/me` already filter by it.
- `GET /api/v1/registrations/{id}` remains `STUDENT`-only; organizers have no read endpoint for a single registration in this feature (roster is Feature 4.4).
- Error identifiers and envelope identical to the paid-registration contract; `422` here intentionally uses `Unprocessable Content` as in the catalog (same status 422 as "Unprocessable Entity" elsewhere in docs).
- `registration.updated` payload matches `docs/api-standards.md §SSE Unified Event Catalog` and the shape published by the webhook path.
- The path segment `cancel` mirrors the workshop-level cancel action, but the HTTP method differs (`POST` vs `PATCH`) by explicit human decision.
