# Spec: Real-Time Event Streaming Hub

> **Status:** APPROVED  
> **Domain:** 10 — System Reliability & Infrastructure  
> **Owner/area:** `shared/realtime` (Core Backend Monolith)  
> **Last revised:** 2026-09-09 — first version

## 1. Overview

The **Real-Time Event Streaming Hub** provides the centralized, cross-cutting infrastructure for distributing asynchronous, unidirectional real-time updates from the UniHub backend monolith and background workers to web and mobile clients using **Server-Sent Events (SSE)** and **Redis Pub/Sub** (ADR-031). It exposes a single, authenticated, long-lived HTTP stream endpoint (`GET /api/v1/stream/events`) multiplexing all platform events (notifications, AI processing milestones, live attendance numbers, registration counters, and payment confirmations). To support stateless horizontal scaling across multiple backend nodes behind Nginx, it subscribes each backend instance to transient Redis Pub/Sub channels (`unihub:stream:user:{userId}` and `unihub:stream:workshop:{workshopId}`), enabling seamless cross-node event delivery. It manages connection lifecycles thread-safely, emits a 15-second heartbeat to prevent idle proxy timeouts, and ensures zero resource leaks upon client disconnects.

## 2. Actors & Preconditions

| Actor                          | Precondition                                                                                                                                                        |
| :----------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `STUDENT`                      | Authenticated with a valid RS256 JWT access token (`is_active = true`), connecting from Web or Mobile client.                                                       |
| `ORGANIZER`                    | Authenticated with a valid RS256 JWT access token scoped to `organization_id` (`is_active = true`), connecting from Web client.                                     |
| `STAFF`                        | Authenticated with a valid RS256 JWT access token (`is_active = true`), connecting from Web or Mobile client.                                                       |
| Background Workers (`workers`) | Internal Spring Batch / Hexagonal worker processes with network access to the shared Redis instance.                                                                |
| Internal Monolith Modules      | Any backend Spring Modulith module (`iam`, `workshops`, `registrations`, `payments`, `notifications`, `attendances`) executing within the core application runtime. |

## 3. Functional Requirements

### Connection Handshake & Authentication

- **FR-01:** The system SHALL expose an HTTP streaming endpoint at `GET /api/v1/stream/events` that returns `Content-Type: text/event-stream; charset=UTF-8`.
- **FR-02:** The system SHALL require valid HTTP Bearer token authentication via the `Authorization: Bearer <jwt>` header on the stream handshake request.
- **FR-03:** The system SHALL reject handshake requests with `401 Unauthorized` (RFC 7807 problem detail) if the JWT is missing, expired, cryptographically invalid, or if its `jti` claim is present in the Redis blacklist.
- **FR-04:** The system SHALL reject handshake requests with `403 Forbidden` (RFC 7807 problem detail) if the authenticated user account has `is_active = false`.
- **FR-05:** The system SHALL apply token-bucket rate limiting on the handshake endpoint via a dedicated `streamTier` (`capacity: 10`, `refill-tokens: 10`, `refill-duration: 60s`, pattern `/api/v1/stream/**`) to protect against connection flood attacks, returning `429 Too Many Requests` (RFC 7807 problem detail with type `urn:unihub:error:rate-limited` wrapped in the standard envelope) if the client exceeds the quota.
- **FR-06:** The system SHALL emit response headers `Cache-Control: no-cache, no-transform`, `Connection: keep-alive`, and `X-Accel-Buffering: no` on the handshake response to prevent intermediate buffering by reverse proxies.

### Connection Registry & Lifecycle Management

- **FR-07:** The system SHALL maintain active client connections in a thread-safe in-memory registry (`SseEmitterRegistry`), indexing emitters by `userId` and associated active subscriptions.
- **FR-08:** The system SHALL support multiple concurrent connections for the same user account (e.g., multiple browser tabs or concurrent Web and Mobile logins).
- **FR-09:** The system SHALL register `onCompletion`, `onTimeout`, and `onError` lifecycle callbacks on every active connection emitter to evict the emitter from memory immediately upon socket termination.
- **FR-10:** The system SHALL schedule and broadcast a periodic comment `: ping\n\n` to all active client connections every 15 seconds to prevent NAT, firewall, and reverse proxy idle socket termination.

### Multi-Node Event Distribution via Redis Pub/Sub

