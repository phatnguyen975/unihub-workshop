# UniHub Workshop - Technical Design

## 1. Overall Architecture

### Architectural Style

**Chosen architecture:** Modular Monolith with event-driven components and Clean Architecture boundaries.

This approach balances the high-traffic requirements with rapid time-to-market expectations and optimized operational overhead. A modular monolith keeps operational complexity low while enforcing strong internal boundaries (registration, payments, check-in, notifications, ingestion, admin). Event-driven components (via a broker and outbox pattern) handle asynchronous workloads like notifications, AI summarization, and CSV ingestion without blocking core transactions.

**Why this is the best fit now:**

- **High concurrency, low ops overhead:** A single deployable unit scales horizontally behind a load balancer while avoiding microservice orchestration burden.
- **Strict data consistency:** Centralized PostgreSQL transactions reduce the risk of overbooking under heavy contention.
- **Clear growth path:** Well-defined module boundaries allow gradual extraction into services if future scale requires it.
- **Rapid delivery requirements:** Faster integration and testing with reduced operational overhead.

> [!TIP]
> This architecture keeps the critical path (seat allocation and payment) synchronous and strongly consistent, while offloading non-critical tasks to async workers.

### Core Components

| Component                       | Purpose                                                    | Notes                                                                  |
| :------------------------------ | :--------------------------------------------------------- | :--------------------------------------------------------------------- |
| React Web App                   | Student discovery/registration and Admin management portal | React + TypeScript UI with RBAC-guarded views                          |
| Android Mobile App (Java / XML) | QR scanning and offline-first check-in                     | Room/SQLite local store with deterministic sync                        |
| Java Backend API                | Central business logic and orchestration                   | Spring Boot modular monolith, HikariCP, Resilience4j, outbox relay     |
| PostgreSQL                      | System of record                                           | Primary-replica topology with pessimistic locking and idempotency keys |
| Message Broker                  | Async event processing                                     | RabbitMQ for notifications, AI, and ingestion events                   |
| Cache / Rate Limiter            | Spike protection and fast reads                            | Redis token bucket and cache-aside workshop reads                      |
| Object Storage                  | PDFs and ingestion files                                   | S3-compatible storage for brochures and CSV drops                      |

### Communication Protocols

- **Web App to Backend:** HTTPS using REST/GraphQL APIs with JWT-based auth.
- **Mobile App to Backend:** HTTPS REST APIs optimized for batch sync and idempotency.
- **Backend to PostgreSQL:** JDBC with transaction isolation and row-level locking.
- **Backend to Broker:** AMQP protocol for async jobs and event fan-out.
- **Backend to External Services:** HTTPS for Payment Gateway, AI service, and notification providers.
- **Legacy CSV Input:** Scheduled file drop over SFTP or secure object storage pickup.

### Resilience Strategy

- **Admission control:** Token Bucket rate limiting in Redis enforced at the API gateway.
- **Strong consistency:** Pessimistic locking in PostgreSQL primary to prevent overbooking.
- **Graceful degradation:** Resilience4j circuit breakers around payment and AI services.
- **Idempotency:** Idempotency keys stored in PostgreSQL with TTL for payments and sync.
- **Offline-first safety:** Room/SQLite local persistence with server-side idempotent reconciliation.

> [!NOTE]
> The synchronous path is kept minimal: validate user, reserve seat, confirm payment, issue QR. Everything else is asynchronous and retry-safe.

## 2. C4 Diagram

### 2.1. Level 1 - System Context

<p align="center">
  <img src="../docs/diagrams/c4_system_context.png" style="width:80%;" alt="System Context Diagram">
  <img src="../docs/diagrams/c4_system_context_key.png" style="width:80%;" alt="System Context Diagram Key">
</p>

The system context view positions UniHub Workshop as the central platform coordinating all workshop lifecycle activities. Three primary personas interact with the system. **Students** browse schedules, register for sessions, and receive QR confirmations. **Organizers** use the system for operational control, including workshop creation, rescheduling, and analytics. **Check-in staff** rely on the system for attendance validation through the mobile app.

