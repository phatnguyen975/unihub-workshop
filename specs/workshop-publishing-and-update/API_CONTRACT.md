# API Contract: Workshop Publishing and Update

> **Status:** APPROVED  
> **Spec:** `specs/workshop-publishing-and-update/SPEC.md`  
> **Last revised:** 2026-10-01

## 1. Overview

This contract defines the two REST endpoints exposed by the `workshops` module for Feature 2.2 — Workshop Publishing and Update:

1. `PATCH /api/v1/admin/workshops/{id}/publish` — Transition a workshop from `DRAFT` to `PUBLISHED` status with strict completeness validation, future schedule enforcement, speaker collision re-checking, and transactional outbox event emission.
2. `PUT /api/v1/admin/workshops/{id}` — Update presentation and capacity expansion attributes (`title`, `slug`, `description`, `coverImageUrl`, `capacity`) of an already `PUBLISHED` workshop while strictly protecting active registrations, purging replaced media, and rejecting prohibited field modifications.

Both endpoints are strictly restricted to authenticated `ORGANIZER` users and enforce multi-tenant isolation (`organization_id == jwt.org`). Concurrency safety is guaranteed via Optimistic Concurrency Control (`version` in request body). This contract traces directly to `specs/workshop-publishing-and-update/SPEC.md` and conforms in full to `docs/api-standards.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** All endpoints are prefixed with `/api/v1/admin/workshops`. Resources and sub-resources follow standard RESTful conventions. _(Source: `docs/api-standards.md`)_
- **Pagination convention:** Not applicable; both endpoints operate on single workshop resources identified by path parameter `{id}`.
- **Success response shape:** Unified `ApiResponse<WorkshopResponse>` envelope — `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. Null fields are dropped via `@JsonInclude(Include.NON_NULL)`. _(Source: `docs/api-standards.md`)_
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`, and optional `validationErrors`), and `metadata` (`processedBy`, `traceId`) with `Content-Type: application/problem+json`. _(Source: `docs/api-standards.md`)_
- **Idempotency-key convention:** Neither endpoint requires the `Idempotency-Key` HTTP header. Concurrency control and duplicate execution prevention are governed by optimistic locking using the mandatory `version` integer field in the request body (returning `409 Conflict` on version mismatch).
- **Auth header convention:** `Authorization: Bearer <JWT>` — RS256 signed, required on every endpoint.
- **Date/time formatting convention:** ISO 8601 with UTC offset and trailing `Z` — `YYYY-MM-DDTHH:mm:ssZ`.

## 3. Endpoints

### PATCH /api/v1/admin/workshops/{id}/publish

**Description:** Transitions a workshop from `DRAFT` status into `PUBLISHED` status. Validates that all mandatory scheduling, location, and capacity fields are populated and valid, verifies that `startsAt` is strictly in the future, and re-checks for speaker schedule collisions. Atomically updates the status, sets `publishedAt = now()`, increments `version`, and writes a `WorkshopPublishedEvent` to the PostgreSQL Transactional Outbox for asynchronous Elasticsearch indexing.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — protected by optimistic locking (`version`). Concurrent requests with identical version will succeed once, with competing requests receiving `409 Conflict`. Repeated sequential requests receive `422 Unprocessable Content` because the workshop is no longer in `DRAFT` status.

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Content-Type`  | Yes      | `application/json` |

**Path Parameters:**

| Parameter | Type | Description                                   |
| :-------- | :--- | :-------------------------------------------- |
| `id`      | UUID | Unique identifier of the workshop to publish. |

**Query Parameters:** None.

**Request Body:**

```json
{
  "version": 0
}
```

**Validation Rules:**

- `version` — Required integer; must not be null. Must match the current stored `version` of the workshop in PostgreSQL.
- `id` — Must reference an existing workshop owned by the caller's organization (`organization_id == jwt.org`).
- Workshop Status — Current status must be `DRAFT`. Attempting to publish an already `PUBLISHED`, `COMPLETED`, or `CANCELED` workshop is rejected with `422 Unprocessable Content`.
- Completeness Validation — The workshop record in PostgreSQL must satisfy:
  - `title` is not null, not blank, and length is between 1 and 255 characters.
  - `startsAt` is not null.
  - `endsAt` is not null and strictly after `startsAt`.
  - `location` is not null, not blank, and maximum 255 characters.
  - `capacity` is not null and between 1 and 1,000,000.
