# Spec: Resilience Platform Setup

> **Status:** APPROVED  
> **Domain:** 10 — System Reliability & Infrastructure  
> **Owner/area:** `shared/config` (Core Backend)  
> **Last revised:** 2026-08-29 — first version

## 1. Overview

This feature establishes the foundational Resilience4j infrastructure for the entire platform: it registers the default circuit-breaker configuration beans, declares default failure-rate and slow-call thresholds in `application.yml`, ensures that Micrometer emits circuit-breaker state-transition events to Prometheus, and provides the `ChaosMockAdapter` base interface that the `dev` Spring profile activates to inject configurable latency and errors for resilience testing. The foundation laid here is a prerequisite for every downstream feature that wraps an external service call in a circuit breaker (`paid-workshop-registration` wrapping Stripe, `notification-delivery-pipeline` wrapping Email Provider, `ai-brochure-summarization` wrapping the AI Summarization Service); those features consume the beans registered here without duplicating configuration.

## 2. Actors & Preconditions

| Actor                                                                                        | Precondition                                                                                                                            |
| :------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------- |
| Spring Boot startup process (Core Backend)                                                   | The Spring Boot application context is initializing; `spring.profiles.active` is set to a known value (`dev` or `prod`).                |
| `dev`-profile developer / resilience tester                                                  | The Core Backend is running under the `dev` Spring profile; no real external services need to be reachable for chaos injection to work. |
| Any business feature that wraps an external call in a circuit breaker (downstream consumers) | `resilience-platform-setup` must have been built and deployed before those features can reference its beans.                            |

## 3. Functional Requirements

### 3.1 Circuit-Breaker Foundation

- **FR-01:** The system SHALL register a default circuit-breaker configuration bean in the Spring application context when the application starts, using the threshold values declared in `application.yml`.
- **FR-02:** The system SHALL read failure-rate threshold, slow-call duration threshold, slow-call rate threshold, permitted calls in `HALF_OPEN` state, and minimum number of calls from `application.yml` — never hard-code these values in Java source.
- **FR-03:** The system SHALL transition a circuit breaker from `CLOSED` to `OPEN` when the recorded failure rate equals or exceeds the configured failure-rate threshold within the configured sliding window.
- **FR-04:** The system SHALL transition a circuit breaker from `CLOSED` to `OPEN` when the recorded slow-call rate equals or exceeds the configured slow-call rate threshold and the slow-call duration threshold within the configured sliding window.
- **FR-05:** The system SHALL transition a circuit breaker from `OPEN` to `HALF_OPEN` automatically after the configured wait duration has elapsed.
- **FR-06:** The system SHALL transition a circuit breaker from `HALF_OPEN` to `CLOSED` when the configured number of test calls in `HALF_OPEN` all succeed.
- **FR-07:** The system SHALL transition a circuit breaker from `HALF_OPEN` back to `OPEN` when any test call in `HALF_OPEN` fails or is slow.
- **FR-08:** The system SHALL return a fast-fail result (without calling the protected dependency) when a circuit breaker is in `OPEN` state, so that the calling thread is never blocked waiting on an unavailable external service.

### 3.2 Micrometer Metric Emission

- **FR-09:** The system SHALL emit a Micrometer counter or gauge metric for every circuit-breaker state transition event (`CLOSED` → `OPEN`, `OPEN` → `HALF_OPEN`, `HALF_OPEN` → `CLOSED`, `HALF_OPEN` → `OPEN`), tagged with the circuit-breaker name.
- **FR-10:** The system SHALL expose the current state of each named circuit breaker (as a Micrometer gauge or tagged metric) so that Prometheus can scrape it and Grafana can alert on `OPEN` and prolonged `HALF_OPEN` states.
- **FR-11:** The system SHALL emit Micrometer metrics for failure rate, slow-call rate, and call count for each named circuit breaker, so that the thresholds that triggered a state transition can be diagnosed after the fact.

### 3.3 ChaosMockAdapter (dev profile only)

- **FR-12:** The system SHALL define a `ChaosMockAdapter` base interface (or abstract type) that declares, at minimum, the ability to inject configurable artificial latency before returning a response and the ability to inject configurable error outcomes instead of returning a real response.
- **FR-13:** The system SHALL activate the `ChaosMockAdapter` implementation(s) only when the `dev` Spring profile is active, ensuring that chaos injection is structurally impossible under any non-`dev` profile (including `prod`).
- **FR-14:** The system SHALL allow the configurable latency and error-injection rate of the `ChaosMockAdapter` to be set via `application-dev.yml` or environment variables — not hard-coded in source — so that different resilience-test scenarios can be triggered without recompiling.
- **FR-15:** The system SHALL ensure that the `ChaosMockAdapter` interface contract is compatible with the same outbound port interfaces used by real external-service adapters, so that downstream business features can substitute the chaos adapter at test time without changing their own code.

