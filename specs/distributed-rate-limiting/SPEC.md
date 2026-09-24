# Spec: Distributed Rate Limiting

> **Status:** APPROVED  
> **Domain:** 10 — System Reliability & Infrastructure  
> **Owner/area:** `shared/web` (HTTP filter interceptor), `shared/config` (Bucket4j + Redis bean configuration), `shared/observability` (Micrometer metrics)  
> **Last revised:** 2026-08-26 — first version

## 1. Overview

The Distributed Rate Limiter is a cross-cutting, application-layer HTTP filter that protects the Core Backend Monolith from traffic spikes — particularly the 12,000+ concurrent user wave that occurs during the opening minutes of a Skills and Career Week registration window. It implements a **Token Bucket** algorithm via **Bucket4j** with **Redis** as the shared token-counter store, ensuring that all horizontally scaled backend nodes (Node 1, Node 2, …) enforce a single, coherent rate limit rather than per-process silos.

The filter is transparent to clients: every request is evaluated against the caller's bucket before any business logic runs. When the bucket has tokens, the request proceeds normally. When the bucket is exhausted, the filter returns `429 Too Many Requests` immediately with standard `X-RateLimit-*` response headers so clients can implement informed back-off. When Redis is unavailable, the system fails open — the filter logs the event, emits a Micrometer counter, and allows the request through — trading strict rate control for availability.

Nginx acts as a pure reverse proxy and load balancer; it performs **no rate limiting**. All rate-limiting logic lives inside the Core Backend Monolith's filter chain.

## 2. Actors & Preconditions

| Actor                                                | Role                     | Precondition                                                                                                                |
| :--------------------------------------------------- | :----------------------- | :-------------------------------------------------------------------------------------------------------------------------- |
| Any authenticated client (STUDENT, ORGANIZER, STAFF) | Subject of rate limiting | A valid, non-revoked JWT is present in the `Authorization: Bearer <token>` header; the `sub` claim is a non-null UUID.      |
| Any unauthenticated client                           | Subject of rate limiting | No JWT present; the client IP address as seen by the backend is used as the bucket key.                                     |
| Core Backend Monolith (HTTP filter chain)            | Enforces rate limits     | Bucket4j library is on the classpath; a Spring-managed Redis connection (Lettuce/Jedis) is available.                       |
| Redis                                                | Token bucket store       | Redis is reachable and the `rate_limit:` key namespace is accessible. If Redis is unreachable, the fail-open path is taken. |
| Prometheus / Grafana                                 | Observability consumer   | Micrometer Prometheus registry is configured (per `observability-platform-setup`).                                          |

## 3. Functional Requirements

### 3.1 Bucket Keying

- **FR-01:** The system SHALL derive the rate-limit bucket key from `jwt.sub` (a UUID string) when the incoming HTTP request carries a parseable JWT in the `Authorization: Bearer` header.
- **FR-02:** The system SHALL derive the rate-limit bucket key from the client IP address when no JWT is present or the JWT cannot be parsed (unauthenticated or malformed-token paths).

### 3.2 Path-Based Bucket Configuration

- **FR-03:** The system SHALL apply a **registration-tier** bucket configuration (tighter capacity and/or refill rate) to all requests whose path matches the registration endpoint group (e.g., `POST /api/v1/registrations/**`).
- **FR-04:** The system SHALL apply an **auth-tier** bucket configuration (default: 20 tokens capacity, refill 20 tokens per 60s) to all requests whose path matches authentication and credential endpoint patterns (e.g., `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/activate`, `/api/v1/auth/otp/**`, `/api/v1/auth/password/**`, `/api/v1/auth/refresh`) to prevent brute-force attacks and CPU exhaustion from Argon2id hashing.
- **FR-05:** The system SHALL apply a **stream-tier** bucket configuration (default: 10 tokens capacity, refill 10 tokens per 60s) to all requests whose path matches real-time SSE stream endpoints (`/api/v1/stream/**`).
- **FR-06:** The system SHALL apply a **default-tier** bucket configuration (fallback capacity and/or refill rate) to all other bucket-protected endpoints not covered by the registration, auth, or stream tiers.
- **FR-07:** The capacity and refill-rate values for each tier SHALL be externalized as configuration properties (e.g., in `application.yml`) so they can be tuned without code changes.

### 3.3 Token Consumption and 429 Response

