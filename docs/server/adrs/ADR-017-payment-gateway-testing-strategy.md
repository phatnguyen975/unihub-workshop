# ADR-017: Payment Gateway Testing Strategy

> **Scope:** server

- **Context:** The system must prove resilience to payment outages and timeouts before production.
- **Decision:** Use Stripe in sandbox mode with a `ChaosMockAdapter` that injects controlled failures and latency.
- **Rationale:** A deterministic chaos adapter validates circuit breaker thresholds, idempotency, and compensating actions without relying on external outages, ensuring exactly-once payment behavior under stress tests.
- **Alternatives Considered:** Manual sandbox testing alone was rejected because it cannot systematically reproduce timeout, retry, and duplicate-charge edge cases. Ad hoc mock libraries were rejected because they do not provide the controlled failure injection needed to validate production-grade resilience policies.
- **Consequences:** Requires strict environment controls so chaos testing cannot reach production.
