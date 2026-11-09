# ADR-012: Stale-While-Revalidate (SWR) Cache-Aside

> **Scope:** server

- **Context:** Search and listing endpoints are read-heavy and must remain responsive during surge traffic.
- **Decision:** Use an SWR cache-aside strategy with Redis, dual TTLs (soft/hard), and non-blocking refresh with `tryLock`.
- **Rationale:** SWR returns cached content immediately while refreshing in the background, preventing thread blocking and cache stampedes. Dual TTLs allow controlled staleness without serving expired data indefinitely. A non-blocking refresh lock prevents concurrent refresh storms.
- **Alternatives Considered:** Pure cache-aside with synchronous refresh was rejected because it would block request threads and create stampedes during the 3-minute traffic spike. Local per-node caches were rejected because they would diverge across instances and break search coherence.
- **Consequences:** Requires cache invalidation discipline and observability on refresh behavior.
