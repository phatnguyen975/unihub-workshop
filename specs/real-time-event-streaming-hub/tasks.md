# Implementation Plan: Real-Time Event Streaming Hub

> **Spec:** `specs/real-time-event-streaming-hub/SPEC.md`  
> **API Contract:** `specs/real-time-event-streaming-hub/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This implementation plan decomposes the `real-time-event-streaming-hub` feature into four ordered vertical slices spanning domain models, application orchestration, Redis Pub/Sub messaging, REST controllers, and observability. The plan starts with establishing the authenticated HTTP streaming handshake and thread-safe in-memory emitter registry, layers in the automated 15-second heartbeat keep-alive cadence, implements cross-node event distribution via Redis Pub/Sub channels for all 7 unified catalog events, and finishes with telemetry instrumentation, rate limiting integration, and Spring Modulith architectural boundary verification.

## Architecture Decisions for This Plan

- **In-Memory Emitter Thread Safety via ConcurrentHashMap:** `SseEmitterRegistry` will index emitters by `UUID userId` mapping to thread-safe concurrent sets (`CopyOnWriteArraySet`) to support multiple tabs/devices per user concurrently without lock contention.
- **Stateless Cross-Node Distribution via StringRedisTemplate:** Event payloads are serialized to JSON strings and broadcasted to Redis channels `unihub:stream:user:{userId}` and `unihub:stream:workshop:{workshopId}` using `StringRedisTemplate`. This avoids Java serialization compatibility issues between nodes and background workers.
- **Fail-Safe Disconnect Cleanup:** Socket write failures during event pushing or heartbeat pinging will directly trigger emitter deregistration in `SseEmitterRegistry` without throwing uncaught exceptions to the application runtime.
- **Open Spring Modulith Accessibility:** The `com.unihub.workshop.shared.realtime` package is housed in `shared` (`@ApplicationModule(type = OPEN)`), ensuring domain modules can cleanly inject `RealtimeEventPublisher` without violating Modulith encapsulation rules.

## Task List

### Phase 1: Core Stream Connection & Lifecycle Management

- [x] **T1 — Implement Authenticated Stream Handshake & In-Memory Emitter Registry**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/domain/SseConnection.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/domain/SseEmitterRegistry.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/application/StreamSubscriptionService.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/presentation/StreamController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/domain/SseEmitterRegistryTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/presentation/StreamControllerTest.java`
  - **Acceptance Criteria:**
    - An authenticated user connecting to `GET /api/v1/stream/events` receives HTTP 200 with headers `Content-Type: text/event-stream; charset=UTF-8`, `Cache-Control: no-cache, no-transform`, and `X-Accel-Buffering: no` (FR-01, FR-06, Scenario 1).
    - Unauthenticated, expired, or blacklisted JWT tokens return HTTP 401 Unauthorized with RFC 7807 problem details (FR-02, FR-03, Scenarios 2 & 3).
    - Inactive user accounts (`is_active = false`) return HTTP 403 Forbidden with RFC 7807 problem details (FR-04, Scenario 4).
    - Active emitters are registered in `SseEmitterRegistry` by `userId`, supporting multiple concurrent sessions per user (FR-07, FR-08).
    - Client disconnects, aborts, or socket completions cleanly evict the emitter from `SseEmitterRegistry` without resource leaks (FR-09, Scenario 8).

- [x] **T2 — Implement Scheduled Heartbeat & Keep-Alive Cadence**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Application, Infrastructure
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/HeartbeatScheduler.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/config/RealtimeProperties.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/HeartbeatSchedulerTest.java`
  - **Acceptance Criteria:**
    - The system periodically emits a comment `: ping\n\n` to all registered active emitters every 15 seconds (FR-10, BR-03, Scenario 5).
    - Broken sockets or disconnected clients encountered during heartbeat ping are caught safely and evicted from `SseEmitterRegistry` without throwing uncaught exceptions or polluting logs (FR-09, Scenario 8).
    - Heartbeat interval is configurable via `app.realtime.heartbeat-interval-seconds` with a default of 15 seconds.

#### Checkpoint: Stream Handshake & Heartbeat Operational

- [x] Client can connect to `GET /api/v1/stream/events`, receive initial SSE stream headers, and observe periodic `: ping\n\n` comments every 15 seconds.
- [x] Broken connections are cleanly evicted from memory.
- [x] All Phase 1 unit and presentation slice tests pass.

### Phase 2: Distributed Event Distribution & Observability

- [ ] **T3 — Implement Multi-Node Redis Pub/Sub Event Distribution & Wire Serialization**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/contracts/RealtimeEventPublisher.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/domain/RealtimeEvent.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/domain/RealtimeEventType.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimePublisherAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimeSubscriber.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/config/RealtimeRedisConfig.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimePubSubTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/RealtimeEventSerializationTest.java`
  - **Acceptance Criteria:**
    - Calling `RealtimeEventPublisher.publishToUser(userId, event)` publishes serialized JSON to Redis channel `unihub:stream:user:{userId}` (FR-11).
    - Calling `RealtimeEventPublisher.publishToWorkshop(workshopId, event)` publishes serialized JSON to Redis channel `unihub:stream:workshop:{workshopId}` (FR-11).
    - `RedisRealtimeSubscriber` receives messages from Redis Pub/Sub across instances, formats them into standard SSE wire format (`id: <uuid>\nevent: <name>\ndata: <json>\n\n`), and writes to all matching active emitters on that node (FR-12, FR-13, FR-15, Scenarios 6 & 7).
    - Supports all 7 event types in the Unified Event Catalog: `notification.new`, `notification.read`, `ai.summary.completed`, `ai.summary.failed`, `attendance.updated`, `registration.updated`, `payment.confirmed` (FR-16, Scenario 9).
    - Discards messages safely with zero errors when an instance holds no matching active emitters (FR-14).

