# API Contract: Paid Workshop Registration

> **Status:** APPROVED  
> **Spec:** `specs/paid-workshop-registration/SPEC.md`  
> **Last revised:** 2026-10-15

## 1. Overview

This contract extends the `POST /api/v1/registrations` endpoint (defined in `specs/free-workshop-registration/API_CONTRACT.md`) to handle the paid workshop branch, and introduces the new `POST /api/v1/payments/webhook` endpoint for Stripe payment event processing. Both endpoints belong to the same backend application (`apps/backend/`), but `POST /api/v1/registrations` is owned by the `registrations` module and `POST /api/v1/payments/webhook` is owned by the new `payments` module.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/registrations` and `/api/v1/payments/webhook` — URI path versioning per `docs/api-standards.md §Versioning Strategy`.
- **Pagination convention:** N/A — no collection endpoints in this contract.
- **Success response shape:** Standard JSON envelope `{ success: true, message, data, metadata: { processedBy, traceId } }`. Null fields omitted via `@JsonInclude(NON_NULL)`.
- **Error response format:** RFC 7807 Problem Details wrapped in standard envelope: `{ success: false, error: { type, title, status, detail, instance, [validationErrors] }, metadata: { processedBy, traceId } }`. Content-Type: `application/problem+json`.
- **Idempotency-key convention:** Required on `POST /api/v1/registrations`. Not applicable to `POST /api/v1/payments/webhook` (Stripe webhook idempotency is enforced via DB unique constraint, not by client keys).
- **Auth header convention:** `Authorization: Bearer <jwt>` (RS256) on `POST /api/v1/registrations`. **No `Authorization` header on `POST /api/v1/payments/webhook`** — it is unauthenticated; the `Stripe-Signature` HMAC header is the sole authentication mechanism.
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z` (e.g., `2026-10-15T08:00:00Z`).
- **Correlation:** `X-Correlation-ID` accepted on `POST /api/v1/registrations` for cross-boundary tracing.
- **Rate-limit headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` returned on `POST /api/v1/registrations` when rate-limited.

## 3. Endpoints

### POST /api/v1/registrations _(extended — paid branch additive)_

**Description:** Registers the authenticated student for a workshop. For `price == 0` workshops (free branch), behavior is unchanged from Feature 4.1 — returns `201 Created` with `status: CONFIRMED` and full QR fields. For `price > 0` workshops (paid branch), creates a `PENDING` registration, calls Stripe to create a Payment Intent, and returns `201 Created` with `status: PENDING` and the `paymentClientSecret` for the client to complete payment. The same endpoint, same request shape; the strategy is selected server-side based on the workshop's price.  
**Required Role / Auth:** `STUDENT` — valid RS256 JWT with role claim `STUDENT`.  
**Idempotency Requirement:** **Required** — identical to the free-branch requirement. The `Idempotency-Key` header (UUIDv4, ≤64 chars) is mandatory. For the paid branch, this key is also forwarded to Stripe as the Payment Intent creation idempotency key to deduplicate Stripe-side calls on retry.

#### Request

**Headers:**

| Header             | Required    | Description                                                                                                                                                                                    |
| :----------------- | :---------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Authorization`    | Yes         | `Bearer <jwt>` — RS256 JWT with role `STUDENT`.                                                                                                                                                |
| `Idempotency-Key`  | Yes         | UUIDv4 string, max 64 characters. Identifies the logical registration intent for deduplication (server-side) and Stripe Payment Intent deduplication (forwarded to Stripe for paid workshops). |
| `X-Correlation-ID` | Recommended | Client-generated UUID for cross-boundary request tracing.                                                                                                                                      |
| `Content-Type`     | Yes         | `application/json`                                                                                                                                                                             |
| `Accept-Language`  | No          | BCP 47 language tag for localized error messages (e.g., `en`, `vi`).                                                                                                                           |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:**

```json
{
  "workshopId": "f0000001-0000-4000-a000-000000000001"
}
```

**Validation Rules:**

- `workshopId` — **Required.** Must be a valid UUIDv4 string. Rejected with `400 Bad Request` if malformed; rejected with `422 Unprocessable Entity` if absent.

#### Response

**Success — 201 Created (paid workshop — PENDING):**