- Temporal Rule — `startsAt` must be strictly in the future (`startsAt > now()`).
- Speaker Schedule Collision — If the workshop has assigned speakers, no speaker's email may overlap with any other active (`PUBLISHED` or `COMPLETED`) workshop in the overlapping `[startsAt, endsAt]` window (excluding the current workshop itself).

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop published successfully.",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "organizationId": "11111111-2222-3333-4444-555555555555",
    "title": "Advanced Microservices with Spring Modulith",
    "slug": "advanced-microservices-with-spring-modulith-a1b2",
    "description": "Deep dive into building event-driven modular monoliths.",
    "coverImageUrl": "http://localhost:9000/unihub-media/workshops/covers/550e8400-e29b-41d4-a716-446655440000.jpg",
    "location": "Auditorium A, Building C",
    "timezone": "Asia/Ho_Chi_Minh",
    "startsAt": "2026-10-15T09:00:00Z",
    "endsAt": "2026-10-15T12:00:00Z",
    "capacity": 100,
    "price": 0.0,
    "currency": "USD",
    "status": "PUBLISHED",
    "version": 1,
    "publishedAt": "2026-09-30T10:00:00Z",
    "createdAt": "2026-09-25T10:00:00Z",
    "updatedAt": "2026-09-30T10:00:00Z",
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

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                                                                             |
| :-------------------------- | :------------------------------ | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON body, invalid path parameter UUID format, or missing `Content-Type: application/json` header.                                                                                                                                                                          |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                                                                                 |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, `org` claim missing, or workshop belongs to a different organization than caller's `org`.                                                                                                                                                           |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the given `id`.                                                                                                                                                                                                                                               |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Supplied `version` does not match the stored workshop version (optimistic locking failure).                                                                                                                                                                                           |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Incomplete mandatory draft fields (`startsAt`, `endsAt`, `location`, `capacity`); `startsAt <= now()`; speaker schedule collision with active workshop; workshop status is not `DRAFT` (e.g. `PUBLISHED`, `COMPLETED`, `CANCELED`); or `version` is missing/null in the request body. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                                                                                                                                                                                                                                                          |

