# UniHub Workshop — Project Structure

## 1. Root Workspace Structure

This repository uses a monorepo approach to separate deliverable applications (Core Backend, Background Workers, Web Portal, and Mobile Check-in) from infrastructure and architecture blueprints. This keeps the Event-Driven Modular Monolith, offline-first JSI SQLite mobile stack, Redis-based SWR caching, RabbitMQ workers, and the Prometheus/Grafana observability stack easy to discover while preserving clean ownership boundaries for each runtime concern. Agent-assisted development is structured through `.agents/`, which defines reusable skills and slash-command workflows that enforce Spec-Driven and Test-Driven Development across the monorepo.

```text
UniHub/
├── AGENTS.md           # Agent rules + Documentation Map (always loaded)
├── .agents/            # Agent skills infrastructure
│   ├── skills/         # Reusable, language-agnostic skill definitions
│   └── commands/       # Slash commands (/spec, /plan, /build, /review, /fix)
├── apps/               # Product applications in the monorepo
│   ├── backend/        # Spring Boot Modulith core backend API
│   ├── workers/        # Background workers (batch, notifications, AI)
│   ├── web/            # React web portal for students and organizers
│   └── mobile/         # React Native offline-first check-in app
├── infra/              # Local Docker, data services, observability configs
├── scripts/            # Executable scripts (Docker deploy, DB seeding, test scenarios)
├── docs/               # Architecture, design, database, and API documentation
└── specs/              # Per-feature spec artifacts produced by the SDD workflow
```

This physical separation mirrors Clean Architecture boundaries: `apps/` holds the deployable units, `infra/` defines the local runtime environment (Redis, RabbitMQ, Elasticsearch, MinIO, Prometheus, Grafana), and `docs/` anchors the system's authoritative architectural and schema decisions referenced during implementation. `specs/` captures the living, per-feature artifacts (`SPEC.md`, `API_CONTRACT.md`, `tasks.md`) produced when a feature goes through the `/spec → /plan → /build → /review` workflow.

Note that PostgreSQL is intentionally omitted from the local `infra/` stack as the system strictly connects to the serverless Neon PostgreSQL Cloud via encrypted JDBC. Operational automation is centralized in `scripts/`, keeping the root directory clean.

```mermaid
---
config:
  layout: elk
---
flowchart LR
  subgraph Apps["Apps"]
    Web["Web"]
    Mobile["Mobile"]
    Backend["Backend"]
    Workers["Workers"]
  end
  subgraph Infrastructure["Infrastructure"]
    Redis[("Redis")]
    Rabbit["RabbitMQ"]
    Postgres[("PostgreSQL")]
    ES[("Elasticsearch")]
    MinIO[("MinIO / S3")]
    Obs["Prometheus/Grafana"]
  end
  subgraph External["External Services"]
    Pay["Payment Gateway"]
    AI["AI Service"]
    Notify["Notification Providers"]
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

  Web@{ shape: rounded}
  Mobile@{ shape: rounded}
  Backend@{ shape: rounded}
  Workers@{ shape: rounded}
  Rabbit@{ shape: rounded}
  Obs@{ shape: rounded}
  Pay@{ shape: rounded}
  AI@{ shape: rounded}
  Notify@{ shape: rounded}
    Web:::app
    Mobile:::app
    Backend:::app
    Workers:::app
    Redis:::cache
    Postgres:::db
    ES:::db
    MinIO:::storage
    Obs:::obs
    Pay:::ext
    AI:::ext
    Notify:::ext
  classDef app fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef cache fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
  classDef db fill:#f0e6ff,stroke:#6b46c1,stroke-width:1.2px,color:#44337a
  classDef storage fill:#e6fffa,stroke:#319795,stroke-width:1.2px,color:#234e52
  classDef obs fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  classDef ext fill:#fff1f0,stroke:#e53e3e,stroke-width:1.2px,color:#742a2a
  style Redis color:#744210,stroke:#b7791f,fill:#fff7d6
  style Rabbit fill:#fff7d6,color:#744210,stroke:#b7791f
  style Postgres color:#744210,stroke:#b7791f,fill:#fff7d6
  style ES color:#744210,stroke:#b7791f,fill:#fff7d6
  style MinIO stroke:#b7791f,fill:#fff7d6,color:#744210
  style Apps fill:#e6f0ff,stroke:#2b6cb0,color:#000000
  style Infrastructure fill:#fff7d6,stroke:#b7791f,color:#000000
  style External fill:#fff1f0,stroke:#e53e3e,color:#000000
```