```json
{
  "success": true,
  "message": "Registration initiated. Please complete payment.",
  "data": {
    "id": "e0000001-0000-4000-a000-000000000001",
    "workshopId": "f0000001-0000-4000-a000-000000000001",
    "studentId": "b0000001-0000-4000-a000-000000000001",
    "status": "PENDING",
    "priceAtRegistration": 50.0,
    "currency": "USD",
    "paymentClientSecret": "pi_3MtwxAEhLOAGSuTX2xHNulnv_secret_987654",
    "createdAt": "2026-10-15T08:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Field descriptions (paid branch — PENDING):**
>
> - `id` — UUID of the newly created `PENDING` registration record.
> - `workshopId` — UUID of the workshop the student is registering for.
> - `studentId` — UUID of the authenticated student's user account (`jwt.sub`).
> - `status` — Always `PENDING` for a successful paid registration creation response.
> - `priceAtRegistration` — Price snapshot captured at registration creation time (in the workshop's declared currency). Not affected by future organizer edits to workshop price.
> - `currency` — ISO 4217 currency code (e.g., `"USD"`). Captured from the workshop at registration creation time.
> - `paymentClientSecret` — The Stripe Payment Intent `client_secret`. The client uses this to invoke Stripe.js / Stripe React Native SDK to complete the payment on the frontend. **This value is sensitive and must not be stored persistently by the client.**
> - `createdAt` — ISO 8601 UTC timestamp of registration creation.
>
> **Fields deliberately absent from paid PENDING response:**
>
> - `qrPayload`, `qrSignature`, `qrIssuedAt` — These fields are `null` for `PENDING` registrations and are cleanly omitted from the JSON payload via `@JsonInclude(NON_NULL)`. They are set atomically only on `PENDING → CONFIRMED` transition (after Stripe confirms the payment via webhook).

**Success — 201 Created (free workshop — CONFIRMED):**

Unchanged from Feature 4.1. See `specs/free-workshop-registration/API_CONTRACT.md §POST /api/v1/registrations` for the full free-branch response shape.

**Error Responses:**

| Status                      | Error Type                             | Condition                                                                                                                                                                                                                                                                                                                                                                                                      |
| :-------------------------- | :------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Request body is malformed JSON, or `workshopId` is not a valid UUID format.                                                                                                                                                                                                                                                                                                                                    |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | JWT is absent, expired, invalid signature, or revoked (JTI blacklist).                                                                                                                                                                                                                                                                                                                                         |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | Authenticated user's role is not `STUDENT`, or the student's `student_record_id` is `NULL` (unlinked profile).                                                                                                                                                                                                                                                                                                 |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No published workshop exists with the given `workshopId`.                                                                                                                                                                                                                                                                                                                                                      |
| `409 Conflict`              | `urn:unihub:error:conflict`            | One of three conditions: (a) `Idempotency-Key` is currently in-flight (PENDING — response includes `Retry-After: 2`); (b) student already has an active (`PENDING` or `CONFIRMED`) registration for this workshop; (c) workshop has zero remaining capacity. The `detail` field distinguishes the sub-condition.                                                                                               |
| `422 Unprocessable Entity`  | `urn:unihub:error:validation`          | One of four conditions: (a) `Idempotency-Key` header is absent or not a valid UUIDv4 / exceeds 64 chars; (b) `workshopId` field is missing from body; (c) workshop is not in `PUBLISHED` status, or has already started, or is in a terminal state; (d) student has a `CONFIRMED` registration for another workshop with an overlapping time slot. The `validationErrors` array provides per-condition detail. |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Bucket4j `registration` tier exceeded (5 requests / 10s / IP). Includes `X-RateLimit-Retry-After` header.                                                                                                                                                                                                                                                                                                      |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected server-side failure. No registration record is created in this case.                                                                                                                                                                                                                                                                                                                                |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | One of two conditions: (a) Redis is unreachable (capacity DECR cannot execute); (b) Resilience4j circuit breaker for Stripe is OPEN. Includes `Retry-After` header. No registration record is created and no capacity is reserved in either case.                                                                                                                                                              |

> **`409` disambiguation via `detail`:**
>
> - `"detail": "A request with this Idempotency-Key is already in progress. Retry after 2 seconds."` + `Retry-After: 2` header.
> - `"detail": "Student is already registered for this workshop."`
> - `"detail": "No seats remaining for this workshop."`

> **Change from Feature 4.1:** The `422` condition `(d) workshop price > 0 (not a free workshop)` that existed in the Feature 4.1 contract is **removed**. Paid workshops (`price > 0`) are now a valid, supported path through this endpoint. Clients that previously relied on the 4.1 contract to detect paid workshops via 422 must update their handling — the endpoint now returns `201 PENDING` with `paymentClientSecret` for paid workshops.

#### Side Effects

**For paid workshops (PENDING creation) — on `201 Created`:**

1. **Redis capacity reserved:** `workshop:{workshopId}:capacity` DECR'd atomically. `workshops.registered_count` incremented within the DB transaction. NOT compensated unless Stripe call fails (in which case 503 is returned, no record is created, and INCR compensates the DECR before responding).
2. **Stripe Payment Intent created:** A Payment Intent is created on Stripe for the workshop price with `registrationId` and `workshopId` in metadata. The `client_secret` is returned to the student.
3. **No outbox event at this stage.** The `WorkshopRegisteredEvent` is NOT published at `PENDING` creation — it is published only after the webhook confirms the payment (see `POST /api/v1/payments/webhook` side effects below).
4. **L1 idempotency cache set:** The idempotency key is cached in Redis with TTL 24 hours and status `COMPLETED`.

**On idempotent replay (`201 Created`, key already `COMPLETED`):** No side effects are re-executed — the cached response is returned as-is. Stripe is not called again.

**For free workshops (CONFIRMED creation):** Unchanged from Feature 4.1. See `specs/free-workshop-registration/API_CONTRACT.md` for the full side effects list.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 15, 16, 17

### POST /api/v1/payments/webhook

**Description:** Receives Stripe webhook events, verifies the `Stripe-Signature` HMAC, and processes `payment_intent.succeeded` and `payment_intent.payment_failed` events. All other event types are acknowledged and ignored. This endpoint is intentionally unauthenticated — the `Stripe-Signature` header is the sole trust mechanism. Must always return `200 OK` for any request that passes signature verification.  
**Required Role / Auth:** **PUBLIC** — no `Authorization` header required or accepted. The `Stripe-Signature` HMAC header is the sole authentication mechanism (per `docs/api-standards.md §Mandatory Headers`).  
**Idempotency Requirement:** **Not Required (client-header based)** — webhook deduplication is enforced server-side via the `payments.provider_payment_id` UNIQUE database constraint, not by a client-supplied header. A duplicate Stripe delivery for the same Payment Intent ID is detected when the `INSERT INTO payments` violates the unique constraint.

#### Request

**Headers:**

| Header             | Required | Description                                                                                                                                                                                                      |
| :----------------- | :------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Stripe-Signature` | Yes      | HMAC-SHA256 signature computed by Stripe using the webhook signing secret. Format: `t=<timestamp>,v1=<signature>`. Requests with an absent, malformed, or invalid signature are rejected with `400 Bad Request`. |
| `Content-Type`     | Yes      | `application/json` (set by Stripe automatically).                                                                                                                                                                |

