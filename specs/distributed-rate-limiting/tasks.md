# Implementation Plan: Distributed Rate Limiting

> **Spec:** `specs/distributed-rate-limiting/SPEC.md`  
> **Status:** APPROVED

## Overview

This plan builds the application-layer Token Bucket rate limiter from scratch. It proceeds in two phases: Phase 1 establishes the core enforcement path (infrastructure wiring, per-user token evaluation, multi-tier routing, and fail-open resilience), which is the riskiest and most foundational block of work; Phase 2 adds the supporting concerns that depend on a working filter (unauthenticated IP keying, CORS header exposure, and Nginx constraint verification). Each task is independently end-to-end testable at the filter level using Spring's `MockMvc` or `WebTestClient`.

## Architecture Decisions for This Plan

- **T1 ships the shared Bucket4j+Redis infrastructure alongside the first caller-observable capability** (authenticated token enforcement) — the bean configuration has no caller-observable behavior on its own, so it cannot be a standalone task; assigning it to T1 avoids a horizontal "wiring only" task while still making it available to every subsequent task that depends on it.
- **IP keying (T2) is sequenced after the core authentication path (T1)** because T2's filter code is an extension of T1's filter; extracting the key resolution logic in T2 depends on the filter skeleton already existing from T1.
- **Path-based 2-tier routing (T3) is its own task** because the tier-dispatch logic is independently testable (a registration path must resolve to the tighter bucket, a browsing path to the relaxed bucket) and touches different configuration properties than T1.
- **Fail-open (T4) is deliberately placed last within Phase 1** — it requires all the happy-path wiring to already exist; testing it in isolation (simulated Redis failure) is cleaner once the normal code path is proven to work.
- **CORS exposure and Nginx assertion (T5) are grouped in Phase 2** because they are configuration concerns that do not affect the core enforcement path and can be verified only after the filter is functionally complete.

## Task List

### Phase 1: Core Enforcement Path

