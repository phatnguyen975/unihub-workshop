# API Contract: Workshop Cancellation

> **Status:** APPROVED  
> **Spec:** `specs/workshop-cancellation/SPEC.md`  
> **Last revised:** 2026-10-02

## 1. Overview

This contract defines the single REST endpoint exposed by the `workshops` module for Feature 2.4 — Workshop Cancellation:

- `PATCH /api/v1/admin/workshops/{id}/cancel` — Transition a `PUBLISHED` workshop to `CANCELED` status. Validates the caller's authorization and organization ownership, enforces temporal eligibility (workshop must not have already concluded), rejects prohibited fields, applies optimistic locking, and atomically commits the status change along with a `WorkshopCanceledEvent` to the PostgreSQL Transactional Outbox. Downstream workers independently handle registration cancellations, refunds, notification emails, Elasticsearch de-indexing, and Redis capacity counter invalidation.

The endpoint is strictly restricted to authenticated `ORGANIZER` users and enforces multi-tenant isolation (`organization_id == jwt.org`). Concurrency safety is guaranteed via optimistic locking (`version` in request body). This contract traces directly to `specs/workshop-cancellation/SPEC.md` and conforms fully to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** Endpoint is prefixed with `/api/v1/admin/workshops`, following the established RESTful admin-workshop URI convention. Sub-resource action suffix `/cancel` is consistent with the `/reschedule` and `/publish` patterns on the same resource. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** Not applicable; this endpoint operates on a single workshop resource identified by path parameter `{id}`.
- **Success response shape:** Unified `ApiResponse<WorkshopResponse>` envelope — `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. Null fields are dropped via `@JsonInclude(Include.NON_NULL)`. _(Source: `docs/api-standards.md`)_
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`), and `metadata` (`processedBy`, `traceId`) with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** The `Idempotency-Key` HTTP header is not required. Concurrency control is governed by optimistic locking using the mandatory `version` integer field in the request body (returning `409 Conflict` on version mismatch). Because `CANCELED` is a terminal state, a replay with the incremented version will be rejected with `422 Unprocessable Entity` (terminal-state rule), providing exactly-once semantics at the business level.
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every request.
- **Date/time formatting convention:** ISO 8601 with UTC offset and trailing `Z` — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### PATCH /api/v1/admin/workshops/{id}/cancel

