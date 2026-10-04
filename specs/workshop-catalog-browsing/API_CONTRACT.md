# API Contract: Workshop Catalog Browsing & Full-Text Search

> **Status:** APPROVED  
> **Spec:** `specs/workshop-catalog-browsing/SPEC.md`  
> **Last revised:** 2026-10-02

## 1. Overview

This contract defines the network interface for the merged Feature 3.1 (`workshop-catalog-browsing`) and Feature 3.2 (`workshop-full-text-search`) implementation cycle. It covers two public REST endpoints served by the Core Backend and the RabbitMQ consumer contract for the `search_sync` background worker:

1. `GET /api/v1/workshops` — Catalog listing and full-text search (module: `search`).
2. `GET /api/v1/workshops/{identifier}` — Individual workshop detail by UUID or slug (module: `workshops`).
3. **Worker contract: `search_sync`** — RabbitMQ message consumption from queue `q.search` (app: `apps/workers/`).

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v{n}/...` path versioning (this contract uses `/api/v1`). Governed by `docs/api-standards.md §Versioning`.
- **Pagination convention:** Keyset cursor pagination via `?cursor=` (opaque Base64-encoded JSON) and `?size=` (default 20, max 100). Cursor JSON payload: `{ id, sortField, primaryValue, secondaryValue }`. Response includes `{ nextCursor, hasMore }`. Governed by `docs/api-standards.md §Pagination`.
- **Search parameter:** `?q=` for free-text keyword search. No alias names (`search`, `keyword`, `query`) permitted. Governed by `docs/api-standards.md §Search`.
- **Sorting parameter:** `?sort={field},{direction}`. Governed by `docs/api-standards.md §Sorting`.
- **Success response shape:** Standard JSON envelope `{ success: boolean, message: string, data: object | object[], metadata: { processedBy, traceId } }`. Governed by `docs/api-standards.md §Standard JSON Payloads`.
- **Error response format:** RFC 7807 Problem Details inside the standard envelope `{ success: false, error: { type, title, status, detail, instance }, metadata }`. Content-Type: `application/problem+json`. Governed by `docs/api-standards.md §Error Response`.
- **Auth header convention:** `Authorization: Bearer <token>` (optional for these public endpoints — no auth required).
- **Date/time formatting:** ISO 8601 UTC with trailing `Z`, e.g., `2026-05-03T12:00:00Z`. Governed by `docs/api-standards.md §Date/Time`.
- **Idempotency-key convention:** Not applicable — both endpoints are safe, idempotent reads (GET).
- **Mandatory response headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining` always present; `X-RateLimit-Retry-After` present on 429 responses. Security response headers (`Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Content-Security-Policy`) are applied globally by the framework filter chain.

## 3. Endpoints

### GET /api/v1/workshops

**Description:** Returns a paginated, sorted, and optionally filtered/searched list of `PUBLISHED` workshops from the Elasticsearch CQRS read model, served via the SWR Redis cache-aside layer.  
**Required Role / Auth:** PUBLIC — no authentication required (`permitAll`).  
**Idempotency Requirement:** Not Required — safe, idempotent GET with no side effects.

#### Request

**Headers:**

| Header             | Required | Description                                                                                                               |
| :----------------- | :------- | :------------------------------------------------------------------------------------------------------------------------ |
| `X-Correlation-ID` | Optional | Client-generated opaque identifier for cross-boundary request tracing. Passed through to `metadata.traceId` when present. |
| `Accept-Language`  | Optional | Preferred language for localized RFC 7807 error messages.                                                                 |

**Path Parameters:** None.

**Query Parameters:**

