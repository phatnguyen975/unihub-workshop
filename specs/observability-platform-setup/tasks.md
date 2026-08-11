# Implementation Plan: Observability Platform Setup

> **Spec:** specs/observability-platform-setup/SPEC.md  
> **Status:** APPROVED

## Overview

This plan wires the full observability stack into the UniHub runtime. The Prometheus, Grafana, and Docker Compose infrastructure already exists in `infra/` and is running — this plan does not recreate it. What remains is: activating Micrometer + Actuator on the backend and workers applications with the correct tags and endpoint restrictions; implementing the HTTP request-logging filter with MDC lifecycle management and dual-profile Logback configuration; adding the Nginx location block that denies external access to `/actuator/*`; correcting two small gaps in existing infra files (Prometheus retention and Grafana `disableDeletion`); and adding the `ModulithArchitectureTest` that enforces Spring Modulith boundaries on the new `shared/` packages.

## Architecture Decisions for This Plan

- **Backend Actuator (T1) before Workers Actuator (T2)** — same pattern applied to both runtimes; verifying the approach works on the backend first makes the workers task an S-sized application of a proven recipe rather than an independent M.
- **Logging filter (T3) after T1** — the filter lives in `shared/web` and depends on `processedBy` resolved from application config; those config properties are established in T1's `application.yml` changes, making T1 a concrete prerequisite.
- **Nginx block (T4) after T1** — T4 requires an actual running `/actuator/prometheus` endpoint to verify that the block correctly denies external access while not breaking the internal scrape path; T1 provides that endpoint.
- **Infra IaC corrections (T5) are independent of application tasks** — `dashboards.yml` and `docker-compose.yml` edits touch only infra files and can be verified purely via container restart without any application code; they are placed in their own phase to keep the diff reviewable and atomic.
- **`ModulithArchitectureTest` (T6) last** — it must reference all `shared/` packages that the feature creates; those packages are created across T1 (`shared/config`) and T3 (`shared/web`). Writing T6 before both would produce an incomplete architecture test.
- **Workers logging filter: out of scope** — FR-17 through FR-22 explicitly target the Core Backend Monolith. The Workers application has no inbound HTTP endpoints served to clients; it is a consumer-side runtime. No logging filter is implemented for workers.

## Task List

### Phase 1: Actuator & Micrometer Activation

_Highest-risk phase: Spring Boot auto-configuration wiring can fail silently (wrong property keys, classpath conflicts). Verifying both runtimes emit correct metrics before proceeding eliminates the risk of building later tasks on a broken foundation._

