# ADR-005: Database Connection Pooling Strategy

> **Scope:** server

- **Context:** High concurrency requires efficient JDBC pooling to prevent saturation.
- **Decision:** Use HikariCP for connection pooling.
- **Rationale:** HikariCP offers low latency and predictable performance, reducing connection churn under the 12,000-user surge and preserving throughput for transactional workloads.
- **Alternatives Considered:** Apache DBCP2 and C3P0 were rejected because they are slower and less predictable under bursty traffic. Unpooled JDBC access was rejected because it would amplify connection churn and risk exhausting PostgreSQL during peak registration spikes.
- **Consequences:** Requires careful pool sizing and monitoring to avoid exhausting PostgreSQL resources.