External dependencies shape the system boundary. The **Legacy Student Management System** provides nightly CSV exports for identity verification, which flow into UniHub without a return path. The **Payment Gateway** processes paid registrations and requires resilient integration to avoid service collapse during outages. The **AI Summarization Service** is invoked to produce concise workshop descriptions from uploaded PDFs. **Notification Providers** deliver immediate confirmations and alerts, and must be pluggable to enable future channels without core changes.

This context emphasizes clear trust boundaries: all users interact through secure HTTPS endpoints, while external systems are integrated through dedicated adapters. It reinforces that UniHub owns transactional integrity and availability, while external services are treated as potentially unreliable dependencies protected with circuit breakers and retry policies.

### 2.2. Level 2 - Container

<p align="center">
  <img src="../docs/diagrams/c4_container.png" style="width:80%;" alt="Container Diagram">
  <img src="../docs/diagrams/c4_container_key.png" style="width:80%;" alt="Container Diagram Key">
</p>

The container view decomposes UniHub into client apps, backend components, and infrastructure services aligned with the modular monolith strategy. The **React + TypeScript Web App** serves Students and Organizers with distinct RBAC-guarded experiences. The **Android Mobile App** is dedicated to check-in staff and includes a local database for offline-first scanning and later synchronization.

At the edge, an **API Gateway / Load Balancer** provides TLS termination, routing, and rate limiting to protect the backend during the 12,000+ user surge. The **Backend API** is a single Java deployable with strict internal modules (registration, payment, notifications, check-in, ingestion, admin). It performs transactional operations against **PostgreSQL**, enforcing seat allocation and payment consistency.

Asynchronous workloads run through a **Message Broker** and **Background Workers** to keep the critical path fast and reliable. Workers handle notifications, AI summarization, and CSV ingestion without blocking user requests. A **Cache / Rate Limiter** layer (Redis) supports burst control and fast availability reads. **Object Storage** holds workshop PDFs and nightly CSV exports, enabling reliable batch processing and auditability.

This structure ensures the system can scale horizontally under traffic spikes, maintain data integrity with PostgreSQL transactions, and support offline check-ins with eventual reconciliation while keeping operational overhead manageable with optimized operational resources.

## 3. High-Level Architecture Diagram

### System Architecture

This diagram shows the production topology: horizontally scaled Spring Boot app servers behind an API gateway, Redis for cache and token bucket rate limiting, PostgreSQL primary-replica for write safety and read scalability, and dedicated workers for outbox relay, AI, and CSV ingestion. External integrations are modeled explicitly.

<p align="center">
  <img src="../docs/diagrams/system_architecture.png" style="width:90%;" alt="System Architecture">
</p>

- **Traffic distribution:** Nginx load balances external HTTPS requests across horizontally scaled Spring Boot app servers.
- **Cache and rate limiting:** App servers use cache-aside reads with Redis and enforce Token Bucket rate limiting at the edge.
- **Read/write separation:** App servers read from the PostgreSQL replica on cache misses and write domain data plus outbox events to the primary.
- **Worker responsibilities:** Workers poll and lock outbox rows and write async results on the primary, while reading from the replica for batch validation workloads such as CSV ingestion.
- **Broker integration:** Workers publish and consume tasks via RabbitMQ to drive AI summarization and notification delivery.

### Workshop Registration Flow (Free & Paid)

This sequence captures the end-to-end registration flow, including concurrency control, idempotent payment retries, and outbox-driven notifications.

```mermaid
sequenceDiagram
  actor Student
  participant Web as Web App
  participant GW as API Gateway
  participant Redis as Redis (Token Bucket)
  participant API as Backend API
  participant DB as PostgreSQL Primary
  participant Pay as Payment Gateway
  participant Relay as Outbox Relay Worker
  participant Broker as RabbitMQ
  participant Notify as Notification Provider

  Student->>Web: Select workshop + submit registration
  Web->>GW: POST /registrations (idempotency key)
  GW->>Redis: Consume token bucket
  alt Rate limited
    Redis-->>GW: Reject
    GW-->>Web: 429 Too Many Requests
  else Allowed
    Redis-->>GW: Allow
    GW->>API: Forward request
    API->>DB: Begin tx, lock workshop row
    API->>DB: Check capacity
    alt Workshop full
      API-->>Web: 409 Sold out
    else Seats available
      alt Free workshop
        API->>DB: Create registration + outbox event
        API->>DB: Commit
        API-->>Web: 201 + QR code
      else Paid workshop
        API->>DB: Create pending registration + idempotency record
        API->>Pay: Charge (idempotency key)
        alt Payment success
          Pay-->>API: Approved
          API->>DB: Confirm registration + outbox event
          API->>DB: Commit
          API-->>Web: 201 + QR code
        else Timeout or failure
          Pay-->>API: Timeout/Error
          API->>DB: Mark payment pending/failed
          API->>DB: Commit
          API-->>Web: 202 Pending or 502 Error
        end
      end
    end
  end

  par Outbox publish and notify
    Relay->>DB: Poll outbox table
    Relay->>Broker: Publish event
    Broker->>Notify: Deliver confirmation
  end

  Note over Web,API: Client retry uses same idempotency key
  Web->>GW: Retry registration request
  GW->>API: Forward
  API->>DB: Lookup idempotency record
  API-->>Web: Return prior result (no double charge)
```

