# Spec: Observability Platform Setup

> **Status:** APPROVED  
> **Owner/area:** `shared/observability` (Core Backend), `shared/config` (Core Backend + Workers), `infra/prometheus/`, `infra/grafana/`  
> **Last revised:** 2026-07-22 — first version

## 1. Overview

`observability-platform-setup` establishes the foundational observability and telemetry infrastructure across the Core Backend Monolith and the Background Workers runtime. It enables real-time monitoring of JVM health, application-layer behaviour, and infrastructure dependencies by:

1. Activating a **Micrometer** metrics registry backed by the **Prometheus** format and exposing metrics at the Spring Boot Actuator endpoint `/actuator/prometheus`.
2. Configuring **Prometheus** pull-based scraping of every Spring Boot instance (backend and workers), as well as infrastructure services (MinIO, RabbitMQ), as Infrastructure-as-Code inside `infra/prometheus/`.
3. Provisioning six **Grafana** base dashboards via IaC (`infra/grafana/`) to visualise JVM, HikariCP, Redis/Bucket4j, RabbitMQ, Resilience4j, and MinIO telemetry.
4. Implementing a **structured JSON HTTP request-logging filter** that injects `traceId`, `correlationId`, and `processedBy` into the MDC context, producing log output conformant with the format defined in `docs/api-standards.md`.

This feature is cross-cutting infrastructure — it does not instrument specific business metrics (e.g. capacity drift counters, rate-limit drop counts). Business features add those custom metrics against the Micrometer registry configured here. The feature must be deployed early, before or alongside the first functional feature, so that every subsequent feature benefits from live telemetry from day one.

## 2. Actors & Preconditions

| Actor                     | Precondition                                                                                                                                                              |
| :------------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Platform / DevOps**     | Prometheus and Grafana containers defined in `infra/docker-compose.yml` must be up and reachable.                                                                         |
| **Core Backend Monolith** | Spring Boot application context is started; `spring-boot-starter-actuator` and `micrometer-registry-prometheus` dependencies are on the classpath.                        |
| **Workers Application**   | Spring Boot Workers application context is started; same Actuator + Micrometer dependencies are on the classpath.                                                         |
| **Prometheus**            | `infra/prometheus/prometheus.yml` references the correct scrape targets (backend, workers, minio, rabbitmq) before the container starts.                                  |
| **Grafana**               | `infra/grafana/provisioning/` directories contain the datasource and dashboard provider YAML files before the container starts, enabling automatic loading on first boot. |
| **HTTP clients**          | Any live request to the backend or workers is a precondition for the logging filter to emit log entries.                                                                  |

## 3. Functional Requirements

### 3.1 Micrometer Registry & Actuator Exposure

- **FR-01:** The system SHALL activate a Micrometer `PrometheusMeterRegistry` on both the Core Backend Monolith and the Workers application so that all auto-configured and custom metrics are accumulated in Prometheus format.
- **FR-02:** The system SHALL expose the `/actuator/prometheus` endpoint on both the Core Backend Monolith and the Workers application, returning a plain-text Prometheus metrics payload with `Content-Type: text/plain; version=0.0.4; charset=utf-8`.
- **FR-03:** The system SHALL restrict Actuator management endpoint exposure so that only `/actuator/prometheus` and `/actuator/health` are accessible over the primary application HTTP port; all other Actuator endpoints (e.g., `/actuator/env`, `/actuator/beans`, `/actuator/loggers`) SHALL be disabled entirely to prevent configuration leakage.
- **FR-04:** The system SHALL enable the following default auto-configured Micrometer binder groups without any custom code:
  - JVM metrics (memory pools, GC counts and durations, thread states, class loading);
  - Tomcat/embedded-server metrics (active threads, requests processed, error count);
  - HikariCP metrics (pool size, active connections, pending threads, acquisition latency);
  - Logback metrics (log event counts by level: `ERROR`, `WARN`, `INFO`).
- **FR-05:** The system SHALL tag every metric emitted by the Core Backend with a `application` tag set to a configurable application name (e.g. `unihub-backend`) and every metric emitted by the Workers with a tag set to `unihub-workers`, so that Prometheus queries can distinguish the two runtimes.
- **FR-06:** The system SHALL add a `host` or `instance` tag to all metrics, set to the hostname or container ID of the running JVM instance, so that per-node metrics can be distinguished in a horizontally scaled deployment.

