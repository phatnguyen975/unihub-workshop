# Implementation Plan: resilience-platform-setup

> **Spec:** `specs/resilience-platform-setup/SPEC.md`  
> **Status:** APPROVED

## Overview

This plan establishes the shared `resilience-platform-setup` infrastructure within the Core Backend. It delivers two independently testable capabilities: (1) the centralized Resilience4j configuration and Micrometer metrics exporter for circuit breakers, and (2) a `dev`-profile-only `ChaosMockAdapter` for resilience testing. Because this is purely a cross-cutting foundational feature, downstream features (like `paid-workshop-registration`) will later use these beans to protect their external calls.

## Architecture Decisions for This Plan

- **Single-Layer (Infrastructure) Scope** — Normally, tasks span all architectural layers. However, this feature is explicitly a foundational setup feature located in `shared/config` with no Domain or Presentation concerns. The tasks are still sliced by capability (Resilience configuration vs. Chaos Mocking), but both only touch the Infrastructure layer.
- **Combined Circuit Breaker and Metrics Setup** — T1 configures both the circuit breaker defaults and the Micrometer metrics publisher. These are inherently intertwined in Resilience4j's Spring Boot auto-configuration, so splitting them would create horizontal, non-testable tasks.

## Task List

### Phase 1: Core Circuit Breaker Foundation

- [x] **T1 — Default Circuit Breaker Configuration & Metrics Exporter**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Infrastructure
  - **Files:** `apps/backend/build.gradle` (if resilience4j/micrometer dependencies need adding), `apps/backend/src/main/resources/application.yml`, `apps/backend/src/main/java/com/unihub/workshop/shared/config/ResilienceConfig.java`, `apps/backend/src/test/java/com/unihub/workshop/shared/config/ResilienceConfigTest.java`
  - **Acceptance Criteria:**
    - Spring context starts and registers the Resilience4j `CircuitBreakerRegistry` with the threshold properties defined in `application.yml` (FR-01, FR-02).
    - When a named circuit breaker breaches thresholds (e.g. failure rate, slow call rate), it correctly transitions states (`CLOSED` → `OPEN` → `HALF_OPEN`) (FR-03, FR-04, FR-05, FR-06, FR-07, FR-08).
    - The `/actuator/prometheus` endpoint successfully exposes `resilience4j_circuitbreaker_state`, call counts, and transition metrics for active circuit breakers (FR-09, FR-10, FR-11).
    - Application context fails to start if invalid Resilience4j configuration is provided in `application.yml`.

#### Checkpoint: Core Circuit Breaker Foundation

- [x] T1 passes its integration tests confirming the state machine transitions and metric exposure.
- [x] Build is clean and dependencies are correctly aligned.

### Phase 2: Resilience Testing Utilities

- [x] **T2 — Dev-Profile ChaosMockAdapter Injection**
  - **Size:** S
  - **Depends on:** —
  - **Layers touched:** Infrastructure
  - **Files:** `apps/backend/src/main/java/com/unihub/workshop/shared/config/ChaosMockAdapter.java`, `apps/backend/src/main/java/com/unihub/workshop/shared/config/DevChaosMockAdapter.java`, `apps/backend/src/main/resources/application-dev.yml`, `apps/backend/src/test/java/com/unihub/workshop/shared/config/DevChaosMockAdapterTest.java`
  - **Acceptance Criteria:**
    - The `DevChaosMockAdapter` bean is only active under the `dev` Spring profile; loading context under `prod` or default fails to inject it (FR-13, BR-01).
    - The adapter correctly reads `chaos.latency-ms` and `chaos.error-rate` from `application-dev.yml` and accurately injects artificial latency and simulated failures upon invocation (FR-12, FR-14).
    - The `ChaosMockAdapter` interface acts as a generic contract capable of wrapping outbound REST client calls for downstream features (FR-15).

#### Checkpoint: Feature Complete

- [x] All tasks above are checked off.
- [x] Every acceptance criterion across all tasks has been verified end-to-end via Spring Boot integration tests.
- [x] Full test suite passes.
- [x] Ready for `/review`.

## Risks and Mitigations

| Risk                                     | Impact | Mitigation                                                                                                                                                                                                    |
| :--------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Resilience4j Version Incompatibility** | Medium | The Spring Boot 4.x / Spring 7 stack requires a compatible Resilience4j starter. Mitigation: explicitly rely on Spring Boot's dependency management for Resilience4j rather than hardcoding a custom version. |
| **Chaos Adapter Leaking to Prod**        | High   | The `ChaosMockAdapter` injecting latency into production would cause an artificial outage. Mitigation: Implement a strict `@Profile("dev")` requirement and explicitly assert its absence in `prod` tests.    |

## Open Questions

None.
