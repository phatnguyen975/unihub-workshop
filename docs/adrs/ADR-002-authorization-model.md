# ADR-002: Authorization Model

> **Scope:** shared

- **Context:** The platform must enforce permissions for `STUDENT`, `ORGANIZER`, and `STAFF` while supporting multi-tenant data isolation and concurrent admin edits.
- **Decision:** Use RBAC with logical multi-tenancy enforced by `organization_id` (for example, `workshop.organization_id == jwt.org`) and optimistic locking (`@Version`) for concurrent admin mutations.
- **Rationale:** RBAC enables `O(1)` checks under surge load, while logical multi-tenancy ensures any `ORGANIZER` within the same organization can manage its workshops without cross-tenant leakage. Optimistic locking prevents lost updates when multiple `ORGANIZERs` modify the same workshop, returning `409 Conflict` on version mismatch.
- **Alternatives Considered:** Pure ACLs were rejected because per-resource permission checks are harder to reason about and more expensive to maintain at scale. Database row-level security was rejected because it would push critical authorization logic deeper into the data layer and complicate application-level conflict handling.
- **Consequences:** Requires consistent org scoping in all data access paths and client handling of optimistic locking conflicts.
