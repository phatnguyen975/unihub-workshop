# ADR-019: Offline Sync Conflict Resolution

> **Scope:** server

- **Context:** Multiple devices can scan the same attendee while offline and later sync concurrently.
- **Decision:** Use server-authoritative reconciliation with workshop-scoped bulk fetch and bulk upsert that returns an HTTP 207 Multi-Status response with per-entry resolution codes (e.g., `SUCCESS`, `ALREADY_USED`, `INVALID_TICKET`).
- **Rationale:** Server-authoritative reconciliation prevents duplicate attendance, preserves auditability, and scales under batch sync. Bulk operations minimize DB chatter and avoid per-row contention during offline sync bursts. Using HTTP 207 avoids exception-based control flow for partial batch failures, allowing the SQLite mobile client to deterministically update local records without failing the entire sync batch.
- **Alternatives Considered:** Client-side merge or last-write-wins reconciliation was rejected because offline devices cannot safely decide attendance authority without risking double booking. Exception-driven batch failure was rejected because a single bad row would abort the entire sync and make deterministic mobile recovery impossible.
- **Consequences:** Requires unique constraints and deterministic response handling on the client.