- [x] **T1 — Backend emits a valid Prometheus metrics payload at `/actuator/prometheus`, tagged with `application="unihub-backend"` and `host`, with all other Actuator endpoints returning 404**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Config/Infrastructure (`shared/config`), Resources (`application.yml`, profile YAMLs), Test
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` — add `management.endpoints.web.exposure.include: prometheus,health`, `management.endpoint.health.show-details: never`, set `spring.application.name`, configure `management.metrics.tags.application` and `management.metrics.tags.host`
    - `apps/backend/src/main/resources/application-dev.yml` — `[NEW]` dev-profile overrides (e.g. `spring.application.name`, logging level)
    - `apps/backend/src/main/resources/application-prod.yml` — `[NEW]` prod-profile overrides
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/ObservabilityConfig.java` — `[NEW]` `@Configuration` that programmatically registers `application` and `host` common tags on `MeterRegistry` (covers FR-05, FR-06 where property-based tags are insufficient)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/ObservabilityConfigTest.java` — `[NEW]` unit test: `MeterRegistry` created with `SimpleMeterRegistry`, verifies `application` and `host` tags appear on a sample meter
  - **Acceptance Criteria:**
    - Unit test passes: `ObservabilityConfigTest` verifies `application` and `host` tags are registered — traced to FR-05, FR-06
    - `GET /actuator/prometheus` (direct, not via Nginx) returns HTTP `200` with `Content-Type: text/plain` and body containing `jvm_memory_used_bytes{application="unihub-backend",...}`, `hikaricp_connections_active`, `tomcat_threads_current_threads`, `logback_events_total` — traced to Scenarios 1, 3 (FR-01, FR-02, FR-04, FR-05)
    - `GET /actuator/env` (direct) returns HTTP `404` — traced to Scenario 2 (FR-03)
    - `./gradlew test` passes clean in `apps/backend`

- [x] **T2 — Workers emits a valid Prometheus metrics payload at its `/actuator/prometheus`, tagged with `application="unihub-workers"`, with all other Actuator endpoints returning 404**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Config/Infrastructure (`shared/config`), Resources (`application.yml`, profile YAMLs), Test
  - **Files:**
    - `apps/workers/src/main/resources/application.yml` — add `management.endpoints.web.exposure.include: prometheus,health`, configure `management.metrics.tags.application=unihub-workers`, `management.metrics.tags.host`
    - `apps/workers/src/main/resources/application-dev.yml` — `[NEW]` dev-profile overrides
    - `apps/workers/src/main/resources/application-prod.yml` — `[NEW]` prod-profile overrides
    - `apps/workers/src/main/java/com/unihub/workshop/workers/shared/config/ObservabilityConfig.java` — `[NEW]` registers `application=unihub-workers` and `host` common tags
    - `apps/workers/src/test/java/com/unihub/workshop/workers/shared/config/ObservabilityConfigTest.java` — `[NEW]` same unit test pattern as T1, verifies `unihub-workers` tag
  - **Acceptance Criteria:**
    - Unit test passes: `ObservabilityConfigTest` verifies `application="unihub-workers"` tag — traced to FR-05, BR-07
    - `GET http://localhost:8083/actuator/prometheus` returns HTTP `200` with label `application="unihub-workers"` distinct from `unihub-backend` — traced to Scenario 4 (FR-01, FR-02, FR-05)
    - `GET http://localhost:8083/actuator/env` returns HTTP `404` — traced to FR-03
    - `./gradlew test` passes clean in `apps/workers`

#### Checkpoint: Phase 1

- [x] `./gradlew test` passes in both `apps/backend` and `apps/workers` with zero failures
- [x] Both `/actuator/prometheus` endpoints reachable directly (bypassing Nginx), returning Prometheus text with correct `application` labels
- [x] Both `/actuator/env` return `404`
- [x] Phase 2 may begin

### Phase 2: HTTP Request Logging Filter

_This is the most logic-dense new code: MDC lifecycle management, log level by HTTP status, dual-profile Logback, and `traceId` propagation. Isolated in its own phase so it can be reviewed and verified on its own merits._