- **FR-08:** The system SHALL atomically decrement one token from the caller's bucket in Redis for each protected request before delegating to the next filter in the chain.
- **FR-09:** The system SHALL return HTTP `429 Too Many Requests` (RFC 7807 `application/problem+json`) immediately — without invoking any downstream filter, use case, or database call — when the token decrement results in an exhausted bucket.
- **FR-10:** Every response to a bucket-protected endpoint — whether the request is allowed (2xx) or rejected (429) — SHALL include the following response headers:
  - `X-RateLimit-Limit`: The total token capacity of the caller's bucket for the matched tier.
  - `X-RateLimit-Remaining`: The number of tokens remaining after this request (0 when the bucket is exhausted).
  - `X-RateLimit-Retry-After`: The number of seconds the client should wait before retrying (0 when tokens are available; a positive integer when the bucket is exhausted).

### 3.4 Fail-Open on Redis Unavailability

- **FR-11:** When the Redis connection cannot be established or a Redis command times out during bucket evaluation, the system SHALL skip bucket evaluation entirely and allow the request to proceed to the next filter in the chain (fail-open).
- **FR-12:** On every fail-open event, the system SHALL log a `WARN`-level structured log entry containing at minimum: the bucket key, the request path, and the exception class/message that caused the Redis failure.
- **FR-13:** On every fail-open event, the system SHALL increment the Micrometer counter `rate_limiter.redis_unavailable` (tagged `tier=<registration|auth|stream|default>`) so that Prometheus can alert on Redis degradation.

### 3.5 Nginx Constraint

- **FR-14:** The Nginx configuration (`infra/nginx/`) SHALL NOT contain any `limit_req_zone`, `limit_req`, or equivalent rate-limiting directives. All rate limiting is performed exclusively by the Core Backend Monolith filter.

### 3.6 CORS Exposure

- **FR-15:** The `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` headers SHALL be declared in the backend's CORS configuration under `Access-Control-Expose-Headers` so that the React web client can read them from cross-origin responses.

## 4. Business Rules

- **BR-01:** The Token Bucket algorithm is the mandated rate-limiting strategy (ADR-019). Sliding window or fixed window algorithms must not be used, as Token Bucket better absorbs legitimate short bursts during the opening registration wave.
- **BR-02:** Redis holds the shared token counter state. No node-local (JVM-heap) token counters may be used as primary enforcement, because they would produce inconsistent limits across horizontally scaled backend nodes.
- **BR-03:** The fail-open decision (FR-11) applies **only** to rate limiting. Redis unavailability for the seat-capacity `DECR` operation (the `capacity:{workshopId}` key used during overbooking prevention) is handled separately and is fail-closed (ADR-010). The rate-limit filter must not conflate or interfere with those capacity keys.
- **BR-04:** The bucket key namespace for rate limiting (e.g., `rate_limit:{key}`) must be distinct from all other Redis key namespaces used by the system (`capacity:`, `jti:blacklist:`, etc.) to prevent accidental collision or eviction interference.
- **BR-05:** Registration-tier limits are tighter than default-tier limits (ADR-019). The specific numeric values are configuration-driven (FR-07); the ordering constraint — registration tier capacity ≤ default tier capacity, and/or registration tier refill rate ≤ default tier refill rate — must be validated at startup to prevent misconfiguration.
- **BR-06:** The rate-limit filter must execute **before** any business logic in the Spring Security filter chain — specifically before the idempotency-key check, before the authentication filter resolves user identity for domain purposes, and before any use-case delegation.

  > **Notes:** The filter reads the JWT to extract `jwt.sub` for keying (FR-01), but does so in a best-effort, read-only manner without validating the JWT signature. Signature validation is the responsibility of the authentication filter. If JWT parsing fails, the filter falls back to IP keying (FR-02) and continues — it must not throw a 401 or 400 at this stage.

- **BR-07:** The `rate_limiter.redis_unavailable` Micrometer counter must be registered in the same `MeterRegistry` configured by `observability-platform-setup`, not in a separate or local registry.
- **BR-08:** The 429 error response body must conform to RFC 7807 (`application/problem+json`) with `type`, `title`, `status`, `detail`, and `instance` fields, wrapped in the standard `ApiResponse` envelope with `metadata: { traceId, processedBy }` — consistent with `docs/api-standards.md` error response standards.

## 5. State Transitions

N/A — this feature does not introduce or modify any stateful PostgreSQL entity. Token bucket state is managed atomically by Bucket4j within Redis and carries no lifecycle that needs database representation.

## 6. Non-Functional Requirements

