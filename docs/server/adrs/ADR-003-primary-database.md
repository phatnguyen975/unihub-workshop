# ADR-003: Primary Database

> **Scope:** server

- **Context:** Seat allocation, payments, and attendance must be ACID-compliant and resistant to overbooking.
- **Decision:** Use PostgreSQL hosted on Neon as the primary transactional database with a primary-replica topology.
- **Rationale:** PostgreSQL provides strong transactional guarantees and mature indexing. Neon delivers managed availability while retaining strict consistency for writes, which is essential to exactly-once payment and registration flows.
- **Alternatives Considered:** MongoDB was rejected because its document model does not provide the same strict ACID guarantees and indexing consistency required for seat allocation, payments, and idempotency. MySQL was rejected because PostgreSQL better fits the project’s concurrency, indexing, and advanced transactional patterns.
- **Consequences:** Requires careful routing of read vs write queries and monitoring of replication lag.