- **FR-11:** The system SHALL expose a programmatic publisher port (`RealtimeEventPublisher`) that allows internal modules and worker processes to publish typed events targeting a specific user (`unihub:stream:user:{userId}`) or a specific workshop (`unihub:stream:workshop:{workshopId}`).
- **FR-12:** The system SHALL configure a Redis message listener (`RedisRealtimeSubscriber`) on every backend instance listening to the topic patterns `unihub:stream:user:*` and `unihub:stream:workshop:*`.
- **FR-13:** When an event is received from Redis Pub/Sub, the backend instance holding the target user's or workshop's active SSE connection(s) SHALL serialize the event into the SSE wire envelope format and write it to the client socket.
- **FR-14:** If a backend instance receives a Redis Pub/Sub message for a user or workshop with no active connections on that instance, it SHALL discard the message safely without error.

### Wire Format & Event Multiplexing

- **FR-15:** The system SHALL format all pushed events using standard SSE field syntax:
  - `id: <uuid>` (unique identifier for client-side deduplication)
  - `event: <event_name>` (the catalog event type)
  - `data: <json_string>` (the serialized JSON payload conforming to the event contract)
  - `retry: 3000` (reconnection interval in milliseconds)
  - Followed by a trailing blank line delimiter (`\n\n`).
- **FR-16:** The system SHALL multiplex all supported platform event types over this single stream:
  - `notification.new`
  - `notification.read`
  - `ai.summary.completed`
  - `ai.summary.failed`
  - `attendance.updated`
  - `registration.updated`
  - `payment.confirmed`

### Fallback & Resilience

- **FR-17:** The system SHALL allow clients that fail to establish or maintain an SSE connection to fallback gracefully to existing REST polling endpoints (`GET .../notifications`, `GET .../brochure/summary-job`) without server-side error.
- **FR-18:** If the Redis service becomes temporarily unavailable, the system SHALL handle Pub/Sub errors gracefully, logging the failure and preventing backend crash or connection leaks.

## 4. Business Rules

- **BR-01 (Single Stream Multiplexing):** A client application SHALL open only one persistent SSE connection per user session; all real-time events across all domains MUST be multiplexed through `GET /api/v1/stream/events`.
- **BR-02 (Fail-Closed Token Verification):** Handshake authentication MUST verify the JWT `jti` claim against the Redis blacklist in fail-closed mode per ADR-010; if Redis is unreachable during handshake, the handshake returns `503 Service Unavailable`.
- **BR-03 (Heartbeat Cadence):** The keep-alive heartbeat ping (`: ping\n\n`) MUST be emitted at a fixed interval of 15 seconds ($\pm 1$ second) across all active connections.
- **BR-04 (Statelessness of Streaming Layer):** No streaming connection state, active socket handle, or historical event log SHALL be stored in PostgreSQL. Historical business entities (such as notifications or attendance records) are persisted by their respective domain tables, whereas SSE delivery is transient.
- **BR-05 (Prompt Connection Eviction):** Any connection that times out, encounters an I/O exception (`ClientAbortException`, broken pipe), or completes normally MUST be deregistered within $\le 500$ milliseconds of the callback firing.
- **BR-06 (Catalog Schema Adherence):** Every event emitted by `RealtimeEventPublisher` MUST strictly match the field names and JSON types specified in the Unified Event Catalog in `docs/api-standards.md`.
- **BR-07 (Scoped Event Routing):** Events published to `unihub:stream:user:{userId}` SHALL only be delivered to connections authenticated as that exact `userId`. Events published to `unihub:stream:workshop:{workshopId}` SHALL only be delivered to connections authorized for that workshop (organizer owning the workshop or staff assigned to it).

## 5. State Transitions

The SSE connection lifecycle represents a transient in-memory connection state machine:

| From State                   | Event / Trigger                                                | To State       | Valid? | Rejection Behavior (if invalid)                       |
| :--------------------------- | :------------------------------------------------------------- | :------------- | :----: | :---------------------------------------------------- |
| _None_                       | Client sends `GET /api/v1/stream/events` with valid JWT        | `CONNECTED`    |  Yes   | N/A                                                   |
| _None_                       | Client sends request with missing/invalid/revoked JWT          | _None_         |   No   | Return `401 Unauthorized` (RFC 7807)                  |
| _None_                       | Client sends request with banned account (`is_active = false`) | _None_         |   No   | Return `403 Forbidden` (RFC 7807)                     |
| _None_                       | Client exceeds handshake rate limit                            | _None_         |   No   | Return `429 Too Many Requests`                        |
| `CONNECTED`                  | Event published to Redis matching connection                   | `STREAMING`    |  Yes   | N/A                                                   |
| `CONNECTED` / `STREAMING`    | Scheduled 15s timer fires                                      | `STREAMING`    |  Yes   | Write `: ping\n\n` to socket                          |
| `STREAMING`                  | Write completes successfully                                   | `CONNECTED`    |  Yes   | N/A                                                   |
| `CONNECTED` / `STREAMING`    | Client closes connection or socket broken                      | `DISCONNECTED` |  Yes   | Emitter evicted from registry, socket closed          |
| `CONNECTED` / `STREAMING`    | Connection duration reaches timeout                            | `TIMED_OUT`    |  Yes   | Emitter evicted, socket closed with completion status |
| `DISCONNECTED` / `TIMED_OUT` | Write attempted on closed emitter                              | `DISCONNECTED` |   No   | Callback triggers immediate cleanup; no-op            |

