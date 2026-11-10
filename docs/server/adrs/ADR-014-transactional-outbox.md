# ADR-014: Transactional Outbox

> **Scope:** server

- **Context:** Domain events must not be lost when database commits succeed but broker publishes fail.
- **Decision:** Use Spring Modulith Event Publication registry (transactional outbox) for event persistence and externalization.
- **Rationale:** Storing events in the same transaction as domain writes guarantees exactly-once publication semantics and prevents inconsistencies during high traffic and retries.
- **Alternatives Considered:** Direct broker publishing from request handlers was rejected because commit/publish failures would create inconsistent state and duplicate event risk. Database polling of domain tables was rejected because it weakens event fidelity and adds unnecessary read load to the primary database.
- **Consequences:** Requires monitoring of publication lag and backlog.