## 2. Agent Skills Infrastructure (`.agents/`)

UniHub uses Agent Skills to enforce Spec-Driven Development (SDD) combined with Test-Driven Development (TDD) across the backend and workers codebases.

```text
.agents/
├── skills/                                 # Language-agnostic skill definitions
│   ├── spec-driven-development/
│   ├── api-contract-design/
│   ├── task-planning-and-breakdown/
│   ├── incremental-implementation/
│   ├── test-driven-development/
│   ├── modular-boundary-enforcement/
│   ├── database-schema-evolution/
│   ├── idempotency-and-exactly-once-design/
│   ├── security-and-hardening/
│   ├── performance-and-efficiency-engineering/
│   ├── code-review-and-quality/
│   └── debugging-and-root-cause-analysis/
└── commands/                               # Slash commands
    ├── spec.md                             # /spec
    ├── plan.md                             # /plan
    ├── build.md                            # /build
    ├── review.md                           # /review
    └── fix.md                              # /fix

specs/                                      # Per-feature artifacts
└── <feature-name>/
    ├── SPEC.md
    ├── API_CONTRACT.md
    ├── tasks.md
    └── review-notes.md
```

## 3. Detailed Application Structures

### Core Backend (`apps/backend/`)

#### Directory Layout

```text
apps/backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/unihub/workshop/
│   │   │       ├── WorkshopApplication.java
│   │   │       ├── shared/                   # Shared kernel (cross-cutting concerns)
│   │   │       │   ├── config/               # Infrastructure configuration (Redis, RabbitMQ, MinIO, Elasticsearch, Observability)
│   │   │       │   ├── security/             # JWT RS256, RBAC, auth utilities
│   │   │       │   ├── idempotency/          # Idempotency storage and replay helpers
│   │   │       │   ├── events/               # Domain event markers and base contracts
│   │   │       │   ├── web/                  # HTTP filters, exception mappers, API utilities
│   │   │       │   └── observability/        # Micrometer metrics, logging context, tracing
│   │   │       ├── iam/                      # Auth, organizations, users, student directory validation
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   │   ├── models/           # Domain entities and value objects
│   │   │       │   │   ├── events/           # Domain events
│   │   │       │   │   └── exceptions/       # Domain exceptions and rules
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   │   ├── usecases/         # Application use case orchestration
│   │   │       │   │   └── ports/            # Port definitions for dependency inversion
│   │   │       │   │       ├── in/           # Inbound port contracts
│   │   │       │   │       └── out/          # Outbound port contracts
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   │   ├── persistence/      # JPA entities and Spring Data repositories
│   │   │       │   │   ├── messaging/        # Messaging and integration adapters
│   │   │       │   │   └── config/           # Module-specific infrastructure configuration
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       │       ├── controllers/      # REST controllers
│   │   │       │       └── dto/              # Request/response DTOs
│   │   │       ├── workshops/                # Workshops, speakers, assignments, brochures, AI job tracking (follows iam template)
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       ├── registrations/            # Registration lifecycle and idempotency tracking (follows iam template)
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       ├── payments/                 # Stripe integration and payment state transitions (follows iam template)
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       ├── attendances/              # Check-ins and offline sync reconciliation (follows iam template)
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       ├── notifications/            # In-app notification APIs for web and mobile (follows iam template)
│   │   │       │   ├── contracts/            # Public module interfaces for other modules
│   │   │       │   ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │       │   ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │       │   ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │       │   └── presentation/         # REST controllers and DTOs
│   │   │       └── search/                   # Elasticsearch CQRS read model (follows iam template)
│   │   │           ├── contracts/            # Public module interfaces for other modules
│   │   │           ├── domain/               # Pure Java entities, value objects, domain events, business rules (no Spring/JPA annotations)
│   │   │           ├── application/          # Use cases (inbound ports) and repository interfaces (outbound ports)
│   │   │           ├── infrastructure/       # JPA entities, Spring Data repositories, external adapters (Redis, HTTP clients)
│   │   │           └── presentation/         # REST controllers and DTOs
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── security/                     # RSA key pair
│   │       │   ├── private.pem
│   │       │   └── public.pem
│   │       └── db/
│   │           └── migration/                # Flyway migration scripts
│   │               └── V1__init_schema.sql
│   └── test/
│       └── java/
│           └── com/unihub/workshop/
│               └── ModulithArchitectureTest.java
├── .env                                      # Local dev env vars (DB, Redis, RabbitMQ, etc.)
├── .env.example                              # Env var template committed to source control
├── build.gradle                              # Gradle build definition
└── settings.gradle                           # Gradle settings for the backend module
```

