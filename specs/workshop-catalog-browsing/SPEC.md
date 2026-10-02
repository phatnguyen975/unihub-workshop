# Spec: Workshop Catalog Browsing & Full-Text Search

> **Status:** APPROVED  
> **Domain:** 3 — Workshop Discovery & Search  
> **Owner/area:** `search` module (catalog listing & full-text search), `workshops` module (individual detail), `search_sync` background worker  
> **Last revised:** 2026-10-02 — first version

## 1. Overview

This specification covers the unified public-facing workshop discovery surface for UniHub's Skills and Career Week platform. It merges Feature 3.1 (`workshop-catalog-browsing`) and Feature 3.2 (`workshop-full-text-search`) into a single coordinated implementation cycle because both features share one network endpoint (`GET /api/v1/workshops`), one data source (Elasticsearch CQRS read model + Redis SWR cache), and one background synchronization worker (`search_sync`). The feature enables unauthenticated visitors and authenticated students to browse and search the catalog of `PUBLISHED` workshops via structured filters, keyword search, sorting, and keyset-cursor pagination, and to view complete detail pages for individual workshops. The `search` Spring Modulith module owns the catalog listing and search responsibility; the `workshops` module owns individual detail retrieval from the PostgreSQL Read Replica; the `search_sync` worker in `apps/workers/` maintains the Elasticsearch index from workshop lifecycle events consumed from RabbitMQ.

## 2. Actors & Preconditions

| Actor                                       | Precondition                                                                                                                                                                                                         |
| :------------------------------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Public / Unauthenticated visitor**        | No authentication required. Must supply a valid `X-Correlation-ID` header (client-generated, advisory).                                                                                                              |
| **Authenticated STUDENT**                   | Must hold a valid, unexpired JWT (RS256). Beyond authentication, no additional precondition — the response shape is identical to the unauthenticated case for these read-only endpoints.                             |
| **ORGANIZER / STAFF (incidental)**          | These roles may call the public endpoints but receive no additional data or access beyond what the public sees; these endpoints have no authorization gate.                                                          |
| **`search_sync` Worker (background actor)** | RabbitMQ broker is reachable; queue `q.search` exists and is bound to the exchange configured by ADR-017. The worker has write access to the Elasticsearch index and write access to Redis (for cache invalidation). |

## 3. Functional Requirements

### 3.1 Catalog Listing & Full-Text Search (`GET /api/v1/workshops`)