**Description:** Transitions a `PUBLISHED` workshop to `CANCELED` status. Enforces temporal eligibility (workshop must not have already concluded), rejects any prohibited field in the request body, applies optimistic locking, and atomically commits the status change plus a `WorkshopCanceledEvent` to the PostgreSQL Transactional Outbox. Downstream registration, refund, notification, and search side-effects are handled asynchronously.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — protected by optimistic locking (`version`) and terminal-state semantics. Concurrent requests with identical version succeed exactly once; competing requests receive `409 Conflict`. After successful cancellation, `CANCELED` is a terminal state — any replay with the incremented version will be rejected with `422 Unprocessable Entity`, guaranteeing exactly-once business semantics.

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Content-Type`  | Yes      | `application/json` |

**Path Parameters:**

| Parameter | Type | Description                                       |
| :-------- | :--- | :------------------------------------------------ |
| `id`      | UUID | Unique identifier of the workshop to be canceled. |

**Query Parameters:** None.

**Request Body:**

```json
{
  "version": 2,
  "reason": "Venue is unavailable due to unforeseen circumstances."
}
```

**Field Descriptions:**

| Field     | Type    | Required | Description                                                                                                                                                                                                                           |
| :-------- | :------ | :------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `version` | integer | Yes      | Current stored version of the workshop for optimistic locking. Must not be null.                                                                                                                                                      |
| `reason`  | string  | No       | Human-readable explanation of the cancellation. Not stored in the database. Forwarded in the event payload for notification emails. Blank or whitespace-only values are treated as absent (null). Max 1000 characters after trimming. |

**Validation Rules:**

- `version` — Required integer; must not be null. Must match the current stored `version` of the workshop in PostgreSQL.
- `reason` — Optional string; when present, must not exceed 1000 characters after trimming. Blank or whitespace-only values are treated as null (absent). Not persisted to any database column.
- `id` — Must be a valid UUID referencing an existing workshop owned by the caller's organization (`organization_id == jwt.org`).
- **Workshop Status** — Target workshop must be in `PUBLISHED` status at the time of the request.
  - If `DRAFT`: rejected with `422 Unprocessable Content` directing caller to `DELETE /api/v1/admin/workshops/{id}`.
  - If `COMPLETED` or `CANCELED`: rejected with `422 Unprocessable Content` indicating terminal state.
- **Temporal Eligibility** — The workshop's `ends_at` must be strictly in the future (`ends_at > now()`). A workshop that has already concluded (`ends_at <= now()`) is rejected with `422 Unprocessable Content` regardless of status.
- **Prohibited Fields** — The following fields MUST NOT appear in the request body. If any are present (i.e., non-null), the request is rejected with `422 Unprocessable Content` with field-level error messages:
  - `title`, `description`, `coverImageUrl`, `location`, `timezone`, `startsAt`, `endsAt`, `capacity`, `price`, `currency`, `speakers`, `status`, `organizationId`

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop canceled successfully.",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "organizationId": "11111111-2222-3333-4444-555555555555",
    "title": "Advanced Microservices with Spring Modulith",
    "slug": "advanced-microservices-with-spring-modulith-a1b2",
    "description": "Deep dive into building event-driven modular monoliths.",
    "coverImageUrl": "http://localhost:9000/unihub-media/workshops/covers/550e8400-e29b-41d4-a716-446655440000.jpg",
    "location": "Auditorium A, Building C",
    "timezone": "Asia/Ho_Chi_Minh",
    "startsAt": "2026-12-01T10:00:00Z",
    "endsAt": "2026-12-01T12:00:00Z",
    "capacity": 100,
    "price": 0.0,
    "currency": "USD",
    "status": "CANCELED",
    "version": 3,
    "publishedAt": "2026-09-30T10:00:00Z",
    "canceledAt": "2026-10-02T08:00:00Z",
    "createdAt": "2026-09-25T10:00:00Z",
    "updatedAt": "2026-10-02T08:00:00Z",
    "speakers": [
      {
        "id": "22222222-3333-4444-5555-666666666666",
        "name": "Dr. Alan Turing",
        "email": "alan.turing@university.edu",
        "bio": "AI & Distributed Systems Researcher",
        "avatarUrl": "http://localhost:9000/unihub-media/speakers/avatars/22222222-3333-4444-5555-666666666666.jpg",
        "linkedinUrl": "https://linkedin.com/in/alan-turing",
        "sortOrder": 0
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> **Note on `canceledAt`:** This field is newly added to `WorkshopResponse` as part of Feature 2.4. It is `null` for workshops in any status other than `CANCELED`. It reflects the exact timestamp at which the workshop was canceled.

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                 |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON body, invalid path parameter UUID format, or missing `Content-Type: application/json` header.                                                                                                              |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                     |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, `org` claim missing, or the workshop belongs to a different organization than the caller's `org`.                                                                                       |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the given `id`.                                                                                                                                                                                   |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Supplied `version` does not match the stored workshop version (optimistic locking failure).                                                                                                                               |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Any of: `version` missing/null; `reason` exceeds 1000 chars after trimming; prohibited field present in body; workshop status is not `PUBLISHED` (DRAFT, COMPLETED, or CANCELED); `ends_at <= now()` (already concluded). |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited` | Too Many Requests     | Default Tier token bucket exhausted for `jwt.sub`.                                                                                                                                                                        |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                                                                                                                                                                                              |

