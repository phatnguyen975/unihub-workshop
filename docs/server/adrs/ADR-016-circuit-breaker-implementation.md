# ADR-016: Circuit Breaker Implementation

> **Scope:** server

- **Context:** Payment gateways can fail or time out, risking cascading failures.
- **Decision:** Use Resilience4j circuit breakers around external integrations.
- **Rationale:** Resilience4j provides Closed/Open/Half-Open states with metrics and failure thresholds, allowing fast-fail behavior and protecting core flows during outages.
- **Alternatives Considered:** Custom circuit breaker logic was rejected because it would duplicate state-machine and metrics concerns that Resilience4j already solves reliably. Bulkhead-only isolation was rejected because it does not address downstream failure propagation or fast-fail behavior for payment integrations.
- **Consequences:** Requires explicit fallback design and careful threshold configuration.