| Parameter   | Type    | Required | Constraints                                                                                                   | Description                                                                                                                                                                                                             |
| :---------- | :------ | :------- | :------------------------------------------------------------------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `q`         | string  | No       | Max 200 characters                                                                                            | Free-text keyword query. When present, executes an ES multi-match across `title`, `description`, `location`, `speakers.name`, `slug` with fuzzy matching. When absent, executes a filter-only query (no `must` clause). |
| `priceType` | string  | No       | Enum: `FREE`, `PAID`                                                                                          | Structured filter on price type. `FREE` matches `price = 0`; `PAID` matches `price > 0`.                                                                                                                                |
| `from`      | string  | No       | ISO-8601 UTC (e.g., `2026-11-01T00:00:00Z`)                                                                   | Filter: include workshops whose `startsAt >= from`.                                                                                                                                                                     |
| `to`        | string  | No       | ISO-8601 UTC (e.g., `2026-11-30T23:59:59Z`)                                                                   | Filter: include workshops whose `startsAt <= to`. Must be greater than `from` if both are supplied.                                                                                                                     |
| `sort`      | string  | No       | One of: `startsAt,asc` (default), `startsAt,desc`, `price,asc`, `price,desc`, `capacity,asc`, `capacity,desc` | Sort order for the result set. Defaults to `startsAt,asc`.                                                                                                                                                              |
| `cursor`    | string  | No       | Opaque Base64-encoded keyset cursor                                                                           | Pagination cursor issued by a previous response's `data.cursor.nextCursor`. Absent on first page.                                                                                                                       |
| `size`      | integer | No       | Min: 1, Max: 100, Default: 20                                                                                 | Page size (number of items to return).                                                                                                                                                                                  |

**Request Body:** None.

**Validation Rules:**