- [x] **T1 — Authenticated users are rate-limited via a per-user Token Bucket backed by Redis, with `X-RateLimit-*` headers returned on every protected response**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Config (Bucket4j + Redis bean, `application.yml` properties, startup validation), Web/Filter (HTTP filter, key extraction, RFC 7807 429 response, header attachment)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/RateLimitConfig.java` — Bucket4j `ProxyManager` bean wired to the Spring-managed `RedisClient`; `@ConfigurationProperties`-bound `RateLimitProperties`; startup `@PostConstruct` validation enforcing registration-tier ≤ browsing-tier
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/RateLimitProperties.java` — typed config record (registration-tier capacity, refill tokens, refill duration; browsing-tier equivalents)
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/RateLimitFilter.java` — `OncePerRequestFilter`; extracts `jwt.sub` (best-effort, no signature check); resolves tier by path; atomically consumes one token; attaches `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After`; writes RFC 7807 `application/problem+json` body on 429; registers itself before the authentication filter in the security filter chain
    - `apps/backend/src/main/resources/application.yml` — new `rate-limit.*` config block with concrete defaults: registration-tier (capacity: 5, refill: 5 tokens per 10s) and browsing-tier (capacity: 100, refill: 100 tokens per 60s)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/RateLimitFilterAuthenticatedTest.java` — unit tests with mocked `ProxyManager`; verifies token consumed, headers present (FR-06, FR-08), 429 body is RFC 7807 (FR-07, BR-08), filter short-circuits before downstream filters
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/RateLimitConfigTest.java` — startup validation test: misconfigured tiers (registration > browsing) must throw `ApplicationContext` failure
  - **Acceptance Criteria:**
    - (FR-01, Scenario 1) A request with `Authorization: Bearer <valid JWT>` passes through the filter when the bucket has tokens; the response carries `X-RateLimit-Limit`, `X-RateLimit-Remaining` (decremented by 1), and `X-RateLimit-Retry-After: 0`.
    - (FR-06, FR-07, FR-08, Scenario 2) When the same user's bucket has 0 tokens, the filter returns `429` before any downstream filter is called; `Content-Type: application/problem+json`; body contains `status: 429`, `type`, `title`, `detail`, `instance`, `traceId`; `X-RateLimit-Remaining: 0`; `X-RateLimit-Retry-After` is a positive integer.
    - (BR-05) Starting the application with registration-tier capacity > browsing-tier capacity fails with a descriptive `ApplicationContext` exception at startup.
    - (BR-04) Redis bucket keys are prefixed `rate_limit:` — no collision with `capacity:` or `jti:blacklist:` namespaces.

- [x] **T2 — Unauthenticated requests and requests with malformed JWTs are rate-limited using the client's real IP address as the bucket key**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Web/Filter (key-resolution branch in `RateLimitFilter`; IP extraction utility), Config (no new beans; browsing-tier bucket is reused for IP-keyed buckets)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/RateLimitFilter.java` — add IP-keying branch: extract leftmost entry from `X-Forwarded-For` header set by Nginx; fall back to `HttpServletRequest.getRemoteAddr()` if header absent; apply browsing-tier bucket; catch JWT parse exceptions and redirect to this branch without returning 400/401
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/ClientIpExtractor.java` — small utility extracting real client IP from `X-Forwarded-For` (first value only, trusted from upstream Nginx)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/RateLimitFilterUnauthenticatedTest.java` — tests for: no `Authorization` header → IP key; malformed JWT → IP key, no 401/400 thrown; `X-Forwarded-For` with multiple values → leftmost used; no `X-Forwarded-For` → `getRemoteAddr()` used
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/ClientIpExtractorTest.java` — unit tests for IP extraction edge cases
  - **Acceptance Criteria:**
    - (FR-02, Scenario 3) A request with no `Authorization` header is keyed by the IP derived from `X-Forwarded-For`; the browsing-tier bucket is used; `X-RateLimit-*` headers reflect the IP-keyed bucket state.
    - (Scenario 8) A request with a malformed or expired JWT does not produce a `400` or `500`; the filter silently falls back to IP keying and continues; downstream filters receive the request normally.
    - (NFR Security) When `X-Forwarded-For` contains multiple comma-separated values, only the leftmost (real client) is used as the key.

- [x] **T3 — Requests to registration endpoints are governed by the tighter registration-tier bucket; all other requests use the relaxed browsing-tier bucket**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Web/Filter (tier-dispatch logic in `RateLimitFilter`), Config (`RateLimitProperties` path-matcher list)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/RateLimitFilter.java` — extract and encapsulate tier-resolution logic: match request path against a configurable list of registration-tier path patterns (e.g. `POST /api/v1/registrations/**`); fall through to browsing-tier if no match
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/RateLimitProperties.java` — add `registrationPathPatterns: List<String>` configurable from `application.yml`
    - `apps/backend/src/main/resources/application.yml` — populate `rate-limit.registration-path-patterns` with `["/api/v1/registrations/**"]`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/RateLimitFilterTierRoutingTest.java` — parameterized tests: `POST /api/v1/registrations` → registration-tier bucket used; `GET /api/v1/workshops` → browsing-tier bucket used; an unknown path → browsing-tier; path patterns are loaded from config, not hardcoded
  - **Acceptance Criteria:**
    - (FR-03, FR-04, Scenario 5) A request to `POST /api/v1/registrations/**` consumes from the registration-tier bucket (verified by `X-RateLimit-Limit` matching the configured registration-tier capacity).
    - (FR-04) A request to any path not matching the registration patterns consumes from the browsing-tier bucket.
    - (FR-05) Changing `rate-limit.registration-path-patterns` in `application.yml` changes which paths use the registration-tier bucket — no code change required.
    - (Scenario 5) The startup validation (T1) still enforces that registration-tier capacity ≤ browsing-tier capacity; this check continues to pass after adding the path matcher config.

- [x] **T4 — When Redis is unreachable during bucket evaluation, the filter fails open: the request is allowed through, a WARN log is written, and the `rate_limiter.redis_unavailable` Micrometer counter is incremented**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Web/Filter (exception handling branch in `RateLimitFilter`), Observability (`rate_limiter.redis_unavailable` counter registered via `MeterRegistry`)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/RateLimitFilter.java` — wrap `ProxyManager.asAsync()` / bucket evaluation call in try-catch; on any `RedisException`, `LettuceException`, or command-timeout exception: allow request to proceed; log WARN with bucket key, path, exception class and message; increment counter
    - `apps/backend/src/main/java/com/unihub/workshop/shared/observability/RateLimitMetrics.java` — registers `rate_limiter.redis_unavailable` Counter with tags `tier=registration` and `tier=browsing` against the shared `MeterRegistry` (BR-07)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/RateLimitFilterFailOpenTest.java` — tests with `ProxyManager` mocked to throw `RedisException`: filter returns 2xx (not 429 or 500); downstream filter chain is invoked; WARN log contains bucket key and path; `rate_limiter.redis_unavailable` counter is incremented once per fail-open event
  - **Acceptance Criteria:**
    - (FR-09, Scenario 4) When the mocked `ProxyManager` throws a Redis connection/timeout exception, the filter calls `filterChain.doFilter()` and the overall HTTP response is NOT `429` or `5xx`.
    - (FR-10, Scenario 4) A `WARN`-level log entry is written; it contains at minimum the bucket key, the request path, and the exception class name.
    - (FR-11, Scenario 4) The Micrometer counter `rate_limiter.redis_unavailable` with tag `tier=<registration|browsing>` is incremented by exactly 1 per event.
    - (BR-07) The counter is bound to the application's shared `MeterRegistry`, not a local or test-only registry.