#### Internal Module Communication

- **Cross-module:** synchronous calls go through module `contracts`, and domain events flow through `ApplicationEventPublisher` into the Modulith registry and out to RabbitMQ.

```mermaid
---
config:
  layout: elk
---
flowchart LR
  subgraph Modules["Spring Modulith Modules"]
    IAM["iam"]
    Workshops["workshops"]
    Registrations["registrations"]
    Payments["payments"]
    Attendances["attendances"]
    Notifications["notifications"]
    Search["search"]
  end
  subgraph Contracts["Module Contracts"]
    IAMC["iam/contracts"]
    WSC["workshops/contracts"]
    REGC["registrations/contracts"]
    PAYC["payments/contracts"]
    ATTC["attendances/contracts"]
    NOTC["notifications/contracts"]
    SRC["search/contracts"]
  end
  subgraph Eventing["Spring Modulith Eventing"]
    Publisher["ApplicationEventPublisher"]
    Registry["Spring Modulith Event Registry"]
    Broker["RabbitMQ"]
  end

  Registrations -- sync read --> WSC
  WSC -- API --> Workshops
  Payments -- sync read --> REGC
  REGC -- API --> Registrations & Registrations
  Attendances -- sync read --> REGC
  Notifications -- sync read --> IAMC
  IAMC -- API --> IAM
  Workshops -- WorkshopPublishedEvent --> Publisher
  Publisher -- internal listener --> Notifications
  Publisher --> Registry
  Registry -- externalize --> Broker

  Workshops@{ shape: rounded}
  Registrations@{ shape: rounded}
  Payments@{ shape: rounded}
  Attendances@{ shape: rounded}
  Search@{ shape: rounded}
  IAMC@{ shape: rounded}
  WSC@{ shape: rounded}
  REGC@{ shape: rounded}
  PAYC@{ shape: rounded}
  ATTC@{ shape: rounded}
  NOTC@{ shape: rounded}
  SRC@{ shape: rounded}
    IAM:::module@{ shape: rounded}
    Workshops:::module
    Registrations:::module
    Payments:::module
    Attendances:::module
    Notifications:::module@{ shape: rounded}
    Search:::module
    IAMC:::contract
    WSC:::contract
    REGC:::contract
    PAYC:::contract
    ATTC:::contract
    NOTC:::contract
    SRC:::contract
    Publisher:::eventing@{ shape: rounded}
    Registry:::eventing@{ shape: rounded}
    Broker:::eventing@{ shape: rounded}
  classDef module fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef contract fill:#f0fff4,stroke:#38a169,stroke-width:1.2px,color:#22543d
  classDef eventing fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  style Modules fill:#e6f0ff,stroke:#2b6cb0,color:#000000
  style Contracts fill:#f0fff4,stroke:#38a169,color:#000000
  style Eventing fill:#fff7d6,stroke:#b7791f,color:#000000
```

- **Intra-module:** dependencies point inward (Presentation -> Application -> Domain), while Infrastructure implements ports defined by Application and owns external integrations.

```mermaid
---
config:
  layout: elk
---
flowchart LR
  Presentation["Presentation\nREST Controllers + DTOs"] -- calls --> Application["Application\nUse Cases + Ports"]
  Application -- executes --> Domain["Domain\nEntities + Rules"]
  Infrastructure["Infrastructure\nJPA + Adapters"] -- implements ports --> Application
  Infrastructure -- integrates --> External["External Systems\nRedis, HTTP APIs"]

  Presentation@{ shape: rounded}
  Application@{ shape: rounded}
  Domain@{ shape: rounded}
  Infrastructure@{ shape: rounded}
  External@{ shape: rounded}
    Presentation:::presentation
    Application:::application
    Domain:::domain
    Infrastructure:::infrastructure
    External:::external
  classDef presentation fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef application fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  classDef domain fill:#e6fffa,stroke:#319795,stroke-width:1.2px,color:#234e52
  classDef infrastructure fill:#f0e6ff,stroke:#6b46c1,stroke-width:1.2px,color:#44337a
  classDef external fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
```