### Offline Check-in & Synchronization Flow

This sequence illustrates offline capture, local persistence, and server-authoritative reconciliation that prevents duplicate attendance records.

```mermaid
sequenceDiagram
  actor Staff
  participant Mobile as Android App
  participant Local as Room/SQLite
  participant API as Backend API
  participant DB as PostgreSQL Primary

  Staff->>Mobile: Scan QR code
  Mobile->>Local: Store check-in event (pending)
  Note over Mobile,Local: Offline-first capture, no network required

  Mobile->>API: Sync batch when online
  API->>DB: Validate and upsert attendance (idempotent key)
  alt New attendance
    DB-->>API: Inserted
    API-->>Mobile: Ack (recorded)
    Mobile->>Local: Mark synced
  else Duplicate or conflict
    DB-->>API: Already recorded
    API-->>Mobile: Ack (existing)
    Mobile->>Local: Mark synced with server status
  end
```

### Async AI Summarization Flow

This sequence shows the asynchronous pipeline for PDF processing using the outbox pattern and brokered workers, including error handling when the AI service is unavailable.

```mermaid
sequenceDiagram
  actor Organizer
  participant Web as Web App
  participant API as Backend API
  participant DB as PostgreSQL Primary
  participant S3 as S3 Object Storage
  participant Relay as Outbox Relay Worker
  participant Broker as RabbitMQ
  participant Worker as AI Worker
  participant AI as AI Service

  Organizer->>Web: Upload brochure PDF
  Web->>API: POST /workshops/{id}/brochure
  API->>S3: Store PDF
  API->>DB: Create summary job + outbox event
  API-->>Web: 202 Accepted

  Relay->>DB: Poll outbox table
  Relay->>Broker: Publish summarization event
  Broker->>Worker: Deliver job
  Worker->>S3: Fetch PDF
  Worker->>AI: Extract and summarize
  alt AI unavailable
    AI-->>Worker: Error/Timeout
    Worker-->>Broker: Retry with backoff
  else Success
    AI-->>Worker: Summary
    Worker->>DB: Store summary result
  end
```

### Nightly Legacy CSV Ingestion Flow

This flowchart represents the nightly batch pipeline, showing fault-tolerant parsing, validation, and row-level quarantine without failing the entire job.

```mermaid
flowchart TD
  Legacy[Legacy Student System] --> S3[S3 CSV Drop]
  Scheduler[Scheduler] --> Job[Spring Batch Job]
  Job --> Fetch[Fetch CSV from S3]
  Fetch --> Parse[Parse rows]
  Parse --> Validate[Validate row]
  Validate -->|Valid| Dedup[Deduplicate]
  Validate -->|Invalid| Quarantine[Quarantine row + error report]
  Dedup --> Upsert[Upsert to PostgreSQL]
  Quarantine --> Continue[Skip row and continue job]
  Upsert --> Complete[Job complete with metrics]
```

## 7. Architectural Decision Records

### ADR 001: Backend Language and Framework