- [x] **T3 — Every inbound HTTP request to the backend produces one structured log entry with `traceId`, `correlationId`, `processedBy`, `clientIp`, `userAgent`, and all FR-19 fields; log format switches between human-readable (dev) and JSON (prod) by Spring profile; MDC is cleared after each request**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Web/Filter (`shared/web`), Config (`shared/config`), Resources (`logback-spring.xml`), Test
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/web/RequestLoggingFilter.java` — `[NEW]` `OncePerRequestFilter`: generates `traceId` (UUID), reads `X-Correlation-ID`, `User-Agent`, extracts client IP, resolves `processedBy` from config, injects all into MDC before delegating to chain, logs a single entry after response commit with all fields (level driven by response status: `INFO` for 2xx/3xx, `WARN` for 4xx, `ERROR` for 5xx), clears MDC in `finally`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/WebConfig.java` — `[NEW]` registers `RequestLoggingFilter` as `FilterRegistrationBean` at `Ordered.HIGHEST_PRECEDENCE` so it fires before Spring Security; maps to `/*`
    - `apps/backend/src/main/resources/logback-spring.xml` — `[NEW]` dual-profile config: `<springProfile name="dev">` → `PatternLayoutEncoder` with format `[%level] [%X{traceId}] %X{http.method} %X{http.uri} -> %X{http.status} (%X{duration_ms}ms) -> %X{processedBy}`; `<springProfile name="prod">` → `LogstashEncoder` JSON appender (dependency already on classpath: `logstash-logback-encoder`)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/web/RequestLoggingFilterTest.java` — `[NEW]` unit tests using `MockHttpServletRequest` / `MockHttpServletResponse` / `MockFilterChain`: (a) MDC contains all required fields during chain execution; (b) MDC is empty after filter completes; (c) log level is `INFO` for 200, `WARN` for 404, `ERROR` for 500; (d) missing headers fallback correctly; (e) filter exception does not swallow the original response
  - **Acceptance Criteria:**
    - All five `RequestLoggingFilterTest` test cases pass — traced to FR-17, FR-18, FR-19, FR-20, Scenarios 9, 12, 15, BR-11
    - `./gradlew test` passes clean
    - Manual smoke (`dev` profile): any request produces a single-line text log matching the pattern `[LEVEL] <uuid> METHOD /path -> STATUS (Xms) -> nodeId` — traced to Scenario 10 (FR-21)
    - Manual smoke (`prod` profile): any request produces a JSON line on stdout containing `traceId` at root and `mdc.correlationId`, `mdc.http.method`, `mdc.http.uri`, `mdc.http.status`, `mdc.duration_ms`, `mdc.processedBy`, `mdc.clientIp`, `mdc.userAgent` in the mdc object — traced to Scenario 9 (FR-19, FR-21)
    - Two sequential requests on the same thread produce two separate log entries with distinct `traceId` values, no MDC bleeding — traced to Scenario 12 (FR-20)
    - An unauthenticated request (no `Authorization` header) still produces a log entry and the request is not blocked — traced to Scenario 15 (BR-11)

#### Checkpoint: Phase 2

- [x] All unit tests pass (`./gradlew test` in `apps/backend`)
- [x] Manual smoke confirms correct log format in both `dev` and `prod` profiles
- [x] No MDC bleeding observed between two sequential requests
- [x] Phase 3 may begin

### Phase 3: Security Boundary & Infra IaC Corrections

_Two independent capabilities — Nginx security block and infra file fixes — grouped together because both are pure configuration changes with no application code dependency, and both are verifiable by restarting containers._

- [x] **T4 — Nginx returns 404 for any request to `/actuator/*` from external callers, while internal Prometheus scraping continues to reach backend nodes directly**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Infrastructure (Nginx config)
  - **Files:**
    - `infra/nginx/templates/default.conf.template` — `[MODIFY]` add `location ~ ^/actuator { return 404; }` before the catch-all `location /` block
  - **Acceptance Criteria:**
    - `curl http://localhost/actuator/prometheus` (through Nginx port 80) returns `404` — traced to Scenario 13 (FR-23, BR-01)
    - `curl http://localhost/actuator/health` (through Nginx port 80) returns `404` — traced to FR-24
    - `curl http://localhost:8081/actuator/prometheus` (direct, bypassing Nginx) still returns `200` — confirms internal scrape path is intact
    - Prometheus UI `/targets` still shows backend and workers scrape targets as `UP` after Nginx config reload — traced to Scenario 5 (FR-07, FR-08)

- [x] **T5 — Prometheus retains metrics for ≥ 15 days and Grafana IaC-provisioned dashboards are protected from accidental deletion via the UI**
  - **Size:** S
  - **Depends on:** —
  - **Layers touched:** Infrastructure (Prometheus Docker config, Grafana provisioning YAML)
  - **Files:**
    - `infra/docker-compose.yml` — `[MODIFY]` change `--storage.tsdb.retention.time=7d` to `--storage.tsdb.retention.time=15d` on the `prometheus` service command (line 207)
    - `infra/grafana/provisioning/dashboards/dashboards.yml` — `[MODIFY]` change `disableDeletion: false` to `disableDeletion: true` (currently violates FR-15)
  - **Acceptance Criteria:**
    - Prometheus service starts with `--storage.tsdb.retention.time=15d` flag visible in container inspect — traced to FR-09
    - After Grafana container restart: an Admin user sees all six dashboards in the Grafana UI without re-importing — traced to Scenario 6, Scenario 14 (FR-12, FR-16, FR-26, FR-27)
    - An Admin user deleting an IaC-provisioned dashboard sees it reappear on the next Grafana reload — traced to Scenario 8 (FR-15, BR-02)
    - All Prometheus scrape targets (backend, workers, rabbitmq, minio) show `UP` — traced to Scenario 5 (FR-07, FR-08)

#### Checkpoint: Phase 3

- [x] `curl http://localhost/actuator/prometheus` through Nginx returns `404`
- [x] `curl http://localhost:8081/actuator/prometheus` direct returns `200`
- [x] Prometheus retention flag is `15d` (verified via `docker inspect unihub-prometheus`)
- [x] All Prometheus targets show `UP`
- [x] Grafana shows all 6 dashboards after a cold restart, and `disableDeletion` prevents permanent deletion
- [x] Phase 4 may begin

### Phase 4: Modulith Architecture Enforcement

_Final task — depends on all shared packages being fully established by T1 and T3. This test enforces the project's modular boundary rules as a permanent, build-breaking guard._

- [x] **T6 — A build-breaking `ModulithArchitectureTest` verifies that `shared/config`, `shared/web`, and `shared/observability` do not violate Spring Modulith package boundary rules**
  - **Size:** S
  - **Depends on:** T1, T3
  - **Layers touched:** Test (`src/test`)
  - **Files:**
    - `apps/backend/src/test/java/com/unihub/workshop/ModulithArchitectureTest.java` — `[NEW]` uses `ApplicationModules.of(WorkshopApplication.class).verify()` to assert that all Spring Modulith module boundaries — including the new `shared/config` and `shared/web` packages — are respected; runs as a standard JUnit 5 test via `./gradlew test`
  - **Acceptance Criteria:**
    - `ModulithArchitectureTest` passes: zero boundary violations reported — traced to the project's mandatory architectural enforcement convention (`docs/project-structure.md §Internal Module Communication`)
    - `./gradlew test` passes clean in `apps/backend` including this test

#### Checkpoint: Feature Complete

- [x] All tasks T1–T6 are checked off
- [x] `./gradlew test` passes in `apps/backend` (covers T1, T3, T6) and `apps/workers` (covers T2) with zero failures
- [x] Every acceptance criterion across all tasks has been verified end-to-end (not just unit-tested)
- [x] Prometheus UI `/targets`: all jobs `UP`
- [x] Grafana: all 6 dashboards loaded without manual import, `disableDeletion` confirmed active
- [x] Nginx: `/actuator/*` blocked externally, internal scrape unaffected
- [x] Ready for `/review observability-platform-setup`

## Risks and Mitigations

| Risk                                                                                                                                                                                                                 | Impact | Mitigation                                                                                                                                                                               |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OncePerRequestFilter` registration order may place the logging filter _after_ the Spring Security filter chain — security-rejected requests (401/403) would bypass logging, violating BR-11                         | Medium | Register at `Ordered.HIGHEST_PRECEDENCE` in `WebConfig`; add a dedicated unit test that asserts the filter logs even when the downstream chain throws a security exception               |
| `logback-spring.xml` introduced in T3 may conflict with existing `logging.*` keys in `application.yml` — Spring Boot loads `logback-spring.xml` first and ignores YAML logging properties if the XML file is present | Low    | Remove any `logging.*` keys from `application.yml` that would duplicate `logback-spring.xml` configuration; verify in manual smoke that log output format switches correctly per profile |
| Grafana `dashboards.yml` uses `disableDeletion` (camelCase) vs the Grafana API field name `disable_deletion` (snake_case) — wrong casing would silently ignore the setting                                           | Low    | T5 verifies the setting empirically by performing a delete action in the Grafana UI after container restart and confirming the dashboard reappears                                       |