## 4. Business Rules

- **BR-01:** The `ChaosMockAdapter` MUST NOT be registered as a bean under any Spring profile other than `dev`. Specifically, it must not be active under `prod`, `test`, or the default (no-profile) context. (Source: `docs/feature-catalog.md §10.2` Key Constraints; ADR-021.)
- **BR-02:** The default circuit-breaker configuration defined here is a shared baseline. Individual downstream features that need a named circuit breaker with different thresholds MAY override only the per-name properties in `application.yml`; they may NOT re-declare the entire Resilience4j bean. (Source: ADR-020 — feature-level instrumentation builds on the foundation here, not parallel to it.)
- **BR-03:** Every named circuit breaker defined by a downstream feature MUST inherit from the default configuration declared here unless a named override is explicitly present in `application.yml`. (Source: ADR-020.)
- **BR-04:** Micrometer metric emission for circuit-breaker events MUST be enabled in the configuration declared by this feature — it is not optional. Disabling it silently breaks the observability requirement stated in ADR-020 and `docs/system-design.md §6`. (Source: ADR-020, ADR-030.)
- **BR-05:** The circuit-breaker bean and the `ChaosMockAdapter` base interface MUST reside in `shared/config` in the Core Backend. They are shared-kernel infrastructure, not module-level concerns, and must therefore be accessible to any module without violating Spring Modulith boundary rules. (Source: `docs/project-structure.md §3 — Core Backend Directory Layout`; `docs/feature-catalog.md §10.2`.)
- **BR-06:** No real network call to an external service (Stripe, Email Provider, AI Service) is made by this feature. It only wires infrastructure; all external calls are the responsibility of the consuming business features. (Source: `docs/feature-catalog.md §10.2` — "Note: This feature does not instrument specific clients".)

## 5. State Transitions

The circuit breaker itself is a stateful entity managed by Resilience4j. The valid and invalid transitions are fixed by the library's state machine, as documented in `docs/system-design.md §6` and ADR-020.

| From State  | Event/Trigger                                                                    | To State    | Valid? | Rejection Behavior (if invalid)                                                     |
| :---------- | :------------------------------------------------------------------------------- | :---------- | :----: | :---------------------------------------------------------------------------------- |
| `CLOSED`    | Failure rate ≥ configured threshold within sliding window                        | `OPEN`      |   ✅   | N/A                                                                                 |
| `CLOSED`    | Slow-call rate ≥ configured threshold within sliding window                      | `OPEN`      |   ✅   | N/A                                                                                 |
| `CLOSED`    | Calls succeed or fail below threshold                                            | `CLOSED`    |   ✅   | N/A (steady state, no transition)                                                   |
| `OPEN`      | Configured wait duration elapses                                                 | `HALF_OPEN` |   ✅   | N/A                                                                                 |
| `OPEN`      | Incoming call arrives before wait duration elapses                               | `OPEN`      |   ✅   | Fast-fail immediately; no attempt to call the protected dependency.                 |
| `HALF_OPEN` | All permitted test calls succeed                                                 | `CLOSED`    |   ✅   | N/A                                                                                 |
| `HALF_OPEN` | Any permitted test call fails or is slow                                         | `OPEN`      |   ✅   | N/A                                                                                 |
| `HALF_OPEN` | Number of incoming calls exceeds `permittedNumberOfCallsInHalfOpenState`         | `HALF_OPEN` |   ✅   | Excess calls are fast-failed (not admitted as test calls).                          |
| Any         | Application forcibly calls `transitionToClosedState()` without earned test calls | —           |   ❌   | Not supported by this feature's configuration; must not be called programmatically. |

> Downstream business features do not add new states; they use this same state machine with their own named circuit-breaker instances.

## 6. Non-Functional Requirements

- **Performance:** The circuit-breaker evaluation on each call must add negligible overhead (microseconds) to the calling thread. The Resilience4j library provides in-process, lock-free state management to meet this requirement.
- **Concurrency:** The default circuit-breaker configuration must be thread-safe under the concurrent load profile of this system (12,000+ concurrent users). Resilience4j's COUNT_BASED or TIME_BASED sliding window MUST be chosen with this in mind; the default must support concurrent call recording without requiring external synchronization.
- **Idempotency:** Configuration bean registration is idempotent by the Spring framework's singleton-bean guarantee. Re-starting the application resets in-memory circuit-breaker state to `CLOSED` (no state persistence is required or desired per ADR-020).
- **Security:** The `ChaosMockAdapter` base interface and its `dev`-profile implementation must be absent from the production classpath. The Spring profile guard (`@Profile("dev")`) is the primary enforcement mechanism. The implementation must not accept chaos parameters from unauthenticated HTTP requests under any profile.
- **Reliability / Failure modes:** If Resilience4j itself cannot initialize (e.g., misconfigured `application.yml`), the application must fail to start (fail-fast) rather than starting with an unconfigured or missing circuit breaker. This prevents the silent absence of the protection layer.
- **Observability:** All Micrometer metric names emitted by this feature must follow the naming conventions in `docs/api-standards.md` (snake*case, `unihub*`prefix or Resilience4j standard names compatible with the Prometheus registry). The`resilience4j-circuit-breaker.json`Grafana dashboard defined in`infra/grafana/dashboards/` must be able to consume these metrics without schema changes.