- **FR-01:** The system SHALL return a paginated list of workshops in `PUBLISHED` status when a public request is made to browse the catalog, regardless of whether a keyword query is supplied.
- **FR-02:** The system SHALL execute all catalog and search queries exclusively against the Elasticsearch CQRS read model, applying structured filters, sorting, and keyset-cursor pagination within a single ES Boolean Query (no PostgreSQL fallback path for catalog listing).
- **FR-03:** The system SHALL, when a keyword parameter `q` is present, execute an ES multi-match query across the fields `title`, `description`, `location`, `speakers.name`, and `slug`, applying fuzzy matching and field boosting, combined with any active structured filter clauses and sorting.
- **FR-04:** The system SHALL, when no keyword parameter `q` is present, execute an ES Boolean Query with only structured filter clauses (equivalent to `match_all` + filters) — no `must` clause.
- **FR-05:** The system SHALL support structured filter parameters: `priceType` (values: `FREE`, `PAID`), `from` (ISO-8601 UTC timestamp), and `to` (ISO-8601 UTC timestamp). Each temporal parameter SHALL be usable independently as a one-sided bound (`startsAt >= from` or `startsAt <= to`). The system SHALL validate that `from` and `to`, when supplied, are parseable ISO-8601 UTC timestamps, and when both are supplied, SHALL validate that `from < to`, rejecting invalid timestamp formats or requests where `from >= to` with `422 Unprocessable Content`.
- **FR-06:** The system SHALL support sorting by the following sort keys: `startsAt,asc` (default), `startsAt,desc`, `price,asc`, `price,desc`, `capacity,asc`, `capacity,desc`.
- **FR-07:** The system SHALL enforce keyset-cursor pagination using `cursor` and `size` parameters: default page size is 20, maximum page size is 100. Exceeding the maximum results in a validation error.
- **FR-08:** The system SHALL reject a cursor whose encoded `sortField` does not match the `sort` parameter of the current request with a `400 Bad Request` response.
- **FR-09:** The system SHALL apply the SWR (Stale-While-Revalidate) Redis cache-aside pattern using cache key format `search:q:{q}:{filters}:{sort}:{cursor}:{size}` with a soft TTL of approximately 5 minutes and a hard TTL of approximately 24 hours.
- **FR-10:** The system SHALL, on a stale cache hit (past soft TTL but within hard TTL), return the stale cached response immediately to the caller and concurrently attempt to acquire a Redis non-blocking `tryLock`; the lock holder performs an async background refresh of the ES query and updates the cache entry; non-lock-holders perform no additional work.
- **FR-11:** The system SHALL, on a cache miss or hard TTL expiry, perform a synchronous ES query wrapped in a Resilience4j Circuit Breaker and cache the result.
- **FR-12:** The system SHALL, when the Resilience4j Circuit Breaker is OPEN or the ES query fails, return a `200 OK` response containing an empty item list and `hasMore: false` — never a `5xx` error — to ensure graceful degradation.
- **FR-13:** The system SHALL include, for each workshop item in the listing response, the following fields: `id`, `slug`, `title`, `coverImageUrl`, `location`, `startsAt`, `endsAt`, `timezone`, `price`, `currency`, `capacity`, `availableSeats`, `priceType` (derived: `FREE` when `price == 0`, `PAID` otherwise), and `organizationId`.
- **FR-14:** The system SHALL resolve `availableSeats` for all items in a page using a Redis batch `MGET` over keys `workshop:{id}:capacity`; for any item whose Redis key is absent (cache miss), the system SHALL fall back to a database-derived count using the formula `capacity - COUNT(registrations WHERE status IN ('CONFIRMED', 'PENDING'))`.
- **FR-15:** The system SHALL apply rate limiting to all callers of this endpoint using the default public bucket tier (Bucket4j Token Bucket via Redis, as configured in the shared rate-limiting infrastructure).

### 3.2 Individual Workshop Detail (`GET /api/v1/workshops/{identifier}`)

- **FR-16:** The system SHALL return the full detail of a single workshop when the `identifier` path parameter matches either the workshop's UUID `id` or its URL `slug`.
- **FR-17:** The system SHALL transparently detect whether `identifier` is a UUID (matches UUID format) or a slug (does not match UUID format) and execute the appropriate lookup without requiring the caller to specify the identifier type.
- **FR-18:** The system SHALL return `404 Not Found` when no workshop matches the `identifier`, regardless of whether the identifier is a UUID or slug.
- **FR-19:** The system SHALL return `404 Not Found` when a workshop matching the `identifier` exists but its `status` is not `PUBLISHED` (i.e., it is `DRAFT`, `COMPLETED`, or `CANCELED`).
- **FR-20:** The system SHALL execute the detail query against the PostgreSQL Read Replica under eventual consistency semantics (ADR-015).
- **FR-21:** The system SHALL include in the detail response: `id`, `slug`, `title`, `description`, `coverImageUrl`, `location`, `startsAt`, `endsAt`, `timezone`, `price`, `currency`, `capacity`, `availableSeats`, `priceType`, `organizationId`, `publishedAt`, `status` (always `PUBLISHED` for visible workshops), and the full list of `speakers` (each with `id`, `name`, `bio`, `avatarUrl`, `linkedinUrl`, `email`, `sortOrder`).
- **FR-22:** The system SHALL resolve `availableSeats` for the detail view using a single Redis `GET` for key `workshop:{id}:capacity`; on Redis cache miss, the system SHALL fall back to the formula `capacity - COUNT(registrations WHERE status IN ('CONFIRMED', 'PENDING'))` queried from the PostgreSQL Read Replica.
- **FR-23:** The system SHALL apply rate limiting to all callers of this endpoint using the same default public bucket tier as FR-15.