### Background Workers (`apps/workers/`)

#### Directory Layout

```text
apps/workers/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/unihub/workshop/workers/
│   │   │       ├── WorkersApplication.java
│   │   │       ├── shared/                   # Shared worker infrastructure
│   │   │       │   ├── config/               # RabbitMQ, S3, Elasticsearch, DB, and observability beans
│   │   │       │   ├── messaging/            # Common AMQP configuration and message utilities
│   │   │       │   └── observability/        # Metrics and logging context
│   │   │       ├── batch_ingestion/          # Nightly CSV ingestion workers (follows ai_processing template)
│   │   │       ├── maintenance/              # Recurring TTL cleanup & database maintenance workers
│   │   │       ├── notifications/            # Notification fan-out workers (follows ai_processing template)
│   │   │       ├── search_sync/              # Elasticsearch CQRS sync workers (follows ai_processing template)
│   │   │       └── ai_processing/            # AI summarization pipeline (hexagonal template)
│   │   │           ├── inbound/              # RabbitMQ listeners and job triggers
│   │   │           ├── core/                 # Use cases, batch jobs, and port interfaces
│   │   │           │   ├── models/           # Internal domain records and data structures (e.g., PdfChunk, JobState)
│   │   │           │   ├── usecases/         # Job orchestration and retry policies
│   │   │           │   ├── ports/            # Port interfaces for adapters
│   │   │           │   │   ├── in/           # Inbound port contracts
│   │   │           │   │   └── out/          # Outbound port contracts
│   │   │           │   └── pipeline/         # PDF extraction, chunking, summarization steps
│   │   │           └── outbound/             # External integrations and persistence
│   │   │               ├── persistence/      # JPA repositories for ai_summary_jobs
│   │   │               ├── storage/          # S3 clients for brochure retrieval
│   │   │               ├── ai_providers/     # External AI API adapters
│   │   │               ├── messaging/        # RabbitMQ publishers for status events
│   │   │               └── config/           # Module-specific adapter configuration
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/
│           └── com/unihub/workshop/workers/
├── .env                                      # Local dev env vars (DB, RabbitMQ, S3, AI API key)
├── .env.example                              # Env var template committed to source control
├── build.gradle                              # Gradle build definition
├── settings.gradle                           # Gradle settings for the workers module
└── README.md                                 # Worker-specific run and troubleshooting notes
```

#### Internal Module Communication

**Hexagonal Architecture (Intra-Module Flow):**

- Both `inbound` and `outbound` adapters depend inward on `core` via ports; `core` has zero outward dependencies.
- External triggers and systems are isolated behind adapters to keep task logic deterministic and testable.

```mermaid
---
config:
  layout: elk
---
flowchart LR
  Triggers["External Triggers\n(RabbitMQ, Cron)"] -- deliver events --> Inbound["Inbound Layer\n(Listeners, Schedulers)"]
  Inbound -- call inbound ports --> Core["Core Layer\n(Use Cases, Models, Ports)"]
  Outbound["Outbound Layer\n(API Clients, Repositories)"] -- implements outbound ports --> Core
  Outbound -- integrates --> External["External Systems\n(S3, DB, AI)"]

  Triggers@{ shape: rounded}
  Inbound@{ shape: rounded}
  Core@{ shape: rounded}
  Outbound@{ shape: rounded}
  External@{ shape: rounded}
    Triggers:::triggers
    Inbound:::inbound
    Core:::core
    Outbound:::outbound
    External:::external
  classDef triggers fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  classDef inbound fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef core fill:#e6fffa,stroke:#319795,stroke-width:1.2px,color:#234e52
  classDef outbound fill:#f0e6ff,stroke:#6b46c1,stroke-width:1.2px,color:#44337a
  classDef external fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
```

**Task-Driven Async Pipeline (AI Processing Example):**

- Worker consumes brokered events, performs local chunking to protect payload limits, and writes results to PostgreSQL.
- On repeated failures, the broker routes the message to the DLQ for inspection and replay.

