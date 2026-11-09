# ADR-007: Object Storage

> **Scope:** server

- **Context:** The system must store PDFs and nightly CSV drops without bloating the primary database.
- **Decision:** Use S3-compatible object storage (for example, MinIO in local environments).
- **Rationale:** Object storage is optimized for large immutable files and integrates cleanly with background workers and batch ingestion. It reduces database load and improves IO scalability under concurrent access.
- **Alternatives Considered:** Storing PDFs or CSVs directly in PostgreSQL was rejected because it would bloat the primary database and slow transactional workloads. Shared filesystem storage was rejected because it weakens durability and horizontal scalability for worker-driven batch processing.
- **Consequences:** Requires explicit access controls and lifecycle policies for retention.
