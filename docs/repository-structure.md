# UniHub Workshop — Repository Structure

## 1. Root Repository Structure

This repository uses a monorepo approach to separate deliverable applications (Core Backend, Background Workers, Web Portal, and Mobile Check-in) from infrastructure and architecture blueprints. This keeps the Event-Driven Modular Monolith, offline-first JSI SQLite mobile stack, Redis-based SWR caching, RabbitMQ workers, and the Prometheus/Grafana observability stack easy to discover while preserving clean ownership boundaries for each runtime concern. Agent-assisted development is structured through `.agents/`, which defines scoped reusable skills and workflow entrypoints that enforce Spec-Driven and Test-Driven Development across the monorepo.

```text
UniHub/
├── AGENTS.md
├── .agents/
│   ├── rules/
│   ├── workflows/
│   ├── skills/{shared,server,client}/
│   ├── templates/{shared,server,client}/
│   └── skill-catalog.md
├── apps/{backend,workers,web,mobile}/
├── docs/{adrs,server,client,data,diagrams}/
├── contracts/http/
├── specs/{server,client}/
├── infra/
└── scripts/
```

This physical separation mirrors Clean Architecture boundaries: `apps/` holds the deployable units, `infra/` defines the local runtime environment (Redis, RabbitMQ, Elasticsearch, MinIO, Prometheus, Grafana), and `docs/` anchors the system's authoritative architectural and schema decisions referenced during implementation. `specs/` captures the living, per-feature artifacts (specifications and task history; public contracts live in contracts/) produced when a feature goes through the `$server-spec` → `$server-plan` → `$server-build` → `$server-review` workflow.

Note that PostgreSQL is intentionally omitted from the local `infra/` stack as the system strictly connects to the serverless Neon PostgreSQL Cloud via encrypted JDBC. Operational automation is centralized in `scripts/`, keeping the root directory clean.

```mermaid
flowchart LR
  subgraph Apps["Apps"]
    Web("Web")
    Mobile("Mobile")
    Backend("Backend")
    Workers("Workers")
  end
  subgraph Infrastructure["Infrastructure"]
    Redis[("Redis")]
    Rabbit("RabbitMQ")
    Postgres[("PostgreSQL")]
    ES[("Elasticsearch")]
    MinIO[("MinIO / S3")]
    Obs("Prometheus/Grafana")
  end
  subgraph External["External Services"]
    Pay("Payment Gateway")
    AI("AI Service")
    Notify("Notification Providers")
  end

  Web -- HTTP/REST --> Backend
  Mobile -- HTTP/REST --> Backend
  Backend -- AMQP --> Rabbit
  Rabbit -- AMQP --> Workers
  Backend -- RESP --> Redis
  Workers -- RESP --> Redis
  Backend -- JDBC/SSL --> Postgres
  Workers -- JDBC/SSL --> Postgres
  Backend -- HTTP/REST --> ES & Pay
  Workers -- HTTP/REST --> ES & AI & Notify
  Backend -- S3 API --> MinIO
  Workers -- S3 API --> MinIO
  Obs -- Micrometer Pull --> Backend & Workers

  classDef app fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef cache fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
  classDef db fill:#f0e6ff,stroke:#6b46c1,stroke-width:1.2px,color:#44337a
  classDef storage fill:#e6fffa,stroke:#319795,stroke-width:1.2px,color:#234e52
  classDef obs fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  classDef ext fill:#fff1f0,stroke:#e53e3e,stroke-width:1.2px,color:#742a2a

  class Web,Mobile,Backend,Workers app
  class Redis cache
  class Postgres,ES db
  class MinIO storage
  class Obs obs
  class Pay,AI,Notify ext

  style Redis color:#744210,stroke:#b7791f,fill:#fff7d6
  style Rabbit fill:#fff7d6,color:#744210,stroke:#b7791f
  style Postgres color:#744210,stroke:#b7791f,fill:#fff7d6
  style ES color:#744210,stroke:#b7791f,fill:#fff7d6
  style MinIO stroke:#b7791f,fill:#fff7d6,color:#744210
  style Apps fill:#e6f0ff,stroke:#2b6cb0,color:#000000
  style Infrastructure fill:#fff7d6,stroke:#b7791f,color:#000000
  style External fill:#fff1f0,stroke:#e53e3e,color:#000000
```

## 2. Tech Stack

| Layer                 | Technology                                                                 |
| :-------------------- | :------------------------------------------------------------------------- |
| Backend runtime       | Java 25, Spring Boot 4.0.6, Gradle Groovy                                  |
| Backend architecture  | Event-Driven Modular Monolith via Spring Modulith                          |
| Workers architecture  | Hexagonal Architecture (inbound/core/outbound)                             |
| Web                   | React (TypeScript)                                                         |
| Mobile                | React Native (TypeScript) + SQLite (offline-first)                         |
| API Gateway           | Nginx (pure reverse proxy + load balancer; no rate limiting at this layer) |
| Primary database      | PostgreSQL on Neon (serverless, primary + read replica)                    |
| Cache / rate limiting | Redis (Bucket4j Token Bucket, cache-aside, JTI blacklist)                  |
| Message broker        | RabbitMQ                                                                   |
| Search                | Elasticsearch (CQRS read model)                                            |
| Object storage        | MinIO (S3-compatible)                                                      |
| Resilience            | Resilience4j (circuit breaker)                                             |
| Auth                  | JWT RS256 (asymmetric), Redis-backed `jti` revocation                      |
| Observability         | Micrometer → Prometheus → Grafana                                          |
| Payments              | Stripe (sandbox, with `ChaosMockAdapter` for resilience testing)           |

The repository is a monorepo: `apps/backend/`, `apps/workers/`, `apps/web/`, `apps/mobile/` — each independently deployable.

## Application Structures

- [Backend/Workers](server/source-structure.md)
- [Web](client/web/source-structure.md)
- [Mobile](client/mobile/source-structure.md)
- [Infrastructure](../infra/README.md)

## Artifact Guides

- [Specifications](../specs/README.md)
- [Contracts](../contracts/README.md)
- [Agent Configuration](../.agents/README.md)