- **Performance:** Bucket evaluation must add exactly one Redis round-trip to the request path. Bucket4j's Redis-backed proxy uses Lua scripts for atomic token decrement, which must not be replaced with non-atomic read-modify-write sequences. The filter must perform no PostgreSQL reads or writes.
- **Concurrency:** The filter must be correct under 12,000+ concurrent inbound requests. Redis Lua-script atomicity guarantees linearizability of token decrements across all backend nodes without application-level locking.
- **Idempotency:** Not applicable — rate limiting operates on request arrival; each request always attempts exactly one token decrement regardless of prior requests.
- **Security:** The bucket key for authenticated users must be `jwt.sub` (the user's UUID), never a client-supplied header value such as `X-User-Id`. For unauthenticated IP keying, the IP must be extracted from the `X-Forwarded-For` header set by Nginx (the trusted proxy), taking only the leftmost entry that represents the real client — not blind trust of all `X-Forwarded-For` values, which can be spoofed by clients.
- **Reliability / Failure modes:**
  - **Redis unreachable:** fail-open (FR-11). Log WARN + increment metric (FR-12, FR-13).
  - **Driver-level fail-fast:** Lettuce client configuration (`REJECT_COMMANDS`, `TimeoutOptions.enabled()`) ensures that when Redis is disconnected, commands are rejected in <1ms without command buffering, allowing the rate-limit filter to execute its fail-open fallback immediately without thread pool blocking.
  - **Redis command timeout:** Bucket4j command timeout must be configured short (recommended ≤ 100 ms) to bound added latency. On timeout, treat as Redis unavailable and fail open.
  - **JVM crash mid-decrement:** Bucket4j sets a TTL on bucket keys in Redis. On key expiry and recreation the counter resets to full capacity — a safe, self-healing outcome.
- **Observability:**
  - `rate_limiter.redis_unavailable` counter (tagged `tier`) — incremented on every fail-open event.
  - Standard `http.server.requests` (Spring Boot auto-configuration) already captures 429 counts by path and status; no additional `rate_limiter.rejected` counter is required unless explicitly added by `observability-platform-setup`.
  - The existing Grafana dashboard `infra/grafana/dashboards/redis-bucket4j.json` must include a panel for `rate_limiter.redis_unavailable` to surface Redis degradation alerting.

## 7. Acceptance Criteria

### Scenario 1: Authenticated request allowed when bucket has tokens

```gherkin
Given an authenticated STUDENT user with a valid JWT (sub = "user-uuid-123")
And the registration-tier bucket for "user-uuid-123" has 5 tokens remaining
When the user sends POST /api/v1/registrations with a valid Authorization header
Then the filter allows the request to proceed to the next filter in the chain
And the response includes X-RateLimit-Limit with the configured registration-tier capacity
And the response includes X-RateLimit-Remaining: 4
And the response includes X-RateLimit-Retry-After: 0
```

### Scenario 2: Authenticated request rejected when bucket is exhausted

```gherkin
Given an authenticated STUDENT user with a valid JWT (sub = "user-uuid-123")
And the registration-tier bucket for "user-uuid-123" has 0 tokens remaining
When the user sends POST /api/v1/registrations with a valid Authorization header
Then the filter returns 429 Too Many Requests without invoking any downstream filter
And the response Content-Type is application/problem+json
And the response body contains a valid RFC 7807 problem detail with status 429
And the response includes X-RateLimit-Limit with the configured registration-tier capacity
And the response includes X-RateLimit-Remaining: 0
And the response includes X-RateLimit-Retry-After with a positive integer (seconds until next token)
```

### Scenario 3: Unauthenticated request keyed by IP

```gherkin
Given a request with no Authorization header from client IP 192.168.1.42
And the default-tier bucket for IP "192.168.1.42" has 10 tokens remaining
When the request reaches the rate-limit filter
Then the filter keys the bucket by IP "192.168.1.42"
And the filter allows the request to proceed
And the response includes the three X-RateLimit-* headers reflecting the default-tier bucket state
```

### Scenario 4: Fail-open when Redis is unavailable

```gherkin
Given the Redis server is unreachable (connection refused or command timeout)
And an authenticated STUDENT user sends POST /api/v1/registrations
When the rate-limit filter attempts bucket evaluation
Then the filter catches the Redis exception
And the filter allows the request to proceed to the next filter in the chain
And a WARN-level structured log entry is written containing the bucket key, request path, and exception details
And the Micrometer counter "rate_limiter.redis_unavailable" with tag "tier=registration" is incremented by 1
```

### Scenario 5: Registration-tier is tighter than or equal to default-tier

```gherkin
Given the application configuration is loaded at startup
When the bucket configuration for registration-tier and default-tier are compared
Then the registration-tier token capacity is less than or equal to the default-tier token capacity
And/Or the registration-tier refill rate is less than or equal to the default-tier refill rate
And startup fails with a descriptive error if this constraint is violated
```

### Scenario 6: Nginx has no rate-limiting directives

```gherkin
Given the Nginx configuration files in infra/nginx/
When the files are inspected for rate-limiting directives
Then no "limit_req_zone" directive exists in any Nginx configuration file
And no "limit_req" directive exists in any Nginx configuration file
```

### Scenario 7: `X-RateLimit-*` headers are CORS-exposed

```gherkin
Given the backend's CORS configuration for the web portal origin
When a browser sends an OPTIONS preflight for POST /api/v1/registrations
Then the Access-Control-Expose-Headers response header includes "X-RateLimit-Limit"
And the Access-Control-Expose-Headers response header includes "X-RateLimit-Remaining"
And the Access-Control-Expose-Headers response header includes "X-RateLimit-Retry-After"
```

### Scenario 8: Malformed JWT falls back to IP keying without error

```gherkin
Given a request that carries a malformed or expired JWT token
When the rate-limit filter processes the request
Then the filter catches the JWT parsing exception without returning a 500 error
And the filter falls back to the client IP address as the bucket key
And bucket evaluation proceeds normally using the IP-keyed default-tier bucket
And the downstream filters receive the request as if no JWT was present at the rate-limiting stage
```

### Scenario 9: Authentication requests route to auth-tier bucket

```gherkin
Given an unauthenticated request from client IP "192.168.1.50" to "POST /api/v1/auth/login"
And the auth-tier bucket for IP "192.168.1.50" has 20 tokens remaining
When the request reaches the rate-limit filter
Then the filter matches the auth-path-patterns and selects the auth-tier configuration
And the filter keys the bucket by IP "192.168.1.50"
And the filter consumes 1 token and allows the request to proceed
And the response header "X-RateLimit-Limit" equals "20"
And the response header "X-RateLimit-Remaining" equals "19"
```

## 8. Out of Scope

- **Admin API for dynamic limit configuration:** There is no runtime endpoint to modify bucket capacities or refill rates. Configuration changes require an `application.yml` update and a rolling redeploy.
- **Per-organization or per-role rate limits:** All users hitting a given endpoint tier are subject to the same bucket configuration. Fine-grained limits by `organization_id` or `role` are not part of this feature.
- **Nginx-level rate limiting:** Nginx is a forwarding proxy only. Adding `ngx_http_limit_req_module` or any equivalent Nginx directive is explicitly prohibited by ADR-019 and is out of scope.
- **IP geolocation-based rules:** No country- or region-based allow/deny lists are implemented.
- **WebSocket or gRPC endpoints:** This feature covers only the HTTP REST API served by the Core Backend Monolith's Servlet layer. Future WebSocket or gRPC surfaces would require separate treatment.
- **Workers application:** Background workers do not serve external HTTP traffic; rate limiting does not apply to them.
- **Redis key TTL management / cleanup jobs:** Bucket4j manages the TTL of its own Redis keys internally. No additional TTL cleanup job is needed for rate-limit bucket keys (unlike `idempotency_keys` or `refresh_tokens`).
- **Seat-capacity DECR fail-closed behavior:** The `capacity:{workshopId}` Redis key used for overbooking prevention is a distinct concern (ADR-010, ADR-013). Its fail-closed behavior is owned by the registration use case, not by this filter.

## 9. Linked References

| Reference                                                    | Relevance                                                                                                                                           |
| :----------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md §10.1 distributed-rate-limiting`    | Authoritative feature entry: module ownership (`shared/web`, `shared/config`, `shared/observability`), system flow refs, ADR refs, key constraints  |
| `docs/system-design.md §6 — Traffic Spike Management`        | Sequence diagram and rationale for Token Bucket in the application filter layer; fail-open model described                                          |
| `docs/system-design.md §4 — Workshop Registration Flow`      | Rate limiting as Step 1 in the registration flow, preceding idempotency check and Redis capacity DECR                                               |
| `docs/system-design.md §6 — System Telemetry & Core Metrics` | Bucket4j rate-limiting drop rate tracked via Redis counter; Grafana `redis-bucket4j.json` dashboard                                                 |
| `docs/adrs.md ADR-010`                                       | Redis fail-open for rate limiting; `rate_limiter.redis_unavailable` metric mandate; fail-closed for capacity DECR (distinct concern)                |
| `docs/adrs.md ADR-019`                                       | Bucket4j Token Bucket mandate; per-user bucket keying by `jwt.sub`; backend filter placement (Nginx excluded); registration vs. default tier tuning |
| `docs/api-standards.md §Standard Response Headers`           | `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` header spec                                                                 |
| `docs/api-standards.md §CORS Policy`                         | `X-RateLimit-*` headers must appear in `Access-Control-Expose-Headers` and `Access-Control-Allow-Headers`                                           |
| `docs/api-standards.md §HTTP Status Codes`                   | 429 Too Many Requests — Bucket4j rate limit exceeded                                                                                                |
| `docs/api-standards.md §Error Response — RFC 7807`           | Mandatory error envelope format for the 429 response body                                                                                           |
| `docs/project-structure.md §Core Backend`                    | `shared/web` (HTTP filters), `shared/config` (infrastructure beans), `shared/observability` (Micrometer metrics) package locations                  |