> **Critical implementation note:** The `Content-Type: application/json` header is set by Stripe. However, the raw byte body **must be preserved** for HMAC verification before any JSON deserialization. The signature is computed over the raw request body bytes. Any framework body-parsing that occurs before HMAC verification invalidates the signature check.

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body (sent by Stripe):**

```json
{
  "id": "evt_3MtwxAEhLOAGSuTX00000001",
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_3MtwxAEhLOAGSuTX00000001",
      "amount": 5000,
      "currency": "usd",
      "status": "succeeded",
      "metadata": {
        "registrationId": "e0000001-0000-4000-a000-000000000001",
        "workshopId": "f0000001-0000-4000-a000-000000000001"
      }
    }
  }
}
```

> **Fields extracted from payload:**
>
> - `type` — Determines which event handler to invoke. Only `payment_intent.succeeded` and `payment_intent.payment_failed` trigger business logic. All others are acknowledged and ignored.
> - `data.object.id` — The Stripe Payment Intent ID (`provider_payment_id` in the `payments` table). Used as the deduplication key.
> - `data.object.amount` — Amount in smallest currency unit (cents). Used for the payment record.
> - `data.object.currency` — ISO 4217 currency code (lowercase, per Stripe convention — e.g., `"usd"`). Normalized to uppercase when stored.
> - `data.object.metadata.registrationId` — UUID of the registration record to transition.
> - `data.object.metadata.workshopId` — UUID of the workshop (used for capacity compensation on failure and SSE event routing).

**Validation Rules:**

