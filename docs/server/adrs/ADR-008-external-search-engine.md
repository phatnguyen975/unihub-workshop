# ADR-008: External Search Engine

> **Scope:** server

- **Context:** Workshop search is read-heavy and must remain fast under surge traffic without impacting transactional workloads.
- **Decision:** Use Elasticsearch as a CQRS read model synchronized asynchronously from PostgreSQL.
- **Rationale:** Elasticsearch offloads CPU-intensive search from the primary database, protecting write throughput during the 12,000-user spike. Background workers synchronize data, keeping transactional correctness in PostgreSQL while enabling fast, relevance-tuned search.
- **Alternatives Considered:** PostgreSQL native full-text search was rejected because it would compete with transaction workloads for CPU and memory on the primary database during the spike window. Algolia was rejected due to cost and external dependency concerns for a high-read academic workload.
- **Consequences:** Requires dual-write synchronization and monitoring of index freshness.
