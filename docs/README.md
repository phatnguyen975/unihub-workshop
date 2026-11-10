# UniHub Workshop — Documentation Map and Authority

## Scope

This map identifies the owner and authority of each documentation concern. Follow the rows relevant to the task; linked guides provide navigation and detail without changing ownership.

## Canonical Sources

| Concern                                                       | Canonical Source                                                                         | Authority                                                        |
| ------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| Product scope, roles, constraints and success                 | [Product Overview](product-overview.md)                                                  | Shared product requirements                                      |
| Product feature IDs, purpose and client applicability         | [Product Features](product-features.md)                                                  | Shared scope; no app delivery status                             |
| Workspace ownership and navigation                            | [Repository Structure](repository-structure.md)                                          | Monorepo structure                                               |
| Shared architectural decisions                                | [Shared ADRs](adrs/README.md)                                                            | Consumer/provider decisions                                      |
| Server architectural decisions                                | [Server ADRs](server/adrs/README.md)                                                     | Backend/workers implementation                                   |
| Client architectural decisions                                | [Client ADRs](client/adrs/README.md)                                                     | Web/mobile; one client numbering namespace                       |
| Server delivery status, modules, persistence and dependencies | [Server Feature Delivery](server/feature-delivery.md)                                    | Server readiness/DONE only                                       |
| Server business/operational flows                             | [System Design](server/system-design.md)                                                 | Provider behavior and implementation                             |
| Service components                                            | [System Architecture](server/system-architecture.md)                                     | System connectivity                                              |
| Server source organization                                    | [Server Source Structure](server/source-structure.md)                                    | Backend/workers layering and communication                       |
| Web source organization                                       | [Web Source Structure](client/web/source-structure.md)                                   | Feature-oriented architecture target                             |
| Mobile source organization                                    | [Mobile Source Structure](client/mobile/source-structure.md)                             | Feature-oriented architecture target and native/local data seams |
| Local infrastructure                                          | [Infrastructure Guide](../infra/README.md)                                               | Services, configuration ownership, layout and local operation    |
| PostgreSQL/read routing                                       | [Server Database](server/database-schema.md)                                             | Server persistence                                               |
| Mobile SQLite design                                          | [Mobile Database](client/mobile/database-schema.md)                                      | Client persistence design                                        |
| HTTP interface ownership and current sources                  | [HTTP Contract Registry](../contracts/http/README.md)                                    | Approved public wire contracts                                   |
| HTTP conventions                                              | [HTTP Standards](../contracts/http/standards.md)                                         | Headers, envelopes, errors, pagination and SSE                   |
| Server request logging                                        | [API Logging](server/api-logging.md)                                                     | Server operational format                                        |
| Client scope/UI/spec/prototype/acceptance                     | [Client Documents](client/README.md), [Client Specifications](../specs/client/README.md) | Platform/capability-owned artifacts                              |
| Agent execution policy and techniques                         | [Agent Configuration](../.agents/README.md)                                              | Task routing and scoped execution                                |
| Artifact document formats                                     | [Artifact Templates](../.agents/templates/README.md)                                     | Output schemas under the owning workflow                         |

Read the relevant sources before drafting a spec, contract or implementation. Each approved artifact governs its own concern; surface conflicts before dependent work. Server modules/database models do not prescribe client views or wire models. Mock requirements are not approved HTTP contracts.

## Task Lookup

- Product scope/core features: overview + shared feature catalog; client experience maps define journeys and supporting UX.
- Server feature work: matching server catalog entry and dependency map, approved server spec/task/contract, matching system flow and owned ADRs.
- Server package boundaries and mapper placement: server source structure and app rules. Queries/schema/read-after-write routing: server database.
- Client design/build/review: matching platform source structure, UI baseline, capability spec/prototype/task plan and client rules. Client progress is independent of server DONE.
- REST/SSE design and integration: public contract registry and HTTP Standards, plus the approved feature requirements. Logging uses API Logging.
- Technical rationale: the decision file linked by its scoped ADR reference; every record is owned once and indexed in its namespace.
