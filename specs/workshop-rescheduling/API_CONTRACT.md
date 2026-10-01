# API Contract: Workshop Rescheduling

> **Status:** APPROVED  
> **Spec:** `specs/workshop-rescheduling/SPEC.md`  
> **Last revised:** 2026-10-01

## 1. Overview

This contract defines the single REST endpoint exposed by the `workshops` module for Feature 2.3 — Workshop Rescheduling:

- `PATCH /api/v1/admin/workshops/{id}/reschedule` — Modify the `startsAt` and `endsAt` of an already `PUBLISHED` workshop. Validates that the new schedule is in the future, that `endsAt` is strictly after `startsAt`, that no speaker is double-booked in the new time window, that no other active workshop occupies the same campus location in the new time window, rejects any prohibited field in the request body, and atomically commits the new schedule along with a `WorkshopRescheduledEvent` to the PostgreSQL Transactional Outbox.

The endpoint is strictly restricted to authenticated `ORGANIZER` users and enforces multi-tenant isolation (`organization_id == jwt.org`). Concurrency safety is guaranteed via optimistic locking (`version` in request body). This contract traces directly to `specs/workshop-rescheduling/SPEC.md` and conforms fully to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** Endpoint is prefixed with `/api/v1/admin/workshops`, following the established RESTful admin-workshop URI convention. Sub-resource action suffix `/reschedule` is consistent with `/publish` and `/cancel` patterns on the same resource. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** Not applicable; this endpoint operates on a single workshop resource identified by path parameter `{id}`.
- **Success response shape:** Unified `ApiResponse<WorkshopResponse>` envelope — `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. Null fields are dropped via `@JsonInclude(Include.NON_NULL)`. _(Source: `docs/api-standards.md`)_
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`), and `metadata` (`processedBy`, `traceId`) with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** The `Idempotency-Key` HTTP header is not required. Concurrency control is governed by optimistic locking using the mandatory `version` integer field in the request body (returning `409 Conflict` on version mismatch). Repeated reschedule requests with the same version will fail with `409 Conflict` once the first has committed, preventing duplicate events.
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every request.
- **Date/time formatting convention:** ISO 8601 with UTC offset and trailing `Z` — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### PATCH /api/v1/admin/workshops/{id}/reschedule

