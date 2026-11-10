# ADR-009: Locking Strategy for Seat Allocation

> **Scope:** server

- **Context:** High-demand workshops can receive hundreds of simultaneous registration attempts for the final seats.
- **Decision:** Use lock-free atomic pre-checks via Redis (`DECR`) for capacity enforcement instead of pessimistic database locking.
- **Rationale:** Redis processes atomic decrements sequentially in microseconds, preventing last-seat race conditions without holding database connections or blocking request threads. Pessimistic locking (`SELECT ... FOR UPDATE`) is an anti-pattern at 12,000-user scale due to connection pool exhaustion risk.
- **Alternatives Considered:** Pessimistic row locking was rejected because it would serialize hot-seat contention and exhaust the JDBC pool during the 3-minute surge. Application-side spin locks or retries were rejected because they waste threads and still cannot guarantee efficient last-seat handling under contention.
- **Consequences:** Requires compensating increments on rollback paths but preserves database availability during spikes.
- **Defense-in-Depth:** Redis DECR is the first-line guard. The database partial unique index on `(workshop_id, student_user_id) WHERE status IN ('PENDING', 'CONFIRMED')` is the last-resort safety net for any edge-case Redis inconsistency. The registration use case **must explicitly catch** the resulting unique constraint violation from the database and translate it into a `409 Conflict` response — not a `500 Internal Server Error`. This two-layer design ensures correctness is preserved even under Redis counter drift, while keeping the DB-level contention path rare enough to not impact throughput.
- **Periodic Reconciliation:** A Capacity Reconciliation Job runs every 5 minutes to detect and correct Redis-DB counter drift caused by missed compensating increments. See the Overbooking Prevention section of `design.md` for the full reconciliation flow.