## 7. Acceptance Criteria

### Scenario 1: Circuit breaker starts in CLOSED state

```gherkin
Given the Core Backend application has started successfully with a valid Resilience4j configuration in application.yml
When the application context is fully initialized
Then a default Resilience4j circuit-breaker configuration bean is present in the Spring context
And the circuit breaker for any named instance starts in CLOSED state
```

_(Traces to: FR-01, FR-02)_

### Scenario 2: Thresholds are read from configuration, not hard-coded

```gherkin
Given application.yml declares a failure-rate threshold of X% and a slow-call threshold of Y milliseconds
When the application starts and a circuit breaker instance is created using the default configuration
Then the circuit breaker's effective failure-rate threshold equals X%
And the circuit breaker's effective slow-call duration threshold equals Y milliseconds
```

_(Traces to: FR-02)_

### Scenario 3: CLOSED → OPEN transition on failure-rate breach

```gherkin
Given a circuit breaker named "test-cb" is in CLOSED state
And the default minimum-number-of-calls has been recorded
When the number of failed calls within the sliding window reaches or exceeds the configured failure-rate threshold
Then the circuit breaker transitions to OPEN state
And a Micrometer state-transition metric is emitted tagged with name "test-cb" and transition "CLOSED_TO_OPEN"
```

_(Traces to: FR-03, FR-09)_

### Scenario 4: Fast-fail while OPEN

```gherkin
Given a circuit breaker is in OPEN state and the wait duration has not yet elapsed
When a new call attempts to use the protected dependency through the circuit breaker
Then the circuit breaker returns a fast-fail result immediately
And the protected dependency is never invoked
And no additional failure is recorded against the sliding window
```

_(Traces to: FR-08)_

### Scenario 5: OPEN → HALF_OPEN after wait duration

```gherkin
Given a circuit breaker is in OPEN state
When the configured wait duration has elapsed
Then the circuit breaker transitions to HALF_OPEN state
And a Micrometer state-transition metric is emitted for the OPEN_TO_HALF_OPEN transition
```

_(Traces to: FR-05, FR-09)_

### Scenario 6: HALF_OPEN → CLOSED on successful test calls

```gherkin
Given a circuit breaker is in HALF_OPEN state
When all permitted test calls complete successfully
Then the circuit breaker transitions to CLOSED state
And a Micrometer state-transition metric is emitted for the HALF_OPEN_TO_CLOSED transition
```

_(Traces to: FR-06, FR-09)_

### Scenario 7: HALF_OPEN → OPEN on failed test call

```gherkin
Given a circuit breaker is in HALF_OPEN state
When at least one permitted test call fails or exceeds the slow-call duration threshold
Then the circuit breaker transitions back to OPEN state
And a Micrometer state-transition metric is emitted for the HALF_OPEN_TO_OPEN transition
```

_(Traces to: FR-07, FR-09)_

### Scenario 8: Micrometer metrics are exposed to Prometheus

```gherkin
Given the Core Backend is running with Resilience4j and Micrometer configured
And a named circuit breaker has been created
When Prometheus scrapes /actuator/prometheus
Then the response contains circuit-breaker state gauge metrics for the named instance
And the response contains circuit-breaker call count, failure rate, and slow-call rate metrics
```

_(Traces to: FR-10, FR-11)_

### Scenario 9: ChaosMockAdapter is active only under dev profile

```gherkin
Given the Core Backend is started with the "dev" Spring profile active
When the Spring application context initializes
Then the ChaosMockAdapter bean is registered and available for injection
```

_(Traces to: FR-13)_

```gherkin
Given the Core Backend is started with the "prod" Spring profile active (or no profile)
When the Spring application context initializes
Then no ChaosMockAdapter bean exists in the application context
And no chaos injection behavior is reachable by any code path
```

_(Traces to: FR-13, BR-01)_

### Scenario 10: ChaosMockAdapter latency and error rate are configurable via application-dev.yml