### 3.3 Background Worker — `search_sync`

- **FR-24:** The `search_sync` worker SHALL consume the following RabbitMQ messages from queue `q.search` and maintain the Elasticsearch workshop index accordingly:
  - `WorkshopPublishedEvent` (carrying workshop details, schedule, pricing, capacity, and speaker list) → upsert the workshop document (including embedded speakers) into the ES index; initialize the Redis availability counter `workshop:{id}:capacity` to the workshop's `capacity` value.
  - `WorkshopUpdatedEvent` → upsert the workshop document in the ES index with updated fields; invalidate all Redis SWR cache keys matching the `search:*` prefix pattern.
  - `WorkshopRescheduledEvent` → upsert the workshop document in the ES index with the new schedule fields; invalidate all Redis SWR cache keys matching the `search:*` prefix pattern.
  - `WorkshopCompletedEvent` → remove the workshop document from the ES index; invalidate all Redis SWR cache keys matching the `search:*` prefix pattern.
  - `WorkshopCanceledEvent` → remove the workshop document from the ES index; invalidate all Redis SWR cache keys matching the `search:*` prefix pattern.
- **FR-25:** The `search_sync` worker SHALL process each message with at-least-once delivery semantics (RabbitMQ manual ACK); duplicate deliveries of the same event SHALL be safe due to idempotent upsert/delete operations on the Elasticsearch index.
- **FR-26:** The `search_sync` worker SHALL, upon receiving `WorkshopPublishedEvent`, set the Redis key `workshop:{id}:capacity` using an unconditional `SET` (not `INCR`/`DECR`) with the authoritative value from the event payload, so that any prior stale counter is overwritten.
- **FR-27:** The system SHALL instrument the `search_sync` worker with Micrometer metrics for: messages consumed per event type, ES upsert/delete latency, Redis cache invalidation count per event, and consumer error rate.

### 3.4 Telemetry (Core Backend `search` module)

- **FR-28:** The system SHALL instrument the SWR cache-aside path with Micrometer counters/gauges for: cache hit (fresh), cache hit (stale), cache miss, lock acquisition success (lock holder), lock acquisition skip (non-holder), ES query latency histogram, and circuit breaker state transition events.

## 4. Business Rules