- **Context:** The backend must handle 12,000 concurrent users, enforce strict transactional integrity for seat allocation, and be delivered under rapid time-to-market requirements with optimized operational resources.
- **Decision:** Use Java with Spring Boot in a modular monolith architecture.
- **Rationale:** Java and Spring Boot provide mature concurrency support, strong ecosystem libraries for resilience patterns, and straightforward transaction management with PostgreSQL. Compared to Node.js, Java offers more predictable multi-threaded performance under sustained load and a larger set of battle-tested tooling for transactional workloads. Compared to Go, Spring Boot accelerates delivery due to richer scaffolding and familiarity while still meeting throughput requirements.
- **Consequences:** Requires JVM tuning and disciplined module boundaries to avoid monolith sprawl, but reduces operational complexity and time-to-delivery.

### ADR 002: Primary Database

- **Context:** Seat allocation and payment confirmation must be ACID-compliant to prevent overbooking and double-charging.
- **Decision:** Use PostgreSQL as the primary transactional database.
- **Rationale:** PostgreSQL provides strong ACID guarantees, row-level locking, and mature indexing, which are critical for the 60-seat race condition. Compared to MongoDB, PostgreSQL offers more reliable transactional semantics and consistent constraints for concurrent seat allocation. This aligns with the requirement to prevent the final seat from being claimed by multiple users.
- **Consequences:** Schema evolution requires migrations and careful modeling, but yields predictable consistency and safer concurrency controls.

### ADR 003: Web Frontend Framework (React + TypeScript)

- **Context:** The web portal serves two roles (Students and Organizers) with distinct workflows and RBAC controls.
- **Decision:** Use React with TypeScript for the web application.
- **Rationale:** React provides strong component reuse for role-specific views and a broad ecosystem for routing and state management. TypeScript adds compile-time type safety and stronger IDE support, reducing runtime errors when handling complex API payloads and RBAC-driven UI states. Compared to plain React with JavaScript, TypeScript improves refactoring safety and long-term maintainability. Compared to Angular, React + TypeScript avoids the heavier framework surface while still delivering strong typing. Compared to Vue, it offers deeper enterprise tooling maturity and larger hiring pools.
- **Consequences:** Requires disciplined state management and UI architecture to avoid complexity in multi-role experiences.

### ADR 004: Mobile App Technology

- **Context:** Check-in staff need fast camera scanning and reliable offline-first persistence under poor connectivity.
- **Decision:** Build the mobile app as Android Native using Java and XML.
- **Rationale:** Native Android provides direct access to camera APIs and fine-grained control of scanning performance. Compared to Flutter or React Native, native Java/XML avoids bridging overhead and offers deeper integration with offline storage and background sync constraints. This is essential for rapid QR scanning and deterministic offline behavior.
- **Consequences:** Limits cross-platform reuse, but delivers the highest reliability for the dedicated Android staff workflow.

### ADR 005: Message Broker

- **Context:** Notifications, AI summarization, and CSV ingestion must run asynchronously without blocking registration flows.
- **Decision:** Use RabbitMQ as the message broker.
- **Rationale:** RabbitMQ provides straightforward queue semantics, reliable delivery, and reduced operational overhead. Compared to Kafka, RabbitMQ has lower operational overhead and fits the task-queue model of notifications and ingestion. Kafka is more suitable for high-throughput event streaming, which is not the primary requirement here.
- **Consequences:** Lower throughput than Kafka for stream-heavy workloads, but adequate for async tasks and easier to operate.

### ADR 006: Caching and Rate Limiting Storage

- **Context:** The system must absorb a 12,000-user traffic spike and enforce fair rate limiting.
- **Decision:** Use Redis for caching and rate-limiting counters.
- **Rationale:** Redis provides atomic operations, expiring keys, and high throughput for token bucket counters. Compared to Memcached, Redis offers richer data structures and persistence options that simplify rate limiting and short-lived availability caching.
- **Consequences:** Requires memory sizing and eviction policy tuning, but offers reliable performance under peak load.

### ADR 007: Mobile Local Database

- **Context:** Offline-first check-in requires durable local storage and deterministic sync.
- **Decision:** Use SQLite via Room persistence library.
- **Rationale:** Room offers compile-time schema validation and structured queries over SQLite, which is robust for offline records. Compared to Realm or custom file storage, Room provides predictable query behavior and easier conflict handling, aligning with the need for reliable reconciliation.
- **Consequences:** Requires schema migrations on mobile, but improves data integrity and maintainability.

### ADR 008: API Design Style