```mermaid
---
config:
  layout: elk
---
flowchart TB
  subgraph Worker["AI Worker Module"]
    Listener["Inbound Listener"]
    Core["Core Logic\n(Chunking + Use Cases)"]
    Outbound["Outbound Adapters"]
  end

  Broker["RabbitMQ Broker"] -- event --> Listener
  Listener -- dispatch --> Core
  Core -- invoke ports --> Outbound
  Outbound -- fetch PDF --> S3["MinIO (S3)"]
  Outbound -- summarize --> AI["AI Service API"]
  Outbound -- persist result --> DB["PostgreSQL"]
  Core -- nack after retries --> Broker
  Broker -- route to DLQ --> DLQ["Dead Letter Queue (DLQ)"]

  Listener@{ shape: rounded}
  Core@{ shape: rounded}
  Outbound@{ shape: rounded}
  Broker@{ shape: rounded}
  S3@{ shape: rounded}
  AI@{ shape: rounded}
  DB@{ shape: rounded}
  DLQ@{ shape: rounded}
    Listener:::worker
    Core:::core
    Outbound:::outbound
    Broker:::broker
    S3:::infra
    AI:::infra
    DB:::infra
    DLQ:::dlq
  classDef broker fill:#fff7d6,stroke:#b7791f,stroke-width:1.2px,color:#744210
  classDef worker fill:#e6f0ff,stroke:#2b6cb0,stroke-width:1.2px,color:#1a365d
  classDef core fill:#e6fffa,stroke:#319795,stroke-width:1.2px,color:#234e52
  classDef outbound fill:#f0e6ff,stroke:#6b46c1,stroke-width:1.2px,color:#44337a
  classDef infra fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
  classDef dlq fill:#ffe6e6,stroke:#c53030,stroke-width:1.2px,color:#742a2a
  style Broker fill:#e6f0ff,stroke:#2b6cb0,color:#1a365d
  style Worker fill:#fff7d6,stroke:#b7791f,color:#000000
```

### Web Portal (`apps/web/`)

#### Directory Layout

#### Internal Module Communication

### Mobile Check-in App (`apps/mobile/`)

#### Directory Layout

#### Internal Module Communication

### Infrastructure & Observability (`infra/`)

```text
infra/                                        # Local Docker runtime and observability stack
├── docker-compose.yml                        # Orchestrates Nginx, Redis, RabbitMQ, ES, MinIO, Prometheus, Grafana
├── .env                                      # Infra-only docker-compose variables (ports, admin passwords)
├── .env.example                              # Sample infra-only variables (apps keep separate .env files)
├── nginx/                                    # API gateway and load balancer config
│   ├── nginx.conf                            # Base Nginx configuration
│   └── templates/                            # envsubst templates for dynamic routing
│       └── default.conf.template             # Reverse proxy and upstream load balancing template
├── redis/                                    # Redis configuration and init scripts
│   └── redis.conf                            # Cache and rate limiting defaults
├── rabbitmq/                                 # RabbitMQ configuration and definitions
│   ├── definitions.json.template             # Exchanges, queues, and bindings
│   └── rabbitmq.conf                         # Broker configuration
├── elasticsearch/                            # Elasticsearch configuration
│   └── elasticsearch.yml                     # Node and index settings for CQRS search
├── minio/                                    # MinIO (S3-compatible) configuration
│   └── init/                                 # Bucket bootstrap scripts
│       └── create-buckets.sh                 # Creates required buckets for PDFs and CSVs
├── prometheus/                               # Prometheus configuration
│   └── prometheus.yml                        # Scrape config for backend and workers
└── grafana/                                  # Grafana provisioning and dashboards
    ├── provisioning/                         # Datasources and dashboards provisioning
    │   ├── datasources/                      # Grafana datasources (IaC)
    │   │   └── prometheus.yml                # Prometheus datasource definition
    │   └── dashboards/                       # Dashboard providers (IaC)
    │       └── dashboards.yml                # File-based dashboard loader
    └── dashboards/                           # Dashboard JSON templates
        ├── jvm-spring-boot.json              # JVM and Spring Boot Micrometer metrics
        ├── database-hikaricp.json            # HikariCP pool saturation and connection health
        ├── redis-bucket4j.json               # Redis cache and Bucket4j rate limit telemetry
        ├── rabbitmq-metrics.json             # Queue depth, consumer lag, and DLQ signals
        ├── resilience4j-circuit-breaker.json # Circuit breaker state and failure rates
        └── minio-storage.json                # MinIO object storage health and capacity
```