- **BR-01:** Only workshops with `status = 'PUBLISHED'` are visible through the public catalog endpoints. Workshops with `status` of `DRAFT`, `COMPLETED`, or `CANCELED` MUST NOT appear in search results or detail responses. _(Source: docs/feature-catalog.md §3.1, docs/system-design.md §4)_
- **BR-02:** The catalog listing and full-text search endpoint always routes to Elasticsearch — there is no conditional routing to PostgreSQL regardless of whether `q` is present. _(Architectural decision confirmed: 1A)_
- **BR-03:** The SWR cache key encodes all query-defining parameters: `search:q:{q}:{filters}:{sort}:{cursor}:{size}`. Different parameter combinations MUST map to different cache keys. An absent `q` parameter MUST be represented consistently (e.g., as empty string or a fixed sentinel) to avoid cache key collision. _(Source: docs/feature-catalog.md §3.2)_
- **BR-04:** On a stale SWR cache hit, only the caller that successfully acquires the Redis `tryLock` may trigger an async ES refresh. All other concurrent callers MUST return the stale cached response immediately without issuing an ES query. _(Source: ADR-016)_
- **BR-05:** On Resilience4j Circuit Breaker OPEN or ES failure, the catalog endpoint MUST return `200 OK` with an empty result set — NOT `503 Service Unavailable`. This ensures the public-facing page degrades gracefully rather than surfacing an error state. _(Source: docs/feature-catalog.md §3.2, ADR-020)_
- **BR-06:** The `availableSeats` field in both listing and detail responses MUST preferentially use the Redis atomic counter `workshop:{id}:capacity`. A DB fallback is only acceptable when the Redis key is absent (cache miss), never as a primary query path. _(Architectural decision confirmed: Decision 5)_
- **BR-07:** The keyset cursor carries an encoded `sortField`. If the caller passes a cursor issued for `sort=startsAt,asc` but the current request uses `sort=price,asc`, the system MUST reject the request with `400 Bad Request` and a descriptive RFC 7807 detail message. _(Source: docs/api-standards.md §Pagination)_
- **BR-08:** `priceType` is a derived, read-only field computed at query time as: `FREE` when `price == 0`, `PAID` when `price > 0`. It is not persisted in the database. _(Source: docs/database-schema.md `workshops` table — `price` column)_
- **BR-09:** The detail lookup endpoint resolves `identifier` by attempting UUID format detection first; if the identifier is a valid UUID, a lookup by `id` is performed; otherwise, a lookup by `slug` is performed. The two code paths produce the same `404` response shape for a non-existent or non-published result. _(Architectural decision confirmed: Decision 4)_
- **BR-10:** `availableSeats` MUST NOT be negative in any response. If the DB fallback formula yields a negative result (e.g., due to data inconsistency from a concurrent cancellation), the system SHALL clamp the returned value to `0`. _(Source: docs/system-design.md §6 — Overbooking Prevention)_
- **BR-11:** The `search_sync` worker's Redis `SET` for `workshop:{id}:capacity` on `WorkshopPublishedEvent` is unconditional — it overwrites any previous value. This makes the DB the authoritative source; Redis is always re-seeded from the event rather than incremented. _(Source: ADR-013)_
- **BR-12:** Cache invalidation triggered by `WorkshopUpdatedEvent`, `WorkshopRescheduledEvent`, `WorkshopCompletedEvent`, and `WorkshopCanceledEvent` MUST invalidate the Redis SWR cache to prevent stale data persisting beyond the hard TTL. Pattern-based invalidation (`search:*`) is the correct scope. _(Source: docs/feature-catalog.md §3.2)_
- **BR-13:** The `size` parameter is capped at 100. Requests with `size > 100` MUST be rejected with `422 Unprocessable Content`. _(Source: docs/api-standards.md §Pagination)_
- **BR-14:** Speakers in the detail response MUST be ordered by `sort_order ASC` to ensure consistent display ordering across clients. _(Source: docs/database-schema.md `workshop_speakers` table)_
- **BR-15:** Temporal filters `from` and `to` operate independently as lower (`startsAt >= from`) and upper (`startsAt <= to`) bounds. Each supplied timestamp MUST be a valid ISO-8601 UTC format. When both `from` and `to` are present, they MUST satisfy `from < to`. Any request where `from` or `to` fails timestamp parsing, or where `from >= to`, MUST be rejected with `422 Unprocessable Content` (`urn:unihub:error:validation`). _(Confirmed by human reviewer)_

## 5. State Transitions

This feature is read-only from the perspective of workshop state — it does not perform any state transition on any entity. However, the visibility rules enforced by this feature depend on the workshop lifecycle state machine managed by other features. The relevant states and their visibility in this feature are:

| Workshop Status | Visible in `GET /api/v1/workshops` | Visible in `GET /api/v1/workshops/{identifier}` | Rejection Behavior                                                     |
| :-------------- | :--------------------------------: | :---------------------------------------------: | :--------------------------------------------------------------------- |
| `DRAFT`         |                 No                 |                       No                        | Item excluded from ES index; detail returns `404 Not Found`            |
| `PUBLISHED`     |                Yes                 |                       Yes                       | Returned normally                                                      |
| `COMPLETED`     |                 No                 |                       No                        | Removed from ES index by `search_sync`; detail returns `404 Not Found` |
| `CANCELED`      |                 No                 |                       No                        | Removed from ES index by `search_sync`; detail returns `404 Not Found` |

The `search_sync` worker transitions the Elasticsearch index and Redis counters in response to domain events, but it does not alter the `status` column in PostgreSQL. State transitions on the `workshops` table are the responsibility of Features 2.x and 2.x (lifecycle management).

## 6. Non-Functional Requirements