**Description:** Modifies the scheduled start and end times of an already `PUBLISHED` workshop. Validates the new time window (future, chronological integrity), re-runs speaker schedule collision checks and campus-wide location collision checks for the new window, rejects any prohibited field in the body, and atomically commits schedule changes plus a `WorkshopRescheduledEvent` to the PostgreSQL Transactional Outbox.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — protected by optimistic locking (`version`). Concurrent requests with identical version succeed exactly once; competing requests receive `409 Conflict`. A successful reschedule increments `version`, so a retry with the old version also receives `409 Conflict`.

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Content-Type`  | Yes      | `application/json` |

**Path Parameters:**

| Parameter | Type | Description                                      |
| :-------- | :--- | :----------------------------------------------- |
| `id`      | UUID | Unique identifier of the workshop to reschedule. |

**Query Parameters:** None.

**Request Body:**

```json
{
  "version": 2,
  "startsAt": "2026-12-01T10:00:00Z",
  "endsAt": "2026-12-01T12:00:00Z"
}
```

**Field Descriptions:**

| Field      | Type    | Required | Description                                                                                 |
| :--------- | :------ | :------- | :------------------------------------------------------------------------------------------ |
| `version`  | integer | Yes      | Current stored version of the workshop for optimistic locking. Must not be null.            |
| `startsAt` | string  | Yes      | New scheduled start time in ISO 8601 UTC format (`YYYY-MM-DDTHH:mm:ssZ`). Must not be null. |
| `endsAt`   | string  | Yes      | New scheduled end time in ISO 8601 UTC format (`YYYY-MM-DDTHH:mm:ssZ`). Must not be null.   |

**Validation Rules:**

- `version` — Required integer; must not be null. Must match the current stored `version` of the workshop in PostgreSQL.
- `startsAt` — Required; must not be null; must be a valid ISO 8601 UTC timestamp; must be strictly in the future at the time the request is processed (`startsAt > now()`).
- `endsAt` — Required; must not be null; must be a valid ISO 8601 UTC timestamp; must be strictly after `startsAt` (`endsAt > startsAt`).
- `id` — Must reference an existing workshop owned by the caller's organization (`organization_id == jwt.org`).
- Workshop Status — Target workshop must be in `PUBLISHED` status.
  - If `DRAFT`: rejected with `422 Unprocessable Content` directing caller to the draft endpoint.
  - If `COMPLETED` or `CANCELED`: rejected with `422 Unprocessable Content` indicating terminal state.
- Speaker Collision — If the workshop has assigned speakers, no speaker email may overlap with any other active (`PUBLISHED` or `COMPLETED`) workshop in the new `[startsAt, endsAt]` window (excluding the current workshop). Detected collisions are rejected with `422 Unprocessable Content` identifying the conflicting speaker email(s).
- Location Collision — No other active (`PUBLISHED` or `COMPLETED`) workshop may occupy the same location in the new `[startsAt, endsAt]` window (excluding the current workshop). Location matching is campus-wide, trimmed, and case-insensitive. Detected collisions are rejected with `422 Unprocessable Content`.
- Prohibited Fields — The following fields MUST NOT appear in the request body. If any are present (i.e., non-null), the request is rejected with `422 Unprocessable Content` with field-level error messages:
  - `title`, `description`, `coverImageUrl`, `location`, `capacity`, `price`, `currency`, `speakers`, `status`, `organizationId`

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop rescheduled successfully.",
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
    "status": "PUBLISHED",
    "version": 3,
    "publishedAt": "2026-09-30T10:00:00Z",
    "createdAt": "2026-09-25T10:00:00Z",
    "updatedAt": "2026-10-01T15:00:00Z",
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

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                                                                                                         |
| :-------------------------- | :------------------------------ | :-------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON body, invalid path parameter UUID format, or missing `Content-Type: application/json` header.                                                                                                                                                                                                      |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                                                                                                             |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, `org` claim missing, or the workshop belongs to a different organization than the caller's `org`.                                                                                                                                                                               |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the given `id`.                                                                                                                                                                                                                                                                           |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Supplied `version` does not match the stored workshop version (optimistic locking failure).                                                                                                                                                                                                                       |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Any of: `version` missing/null; `startsAt` missing/null/in-the-past; `endsAt` missing/null/not-after-startsAt; workshop status is not `PUBLISHED` (in `DRAFT`, `COMPLETED`, or `CANCELED`); speaker schedule collision in new window; location collision in new window; prohibited field present in request body. |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited` | Too Many Requests     | Default Tier token bucket exhausted for `jwt.sub`.                                                                                                                                                                                                                                                                |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                                                                                                                                                                                                                                                                                      |