### 3.2 Prometheus Configuration (IaC)

- **FR-07:** The system SHALL provide a `prometheus.yml` file in `infra/prometheus/` that defines scrape jobs for:
  - All Spring Boot instances (Core Backend and Workers) targeting `/actuator/prometheus`.
  - MinIO targeting `/minio/v2/metrics/cluster`.
  - RabbitMQ targeting `/metrics` (via the `rabbitmq_prometheus` plugin on port 15692).
- **FR-08:** The system SHALL configure a scrape interval of 15 seconds or lower in `prometheus.yml` for all UniHub scrape jobs, so that dashboard refresh cycles reflect near-real-time system state.
- **FR-09:** The system SHALL configure Prometheus with a retention period sufficient for at least 15 days of metrics storage under normal load (configurable via environment variable in `docker-compose.yml`).
- **FR-10:** The `prometheus.yml` file SHALL be referenced by the `prometheus` Docker service in `infra/docker-compose.yml` as a volume mount, so that configuration changes take effect on container restart without rebuilding the image.

### 3.3 Grafana Dashboard Provisioning (IaC)

- **FR-11:** The system SHALL provision a Prometheus datasource in Grafana by providing a YAML file at `infra/grafana/provisioning/datasources/prometheus.yml` that points to the Prometheus container's internal Docker network address, so that dashboards can query metrics without manual UI configuration.
- **FR-12:** The system SHALL provision a file-based dashboard provider in Grafana by providing a YAML file at `infra/grafana/provisioning/dashboards/dashboards.yml` that instructs Grafana to load all JSON files from the `infra/grafana/dashboards/` directory on startup.
- **FR-13:** The system SHALL provide the following six Grafana dashboard JSON files in `infra/grafana/dashboards/`:
  | File | Metrics Coverage |
  | :---------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------- |
  | `jvm-spring-boot.json` | JVM heap/non-heap memory, GC pause durations, live thread count, CPU usage, uptime |
  | `database-hikaricp.json` | HikariCP active connections, connection acquisition latency, pool pending threads, connection timeouts |
  | `redis-bucket4j.json` | Redis command latency, Bucket4j rate-limit drop count (HTTP 429), Redis availability failures (`rate_limiter.redis_unavailable` counter) |
  | `rabbitmq-metrics.json` | Queue depth per queue, consumer count, delivery rate, DLQ message count, consumer lag |
  | `resilience4j-circuit-breaker.json` | Circuit breaker state transitions (CLOSED/OPEN/HALF_OPEN), failure rate, slow call rate, not-permitted calls |
  | `minio-storage.json` | MinIO bucket object count, total storage used, request error rate |
- **FR-14:** Each Grafana dashboard JSON file SHALL be parameterised with template variables for `datasource` and `instance` (or `job`) so that dashboards function correctly in both single-node and multi-node deployments without modification.
- **FR-15:** The system SHALL set Grafana to provision dashboards in `disableDelete: true` mode for the file-based provider, so that IaC-managed dashboards cannot be accidentally deleted from the Grafana UI.
- **FR-16:** The system SHALL configure the Grafana container in `infra/docker-compose.yml` with volume mounts for both `infra/grafana/provisioning/` and `infra/grafana/dashboards/`, ensuring all provisioning files are present at first boot.

### 3.4 Structured JSON Request Logging Filter

- **FR-17:** The system SHALL implement an HTTP request-logging filter on the Core Backend Monolith that intercepts every inbound request and, after the response is committed, writes a single structured log entry per request.
- **FR-18:** For every intercepted request, the logging filter SHALL inject the following values into the MDC context before delegating to the filter chain, so that all downstream log statements within the same request thread carry these fields automatically:
  | MDC key | Source |
  | :-------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | `traceId` | Server-generated unique identifier for the request (e.g., UUID or 16-character hex string); also written to the `metadata.traceId` field of success responses per `docs/api-standards.md`. |
  | `correlationId` | Value of the inbound `X-Correlation-ID` request header; if absent, an empty string or omitted. |
  | `processedBy` | Hostname or configured node ID of the Spring Boot instance handling the request (same value written to `metadata.processedBy` in success responses). |