- `q` — optional; if present, must be a non-blank string with max 200 characters. Blank strings (whitespace-only) are treated as absent.
- `priceType` — optional; if present, must exactly match `FREE` or `PAID` (case-sensitive). Any other value: `422 Unprocessable Content`.
- `from` / `to` — optional; each, if present, must be a parseable ISO-8601 UTC timestamp. Either parameter can be used independently as a one-sided bound (`startsAt >= from` or `startsAt <= to`). If both `from` and `to` are present, `from` must be strictly earlier than `to` (`from < to`). Supplying an unparseable timestamp or violating `from < to` results in `422 Unprocessable Content`.
- `sort` — optional; if present, must exactly match one of the six enumerated values. Any other value: `422 Unprocessable Content`.
- `cursor` — optional; if present, must be a valid Base64-decodable string yielding a valid keyset cursor JSON payload. Malformed cursor: `400 Bad Request`.
- `cursor` + `sort` mismatch — if `cursor.sortField` does not match the current `sort` parameter, the request is rejected: `400 Bad Request`.
- `size` — optional; if present, must be an integer between 1 and 100 inclusive. Values > 100: `422 Unprocessable Content`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshops retrieved",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "slug": "design-sprint-2026",
        "title": "Design Sprint 2026",
        "coverImageUrl": "https://storage.unihub.io/workshops/covers/design-sprint.jpg",
        "location": "Room A101, Engineering Faculty",
        "startsAt": "2026-11-15T08:00:00Z",
        "endsAt": "2026-11-15T12:00:00Z",
        "timezone": "Asia/Ho_Chi_Minh",
        "price": "0.00",
        "currency": "USD",
        "capacity": 80,
        "availableSeats": 42,
        "priceType": "FREE",
        "organizationId": "org-uuid-here"
      }
    ],
    "cursor": {
      "nextCursor": "eyJpZCI6IjU1MGU4NDAw...",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Response Field Definitions — `PublicWorkshopCard`:**

| Field            | Type             | Description                                                                                                                    |
| :--------------- | :--------------- | :----------------------------------------------------------------------------------------------------------------------------- |
| `id`             | UUID string      | Workshop unique identifier.                                                                                                    |
| `slug`           | string           | URL-safe slug for the workshop detail page.                                                                                    |
| `title`          | string           | Workshop display title.                                                                                                        |
| `coverImageUrl`  | string / null    | Cover image URL; null if not set. Serialized via `@JsonInclude(NON_NULL)`.                                                     |
| `location`       | string           | Physical location of the workshop.                                                                                             |
| `startsAt`       | ISO-8601 UTC     | Workshop start time.                                                                                                           |
| `endsAt`         | ISO-8601 UTC     | Workshop end time.                                                                                                             |
| `timezone`       | string           | IANA timezone identifier (e.g., `Asia/Ho_Chi_Minh`).                                                                           |
| `price`          | string (decimal) | Price per seat as a decimal string (e.g., `"0.00"`, `"250.00"`). Decimal string used to avoid floating-point precision issues. |
| `currency`       | string           | ISO 4217 currency code (e.g., `"USD"`).                                                                                        |
| `capacity`       | integer          | Maximum number of seats.                                                                                                       |
| `availableSeats` | integer          | Remaining seats (≥ 0). Resolved from Redis `workshop:{id}:capacity`; falls back to DB formula on cache miss. Never negative.   |
| `priceType`      | string           | `"FREE"` when `price == 0`, `"PAID"` when `price > 0`. Derived; not persisted.                                                 |
| `organizationId` | UUID string      | Owning organization identifier.                                                                                                |

**Circuit Breaker OPEN / ES unavailable — 200 OK (graceful degradation):**

```json
{
  "success": true,
  "message": "Workshops retrieved",
  "data": {
    "items": [],
    "cursor": {
      "nextCursor": null,
      "hasMore": false
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

> [!NOTE]
> This empty-result 200 OK is the circuit breaker open / ES failure response. No `5xx` or `503` is returned from this endpoint — graceful degradation per BR-05 and ADR-020.

**Error Responses:**

| Status                      | Error `type`                    | Condition                                                                                                                                                                          |
| :-------------------------- | :------------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Malformed or undecodable `cursor` value (Base64 decode failure or invalid JSON structure).                                                                                         |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | `cursor.sortField` does not match the current `sort` parameter (cursor sort field mismatch). Error `detail` MUST name both the cursor's sort field and the current sort parameter. |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | `priceType` is not one of `FREE` or `PAID`.                                                                                                                                        |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | `sort` is not one of the six enumerated sort values.                                                                                                                               |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | `from` or `to` is not a valid ISO-8601 UTC timestamp.                                                                                                                              |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | Both `from` and `to` are present but `from >= to`.                                                                                                                                 |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | `size` exceeds 100 or is less than 1.                                                                                                                                              |
| `422 Unprocessable Content` | `urn:unihub:error:validation`   | `q` exceeds 200 characters.                                                                                                                                                        |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited` | Default public Token Bucket rate limit exhausted for this caller. Includes `Retry-After` header.                                                                                   |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Unexpected server error (e.g., Redis unavailable and ES also fails — both fallback paths exhausted).                                                                               |

#### Side Effects

- **SWR stale hit:** A non-blocking Redis `tryLock` attempt is made. The lock holder fires an async ES query and updates the Redis cache entry. No side effect visible to the caller.
- **Cache miss:** ES query result is written to the Redis SWR cache with the configured soft and hard TTLs.
- No writes to the primary database.
- No writes to Elasticsearch from this endpoint.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15
- **Scenarios:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 20, 21, 22, 23

### GET /api/v1/workshops/{identifier}

**Description:** Returns the full detail of a single `PUBLISHED` workshop identified by either its UUID `id` or its URL `slug`. Non-published workshops and non-existent identifiers both return `404 Not Found`.  
**Required Role / Auth:** PUBLIC — no authentication required (`permitAll`).  
**Idempotency Requirement:** Not Required — safe, idempotent GET with no side effects.

#### Request

**Headers:**

| Header             | Required | Description                                                            |
| :----------------- | :------- | :--------------------------------------------------------------------- |
| `X-Correlation-ID` | Optional | Client-generated opaque identifier for cross-boundary request tracing. |
| `Accept-Language`  | Optional | Preferred language for localized RFC 7807 error messages.              |

**Path Parameters:**

| Parameter    | Type   | Description                                                                                                                                                                                                                                 |
| :----------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `identifier` | string | Either the workshop's UUID `id` (e.g., `550e8400-e29b-41d4-a716-446655440000`) or its URL `slug` (e.g., `design-sprint-2026`). The system automatically detects the type: valid UUID format → lookup by `id`; otherwise → lookup by `slug`. |

**Query Parameters:** None.

**Request Body:** None.

**Validation Rules:**

- `identifier` — required path parameter; must be a non-blank string. Maximum length is enforced by the URL length limit of the gateway (not a specific field constraint). No other format enforcement beyond UUID-vs-slug detection.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Workshop retrieved",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "slug": "design-sprint-2026",
    "title": "Design Sprint 2026",
    "description": "A hands-on workshop covering the full Google Design Sprint methodology...",
    "coverImageUrl": "https://storage.unihub.io/workshops/covers/design-sprint.jpg",
    "location": "Room A101, Engineering Faculty",
    "startsAt": "2026-11-15T08:00:00Z",
    "endsAt": "2026-11-15T12:00:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "price": "0.00",
    "currency": "USD",
    "capacity": 80,
    "availableSeats": 42,
    "priceType": "FREE",
    "organizationId": "org-uuid-here",
    "publishedAt": "2026-10-01T09:00:00Z",
    "status": "PUBLISHED",
    "speakers": [
      {
        "id": "spk-uuid-here",
        "name": "Jane Doe",
        "bio": "Senior UX Designer with 10 years experience...",
        "avatarUrl": "https://storage.unihub.io/speakers/jane-doe.jpg",
        "linkedinUrl": "https://linkedin.com/in/janedoe",
        "email": "jane.doe@example.com",
        "sortOrder": 0
      }
    ]
  },
  "metadata": {
    "processedBy": "unihub-app-2",
    "traceId": "9a1b2c3d4e5f6789"
  }
}
```

**Response Field Definitions — `PublicWorkshopDetail`:**

| Field                    | Type             | Description                                                                                                                  |
| :----------------------- | :--------------- | :--------------------------------------------------------------------------------------------------------------------------- |
| `id`                     | UUID string      | Workshop unique identifier.                                                                                                  |
| `slug`                   | string           | URL-safe slug.                                                                                                               |
| `title`                  | string           | Workshop display title.                                                                                                      |
| `description`            | string / null    | Long-form description. Null if absent; serialized via `@JsonInclude(NON_NULL)`.                                              |
| `coverImageUrl`          | string / null    | Cover image URL. Null if absent.                                                                                             |
| `location`               | string           | Physical location.                                                                                                           |
| `startsAt`               | ISO-8601 UTC     | Workshop start time.                                                                                                         |
| `endsAt`                 | ISO-8601 UTC     | Workshop end time.                                                                                                           |
| `timezone`               | string           | IANA timezone identifier.                                                                                                    |
| `price`                  | string (decimal) | Price per seat as a decimal string.                                                                                          |
| `currency`               | string           | ISO 4217 currency code.                                                                                                      |
| `capacity`               | integer          | Maximum number of seats.                                                                                                     |
| `availableSeats`         | integer          | Remaining seats (≥ 0). Resolved from Redis `workshop:{id}:capacity`; falls back to DB formula on cache miss. Never negative. |
| `priceType`              | string           | `"FREE"` or `"PAID"`. Derived.                                                                                               |
| `organizationId`         | UUID string      | Owning organization identifier.                                                                                              |
| `publishedAt`            | ISO-8601 UTC     | Timestamp when the workshop was published.                                                                                   |
| `status`                 | string           | Always `"PUBLISHED"` for workshops visible through this endpoint.                                                            |
| `speakers`               | array            | Ordered list of speakers (by `sortOrder ASC`). May be empty array if no speakers are attached.                               |
| `speakers[].id`          | UUID string      | Speaker unique identifier.                                                                                                   |
| `speakers[].name`        | string           | Speaker display name.                                                                                                        |
| `speakers[].bio`         | string / null    | Speaker biography. Null if absent.                                                                                           |
| `speakers[].avatarUrl`   | string / null    | Speaker portrait URL. Null if absent.                                                                                        |
| `speakers[].linkedinUrl` | string / null    | Speaker LinkedIn profile URL. Null if absent.                                                                                |
| `speakers[].email`       | string           | Speaker email address.                                                                                                       |
| `speakers[].sortOrder`   | integer          | Display ordering index (ascending).                                                                                          |

**Error Responses:**

| Status                      | Error `type`                           | Condition                                                                                                    |
| :-------------------------- | :------------------------------------- | :----------------------------------------------------------------------------------------------------------- |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No workshop exists with the given `identifier` (UUID or slug).                                               |
| `404 Not Found`             | `urn:unihub:error:not-found`           | A workshop exists with the given `identifier` but its `status` is `DRAFT`, `COMPLETED`, or `CANCELED`.       |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Default public Token Bucket rate limit exhausted for this caller. Includes `Retry-After` header.             |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | PostgreSQL Read Replica is unavailable and the detail query cannot be served. Includes `Retry-After` header. |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected server error.                                                                                     |

> [!IMPORTANT]
> The `404 Not Found` response does NOT distinguish between "workshop does not exist" and "workshop exists but is not PUBLISHED". Both conditions produce the same response shape and `type` to prevent enumeration of non-public workshops by external callers.

#### Side Effects

- On Redis cache hit for `availableSeats`: none.
- On Redis cache miss for `availableSeats`: one additional read query to the PostgreSQL Read Replica for the registration count.
- No writes to any datastore.

#### Spec Traceability

- **Satisfies:** FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, FR-23
- **Scenarios:** 11, 12, 13, 14, 15, 16, 20

### Worker Contract: search_sync (RabbitMQ Consumer)

> **Note:** This is not a REST endpoint. It is documented here because it is part of the same feature's network boundary — the worker consumes messages from an external message broker and produces side effects (ES writes, Redis writes) that callers of the two REST endpoints above depend on for data freshness.

**Description:** The `search_sync` worker in `apps/workers/` consumes workshop lifecycle domain events from RabbitMQ queue `q.search` and maintains the Elasticsearch workshop index and Redis availability counters.  
**Queue:** `q.search`  
**Exchange binding:** Workshop lifecycle exchange (configured per ADR-017). The queue receives events published by Core Backend modules via the Spring Modulith Outbox → RabbitMQ flow.  
**Delivery semantics:** At-least-once (manual ACK). All ES and Redis operations must be idempotent.  
**Required Worker Auth:** Worker authenticates to RabbitMQ using the configured service credentials (not a user JWT). No HTTP-level auth applies.

#### Consumed Messages

All messages conform to the domain event envelope used by the Spring Modulith Event Registry → RabbitMQ flow:

```json
{
  "eventId": "uuid-v4",
  "eventType": "WorkshopPublishedEvent",
  "occurredAt": "2026-10-01T09:00:00Z",
  "payload": {}
}
```

**Message: `WorkshopPublishedEvent`**

| Field                    | Type             | Description                                                       |
| :----------------------- | :--------------- | :---------------------------------------------------------------- |
| `payload.workshopId`     | UUID string      | Workshop identifier.                                              |
| `payload.organizationId` | UUID string      | Owning organization.                                              |
| `payload.title`          | string           | Workshop title.                                                   |
| `payload.slug`           | string           | URL slug.                                                         |
| `payload.description`    | string / null    | Workshop description.                                             |
| `payload.coverImageUrl`  | string / null    | Cover image URL.                                                  |
| `payload.location`       | string           | Workshop location.                                                |
| `payload.startsAt`       | ISO-8601 UTC     | Start time.                                                       |
| `payload.endsAt`         | ISO-8601 UTC     | End time.                                                         |
| `payload.timezone`       | string           | IANA timezone.                                                    |
| `payload.price`          | string (decimal) | Price per seat.                                                   |
| `payload.currency`       | string           | ISO 4217 code.                                                    |
| `payload.capacity`       | integer          | Maximum seats.                                                    |
| `payload.publishedAt`    | ISO-8601 UTC     | Publication timestamp.                                            |
| `payload.speakers`       | array            | Speaker list (same structure as `PublicWorkshopDetail.speakers`). |

**Processing on `WorkshopPublishedEvent`:**

1. Upsert the workshop document in the Elasticsearch index (document ID = `workshopId`).
2. Execute `SET workshop:{workshopId}:capacity {capacity}` in Redis — unconditional overwrite (not `INCR`/`DECR`).
3. ACK the message.

**Message: `WorkshopUpdatedEvent`**

| Field                | Type        | Description                                                                                              |
| :------------------- | :---------- | :------------------------------------------------------------------------------------------------------- |
| `payload.workshopId` | UUID string | Workshop identifier.                                                                                     |
| `payload.*`          | various     | All updatable fields (same schema as `WorkshopPublishedEvent.payload`) reflecting the post-update state. |

**Processing on `WorkshopUpdatedEvent`:**

1. Upsert the workshop document in the Elasticsearch index with updated field values.
2. Invalidate all Redis SWR cache keys matching pattern `search:*`.
3. ACK the message.

**Message: `WorkshopRescheduledEvent`**

| Field                | Type         | Description          |
| :------------------- | :----------- | :------------------- |
| `payload.workshopId` | UUID string  | Workshop identifier. |
| `payload.startsAt`   | ISO-8601 UTC | New start time.      |
| `payload.endsAt`     | ISO-8601 UTC | New end time.        |
| `payload.timezone`   | string       | IANA timezone.       |

**Processing on `WorkshopRescheduledEvent`:**

1. Upsert the workshop document in the Elasticsearch index with updated schedule fields.
2. Invalidate all Redis SWR cache keys matching pattern `search:*`.
3. ACK the message.

**Message: `WorkshopCompletedEvent`**

| Field                 | Type         | Description           |
| :-------------------- | :----------- | :-------------------- |
| `payload.workshopId`  | UUID string  | Workshop identifier.  |
| `payload.completedAt` | ISO-8601 UTC | Completion timestamp. |

**Processing on `WorkshopCompletedEvent`:**

1. Delete the workshop document from the Elasticsearch index (by `workshopId`). No-op if document does not exist.
2. Invalidate all Redis SWR cache keys matching pattern `search:*`.
3. ACK the message.

**Message: `WorkshopCanceledEvent`**

| Field                | Type         | Description             |
| :------------------- | :----------- | :---------------------- |
| `payload.workshopId` | UUID string  | Workshop identifier.    |
| `payload.canceledAt` | ISO-8601 UTC | Cancellation timestamp. |

**Processing on `WorkshopCanceledEvent`:**

1. Delete the workshop document from the Elasticsearch index (by `workshopId`). No-op if document does not exist.
2. Invalidate all Redis SWR cache keys matching pattern `search:*`.
3. ACK the message.

**Error Handling (Worker):**

| Condition                                                   | Behavior                                                                                                                                                                                               |
| :---------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Malformed message (unparseable JSON or unknown `eventType`) | NACK without requeue; route to dead-letter queue for manual inspection. Log error with raw message metadata (not raw body) and emit `search_sync.consumer.errors` counter.                             |
| ES write failure (transient)                                | NACK with requeue. Retry governed by RabbitMQ retry policy. Circuit breaker applies if configured.                                                                                                     |
| Redis write failure (transient)                             | Log warning, continue to ACK if ES write succeeded; Redis counter will be resynchronized on the next `WorkshopPublishedEvent` or reconciliation job. Do not fail the message for a Redis-only failure. |
| Duplicate message delivery (same `eventId`)                 | ES upsert and delete are idempotent; Redis `SET` is unconditional. Message is processed and ACKed normally.                                                                                            |

#### Spec Traceability

- **Satisfies:** FR-24, FR-25, FR-26, FR-27
- **Scenarios:** 17, 18, 19

## 4. Rate Limiting Behavior

Both REST endpoints (`GET /api/v1/workshops` and `GET /api/v1/workshops/{identifier}`) are subject to the **default public Token Bucket** rate-limiting tier:

- **Algorithm:** Token Bucket via Bucket4j + Redis.
- **Limit:** 100 tokens per bucket key per 60-second window.
- **Bucket key:** IP address (for unauthenticated requests) or JWT subject + IP (for authenticated requests), as configured in the shared rate-limiting infrastructure.
- **Response on exhaustion:** `429 Too Many Requests` with body per RFC 7807 error envelope (`urn:unihub:error:rate-limited`).
- **Response headers always present:** `X-RateLimit-Limit: 100`, `X-RateLimit-Remaining: <n>`.
- **Response header on exhaustion:** `X-RateLimit-Retry-After: <seconds>` and `Retry-After: <seconds>`.

The `/api/v1/workshops` and `/api/v1/workshops/**` paths must be added to `app.security.public-paths` in `application.yml` — they are not currently listed there.

The `search_sync` worker is not subject to HTTP rate limiting (it communicates over AMQP, not HTTP).

## 5. Idempotency Behavior

Both REST endpoints are safe, idempotent reads. No `Idempotency-Key` header is required or accepted. Duplicate requests produce identical responses (modulo cache state changes between requests).

The `search_sync` worker must ensure ES upserts and deletes are idempotent:

- **Upsert:** ES `index` API with document ID = `workshopId` — always overwrites regardless of prior state.
- **Delete:** ES `delete` API with document ID = `workshopId` — returns success even if the document was already absent.

## 6. Cross-Endpoint Consistency Notes

1. **`PublicWorkshopCard` vs `PublicWorkshopDetail` field overlap:** Both response shapes share the fields `id`, `slug`, `title`, `coverImageUrl`, `location`, `startsAt`, `endsAt`, `timezone`, `price`, `currency`, `capacity`, `availableSeats`, `priceType`, and `organizationId`. Field names and types are intentionally identical between the two shapes so that a client moving from the list view to the detail view observes no naming inconsistency.
2. **`price` as decimal string:** Both endpoints return `price` as a decimal string (e.g., `"0.00"`) rather than a JSON number, to preserve decimal precision and avoid floating-point representation issues. This is consistent with the existing `payments` contract elsewhere in the system.
3. **`availableSeats` non-negativity:** Both endpoints guarantee `availableSeats >= 0`. This is enforced in the application layer (clamp to 0) rather than relying on the database constraint or Redis counter to be in a consistent state at all times.
4. **`speakers` field:** Present only in `PublicWorkshopDetail` (detail endpoint), absent from `PublicWorkshopCard` (listing endpoint). This is intentional — the listing endpoint serves a lightweight card representation for catalog performance; speaker details are loaded only on the detail page.
5. **Null field handling:** Both response shapes apply `@JsonInclude(NON_NULL)` — any field whose value is `null` (e.g., `coverImageUrl`, `description`, `bio`, `avatarUrl`, `linkedinUrl`) is omitted from the serialized JSON payload rather than included as `"fieldName": null`. Clients must treat absent fields as semantically equivalent to null.
6. **Error response `instance` field:** In all `404` responses from `GET /api/v1/workshops/{identifier}`, the RFC 7807 `instance` field is set to the request path (e.g., `/api/v1/workshops/design-sprint-2026`), consistent with the error response examples in `docs/api-standards.md`.