- **Context:** Two clients (web and mobile) must integrate quickly and reliably under accelerated delivery requirements.
- **Decision:** Use RESTful APIs for core services.
- **Rationale:** REST offers clear resource modeling, easier caching, and simpler client integration. Compared to GraphQL, REST avoids additional schema management and resolver performance pitfalls, reducing delivery risk through streamlined development and operations. GraphQL can be introduced later if client flexibility becomes a stronger need.
- **Consequences:** May require multiple endpoints for complex UI screens, but simplifies initial delivery and observability.

### ADR 009: Authentication Mechanism

- **Context:** The system must scale under traffic spikes and support multiple client platforms.
- **Decision:** Use stateless JWT-based authentication with role claims.
- **Rationale:** JWTs reduce server-side session storage and scale horizontally behind a load balancer. Compared to stateful sessions, JWTs avoid shared session stores and simplify API scaling during peak load. RBAC claims embedded in tokens support quick authorization decisions.
- **Consequences:** Requires token rotation and revocation strategy, and careful token lifetime management for security.

### ADR 010: Object Storage

- **Context:** The system must store PDF brochures and nightly CSV drops reliably and cheaply.
- **Decision:** Use S3-compatible object storage.
- **Rationale:** Object storage is cost-effective for large immutable files and integrates well with background processing. Compared to storing files in PostgreSQL, object storage avoids database bloat and improves IO performance for large files.
- **Consequences:** Requires access controls and lifecycle policies, but improves scalability and operational clarity.

### ADR 011: Database Connection Pooling Strategy

- **Context:** High concurrency requires efficient database connection management to prevent saturation.
- **Decision:** Use HikariCP for JDBC connection pooling.
- **Rationale:** HikariCP offers low-latency pooling and strong observability. Compared to default or older pools, it provides better performance under bursty load, aligning with the 12,000-user surge window.
- **Consequences:** Requires careful tuning of pool sizes to avoid exhausting PostgreSQL resources.

### ADR 012: Event Outbox Pattern

- **Context:** Async processing must remain consistent with transactional writes to prevent lost events.
- **Decision:** Implement a transactional outbox pattern for event publishing.
- **Rationale:** The outbox pattern ensures events are stored with the same transaction as domain changes and later published reliably. Compared to direct broker writes from request threads, it avoids inconsistency when failures occur between database commit and broker publish.
- **Consequences:** Adds a background relay process and operational monitoring for outbox lag.

### ADR 013: AI Summarization Integration

- **Context:** Workshop summaries must be generated from PDFs without blocking admin workflows.
- **Decision:** Use an async worker pipeline that extracts text, cleans it, and calls the AI service.
- **Rationale:** An async pipeline isolates AI latency and external failures from the user experience. Compared to synchronous in-request processing, it prevents timeouts and improves reliability under traffic spikes.
- **Consequences:** Introduces eventual consistency for summaries and requires status tracking for processing jobs.

### ADR 014: Database Locking Strategy

- **Context:** High-demand workshops can receive hundreds of simultaneous registration attempts for the final seats, and overbooking must be prevented.
- **Decision:** Use pessimistic row-level locking for seat allocation (for example, `SELECT ... FOR UPDATE` on the workshop capacity row).
- **Rationale:** Pessimistic locking serializes competing transactions for the same workshop, guaranteeing only one transaction can claim the final seat. Compared to optimistic locking with version checks, pessimistic locking avoids excessive retries during the 12,000-user spike where contention is expected to be high.
- **Consequences:** Increases lock contention under heavy load and requires careful transaction scoping to keep locks short-lived.

### ADR 015: Database Replication Strategy

- **Context:** Read-heavy browse traffic during the surge can overload the primary database if reads and writes are co-located.
- **Decision:** Use a primary-replica PostgreSQL topology with read/write separation.
- **Rationale:** Read replicas offload workshop browsing and analytics queries from the primary, preserving write performance for registration and payments. Compared to a single primary, this reduces contention during traffic spikes. Replication lag is acceptable for browse views, while transactional writes remain on the primary.
- **Consequences:** Introduces eventual consistency on read replicas and requires routing logic to avoid stale reads for critical operations.

### ADR 016: Caching Strategy Pattern