- **FR-19:** The structured log entry produced by the filter SHALL include at minimum the following fields in the exact structure required by `docs/api-standards.md §API Logging` production JSON format:
  | Field | Description |
  | :------------------ | :-------------------------------------------------------------- |
  | `@timestamp` | ISO 8601 UTC timestamp of log emission |
  | `level` | Log level (`INFO` for 2xx/3xx; `WARN` for 4xx; `ERROR` for 5xx) |
  | `logger_name` | Fully-qualified class name of the logging filter |
  | `message` | Fixed string `"API Request Completed"` |
  | `traceId` | Same value as MDC `traceId` |
  | `mdc.correlationId` | Inbound `X-Correlation-ID` header value |
  | `mdc.http.method` | HTTP method (e.g., `GET`, `POST`) |
  | `mdc.http.uri` | Request URI path |
  | `mdc.http.status` | HTTP response status code as a string |
  | `mdc.duration_ms` | Total request duration in milliseconds |
  | `mdc.processedBy` | Hosting node ID |
  | `mdc.clientIp` | Client IP address as observed by the application |
  | `mdc.userAgent` | `User-Agent` request header value |
- **FR-20:** The logging filter SHALL always clear the MDC context after the response is committed and before the thread returns to the pool, to prevent MDC leakage between requests in a thread-pool environment.
- **FR-21:** The system SHALL configure `logback-spring.xml` with two profiles as per API Standards:
  - Under the `dev` Spring profile: a human-readable text appender in the format `[LEVEL] traceId METHOD URI -> STATUS (DURATIONms) -> PROCESSED_BY`.
  - Under the `prod` Spring profile: a structured JSON appender (e.g., via `logstash-logback-encoder`) producing the JSON format specified in FR-19.
- **FR-22:** The `traceId` generated by the logging filter SHALL also be injected into the `metadata.traceId` field of every successful API response envelope, so that clients can correlate their request to server-side logs using the trace identifier.

### 3.5 Nginx & Security Boundary

- **FR-23:** The system SHALL configure the Nginx API gateway to explicitly deny public external access to any `/actuator/*` path. Prometheus SHALL scrape the `/actuator/prometheus` endpoint directly from the backend nodes via the internal network.
- **FR-24:** The system SHALL ensure that the `/actuator/health` endpoint is accessible only for internal health-check or load-balancer probing purposes; it SHALL NOT be publicly exposed to students, organizers, or staff.

### 3.6 Docker Compose Wiring

- **FR-25:** The system SHALL define `prometheus` and `grafana` services in `infra/docker-compose.yml`, each connected to the same internal Docker network as the backend and workers services, so that pull-based scraping can reach the `/actuator/prometheus` endpoints by container hostname.
- **FR-26:** The system SHALL configure the `grafana` Docker service to persist dashboard and datasource state across container restarts via a named Docker volume.
- **FR-27:** The system SHALL configure the `prometheus` Docker service to persist its TSDB (time-series storage) across container restarts via a named Docker volume, so that metric history is not lost on container restart.
- **FR-28:** The system SHALL expose Grafana's HTTP port to the host machine (e.g., `3000:3000`) and Prometheus's HTTP port (e.g., `9090:9090`) in the `docker-compose.yml` port mappings, for local developer access.

## 4. Business Rules