- **Performance:**
  - Redis SWR cache fresh-hit response time: ≤ 5 ms (target P99 at the application layer, excluding network).
  - ES query response time (cache miss path, circuit breaker CLOSED): ≤ 200 ms P99.
  - PostgreSQL Read Replica detail query latency: ≤ 50 ms P99.
  - Redis batch `MGET` for `availableSeats` on a full page of 20–100 items: single round-trip; ≤ 10 ms P99.
- **Concurrency:**
  - The system must handle 12,000+ concurrent users making catalog and search requests within a 10-minute registration window (peak 60% within first 3 minutes) without data corruption, duplicate ES queries, or cache stampede. The non-blocking `tryLock` pattern on SWR stale hits is the primary guard against cache stampede.
  - The `search_sync` worker must process RabbitMQ messages with at-least-once delivery semantics; ES upsert and delete operations must be idempotent.
- **Idempotency:**
  - `GET /api/v1/workshops` and `GET /api/v1/workshops/{identifier}` are safe, idempotent reads — no idempotency key mechanism is required.
  - `search_sync` ES writes must be idempotent (upsert by document ID; delete is a no-op if document not found).
- **Rate Limiting:**
  - Both public REST endpoints are subject to the default Token Bucket rate limit tier (100 tokens, 60-second refill window, via Bucket4j + Redis). On exhaustion, the response is `429 Too Many Requests` with `Retry-After` header. No per-user authentication is required for rate limiting on public paths — the limit is applied per IP or per token bucket key as configured in the shared rate-limiting infrastructure.
- **Security:**
  - Both `GET /api/v1/workshops` and `GET /api/v1/workshops/{identifier}` are fully public (`permitAll` — no authentication gate).
  - No authenticated context is exposed in error messages or response metadata beyond what is stated in this spec.
  - All query parameters (e.g., `q`, `cursor`) are treated as untrusted external input; they must be validated and sanitized before use in ES queries to prevent injection. ES query construction must use the ES client's structured query DSL — never raw string interpolation.
  - The `cursor` parameter is a Base64-encoded opaque token; decoding failures must be rejected with `400 Bad Request`, not logged with raw content.
- **Reliability / Failure Modes:**
  - If Elasticsearch is unavailable or the circuit breaker is OPEN: `200 OK` with empty item list.
  - If Redis is unavailable during SWR lookup: proceed to synchronous ES query (skip cache layer).
  - If Redis `tryLock` acquisition fails: return stale cached result without background refresh (non-blocking, never blocks the response thread).
  - If the PostgreSQL Read Replica is unavailable during detail lookup: return `503 Service Unavailable` (detail endpoint does not have a graceful degradation target as meaningful data requires the DB).
  - `search_sync` consumer failures: message is nacked and requeued per RabbitMQ dead-letter configuration; the worker must not crash the entire worker process on a single malformed message.
- **Observability:**
  - SWR metrics: `search.cache.hit` (tags: `type=fresh|stale`), `search.cache.miss`, `search.cache.lock.acquired`, `search.cache.lock.skipped`.
  - ES query: `search.es.query.duration` (histogram), `search.es.circuit_breaker.state` (gauge).
  - `search_sync` worker: `search_sync.messages.consumed` (tags: `eventType`), `search_sync.es.write.duration` (histogram), `search_sync.cache.invalidated` (counter), `search_sync.consumer.errors` (counter).
  - All metrics emitted via Micrometer to Prometheus → Grafana.

## 7. Acceptance Criteria

### Scenario 1: Public catalog browse — no filters, no keyword

```gherkin
Given the public catalog endpoint is accessible with no authentication
And at least 3 workshops with status PUBLISHED exist in the Elasticsearch index
When a GET request is made to the catalog endpoint with no query parameters
Then the response status is 200 OK
And the response body follows the standard success envelope
And the `data.items` array contains only workshops with status PUBLISHED
And each item includes id, slug, title, coverImageUrl, location, startsAt, endsAt, timezone, price, currency, capacity, availableSeats, priceType, and organizationId
And `data.cursor.hasMore` reflects whether additional pages exist
And results are sorted by startsAt ascending (default sort)
```

_(Satisfies FR-01, FR-02, FR-04, FR-06, FR-13)_

### Scenario 2: Full-text search with keyword `q`