## 6. Non-Functional Requirements

- **Performance & Latency:**
  - Event distribution latency from `RealtimeEventPublisher.publish()` to client socket delivery SHALL be $\le$ 50 ms at p95 under standard load.
  - Heartbeat scheduling SHALL execute asynchronously across a non-blocking worker pool without stalling HTTP request threads.
- **Concurrency & Resource Footprint:**
  - The in-memory registry SHALL support $\ge$ 12,000 concurrent idle/streaming connections per backend cluster node.
  - Memory overhead per active connection in `SseEmitterRegistry` SHALL NOT exceed 50 KB.
- **Idempotency & Deduplication:**
  - Every event emitted SHALL include a unique `id` (UUID v4) and `eventId` in payload, allowing client-side handlers to discard duplicate events if client retries or reconnections occur.
- **Rate-Limiting:**
  - Handshake requests at `GET /api/v1/stream/events` SHALL be metered by Bucket4j distributed rate limiting via a dedicated `streamTier` (`capacity: 10`, `refill-tokens: 10`, `refill-duration: 60s`) mapped to `/api/v1/stream/**`. Rejections return HTTP `429 Too Many Requests` with type `urn:unihub:error:rate-limited` wrapped in the standard API response envelope.
- **Security & Network Hardening:**
  - Connection handshake requires RS256 JWT Bearer authentication. Query-string access tokens are disallowed to prevent leakage in proxy access logs.
  - Response headers must include `Cache-Control: no-cache, no-transform`, `X-Accel-Buffering: no`, and `Connection: keep-alive`.
  - Upstream Nginx proxy must configure `proxy_buffering off;`, `chunked_transfer_encoding on;`, and `proxy_set_header Accept-Encoding "";` for `/api/v1/stream/`.
- **Reliability & Failure Modes:**
  - Abrupt client disconnects (broken pipes) must be caught cleanly without printing full stack traces in logs (handled as `DEBUG` or quiet eviction).
  - Unavailability of Redis Pub/Sub must not degrade normal synchronous REST endpoints.
- **Observability & Metrics:**
  - Micrometer gauge `realtime.sse.active_connections` (tagged by role and node instance).
  - Micrometer counter `realtime.events.pushed.total` (tagged by `event_type`).
  - Micrometer counter `realtime.sse.disconnects.total` (tagged by reason: `client_abort`, `timeout`, `error`).

## 7. Acceptance Criteria

### Scenario 1: Successful Stream Handshake and Initial Connection

```gherkin
Given a user with role "STUDENT" and active status "true"
And a valid, non-expired RS256 JWT access token
When the client sends "GET /api/v1/stream/events" with header "Authorization: Bearer <jwt>"
Then the system returns HTTP status 200 OK
And the response header "Content-Type" is "text/event-stream; charset=UTF-8"
And the response header "Cache-Control" contains "no-cache, no-transform"
And the response header "X-Accel-Buffering" is "no"
And the connection is registered in the in-memory SseEmitterRegistry
```

### Scenario 2: Handshake Rejection on Missing or Invalid Token

```gherkin
Given a client request to "GET /api/v1/stream/events"
When the "Authorization" header is missing, malformed, or signed by an unknown key
Then the system returns HTTP status 401 Unauthorized
And the response content-type is "application/problem+json"
And no SSE stream is opened
```

### Scenario 3: Handshake Rejection on Revoked JTI

```gherkin
Given a valid RS256 JWT access token whose "jti" is present in the Redis blacklist
When the client sends "GET /api/v1/stream/events" with header "Authorization: Bearer <jwt>"
Then the system returns HTTP status 401 Unauthorized
And no SSE stream is opened
```

### Scenario 4: Handshake Rejection on Inactive or Banned User

```gherkin
Given a user whose "is_active" status in the database is "false"
And a valid JWT issued before the account was deactivated
When the client sends "GET /api/v1/stream/events" with header "Authorization: Bearer <jwt>"
Then the system returns HTTP status 403 Forbidden
And the response content-type is "application/problem+json"
And no SSE stream is opened
```