- [ ] **T4 — Implement Telemetry Metrics, Stream Rate Limiting Tier & Modulith Architecture Verification**
  - **Size:** M
  - **Depends on:** T1, T2, T3
  - **Layers touched:** Infrastructure, Rate Limiting, Observability, Architecture Test
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/RealtimeMetrics.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/ratelimit/RateLimitProperties.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/ratelimit/RateLimitFilter.java`
    - `apps/backend/src/main/resources/application.yml`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/realtime/infrastructure/RealtimeMetricsTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/ratelimit/RateLimitFilterTierRoutingTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/ModulithArchitectureTest.java`
  - **Acceptance Criteria:**
    - Micrometer gauge `realtime.sse.active_connections` accurately tracks the number of open SSE connections in memory across connect/disconnect events (NFR Observability).
    - Micrometer counter `realtime.events.pushed.total` increments tagged by `event_type` upon each event sent to an emitter socket (NFR Observability).
    - Micrometer counter `realtime.sse.disconnects.total` tracks connection termination reasons (`client_abort`, `timeout`, `error`) (NFR Observability).
    - Rate limiter configures a dedicated `streamTier` (`capacity: 10`, `refill-tokens: 10`, `refill-duration: 60s`) mapped to `/api/v1/stream/**`, protecting against socket flood DoS while isolating general browsing quota (FR-05).
    - Handshake rate-limit rejection returns HTTP `429 Too Many Requests` with type `urn:unihub:error:rate-limited` wrapped in the standard `ApiResponse` envelope (`{"success": false, "error": {...}, "metadata": {...}}`) with `X-RateLimit-*` headers (API Contract).
    - All existing rate limit tests in F-10.2 continue to pass without regression.
    - `ModulithArchitectureTest` verifies that `com.unihub.workshop.shared.realtime` respects module boundaries as an open shared module without package encapsulation violations.

#### Checkpoint: Feature Complete

- [ ] All 4 tasks (T1 through T4) are checked off.
- [ ] Every acceptance criterion from `SPEC.md` and `API_CONTRACT.md` has been verified via unit tests.
- [ ] `ModulithArchitectureTest` and full unit test suite pass.
- [ ] Ready for `/review real-time-event-streaming-hub`.

## Risks and Mitigations

| Risk                                                                                                                       | Impact | Mitigation                                                                                                                                                                                           |
| :------------------------------------------------------------------------------------------------------------------------- | :----: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **High Concurrency Socket Exhaustion:** 12,000+ open HTTP connections could exhaust JVM memory or server file descriptors. |  High  | In-memory emitters store minimal state ($< 50$ KB per emitter); Nginx handles TCP multiplexing; strict 15s ping and prompt eviction on broken sockets prevent dangling socket descriptors.           |
| **Gzip / Proxy Buffering Delays:** Reverse proxy buffering SSE events would stall real-time delivery to clients.           |  High  | Nginx `/api/v1/stream/` is pre-configured with `proxy_buffering off;`, `chunked_transfer_encoding on;`, and `proxy_set_header Accept-Encoding "";`. Stream controller emits `X-Accel-Buffering: no`. |
| **Redis Outage Affecting Synchronous Handshake:** Unreachable Redis during handshake could hang threads.                   | Medium | JTI verification uses fail-closed resilience policy with bounded timeout (ADR-010); Redis Pub/Sub connection failures are caught and logged without crashing the core monolith.                      |

## Open Questions / Assumptions

- _None._ All technical interfaces, Redis topic conventions, and SSE wire envelopes are finalized and approved in `SPEC.md` and `API_CONTRACT.md`.