- **Context:** Workshop browsing and availability checks are read-heavy, especially during the initial surge.
- **Decision:** Use the cache-aside pattern with Redis for workshop listings and non-critical availability reads.
- **Rationale:** Cache-aside allows the backend to control freshness and avoids stale writes. Compared to write-through caching, cache-aside reduces write amplification and keeps the critical write path simple. Stale reads are mitigated with short TTLs and explicit invalidation after capacity updates.
- **Consequences:** Requires cache invalidation logic and accepts brief inconsistencies for non-critical reads.

### ADR 017: Idempotency Mechanism

- **Context:** Payment retries and gateway timeouts can cause duplicate charges without strict idempotency control.
- **Decision:** Use idempotency keys stored in PostgreSQL with a unique constraint and a TTL window (for example, 24 hours).
- **Rationale:** Persisting idempotency records in PostgreSQL ensures durability across restarts and aligns with transactional workflows. Compared to Redis-only storage, PostgreSQL avoids data loss that could lead to double-charging. Keys are hashed with request fingerprints and stored alongside status to safely replay responses.
- **Consequences:** Adds storage overhead and requires cleanup of expired keys, but guarantees exactly-once payment execution.

### ADR 018: Batch Processing Strategy

- **Context:** Nightly CSV ingestion must tolerate malformed rows and duplicates without failing the entire pipeline.
- **Decision:** Use Spring Batch with chunk processing, skip policies, and error quarantining.
- **Rationale:** Spring Batch provides structured retries, per-row validation, and fault-tolerant processing. Compared to ad-hoc scripts, it prevents a single bad row from crashing the job and enables reporting on skipped records for audit.
- **Consequences:** Adds configuration complexity and requires job monitoring, but delivers robust ingestion for the legacy system.

### ADR 019: Pluggable Notification Architecture

- **Context:** The system must support immediate notifications and allow future channels (for example, Telegram) without core changes.
- **Decision:** Use a provider abstraction with strategy-based adapters for notification channels.
- **Rationale:** A pluggable interface decouples core workflows from delivery mechanisms. Compared to hard-coded email logic, adapters allow new channels to be introduced with minimal changes and consistent retry behavior.
- **Consequences:** Requires adapter contracts and configuration management, but prevents vendor lock-in and reduces future integration cost.

### ADR 020: Offline Sync Conflict Resolution

- **Context:** Offline check-in must preserve attendance records and reconcile safely when connectivity returns.
- **Decision:** Use server-authoritative reconciliation with idempotent check-in events keyed by workshop and student identifiers.
- **Rationale:** Server-authoritative reconciliation avoids conflicting outcomes when multiple offline devices sync the same attendee. Compared to client-wins merging, idempotent server-side upserts ensure no duplicate attendance is recorded and preserve auditability.
- **Consequences:** Requires careful event modeling and unique constraints, but guarantees consistent attendance records after offline periods.

### ADR 021: Rate Limiting Algorithm

- **Context:** Registration can spike to 12,000 concurrent users within minutes, and the backend must ensure fair access while preventing overload.
- **Decision:** Use the Token Bucket algorithm implemented via the Bucket4j library in the Spring Boot backend, with distributed state stored in Redis.
- **Rationale:** Token Bucket smooths bursts while allowing controlled short-term spikes, which aligns with the initial surge profile. Compared to Fixed Window, it avoids boundary spikes, and compared to Sliding Window, it is simpler and cheaper to compute at high scale while still providing fairness. Using Bucket4j provides production-ready, thread-safe integration with Redis without writing custom Lua scripts.
- **Consequences:** Requires careful tuning of refill rates and burst sizes; if misconfigured, legitimate users may see throttling during peak demand.

### ADR 022: Circuit Breaker Implementation

- **Context:** Payment gateways can fail or time out; the system must protect core functionality and prevent cascading failures during outages.
- **Decision:** Use Resilience4j for circuit breakers in the Java/Spring Boot backend.
- **Rationale:** Resilience4j is actively maintained, Spring Boot friendly, and supports Closed/Open/Half-Open states with metrics and fallback patterns. Compared to Netflix Hystrix (now in maintenance mode), Resilience4j offers lower overhead and modern integration without legacy constraints.
- **Consequences:** Adds dependency configuration and requires explicit fallback design; incorrect thresholds can cause premature open states or delayed recovery.
