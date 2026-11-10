# ADR-001: Backend Framework and Architecture

> **Scope:** server

- **Context:** The backend must handle 12,000+ concurrent users, enforce strict transactional integrity for seat allocation and payments, and remain operationally simple under rapid delivery constraints.
- **Decision:** Use Java with Spring Boot in an Event-Driven Modular Monolith architecture powered by Spring Modulith.
- **Rationale:** Java and Spring Boot offer predictable multi-threaded performance, robust transaction management, and mature resilience libraries. Spring Modulith enforces module boundaries while preserving single-deploy simplicity, which reduces operational overhead compared to microservices and avoids cross-service consistency pitfalls during the registration surge.
- **Alternatives Considered:** Microservices were rejected because exactly-once registration/payment flows would require distributed transactions or compensating sagas across services, adding avoidable ops overhead for a university team. Go or Node.js were rejected because this project benefits more from Spring’s transaction, security, and batch ecosystem than from a different runtime.
- **Consequences:** Requires disciplined module boundaries and JVM tuning, but delivers strong consistency and simpler operations under high concurrency.