- `Stripe-Signature` — **Required.** HMAC-SHA256 verification against the raw request body using `STRIPE_WEBHOOK_SECRET`. Rejected with `400 Bad Request` if absent, malformed, timestamp tolerance exceeded (Stripe default: ±5 minutes), or signature mismatch.
- `data.object.metadata.registrationId` — Must be present and a valid UUID in the Payment Intent metadata. If absent, the webhook is logged as a warning and acknowledged (`200 OK`) — not rejected.

#### Response

**Success — 200 OK (event processed or intentionally ignored):**

```json
{
  "success": true,
  "message": "Webhook received.",
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Always-200 contract:** This endpoint returns `200 OK` for any request that passes `Stripe-Signature` verification — including events for unknown types, already-processed Payment Intents (duplicates), and stale registrations. The only valid non-200 response is `400 Bad Request` on signature verification failure.

**Error Responses:**

| Status                      | Error Type                     | Condition                                                                                                                                                                                                                   |
| :-------------------------- | :----------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request` | `Stripe-Signature` header is absent, malformed, outside the timestamp tolerance window (default ±5 minutes), or the HMAC signature does not match.                                                                          |
| `500 Internal Server Error` | `urn:unihub:error:internal`    | Unexpected server-side failure **during signature verification** only. Any failure after successful signature verification still returns `200 OK` to prevent Stripe retries, and the error is logged for ops investigation. |

> **Operational note on 500:** A `500` from this endpoint (after signature verification succeeds) would cause Stripe to retry indefinitely. Therefore, the implementation must catch all post-verification exceptions, log them, and return `200 OK`. Only pre-verification failures (signature check itself throws an unrecoverable error) may legitimately propagate as `500`.

#### Side Effects

**On `payment_intent.succeeded` (first delivery — no duplicate):**

1. **Payment record created:** A new `payments` row is inserted with `status = COMPLETED`, `provider_payment_id = <Payment Intent ID>`, `amount`, `currency`, and `registration_id`, within the same DB transaction.
2. **Registration transitioned to CONFIRMED:** The `registrations` row is updated: `status = CONFIRMED`, `qr_payload`, `qr_signature`, and `qr_issued_at` set atomically within the same DB transaction.
3. **Outbox event published:** A `WorkshopRegisteredEvent` is written to the Spring Modulith `event_publication` outbox within the same DB transaction. After commit, the event is forwarded to RabbitMQ (routing key `workshop.registered`, exchange `unihub.events`, queue `q.notifications`), triggering the notification worker to:
   - Deliver a confirmation email with the QR PNG attachment (CID: `ticket-qr`) to the student's registered email address.
   - Create an in-app notification record.
   - Push an SSE `notification.new` event to the student's stream.
4. **Post-commit SSE publish (fire-and-forget):**
   - `payment.confirmed` → `unihub:stream:user:{studentUserId}` — payload: `{ registrationId, workshopId, qrPayload, qrIssuedAt }`.
   - `registration.updated` → `unihub:stream:workshop:{workshopId}` — payload: `{ workshopId, confirmedCount, pendingCount, capacity, availableSeats, lastUpdatedAt }`.

**On `payment_intent.succeeded` (duplicate delivery — `provider_payment_id` already exists):**

No side effects. The DB unique constraint prevents the duplicate `INSERT`. The endpoint returns `200 OK` after detecting the duplicate without re-firing any transition or events.

**On `payment_intent.payment_failed` (first delivery):**

1. **Payment record created:** A new `payments` row is inserted with `status = FAILED`, `provider_payment_id`, `amount`, `currency`, and `registration_id`.
2. **Registration transitioned to FAILED:** `registrations.status` set to `FAILED` within the same DB transaction.
3. **Capacity restored:** `workshop:{workshopId}:capacity` INCR'd in Redis and `workshops.registered_count` decremented within the same DB transaction.
4. **No outbox event, no QR fields.** The student is NOT sent a confirmation email. No SSE events for this case (a separate "payment failed" notification may be defined in a future feature).

**On unrecognized event type (any event with valid signature):**

None. `200 OK` returned immediately.

#### Spec Traceability

- **Satisfies:** FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, FR-26, FR-27
- **Scenarios:** 9, 10, 11, 12, 13, 14

## 4. Rate Limiting Behavior

**`POST /api/v1/registrations`:** Subject to the `registration` Bucket4j Token Bucket tier:

