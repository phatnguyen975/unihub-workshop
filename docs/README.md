# Documentation Map and Authority

| Concern                                                       | Canonical Source                                                                | Authority                                                        |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| Product scope, roles, constraints and success                 | [Product Overview](product-overview.md)                                         | Shared product requirements                                      |
| Product feature IDs, purpose and client applicability         | [Feature Catalog](product-features.md)                                          | Shared scope; no app delivery status                             |
| Workspace ownership and navigation                            | [Repository Structure](repository-structure.md)                                 | Monorepo structure                                               |
| Shared architectural decisions                                | [Shared ADRs](adrs/README.md)                                                   | Consumer/provider decisions                                      |
| Server architectural decisions                                | [Server ADRs](server/adrs/README.md)                                            | Backend/workers implementation                                   |
| Client architectural decisions                                | [Client ADRs](client/adrs/README.md)                                            | Web/mobile; one client numbering namespace                       |
| Server delivery status, modules, persistence and dependencies | [Server feature catalog](server/feature-delivery.md)                            | Server readiness/DONE only                                       |
| Server business/operational flows                             | [System design](server/system-design.md)                                        | Provider behavior and implementation                             |
| Service components                                            | [Architecture](server/system-architecture.md)                                   | System connectivity                                              |
| Server source organization                                    | [Server structure](server/source-structure.md)                                  | Backend/workers layering and communication                       |
| Web source organization                                       | [Web structure](client/web/source-structure.md)                                 | Feature-oriented architecture target                             |
| Mobile source organization                                    | [Mobile structure](client/mobile/source-structure.md)                           | Feature-oriented architecture target and native/local data seams |
| Local infrastructure                                          | [Infrastructure guide](../infra/README.md)                                      | Services, configuration ownership, layout and local operation    |
| PostgreSQL/read routing                                       | [Server database](server/database-schema.md)                                    | Server persistence                                               |
| Mobile SQLite design                                          | [Mobile database](client/mobile/database-schema.md)                             | Client persistence design                                        |
| HTTP interface ownership and current sources                  | [Contract registry](../contracts/http/README.md)                                | Approved public wire contracts                                   |
| HTTP conventions                                              | [HTTP standards](../contracts/http/standards.md)                                | Headers, envelopes, errors, pagination and SSE                   |
| Server request logging                                        | [API logging](server/api-logging.md)                                            | Server operational format                                        |
| Client scope/UI/spec/prototype/acceptance                     | [Client documents](client/README.md), [client specs](../specs/client/README.md) | Platform/capability-owned artifacts                              |

Read the relevant sources before drafting a spec, contract or implementation. Each approved artifact governs its own concern; surface conflicts before dependent work. Server modules/database models do not prescribe client views or wire models. Mock requirements are not approved HTTP contracts.

## Task Lookup

- Product scope/core features: overview + shared feature catalog; client experience maps define journeys and supporting UX.
- Server feature work: matching server catalog entry and dependency map, approved server spec/task/contract, matching system flow and owned ADRs.
- Server package boundaries and mapper placement: server project structure and app rules. Queries/schema/read-after-write routing: server database.
- Client design/build/review: matching platform project structure, UI baseline, capability spec/prototype/task plan and client rules. Client progress is independent of server DONE.
- REST/SSE design and integration: public contract registry and HTTP standards, plus the approved feature requirements. Logging uses API logging.
- Technical rationale: the decision file linked by its scoped ADR reference; every record is owned once and indexed in its namespace.

## Delivery and Artifact Guides

- [Server Delivery](server/feature-delivery.md)
- [Web Delivery](client/web/feature-delivery.md)
- [Mobile Delivery](client/mobile/feature-delivery.md)
- [Specifications](../specs/README.md)
- [Agent Configuration](../.agents/README.md)
- [Artifact Templates](../.agents/templates/README.md)