```gherkin
Given workshops with status PUBLISHED exist in the Elasticsearch index
And one workshop has title "Machine Learning Bootcamp"
When a GET request is made to the catalog endpoint with q="machine learning"
Then the response status is 200 OK
And the `data.items` array includes the workshop with title "Machine Learning Bootcamp"
And no workshops with status DRAFT, COMPLETED, or CANCELED appear in the result
```

_(Satisfies FR-01, FR-03, BR-01)_

### Scenario 3: Filter by `priceType=FREE`

```gherkin
Given published workshops exist with various prices (some free, some paid)
When a GET request is made to the catalog endpoint with priceType=FREE
Then the response status is 200 OK
And all returned items have price = 0 and priceType = FREE
And no PAID workshop appears in the result
```

_(Satisfies FR-01, FR-05, FR-13, BR-08)_

### Scenario 4: Filter by `from` and `to` date range

```gherkin
Given published workshops exist with startsAt spanning multiple dates
When a GET request is made to the catalog endpoint with from=2026-11-01T00:00:00Z and to=2026-11-30T23:59:59Z
Then the response status is 200 OK
And all returned items have startsAt within the specified date range
```

_(Satisfies FR-01, FR-05, BR-15)_

### Scenario 5: Pagination — first page and next page via cursor

```gherkin
Given at least 25 published workshops exist in the ES index
And the default page size is 20
When a GET request is made with size=20 and no cursor
Then the response returns 20 items
And data.cursor.hasMore is true
And data.cursor.nextCursor is a non-null opaque string
When a subsequent GET request is made with cursor=<nextCursor> and size=20
Then the response returns the next page of items without duplication
```

_(Satisfies FR-07, FR-09)_

### Scenario 6: Cursor sort field mismatch is rejected

```gherkin
Given a cursor was issued for a request with sort=startsAt,asc
When a GET request is made using that cursor with sort=price,asc
Then the response status is 400 Bad Request
And the RFC 7807 error type is urn:unihub:error:bad-request
And the detail message describes the cursor sort field mismatch
```

_(Satisfies FR-08, BR-07)_

### Scenario 7: size exceeding maximum is rejected

```gherkin
When a GET request is made to the catalog endpoint with size=101
Then the response status is 422 Unprocessable Content
And the RFC 7807 error type is urn:unihub:error:validation
```

_(Satisfies FR-07, BR-13)_

### Scenario 8: SWR fresh cache hit — no ES query

```gherkin
Given a previous identical request was made within the soft TTL window
And the result is cached in Redis
When the same request is made again
Then the response status is 200 OK
And the response is served from cache without querying Elasticsearch
```

_(Satisfies FR-09, FR-10)_

### Scenario 9: SWR stale cache hit — immediate stale return + async refresh

```gherkin
Given a previous identical request result is in Redis past the soft TTL but within the hard TTL
When the same request is made again
Then the response is returned immediately with the stale cached result
And a background async process is triggered to refresh the cache
And the caller is not blocked waiting for the ES query to complete
```

_(Satisfies FR-10, BR-04)_

### Scenario 10: Elasticsearch circuit breaker OPEN — graceful degradation

```gherkin
Given the Elasticsearch circuit breaker is OPEN (ES unavailable)
And no cache entry exists for the requested query
When a GET request is made to the catalog endpoint
Then the response status is 200 OK
And the data.items array is empty
And data.cursor.hasMore is false
And no 5xx error is returned
```

_(Satisfies FR-12, BR-05)_

### Scenario 11: Individual detail by UUID — published workshop

```gherkin
Given a published workshop exists with id "550e8400-e29b-41d4-a716-446655440000" and slug "design-sprint-2026"
When a GET request is made to the detail endpoint with identifier "550e8400-e29b-41d4-a716-446655440000"
Then the response status is 200 OK
And the response body includes id, slug, title, description, coverImageUrl, location, startsAt, endsAt, timezone, price, currency, capacity, availableSeats, priceType, organizationId, publishedAt, status, and speakers
And each speaker object includes id, name, bio, avatarUrl, linkedinUrl, email, sortOrder
And speakers are ordered by sortOrder ascending
```