### Scenario 5: Heartbeat Ping Broadcast

```gherkin
Given an active, established SSE connection
When the 15-second heartbeat scheduler fires
Then the system writes ": ping\n\n" to the connection socket
And the connection remains in CONNECTED state
```

### Scenario 6: User-Targeted Event Delivery Across Backend Nodes

```gherkin
Given an active SSE connection for student "user-123" on backend Node 1
When an event producer on backend Node 2 calls "RealtimeEventPublisher.publishToUser('user-123', event)"
Then the event is published to Redis channel "unihub:stream:user:user-123"
And Node 1 receives the event from Redis Pub/Sub
And Node 1 formats the event according to the SSE wire specification
And the student client receives:
  """
  id: <uuid>
  event: notification.new
  data: {"eventId":"...","eventType":"notification.new","timestamp":"...","payload":{...}}
  retry: 3000

  """
```

### Scenario 7: Workshop-Scoped Event Delivery to Organizer and Staff

```gherkin
Given an active SSE connection for organizer "org-user" on Node 1
And an active SSE connection for assigned staff "staff-user" on Node 2
When an attendance sync completes for workshop "workshop-abc"
And the backend publishes "attendance.updated" to workshop "workshop-abc"
Then both Node 1 and Node 2 receive the event from Redis channel "unihub:stream:workshop:workshop-abc"
And the organizer client receives the "attendance.updated" event
And the staff client receives the "attendance.updated" event
```

### Scenario 8: Clean Connection Eviction on Client Disconnect

```gherkin
Given an active SSE connection in SseEmitterRegistry
When the client closes the browser tab or drops network connectivity
And the server attempts to write to the broken socket or receives completion signal
Then the onCompletion or onError callback is triggered
And the emitter is immediately removed from SseEmitterRegistry
And active connection gauge is decremented by 1
And no memory leaks or dangling file descriptors remain
```

### Scenario 9: Multi-Device Notification Read Synchronization

```gherkin
Given a user with active SSE connections on Web Browser (Session A) and Mobile Device (Session B)
When the user marks notifications as read via "PATCH /api/v1/notifications/{id}/read" on Session A
Then the notifications module updates the database
And calls "RealtimeEventPublisher.publishToUser(userId, notificationReadEvent)"
Then Session B receives:
  """
  id: <uuid>
  event: notification.read
  data: {"eventId":"...","eventType":"notification.read","timestamp":"...","payload":{"id":"...","all":false,"unreadCount":3,"readAt":"..."}}
  retry: 3000

  """
And the Mobile app immediately decrements its unread badge without polling
```

## 8. Out of Scope

- **Message Persistence in Database:** Storing historical streaming events in PostgreSQL is out of scope. Event durability is owned by individual domain tables (e.g. `in_app_notifications`, `attendances`, `payments`).
- **Domain Event Generation:** Business logic that determines when to trigger notifications, recalculate check-in counts, or process brochure summaries belongs strictly to domain modules (F-4.4, F-5.1, F-6.3, F-7.1, F-7.2, F-8.1).
- **Bi-Directional Communication:** Client-to-server messaging over the stream is explicitly excluded (ADR-031). Clients issue standard HTTP POST/PATCH/DELETE calls for all upstream actions.
- **Client UI Presentation:** Rendering UI toasts, badge counters, audio chimes, or data visualization charts is the responsibility of the Web (`@microsoft/fetch-event-source`) and Mobile (`react-native-sse`) client applications.

## 9. Linked References

- `docs/overview.md` — Platform architecture, concurrency constraints (12,000+ users).
- `docs/feature-catalog.md §10.5` — `real-time-event-streaming-hub` specification, module ownership, constraints.
- `docs/feature-catalog.md §4.4, §5.1, §6.3, §7.1, §7.2, §8.1` — Downstream real-time event consumers and triggers.
- `docs/system-design.md §6` — Real-Time Event Distribution Architecture via SSE & Redis Pub/Sub.
- `docs/api-standards.md` — Server-Sent Events (SSE) Streaming Standards, Wire Envelope, Unified Event Catalog.
- `docs/adrs.md` — ADR-005 (JWT RS256 Auth), ADR-006 (RBAC), ADR-010 (Redis Resilience), ADR-031 (SSE and Redis Pub/Sub).
- `docs/project-structure.md` — `shared/realtime` package organization, Spring Modulith `@ApplicationModule(type = OPEN)`.
- `infra/nginx/templates/default.conf.template` — `/api/v1/stream/` unbuffered reverse proxy configuration.