**Error Response Example (409 Conflict — Version Mismatch):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop version conflict: expected version 2 but found version 3.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop in DRAFT Status):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop cannot be canceled because it is in DRAFT status. Use DELETE /api/v1/admin/workshops/{id} to delete a draft.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel",
    "validationErrors": ["status: Only PUBLISHED workshops may be canceled"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop in Terminal State):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop cannot be canceled because it is in a terminal state (COMPLETED).",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel",
    "validationErrors": [
      "status: Workshops in terminal status (COMPLETED, CANCELED) cannot be canceled"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop Already Concluded):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop has already concluded and cannot be canceled.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel",
    "validationErrors": [
      "ends_at: Workshop has already concluded. Only workshops that have not yet ended may be canceled."
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Prohibited Field):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Field 'title' cannot be modified via the cancel endpoint.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel",
    "validationErrors": [
      "title: Field is prohibited on PATCH /cancel. Only version and reason are accepted."
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — reason Too Long):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "The cancellation reason exceeds the maximum allowed length of 1000 characters.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel",
    "validationErrors": [
      "reason: Must not exceed 1000 characters after trimming"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

Upon successful cancellation, the following occur atomically in a single PostgreSQL transaction:

1. Sets `status` to `CANCELED`.
2. Sets `canceled_at = now()`.
3. Sets `updated_at = now()` and `updated_by = callerId`.
4. Increments `version = version + 1`.
5. Inserts a `WorkshopCanceledEvent` record into `event_publication` (Transactional Outbox) with payload:

```json
{
  "workshopId": "550e8400-e29b-41d4-a716-446655440000",
  "organizationId": "11111111-2222-3333-4444-555555555555",
  "title": "Advanced Microservices with Spring Modulith",
  "slug": "advanced-microservices-with-spring-modulith-a1b2",
  "startsAt": "2026-12-01T10:00:00Z",
  "endsAt": "2026-12-01T12:00:00Z",
  "canceledAt": "2026-10-02T08:00:00Z",
  "reason": "Venue is unavailable due to unforeseen circumstances.",
  "occurredAt": "2026-10-02T08:00:00Z"
}
```

> **Note on `reason`:** This field is present in the event payload but is NOT stored in the `workshops` database table. It is sourced exclusively from the request body and forwarded to allow notification workers to include it in cancellation emails. When not supplied or blank, `reason` is `null` in the event payload.

After transaction commit, Spring Modulith externalizes the `WorkshopCanceledEvent` via `@Externalized("unihub.events::workshop.canceled")` to RabbitMQ exchange `unihub.events` with routing key `workshop.canceled`.

Existing RabbitMQ bindings (already configured in `infra/rabbitmq/definitions.json.template`):

| Routing Key         | Queue             | Consumer                                    |
| :------------------ | :---------------- | :------------------------------------------ |
| `workshop.canceled` | `q.search`        | `search_sync` worker — de-indexes from ES   |
| `workshop.canceled` | `q.notifications` | `notifications` worker — sends email alerts |

> Additional bindings for registration cancellation and refund processing will be established as part of the respective downstream features. No synchronous calls to registration, payment, notification, or search services occur within the HTTP request thread.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15
- **Business Rules:** BR-01, BR-02, BR-03, BR-04, BR-05, BR-06, BR-07, BR-08
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15

## 4. Rate Limiting Behavior

This endpoint falls under the **Default Tier** of the Bucket4j + Redis Token Bucket enforced at the Core Backend Monolith filter layer (not at Nginx). _(Source: `docs/system-design.md` — Rate Limiting Tiers; `apps/backend/src/main/resources/application.yml`)_

| Endpoint                                    | Tier    | Capacity   | Refill Rate  | Key                               |
| :------------------------------------------ | :------ | :--------- | :----------: | :-------------------------------- |
| `PATCH /api/v1/admin/workshops/{id}/cancel` | Default | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |

The Default Tier is appropriate because:

- Callers are authenticated `ORGANIZER` actors (low population, no anonymous spike risk).
- Workshop cancellation is a deliberate, rare administrative operation.
- 100 requests per minute per user easily accommodates all legitimate cancellation actions.

When the bucket is exhausted, the server returns HTTP `429 Too Many Requests`:

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 30 seconds.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/cancel"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

Response headers returned on rate limiting:

- `X-RateLimit-Limit: 100`
- `X-RateLimit-Remaining: 0`
- `X-RateLimit-Reset: <epoch-seconds>`

## 5. Idempotency Behavior

The endpoint does not accept or require an `Idempotency-Key` HTTP header. Idempotency and lost-update prevention are handled through two complementary mechanisms:

1. **Mandatory `version` in Request Body:** Every caller must provide the current `version` integer for optimistic locking.
2. **First Write Wins (Optimistic Locking):** The first transaction matching `version == current_version` succeeds, committing `status = CANCELED`, `canceled_at = now()`, and `version = version + 1`.
3. **Competing or Repeated Retries Return 409 Conflict:** Any concurrent or delayed duplicate request carrying the old version is aborted with `409 Conflict` before any mutations or outbox writes occur.
4. **Terminal-State Guard (Exactly-Once at Business Level):** Because `CANCELED` is a terminal state, any replay with the incremented version is rejected with `422 Unprocessable Entity` (terminal state rule), guaranteeing at most one successful cancellation per workshop over its entire lifetime.

## 6. Cross-Endpoint Consistency Notes

1. **Resource Representation Symmetry:** The `data` payload of `PATCH .../cancel` returns the identical `WorkshopResponse` shape as `POST /api/v1/admin/workshops/drafts`, `PATCH .../publish`, `PUT .../{id}`, and `PATCH .../reschedule`. The addition of `canceledAt` to this response is additive — no existing consumer field is removed or renamed. `canceledAt` is `null` for workshops in other statuses and is omitted from responses via `@JsonInclude(NON_NULL)`.
2. **Explicit Versioning Across All Mutations:** Consistent with Feature 2.1, 2.2, and 2.3, all state mutations require `version` in the request body to prevent silent overwrite races.
3. **Uniform Error Taxonomy:** Error types strictly reuse established URNs — no new error type identifiers are introduced by this feature:
   - `urn:unihub:error:conflict` — optimistic locking mismatches.
   - `urn:unihub:error:validation` — business rule and input validation rejections.
   - `urn:unihub:error:forbidden` — RBAC role violations and cross-tenant attempts.
   - `urn:unihub:error:not-found` — non-existent workshop identifiers.
   - `urn:unihub:error:bad-request` — malformed payloads or invalid parameter types.
   - `urn:unihub:error:rate-limited` — token bucket exhaustion.
4. **Routing Key Differentiation:** `WorkshopCanceledEvent` uses routing key `workshop.canceled`, distinguishing it from `workshop.rescheduled` and `search` keys used by other workshop lifecycle events. This allows consumers to selectively bind based on lifecycle event type.
5. **reason Field — Event-Only, No DB Column:** Unlike all other fields in `WorkshopCanceledEvent`, `reason` has no counterpart column in the `workshops` table. This is intentional and matches the established pattern where event payloads may carry transient enrichment data beyond what is persisted. This asymmetry is called out explicitly here so future maintainers do not attempt to add a `reason` column to the schema.