- **BR-01:** The `/actuator/prometheus` endpoint must NOT be accessible to end-users (students, organizers, staff) through the Nginx gateway. Prometheus scrapes this endpoint directly from the internal Docker network. _(Source: `docs/feature-catalog.md §10.3`, `docs/project-structure.md §infra/`)_
- **BR-02:** Grafana dashboards must be provisioned via IaC (file-based YAML + JSON) — not created manually in the Grafana UI. This ensures dashboards survive container recreation and are version-controlled. _(Source: `docs/feature-catalog.md §10.3` key constraint, `docs/adrs.md ADR-030`)_
- **BR-03:** The Prometheus pull model must be used. The backend and workers must never push metrics to Prometheus. _(Source: `docs/adrs.md ADR-030` — pull-model rationale)_
- **BR-04:** The HTTP logging filter must inject `traceId`, `correlationId`, and `processedBy` into the MDC context for every request, and must clear the MDC after the response is committed. _(Source: `docs/feature-catalog.md §10.3` key constraint, `docs/api-standards.md §API Logging`)_
- **BR-05:** The `traceId` used in MDC and log entries must be identical to the `metadata.traceId` value returned to the client in the success response envelope. _(Source: `docs/api-standards.md §Standard JSON Payloads`, `docs/api-standards.md §API Logging`)_
- **BR-06:** Default JVM, Tomcat, HikariCP, and Logback metrics must be enabled automatically via Micrometer auto-configuration; these must not require custom instrumentation code within this feature. _(Source: `docs/adrs.md ADR-030`)_
- **BR-07:** The `application` common tag must be distinct for the backend and workers instances so that Prometheus and Grafana can distinguish metric sources when both runtimes are scraped. _(Source: `docs/feature-catalog.md §10.3`, `docs/adrs.md ADR-030`)_
- **BR-08:** This feature does NOT configure custom business metrics (e.g., `capacity.drift.detected`, `rate_limiter.redis_unavailable`, circuit-breaker-specific counters). Those are added by the respective business features using the registry configured here. _(Source: `docs/feature-catalog.md §10.3` description)_
- **BR-09:** The Resilience4j base configuration (enabling metric emission for circuit breaker state transitions) is owned by `resilience-platform-setup`, not by this feature. This feature only provisions the Grafana dashboard that visualises those metrics. _(Source: `docs/feature-catalog.md §10.2` vs `§10.3`)_
- **BR-10:** Actuator endpoints other than `/actuator/prometheus` and `/actuator/health` must be disabled entirely. Exposing `/actuator/env` or `/actuator/beans` would leak secrets and internal configuration. _(Source: `docs/adrs.md ADR-005`, general security principle)_
- **BR-11:** The logging filter must operate on all inbound requests, including unauthenticated ones (e.g., Prometheus scrape of `/actuator/prometheus`, `POST /api/v1/payments/webhook`). The absence of a JWT must not prevent the filter from logging. _(Source: `docs/api-standards.md §API Logging`)_
- **BR-12:** Log output format must be controlled by the active Spring profile (`dev` vs `prod`) via `logback-spring.xml`. Structured JSON output is mandatory in the `prod` profile. _(Source: `docs/api-standards.md §API Logging`)_
- **BR-13:** The `processedBy` value in MDC and response metadata must reflect the actual hostname or node identifier of the handling Spring Boot instance, enabling per-node troubleshooting in a horizontally scaled deployment. _(Source: `docs/api-standards.md §Standard JSON Payloads metadata.processedBy`)_
- **BR-14:** PostgreSQL is not included in the local `infra/` Docker stack (it is Neon serverless). The observability setup must not attempt to provision or scrape a local PostgreSQL container. _(Source: `docs/project-structure.md §Infrastructure — "PostgreSQL is intentionally omitted from the local infra/ stack"`)_

## 5. State Transitions

**N/A** — this feature does not introduce or modify any stateful business entity. The Micrometer registry, Prometheus scrape configuration, and Grafana provisioning files are static infrastructure configuration with no lifecycle state machine.

## 6. Non-Functional Requirements

- **Performance:** The Micrometer `PrometheusMeterRegistry` accumulates metrics in-memory and produces the text payload on demand. The `/actuator/prometheus` endpoint must respond within 200 ms under normal load. Metric collection overhead must not add more than 1 ms to the median request latency.
- **Concurrency:** The Micrometer registry is thread-safe by design. The HTTP logging filter must be safe under the full concurrent request load (12,000+ concurrent users during the registration window), as it runs on the same Tomcat thread pool as all other requests.
- **Idempotency:** The Grafana and Prometheus provisioning files are declarative. Restarting or recreating the containers must result in the same provisioned state every time (idempotent provisioning). Named Docker volumes must preserve TSDB and Grafana state across restarts.
- **Rate-limiting:** The `/actuator/prometheus` endpoint must be excluded from the Bucket4j rate-limiter filter, as Prometheus scrapes it on a fixed interval and must not be throttled.
- **Security:**
  - `/actuator/prometheus` must not be reachable via Nginx from external clients.
  - No credentials, secrets, or JWT tokens may appear in any log entry produced by the logging filter.
  - All Grafana admin credentials must be configured via environment variables, never hardcoded in `docker-compose.yml` or provisioning files.
  - MDC context must be cleared after every request to prevent data leakage between threads.