_(Satisfies FR-16, FR-17, FR-21, BR-14)_

### Scenario 12: Individual detail by slug — published workshop

```gherkin
Given a published workshop exists with slug "design-sprint-2026"
When a GET request is made to the detail endpoint with identifier "design-sprint-2026"
Then the response status is 200 OK
And the workshop detail matches the workshop identified by that slug
```

_(Satisfies FR-16, FR-17)_

### Scenario 13: Detail — non-existent identifier

```gherkin
When a GET request is made to the detail endpoint with an identifier that does not match any workshop
Then the response status is 404 Not Found
And the RFC 7807 error type is urn:unihub:error:not-found
```

_(Satisfies FR-18)_

### Scenario 14: Detail — DRAFT workshop returns 404

```gherkin
Given a workshop exists with status DRAFT and a known UUID
When a GET request is made to the detail endpoint using that UUID
Then the response status is 404 Not Found
And the RFC 7807 error type is urn:unihub:error:not-found
```

_(Satisfies FR-19, BR-01)_

### Scenario 15: Detail — availableSeats uses Redis counter, falls back to DB

```gherkin
Given a published workshop exists with id "abc-123"
And the Redis key workshop:abc-123:capacity has value 42
When a GET request is made to the detail endpoint for that workshop
Then the response includes availableSeats = 42
And no database query is issued for the availableSeats value
```

_(Satisfies FR-22, BR-06)_

```gherkin
Given a published workshop exists with id "def-456"
And the Redis key workshop:def-456:capacity does not exist
And the workshop has capacity 100 and 73 registrations in CONFIRMED or PENDING status
When a GET request is made to the detail endpoint for that workshop
Then the response includes availableSeats = 27
```

_(Satisfies FR-22, BR-06)_

### Scenario 16: availableSeats clamped to zero on negative DB result

```gherkin
Given a workshop has capacity 10 and 12 CONFIRMED registrations (inconsistency condition)
And the Redis key is absent
When the detail or listing endpoint resolves availableSeats via DB fallback
Then the response contains availableSeats = 0
And availableSeats is never returned as a negative value
```

_(Satisfies FR-14, FR-22, BR-10)_

### Scenario 17: search_sync — WorkshopPublishedEvent upserts ES and seeds Redis

```gherkin
Given a WorkshopPublishedEvent is published to the RabbitMQ queue q.search
When the search_sync worker consumes the event
Then the workshop document is upserted in the Elasticsearch index
And the Redis key workshop:{id}:capacity is SET to the workshop's capacity value unconditionally
```

_(Satisfies FR-24, FR-26, BR-11)_

### Scenario 18: search_sync — WorkshopCanceledEvent removes from ES and invalidates cache

```gherkin
Given a WorkshopCanceledEvent is published to the RabbitMQ queue q.search
When the search_sync worker consumes the event
Then the workshop document is deleted from the Elasticsearch index
And all Redis SWR cache keys matching the pattern search:* are invalidated
```

_(Satisfies FR-24, BR-12)_

### Scenario 19: search_sync — duplicate event delivery is idempotent

```gherkin
Given a WorkshopPublishedEvent for workshop id "xyz-789" is delivered twice to the worker
When the search_sync worker processes both deliveries
Then the Elasticsearch index contains exactly one document for workshop id "xyz-789"
And no error or inconsistency results from the duplicate delivery
```

_(Satisfies FR-25)_

### Scenario 20: Rate limit exceeded

```gherkin
Given the default public rate limit bucket is exhausted for the caller's bucket key
When a GET request is made to either catalog or detail endpoint
Then the response status is 429 Too Many Requests
And the response includes X-RateLimit-Limit, X-RateLimit-Remaining, and Retry-After headers
```

_(Satisfies FR-15, FR-23)_

### Scenario 21: One-sided temporal filter (`from` only or `to` only)