#### Checkpoint: Phase 1 — Core Enforcement Complete

- [x] All T1–T4 acceptance criteria pass end-to-end with a running Redis (for happy paths) and mocked Redis (for fail-open paths).
- [x] `ModulithArchitectureTest` passes — no module boundary violations introduced.
- [x] Full unit test suite is green; no existing tests broken.
- [x] The filter is registered in the correct position in the Spring Security filter chain (before the JWT authentication filter).

### Phase 2: Configuration Compliance and Observability Verification

- [x] **T5 — The `X-RateLimit-*` headers are exposed in the CORS configuration so the web client can read them, and the Nginx configuration contains no rate-limiting directives**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Config (Spring CORS bean), Infra (Nginx config files)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/CorsConfig.java` (or the existing CORS configuration class) — add `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` to `exposedHeaders` (and `allowedHeaders` if not already present via wildcard) per `docs/api-standards.md §CORS Policy`
    - `infra/nginx/nginx.conf` — confirm absence of `limit_req_zone` / `limit_req` directives; add a comment asserting the rate limiting is performed at the application layer (per ADR-019)
    - `infra/nginx/templates/default.conf.template` — same assertion/verification
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/CorsConfigTest.java` — test that `Access-Control-Expose-Headers` on a cross-origin request includes the three `X-RateLimit-*` header names
  - **Acceptance Criteria:**
    - (FR-13, Scenario 7) A `MockMvc` CORS preflight (`OPTIONS`) to a rate-limited endpoint returns an `Access-Control-Expose-Headers` value that includes all three: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After`.
    - (FR-12, Scenario 6) `infra/nginx/nginx.conf` and `infra/nginx/templates/default.conf.template` contain no `limit_req_zone` or `limit_req` directives (verified by a test that reads the files and asserts the absence of these strings, or confirmed as part of code review).
    - (docs/api-standards.md §CORS) The existing CORS policy for the web portal origin is not broken — previously allowed methods and headers remain configured.

#### Checkpoint: Feature Complete

- [x] All tasks T1–T5 are checked off.
- [x] Every acceptance criterion across all tasks has been verified end-to-end (including Scenarios 1–8 from `SPEC.md §7`).
- [x] `ModulithArchitectureTest` passes.
- [x] Full unit test suite is green; build is clean.
- [x] Ready for `/review distributed-rate-limiting`.

## Risks and Mitigations

| Risk                                                                                                                                                 | Impact                                                                                                                                | Mitigation                                                                                                                                                                                                                        |
| :--------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bucket4j's Redis-backed `ProxyManager` Lua scripts may not be available on the Redis version used in `infra/redis/`                                  | High — atomic decrement would be silently broken, allowing races                                                                      | Verify Redis version ≥ 2.6 (required for Lua scripting) in `infra/redis/redis.conf` at the start of T1; fail fast with a clear startup error if not met                                                                           |
| Spring Security filter ordering — placing the rate-limit filter before the JWT auth filter may conflict with an existing `SecurityFilterChain` order | High — if JWT extraction happens before rate-limiting, the `jwt.sub` key path (FR-01) still works but the filter order violates BR-06 | Add an explicit `@Order` or `addFilterBefore(RateLimitFilter, UsernamePasswordAuthenticationFilter.class)` in `RateLimitConfig`; add a test that inspects the filter chain order at context startup                               |
| `X-Forwarded-For` header handling — if Nginx is configured to append rather than replace, the leftmost entry may still be a client-controlled value  | Medium — allows IP spoofing to bypass rate limiting for unauthenticated paths                                                         | Confirm Nginx is configured with `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` (append) and document that `ClientIpExtractor` must take the _first_ (original client) entry; add a test with a multi-value header |
| Initial numeric values for token capacity and refill rate may be mis-sized for the 12,000-user peak                                                  | Medium — too tight causes false 429s; too relaxed offers no protection                                                                | Set concrete defaults in `application.yml` (registration-tier: 5 tokens / 10 s; browsing-tier: 100 tokens / 60 s); explicitly note these are tunable; flag for load-test validation before production deploy                      |
| `rate_limiter.redis_unavailable` counter not picked up by Grafana dashboard `redis-bucket4j.json`                                                    | Low — operational blind spot during Redis degradation                                                                                 | As part of T4, verify the counter appears in `/actuator/prometheus` output; update `infra/grafana/dashboards/redis-bucket4j.json` with a panel for this metric                                                                    |

## Open Questions

None.