- **Reliability / Failure modes:**
  - If Prometheus is temporarily unavailable, the backend and workers continue operating normally; the `/actuator/prometheus` endpoint remains available and Prometheus will resume scraping when it recovers.
  - If Grafana is unavailable, it has no impact on the application's ability to serve requests.
  - The logging filter must not suppress or swallow the original request/response even if an internal filter exception occurs; any filter-internal error must be caught, logged minimally, and the request must complete normally.
- **Observability:** This feature is the observability foundation itself. The health of this feature is self-evidencing: if `/actuator/prometheus` is reachable and returns metric data, and Grafana dashboards display live series, the feature is functioning correctly.

## 7. Acceptance Criteria

### Scenario 1: Prometheus endpoint returns metrics on the backend (FR-01, FR-02, FR-04)

- **Given** the Core Backend Monolith is started
- **When** a GET request is made to its `/actuator/prometheus` endpoint from within the internal network
- **Then** the response HTTP status is `200 OK`
- **And** the `Content-Type` header contains `text/plain`
- **And** the response body contains metric families including `jvm_memory_used_bytes`, `hikaricp_connections_active`, `tomcat_threads_current_threads`, and `logback_events_total`

### Scenario 2: Non-health, non-prometheus Actuator endpoints are inaccessible (FR-03)

- **Given** the Core Backend Monolith is running
- **When** a GET request is made to `/actuator/env`
- **Then** the response HTTP status is `404 Not Found` or `401 Unauthorized`
- **And** the endpoint does not return any application configuration or environment data

### Scenario 3: Metrics carry application and host tags (FR-05, FR-06)

- **Given** the Core Backend Monolith is running with `application` tag configured as `unihub-backend`
- **When** the `/actuator/prometheus` payload is inspected
- **Then** all metric families include a label `application="unihub-backend"`
- **And** all metric families include a label identifying the specific hosting instance

### Scenario 4: Workers application exposes its own Prometheus endpoint (FR-01, FR-02, FR-05)

- **Given** the Workers application is started
- **When** a GET request is made to its `/actuator/prometheus` endpoint
- **Then** the response is `200 OK`
- **And** the metrics payload includes a label `application="unihub-workers"` distinct from the backend's label

### Scenario 5: Prometheus scrapes all targets successfully (FR-07, FR-08, FR-25)

- **Given** the full infrastructure stack is running
- **And** `prometheus.yml` references the correct internal hostnames (backend, workers, minio, rabbitmq)
- **When** the Prometheus UI at `/targets` is loaded
- **Then** all scrape targets show `State: UP`
- **And** the last successful scrape time is within the configured scrape interval

### Scenario 6: Grafana starts with all six dashboards pre-provisioned (FR-11, FR-12, FR-13, FR-16)

- **Given** the Docker Compose stack is running
- **And** the Grafana container has provisioning directories mounted
- **When** a user logs into Grafana without importing any dashboard manually
- **Then** the Grafana Dashboards list contains exactly the following dashboards: JVM & Spring Boot, Database – HikariCP, Redis & Bucket4j, RabbitMQ Metrics, Resilience4j Circuit Breaker, MinIO Storage

### Scenario 7: Grafana dashboards display live data from Prometheus (FR-11, FR-13, FR-14)

- **Given** Prometheus is successfully scraping the backend
- **When** a user opens the `JVM & Spring Boot` dashboard and sets the time range to "Last 5 minutes"
- **Then** at least one panel (e.g., JVM Memory Used) displays a non-zero data series
- **And** the `instance` template variable is populated with the scraped instance labels

### Scenario 8: IaC-provisioned dashboards cannot be permanently deleted via UI (FR-15)

- **Given** a Grafana Admin user is logged in
- **When** the user deletes an IaC-provisioned dashboard from the Grafana UI
- **Then** the dashboard reappears upon the next Grafana restart or reload
- **And** the source JSON file in `infra/grafana/dashboards/` is unchanged

### Scenario 9: Structured JSON log entry is emitted for each request in prod profile (FR-17, FR-18, FR-19, FR-21)