```gherkin
Given published workshops exist with startsAt spanning past, current, and future dates
When a GET request is made to the catalog endpoint with only from=2026-11-01T00:00:00Z
Then the response status is 200 OK
And all returned items have startsAt greater than or equal to 2026-11-01T00:00:00Z
When a separate GET request is made with only to=2026-11-30T23:59:59Z
Then the response status is 200 OK
And all returned items have startsAt less than or equal to 2026-11-30T23:59:59Z
```

_(Satisfies FR-01, FR-05, BR-15)_

### Scenario 22: Temporal filter rejection when `from >= to`

```gherkin
When a GET request is made to the catalog endpoint with from=2026-12-01T00:00:00Z and to=2026-11-01T00:00:00Z
Then the response status is 422 Unprocessable Content
And the RFC 7807 error type is urn:unihub:error:validation
And the detail message indicates that 'from' must be strictly earlier than 'to'
```

_(Satisfies FR-05, BR-15)_

### Scenario 23: Temporal filter rejection on malformed timestamp

```gherkin
When a GET request is made to the catalog endpoint with from="not-a-date"
Then the response status is 422 Unprocessable Content
And the RFC 7807 error type is urn:unihub:error:validation
And the detail message indicates an unparseable ISO-8601 UTC timestamp format
```

_(Satisfies FR-05, BR-15)_

## 8. Out of Scope

The following capabilities are explicitly excluded from this feature and must not be implemented as part of this cycle:

1. **Admin / Organizer-facing workshop detail (`GET /api/v1/admin/workshops/{id}`)** — served from Feature 2.1, which allows viewing any status and uses the primary DB.
2. **Workshop registration** — any registration action (free or paid) is the responsibility of Features 4.1 and 4.2; these endpoints are read-only.
3. **Workshop creation, update, or cancellation** — lifecycle management is the responsibility of Features 2.x; these endpoints are read-only.
4. **Authenticated-only workshop catalog** — no additional data is returned for authenticated users; authentication is optional and changes nothing in the response.
5. **Staff assignment lookup or check-in initiation** — those are Features 6.x.
6. **Real-time seat count updates via SSE** — the SSE `registration.updated` event is owned by the `registrations` module (Feature 4.x); this spec only covers the REST snapshot value of `availableSeats`.
7. **AI-generated workshop summaries** — Feature 2.5.
8. **Workshop brochure/PDF retrieval** — out of scope for this feature.
9. **Mobile offline sync of workshop catalog** — mobile SQLite sync is Feature 7.x.
10. **Elasticsearch index schema migration or initial index creation** — initial index creation is infrastructure setup; this feature assumes the index exists and is accessible.
11. **CSV bulk workshop ingestion** — Feature 2.6.
12. **ORGANIZER-scoped search** — no `organization_id` filter is available on the public catalog; the catalog shows all PUBLISHED workshops across all organizations.
13. **Sorting by relevance score** — relevance sorting (ES `_score`) is not offered as a named sort option; only the six deterministic sort keys listed in FR-06 are supported.

## 9. Linked References

| Document                    | Sections Consulted                                                                                                                                                                                                                       |
| :-------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | §3 Workshop Discovery & Search (3.1 and 3.2), §Appendix A: Feature Dependency Map                                                                                                                                                        |
| `docs/system-design.md`     | §4 Workshop Search & Cache-Aside Flow (SWR sequence diagram), §6 Overbooking Prevention                                                                                                                                                  |
| `docs/adrs.md`              | ADR-012 (Elasticsearch CQRS), ADR-013 (Redis capacity counter), ADR-015 (Read Replica routing), ADR-016 (SWR dual TTL + tryLock), ADR-017 (RabbitMQ event-driven sync), ADR-018 (Outbox pattern), ADR-020 (Resilience4j circuit breaker) |
| `docs/database-schema.md`   | `workshops` table (columns, indexes), `workshop_speakers` table (columns, sort_order)                                                                                                                                                    |
| `docs/api-standards.md`     | §Pagination (keyset cursor format), §Sorting, §HTTP Status Codes, §Success/Error envelope, §Rate limiting headers                                                                                                                        |
| `docs/project-structure.md` | §Backend Clean Architecture layout, §Workers Hexagonal Architecture layout, §Inter-module communication patterns                                                                                                                         |