**Error Response Example (409 Conflict — Version Mismatch):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "Workshop version conflict: expected version 0 but found version 1.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/publish"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Incomplete Draft):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop cannot be published from DRAFT when required fields are missing.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/publish",
    "validationErrors": [
      "location: must not be null or blank",
      "endsAt: must not be null"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Speaker Conflict):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Speaker schedule collision detected for speaker email: alan.turing@university.edu during overlapping workshop.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/publish",
    "validationErrors": [
      "speakers: Speaker 'alan.turing@university.edu' has an overlapping schedule with another active workshop"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- Atomically in PostgreSQL:
  1. Sets `status = 'PUBLISHED'`.
  2. Sets `published_at = now()`.
  3. Sets `updated_at = now()` and `updated_by = callerId`.
  4. Increments `version = version + 1`.
  5. Inserts a `WorkshopPublishedEvent` record into `event_publication` (Transactional Outbox).
- Spring Modulith externalizes the event to RabbitMQ exchange `unihub.events` with routing key `search`.
- Consumed by `search_sync` worker to index document into Elasticsearch.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, BR-01, BR-02, BR-03, BR-06, BR-09
- **Scenarios:** 1, 2, 3, 4, 5, 6, 11

### PUT /api/v1/admin/workshops/{id}

**Description:** Updates presentation and capacity expansion attributes of an already `PUBLISHED` workshop. Synchronizes URL `slug` with updated `title`, purges replaced cover image asset from MinIO storage, enforces that `capacity` is not reduced below current active registrations (`CONFIRMED` + `PENDING`), and inserts a `WorkshopUpdatedEvent` into the Transactional Outbox. Prohibits changes to scheduling (`startsAt`, `endsAt`), commercial pricing (`price`, `currency`), status, ownership, and speakers. Does not notify students.  
**Required Role / Auth:** `ORGANIZER` — JWT with `role = ORGANIZER` and `org` claim matching the workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — protected by optimistic locking (`version`). Retries with the same version will receive `409 Conflict`.

#### Request

**Headers:**

| Header          | Required | Description        |
| :-------------- | :------- | :----------------- |
| `Authorization` | Yes      | `Bearer <JWT>`     |
| `Content-Type`  | Yes      | `application/json` |

**Path Parameters:**

| Parameter | Type | Description                                  |
| :-------- | :--- | :------------------------------------------- |
| `id`      | UUID | Unique identifier of the workshop to update. |

**Query Parameters:** None.

**Request Body:**

```json
{
  "version": 1,
  "title": "Advanced Microservices with Spring Modulith — Revised Edition",
  "description": "Expanded course overview with additional case studies and hands-on labs.",
  "coverImageUrl": "http://localhost:9000/unihub-media/workshops/covers/660e8400-e29b-41d4-a716-446655440000.jpg",
  "capacity": 150
}
```

**Validation Rules:**

- `version` — Required integer; must not be null. Must match the current stored `version` of the workshop in PostgreSQL for optimistic locking.
- `title` — Optional string. If omitted or null, retains the existing workshop title and slug. If provided: must not be blank or whitespace-only; length must be between 1 and 255 characters; automatically synchronizes `slug`.
- `description` — Optional string; maximum 5,000 characters. If omitted, retains existing description. If provided: blank string is normalized to null.
- `coverImageUrl` — Optional string; maximum 2,048 characters. If omitted, retains existing cover image. If provided: must be a syntactically valid HTTP or HTTPS URL (blank string normalized to null, purging old MinIO asset).
- `capacity` — Optional integer; must be in range `[1, 1,000,000]`. If omitted or null, retains existing capacity.
- Active Registration Floor — If `capacity` is provided, it must be greater than or equal to the count of active registrations (`CONFIRMED` + `PENDING`) queried from the primary database (`capacity >= count(active_registrations)`).
- Partial Update Semantics — Callers may supply only the fields they wish to update alongside the mandatory `version`. Omitted or null fields retain their existing values in the database.
- Workshop Status — The target workshop must be in `PUBLISHED` status.
  - If target is in `DRAFT` status: request is rejected with `422 Unprocessable Content` with message directing caller to `POST /api/v1/admin/workshops/drafts`.
  - If target is in `COMPLETED` or `CANCELED` status: request is rejected with `422 Unprocessable Content` (terminal states cannot be updated).
- Prohibited Field Rejection — The client MUST NOT supply or attempt to modify prohibited fields:
  - `startsAt` / `endsAt` (must use `PATCH /api/v1/admin/workshops/{id}/reschedule`).
  - `price` / `currency` (strictly immutable once published).
  - `status` (must use dedicated state transitions).
  - `organizationId` (immutable tenant boundary).
  - `speakers` (immutable via generic update).
  - If any prohibited field is present in the request body, the backend rejects the request with `422 Unprocessable Content`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop updated successfully.",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "organizationId": "11111111-2222-3333-4444-555555555555",
    "title": "Advanced Microservices with Spring Modulith — Revised Edition",
    "slug": "advanced-microservices-with-spring-modulith-revised-edition-c3d4",
    "description": "Expanded course overview with additional case studies and hands-on labs.",
    "coverImageUrl": "http://localhost:9000/unihub-media/workshops/covers/660e8400-e29b-41d4-a716-446655440000.jpg",
    "location": "Auditorium A, Building C",
    "timezone": "Asia/Ho_Chi_Minh",
    "startsAt": "2026-10-15T09:00:00Z",
    "endsAt": "2026-10-15T12:00:00Z",
    "capacity": 150,
    "price": 0.0,
    "currency": "USD",
    "status": "PUBLISHED",
    "version": 2,
    "publishedAt": "2026-09-30T10:00:00Z",
    "createdAt": "2026-09-25T10:00:00Z",
    "updatedAt": "2026-09-30T14:30:00Z",
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

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                                                                                                                                                                                                                                                                  |
| :-------------------------- | :------------------------------ | :-------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | Malformed JSON body, invalid path parameter UUID format, or missing `Content-Type: application/json` header.                                                                                                                                                                                                                               |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, invalid-signature, or Redis-blacklisted Bearer JWT.                                                                                                                                                                                                                                                                      |
| `403 Forbidden`             | `urn:unihub:error:forbidden`    | Forbidden             | Caller `role` is not `ORGANIZER`, `org` claim missing, or workshop belongs to a different organization than caller's `org`.                                                                                                                                                                                                                |
| `404 Not Found`             | `urn:unihub:error:not-found`    | Not Found             | No workshop exists with the given `id`.                                                                                                                                                                                                                                                                                                    |
| `409 Conflict`              | `urn:unihub:error:conflict`     | Conflict              | Supplied `version` does not match the stored workshop version (optimistic locking failure).                                                                                                                                                                                                                                                |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Unprocessable Content | Validation failure (`title` blank/missing, `capacity < 1`, invalid URL); `capacity` reduced below active registration count (`CONFIRMED` + `PENDING`); target workshop is not in `PUBLISHED` status (in `DRAFT`, `COMPLETED`, or `CANCELED`); prohibited field supplied in request body; or `version` is missing/null in the request body. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Slug uniqueness retry exhausted or unexpected database failure.                                                                                                                                                                                                                                                                            |

**Error Response Example (422 Unprocessable Content — Capacity Below Active Registrations):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Capacity cannot be reduced below the active registration count of 70 (confirmed: 60, pending: 10).",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000",
    "validationErrors": [
      "capacity: Proposed capacity (50) cannot be less than active registrations count (70)"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Prohibited Field Modification):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Field 'price' cannot be modified on a published workshop.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000",
    "validationErrors": [
      "price: Commercial pricing is immutable once published"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Unprocessable Content — Updating DRAFT via PUT):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Workshop is in DRAFT status. Please use POST /api/v1/admin/workshops/drafts to update drafts.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000",
    "validationErrors": [
      "status: Workshops in DRAFT status cannot be updated via PUT /api/v1/admin/workshops/{id}"
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- Atomically in PostgreSQL:
  1. Updates `title`, `slug` (regenerated from title), `description`, `cover_image_url`, and `capacity`.
  2. Sets `updated_at = now()` and `updated_by = callerId`.
  3. Increments `version = version + 1`.
  4. Inserts a `WorkshopUpdatedEvent` record into `event_publication` (Transactional Outbox).
- Storage cleanup: If `coverImageUrl` was replaced or removed, the previous asset in MinIO bucket `unihub-media` is deleted asynchronously (if hosted in system MinIO storage `workshops/covers/`).
- Spring Modulith externalizes `WorkshopUpdatedEvent` to RabbitMQ exchange `unihub.events` with routing key `search`.
- Consumed by `search_sync` worker to update Elasticsearch index and invalidate Redis search caches.
- No notifications are sent to students.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23, FR-24, FR-25, BR-04, BR-05, BR-06, BR-07, BR-08, BR-09, BR-10
- **Scenarios:** 7, 8, 9, 10, 11, 12, 13

## 4. Rate Limiting Behavior

Both endpoints fall under the **Default Tier** of the Bucket4j + Redis Token Bucket enforced at the Core Backend Monolith filter layer (not at Nginx). _(Source: `docs/system-design.md` — Rate Limiting Tiers)_

| Endpoint                                     | Tier    |  Capacity  | Refill Rate  | Key                               |
| :------------------------------------------- | :------ | :--------: | :----------: | :-------------------------------- |
| `PATCH /api/v1/admin/workshops/{id}/publish` | Default | 100 tokens | 100 per 60 s | `jwt.sub` (authenticated user ID) |
| `PUT /api/v1/admin/workshops/{id}`           | Default | 100 tokens | 100 per 60 s | `jwt.sub`                         |

The Default Tier is appropriate because:

- Callers are authenticated `ORGANIZER` actors (low population, no anonymous spike risk).
- Workshop publishing and detail editing are deliberate, low-frequency administrative operations.
- 100 requests per minute per user easily accommodates legitimate organizer actions.

When the bucket is exhausted, the server returns HTTP `429 Too Many Requests`:

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:rate-limited",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 30 seconds.",
    "instance": "/api/v1/admin/workshops/550e8400-e29b-41d4-a716-446655440000/publish"
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

Neither endpoint accepts or requires an `Idempotency-Key` HTTP header. Idempotency and lost-update prevention are handled exclusively through JPA Optimistic Concurrency Control:

1. **Mandatory Version in Request Body:** Every caller must provide the current `version` integer (e.g. `{"version": 0}`).
2. **First Write Wins:** The first transaction matching `version == current_version` succeeds, committing mutations and incrementing `version = version + 1`.
3. **Competing or Repeated Retries Return 409 Conflict:** Any concurrent or delayed duplicate request carrying the old version is aborted with `409 Conflict` before any mutations or outbox writes can occur.
4. **State Machine Invalidation:** If a client retries a `publish` call with the newly incremented version, the workshop status is already `PUBLISHED`, so the request fails with `422 Unprocessable Content` (invalid state transition), preventing duplicate events or duplicate publishing.

## 6. Cross-Endpoint Consistency Notes

1. **Resource Representation Symmetry:** The `data` payload of both `PATCH .../publish` and `PUT .../{id}` returns the identical `WorkshopResponse` shape as `POST /api/v1/admin/workshops/drafts` and `GET /api/v1/admin/workshops/{id}`, maintaining full structural compatibility across the admin workshop API surface.
2. **Explicit Versioning Across Mutations:** In alignment with Feature 2.1 (`POST .../drafts`), Feature 2.2 (`PATCH .../publish`, `PUT .../{id}`), and Feature 2.4 (`PATCH .../cancel`), all state mutations in the `workshops` module require `version` in the request body to prevent silent overwrite races.
3. **Uniform Error Taxonomy:** Error types strictly reuse established URNs:
   - `urn:unihub:error:conflict` for optimistic locking mismatches.
   - `urn:unihub:error:validation` for business rule and input validation rejections.
   - `urn:unihub:error:forbidden` for RBAC role violations and cross-tenant attempts.
   - `urn:unihub:error:not-found` for non-existent workshop identifiers.
   - `urn:unihub:error:bad-request` for malformed payloads or invalid parameter types.
4. **Outbox Routing Key Symmetry:** Both `WorkshopPublishedEvent` and `WorkshopUpdatedEvent` target Modulith externalization channel `unihub.events::search` delivering to RabbitMQ queue `q.search`, establishing an event pipeline for the search worker.