- **Limit:** 5 tokens per 10-second window, per client IP address.
- **Response on exhaustion:** `429 Too Many Requests` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` headers.
- **Enforcement order:** Rate limiting is the outermost guard — applied before JWT validation, idempotency lookup, or any business logic.

**`POST /api/v1/payments/webhook`:** No Bucket4j rate limiting applied. Stripe's delivery rate is controlled by Stripe itself. Signature verification is the only guard.

## 5. Idempotency Behavior

### 5.1 `POST /api/v1/registrations` — Client-supplied `Idempotency-Key`

Applies to the paid branch identically to the free branch (defined in `specs/free-workshop-registration/API_CONTRACT.md §5`):

- Key scoped to `(studentId, idempotencyKey)` composite.
- L1: Redis lock (status `PENDING`, TTL 30s) → L2: DB `idempotency_keys` table (status `COMPLETED`, response snapshot stored).
- On idempotent replay: cached `201 Created` response returned; Stripe is NOT called again.
- **Additional behavior for paid branch:** The same `Idempotency-Key` value is forwarded to Stripe as the Payment Intent creation idempotency key. This means if the server processes the same key twice (e.g., Redis lock evicted before DB row written), Stripe returns the same Payment Intent rather than creating a new one.
- Business errors (4xx other than idempotency-specific 409) do NOT create an `idempotency_keys` record. Only `201 Created` outcomes are stored as `COMPLETED`.

### 5.2 `POST /api/v1/payments/webhook` — Server-enforced via DB Unique Constraint

No client-supplied key. Deduplication is enforced by the `payments.provider_payment_id` UNIQUE database constraint:

- First delivery of a `payment_intent.succeeded` for Payment Intent `PI-1` → `INSERT INTO payments` succeeds → full processing.
- Second delivery of the same event → `INSERT INTO payments` violates UNIQUE constraint → caught and handled gracefully → `200 OK` returned; no re-processing.

## 6. Cross-Endpoint Consistency Notes

- **`studentId` field name:** Consistent with Feature 4.1 — refers to `jwt.sub`, the student's user UUID. Named `studentId` in responses, not `userId`.
- **`workshopId` field name:** Consistent with Feature 4.1 and the webhook `metadata.workshopId`.
- **`status` enum values:** `PENDING` is now a valid value in `POST /api/v1/registrations` responses (for paid branch). Feature 4.1 could only return `CONFIRMED` from this endpoint. Clients must handle both values.
- **`priceAtRegistration` and `currency`:** Present in the paid PENDING response to provide the student with the price snapshot and currency locked for payment. In free registration responses, these fields are null and omitted via `@JsonInclude(NON_NULL)`.
- **`paymentClientSecret`:** Present only in paid PENDING responses; absent (null → omitted) in free CONFIRMED responses and in any GET endpoint responses. Clients must treat this as a one-time-use value.
- **QR fields in POST response:** For free registrations (4.1), `qrPayload`, `qrSignature`, `qrIssuedAt` are present in the `201 Created` POST response. For paid registrations (4.2), these fields are absent at `201 Created` and are only available later via `GET /api/v1/registrations/{id}` after the registration is `CONFIRMED`.
- **Webhook `200 OK` response shape:** Follows the standard success envelope (`success: true, message, data: {}, metadata`) for consistency, even though Stripe does not consume the response body.
- **Error shape:** All error responses use the same RFC 7807-wrapped envelope. No endpoint has a one-off error shape. The `400 Bad Request` on the webhook endpoint uses the same `urn:unihub:error:bad-request` type as other endpoints.
- **SSE event `payment.confirmed`** payload `{ registrationId, workshopId, qrPayload, qrIssuedAt }` — consistent with `docs/api-standards.md §SSE Unified Event Catalog`. Note: `qrSignature` is intentionally excluded from the SSE payload (the student's app fetches the full ticket via `GET /api/v1/registrations/{id}`).
- **`GET /api/v1/registrations/me`:** Fully includes `PENDING` paid registrations in the list (ordered by `createdAt DESC`), allowing students to see their in-progress orders. The list supports filtering via `?status=PENDING`. As established in Feature 4.1, list items do not include QR fields regardless of status.
- **`GET /api/v1/registrations/{id}`:** Fully handles `PENDING` registrations (QR fields are null → omitted). After payment confirmation via webhook, the registration status transitions to `CONFIRMED` and the full ticket detail including QR fields is returned.