- **Given** the Core Backend Monolith is running with the `prod` Spring profile
- **When** a GET request with header `X-Correlation-ID: req-abc123` is made to `/api/v1/workshops`
- **Then** a JSON log entry is written to stdout containing: a top-level `traceId` field with a non-empty value; `mdc.correlationId` equal to `req-abc123`; `mdc.http.method` equal to `GET`; `mdc.http.uri` equal to `/api/v1/workshops`; `mdc.http.status` matching the response status code; `mdc.duration_ms` with a positive integer; and `mdc.processedBy` with the configured node ID

### Scenario 10: Human-readable text log is emitted in dev profile (FR-21)

- **Given** the Core Backend Monolith is running with the `dev` Spring profile
- **When** a POST request is made to `/api/v1/auth/login`
- **Then** a log line matching the format `[LEVEL] traceId POST /api/v1/auth/login -> STATUS (DURATIONms) -> PROCESSED_BY` is written to stdout

### Scenario 11: traceId is consistent between log entry and success response (FR-22, FR-19)

- **Given** the Core Backend Monolith is running
- **When** a GET request is made to any resource endpoint that returns a 2xx response
- **Then** the JSON response body contains `metadata.traceId` with a non-empty string value
- **And** the corresponding structured log entry contains a `traceId` field with the identical value

### Scenario 12: MDC is cleared between requests (FR-20)

- **Given** the Core Backend Monolith is running under a thread-pool model
- **When** two successive requests are processed (potentially on the same thread)
- **Then** the MDC fields (`traceId`, `correlationId`, `processedBy`) of the first request do not appear in the log entry of the second request
- **And** each log entry contains only MDC values from that specific request

### Scenario 13: /actuator/prometheus is not routable through Nginx (FR-23)

- **Given** Nginx is running as the public-facing gateway
- **When** an external HTTP client makes a GET request to the public gateway path `/actuator/prometheus`
- **Then** Nginx returns `404 Not Found` or `403 Forbidden`
- **And** the backend metrics payload is not exposed to the external caller

### Scenario 14: Container restart preserves metrics history and dashboards (FR-26, FR-27)

- **Given** Prometheus has scraped metrics for at least 5 minutes and Grafana has loaded all dashboards
- **When** the `prometheus` and `grafana` containers are stopped and restarted (volumes are preserved)
- **Then** the Prometheus TSDB retains historical metrics data from before the restart
- **And** Grafana shows all six dashboards without re-importing

### Scenario 15: Logging filter operates on unauthenticated requests (FR-17, BR-11)

- **Given** the Core Backend Monolith is running
- **When** a POST request without an `Authorization` header is made to `/api/v1/payments/webhook`
- **Then** a structured log entry is still emitted for the request
- **And** the request is not blocked or altered by the logging filter

## 8. Out of Scope

- **Custom business metrics instrumentation:** This feature does not add `capacity.drift.detected`, `rate_limiter.redis_unavailable`, `registrations.total`, or any domain-specific counter or gauge. Those are added by the respective business features against the registry configured here.
- **Resilience4j bean configuration:** Setting up circuit breaker bean definitions, failure thresholds, and slow-call durations is owned by `resilience-platform-setup`. This feature only provisions the Grafana dashboard that visualises those metrics.
- **Distributed tracing (spans/traces):** This feature implements MDC-based `traceId` log propagation only. It does NOT introduce OpenTelemetry, Zipkin, Jaeger, or any distributed trace-propagation protocol across service boundaries.
- **Log aggregation backend:** This feature configures structured log format and stdout destination only. It does NOT provision an ELK stack, Loki, or any log-ingestion infrastructure.
- **Prometheus alerting rules:** This feature provisions Grafana dashboards for visual inspection only. It does NOT define Prometheus `alert_rules.yml` or Grafana alert notification channels.
- **Application Performance Monitoring (APM):** No APM agent (e.g., Elastic APM, Datadog, New Relic) is installed or configured.
- **PostgreSQL Prometheus exporter:** No `postgres_exporter` or direct PostgreSQL metrics scraping is in scope; the primary database is Neon serverless (external managed service), not a local container.
- **Mobile and Web client telemetry:** No client-side metrics or error tracking (e.g., Sentry, Firebase Crashlytics) are in scope.
- **Nginx access log metrics scraping:** Nginx acts as a pure forwarding proxy and no Nginx-specific Prometheus exporter is configured by this feature.