```gherkin
Given application-dev.yml declares chaos.latency-ms=500 and chaos.error-rate=0.3
And the Core Backend is started under the "dev" profile
When the ChaosMockAdapter is invoked
Then it introduces approximately 500 ms of artificial latency
And it rejects approximately 30% of calls with a configurable error
```

_(Traces to: FR-14)_

### Scenario 11: Application fails to start on invalid Resilience4j configuration

```gherkin
Given application.yml contains an invalid Resilience4j configuration (e.g., failure-rate-threshold greater than 100)
When the Core Backend application attempts to start
Then the application fails to start with a descriptive configuration error
And it does not start in a partially-configured state
```

_(Traces to: NFR — Reliability / Failure modes)_

## 8. Out of Scope

- **Instrumenting specific external service calls with circuit breakers.** Wrapping the Stripe payment client, the Email Provider adapter, and the AI Summarization Service call in named circuit breakers is done in `paid-workshop-registration`, `notification-delivery-pipeline`, and `ai-brochure-summarization` respectively. This feature only provides the shared base configuration they will reference.
- **Bulkhead or retry configuration.** Resilience4j also supports Bulkheads, Retries, and Rate Limiters. These are not part of this feature's scope; only the circuit-breaker pattern is established here.
- **Worker-side circuit breakers.** The Workers application (`apps/workers/`) is not covered by this feature. Any resilience configuration needed in workers is the responsibility of the worker-level business features.
- **Persistent circuit-breaker state across restarts.** In-memory state reset on restart is the expected behavior per ADR-020. Durable state storage is explicitly out of scope.
- **Dynamic reconfiguration of thresholds at runtime.** Threshold changes require a restart and are applied via `application.yml` only; no live-reload or admin API for threshold mutation is provided by this feature.
- **Distributed circuit-breaker state sharing across backend nodes.** Each backend node (Node 1, Node 2) maintains its own independent in-memory circuit-breaker state. Cross-node state synchronization is explicitly out of scope.
- **Chaos HTTP endpoint for external callers.** The `ChaosMockAdapter` is a code-level injection mechanism only; there is no REST endpoint that triggers or adjusts chaos behavior at runtime.
- **Chaos injection in any app other than the Core Backend.** The `ChaosMockAdapter` base interface is defined only in `apps/backend/shared/config`; it does not apply to the Workers app or any other app in the monorepo.

## 9. Linked References

| Source                      | Section                                                 | Relevance                                                                                                           |
| :-------------------------- | :------------------------------------------------------ | :------------------------------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md`   | §10.2 `resilience-platform-setup`                       | Authoritative feature definition: module owner, key constraints, ADR refs, scope note on downstream instrumentation |
| `docs/feature-catalog.md`   | §4.2 `paid-workshop-registration` — Key Constraints     | Explicitly requires circuit breaker "configured via `resilience-platform-setup`" for Stripe call                    |
| `docs/feature-catalog.md`   | §7.1 `notification-delivery-pipeline` — Key Constraints | Explicitly requires circuit breaker "configured via `resilience-platform-setup`" for Email Provider call            |
| `docs/feature-catalog.md`   | §8.1 `ai-brochure-summarization` — Key Constraints      | Explicitly requires circuit breaker "configured via `resilience-platform-setup`" for AI API call                    |
| `docs/system-design.md`     | §6 — Third-Party Service Resilience                     | Circuit breaker CLOSED/OPEN/HALF_OPEN state machine diagram and rationale                                           |
| `docs/system-design.md`     | §6 — System Telemetry & Core Metrics to Track           | Resilience4j state transitions must be tracked in Grafana                                                           |
| `docs/adrs.md`              | ADR-020 (Circuit Breaker Implementation)                | Resilience4j decision, Closed/Open/Half-Open model, Micrometer metrics requirement, fallback design requirement     |
| `docs/adrs.md`              | ADR-021 (Payment Gateway Testing Strategy)              | ChaosMockAdapter rationale — dev-only, controlled failure injection, strict environment controls                    |
| `docs/adrs.md`              | ADR-030 (Core Observability and Monitoring Stack)       | Micrometer → Prometheus → Grafana integration; `resilience4j-circuit-breaker.json` dashboard listed                 |
| `docs/project-structure.md` | §3 — Core Backend Directory Layout                      | `shared/config` as the correct package for cross-cutting infrastructure beans                                       |
| `docs/project-structure.md` | §3 — `infra/grafana/dashboards/`                        | `resilience4j-circuit-breaker.json` listed as a Grafana dashboard that must be populated by this feature's metrics  |
| `docs/api-standards.md`     | Logging/metrics naming conventions (if applicable)      | Metric name format compatibility with Prometheus registry                                                           |