**Error Response Example (409 Conflict — Version Mismatch):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop version conflict: expected version 2 but found version 3.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Workshop Not PUBLISHED):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop cannot be rescheduled because it is in DRAFT status. Use the draft endpoint to modify the schedule.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule",
    "validationErrors": ["status: Only PUBLISHED workshops may be rescheduled"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — New startsAt in the Past):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "The new scheduled start time must be strictly in the future.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule",
    "validationErrors": ["startsAt: Must be strictly in the future"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Speaker Collision):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Speaker schedule collision detected in the new time window.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule",
    "validationErrors": [
      "speakers: Speaker 'jane@speaker.com' has an overlapping schedule with another active workshop in the new time window"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Location Collision):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Location collision detected: another active workshop is scheduled at the same location during the new time window.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule",
    "validationErrors": [
      "location: 'Auditorium A, Building C' is already booked by another active workshop during the proposed time window"
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
    "detail": "Field 'location' cannot be modified via the reschedule endpoint.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule",
    "validationErrors": [
      "location: Field is prohibited on PATCH /reschedule. Only startsAt and endsAt may be changed."
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

Upon successful reschedule, the following occur atomically in a single PostgreSQL transaction:

1. Sets `starts_at` and `ends_at` to the new values.
2. Sets `updated_at = now()` and `updated_by = callerId`.
3. Increments `version = version + 1`.
4. Inserts a `WorkshopRescheduledEvent` record into `event_publication` (Transactional Outbox) with payload:

```json
{
  "workshopId": "550e8400-e29b-41d4-a716-446655440000",
  "organizationId": "11111111-2222-3333-4444-555555555555",
  "title": "Advanced Microservices with Spring Modulith",
  "slug": "advanced-microservices-with-spring-modulith-a1b2",
  "location": "Auditorium A, Building C",
  "timezone": "Asia/Ho_Chi_Minh",
  "previousStartsAt": "2026-10-15T09:00:00Z",
  "previousEndsAt": "2026-10-15T12:00:00Z",
  "newStartsAt": "2026-12-01T10:00:00Z",
  "newEndsAt": "2026-12-01T12:00:00Z",
  "updatedAt": "2026-10-01T15:00:00Z",
  "occurredAt": "2026-10-01T15:00:00Z"
}
```

After transaction commit, Spring Modulith externalizes the `WorkshopRescheduledEvent` via `@Externalized("unihub.events::workshop.rescheduled")` to RabbitMQ exchange `unihub.events` with routing key `workshop.rescheduled`. Downstream consumers (notification worker, search sync worker) independently consume from this event.

No synchronous calls to notification services, Elasticsearch, or RabbitMQ occur within the HTTP request thread.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17
- **Business Rules:** BR-01, BR-02, BR-03, BR-04, BR-05, BR-06, BR-07, BR-08, BR-09
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12

## 4. Rate Limiting Behavior

This endpoint falls under the **Default Tier** of the Bucket4j + Redis Token Bucket enforced at the Core Backend Monolith filter layer (not at Nginx). _(Source: `docs/system-design.md` — Rate Limiting Tiers)_

| Endpoint                                        | Tier    | Capacity   | Refill Rate  | Key                               |
| :---------------------------------------------- | :------ | :--------- | :----------: | :-------------------------------- |
| `PATCH /api/v1/admin/workshops/{id}/reschedule` | Default | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |

The Default Tier is appropriate because:

- Callers are authenticated `ORGANIZER` actors (low population, no anonymous spike risk).
- Workshop rescheduling is a deliberate, low-frequency administrative operation.
- 100 requests per minute per user easily accommodates legitimate reschedule actions.

When the bucket is exhausted, the server returns HTTP `429 Too Many Requests`:

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 30 seconds.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/reschedule"
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

The endpoint does not accept or require an `Idempotency-Key` HTTP header. Idempotency and lost-update prevention are handled exclusively through JPA Optimistic Concurrency Control:

1. **Mandatory `version` in Request Body:** Every caller must provide the current `version` integer.
2. **First Write Wins:** The first transaction matching `version == current_version` succeeds, committing the new schedule and incrementing `version = version + 1`.
3. **Competing or Repeated Retries Return 409 Conflict:** Any concurrent or delayed duplicate request carrying the old version is aborted with `409 Conflict` before any mutations or outbox writes occur.
4. **Sequential Retries with New Version Are Valid:** A reschedule followed by another reschedule (with the newly incremented version) is intentionally allowed. Repeated reschedules are a legitimate business operation. Each successful reschedule emits exactly one `WorkshopRescheduledEvent` into the outbox, carrying distinct `previous*` and `new*` timestamps.

## 6. Cross-Endpoint Consistency Notes

1. **Resource Representation Symmetry:** The `data` payload of `PATCH .../reschedule` returns the identical `WorkshopResponse` shape as `POST /api/v1/admin/workshops/drafts`, `PATCH .../publish`, and `PUT .../{id}`, maintaining full structural compatibility across the admin workshop API surface.
2. **Explicit Versioning Across All Mutations:** Consistent with Feature 2.1 (`POST .../drafts`), Feature 2.2 (`PATCH .../publish`, `PUT .../{id}`), and Feature 2.4 (`PATCH .../cancel`), all state mutations require `version` in the request body to prevent silent overwrite races.
3. **Uniform Error Taxonomy:** Error types strictly reuse established URNs — no new error type identifiers are introduced by this feature:
   - `urn:unihub:error:conflict` — optimistic locking mismatches.
   - `urn:unihub:error:validation` — business rule and input validation rejections.
   - `urn:unihub:error:forbidden` — RBAC role violations and cross-tenant attempts.
   - `urn:unihub:error:not-found` — non-existent workshop identifiers.
   - `urn:unihub:error:bad-request` — malformed payloads or invalid parameter types.
   - `urn:unihub:error:rate-limited` — token bucket exhaustion.
4. **Routing Key Differentiation from Published/Updated Events:** Unlike `WorkshopPublishedEvent` and `WorkshopUpdatedEvent` (which use routing key `search`), `WorkshopRescheduledEvent` uses routing key `workshop.rescheduled`. This allows notification consumers to selectively bind to reschedule events without receiving all search-index update events.
