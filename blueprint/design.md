# UniHub Workshop - Technical Design

## 1. Overall Architecture

### Architectural Style

**Chosen architecture:** Modular Monolith with event-driven components and Clean Architecture boundaries.

This approach balances the high-traffic requirements with rapid time-to-market expectations and optimized operational overhead. A modular monolith keeps operational complexity low while enforcing strong internal boundaries (registration, payments, check-in, notifications, ingestion, admin). Event-driven components (via a broker and Spring Modulith Event Publication registry) handle asynchronous workloads like notifications, AI summarization, and CSV ingestion without blocking core transactions.

**Why this is the best fit now:**

- **High concurrency, low ops overhead:** A single deployable unit scales horizontally behind a load balancer while avoiding microservice orchestration burden.
- **Strict data consistency:** Centralized PostgreSQL transactions reduce the risk of overbooking under heavy contention.
- **Clear growth path:** Well-defined module boundaries allow gradual extraction into services if future scale requires it.
- **Rapid delivery requirements:** Faster integration and testing with reduced operational overhead.

> [!TIP]
> This architecture keeps the critical path (seat allocation and payment) synchronous and strongly consistent, while offloading non-critical tasks to async workers.

### Core Components

| Component                       | Purpose                                                    | Notes                                                                                                         |
| :------------------------------ | :--------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------ |
| React Web App                   | Student discovery/registration and Admin management portal | React + TypeScript UI with RBAC-guarded views                                                                 |
| Android Mobile App (Java / XML) | QR scanning and offline-first check-in                     | Room/SQLite local store with deterministic sync                                                               |
| Java Backend API                | Central business logic and orchestration                   | Spring Boot modular monolith, HikariCP, Resilience4j, Spring Modulith event publication registry              |
| PostgreSQL                      | System of record                                           | Primary-replica topology with pessimistic locking, idempotency keys, and native FTS (GIN) for workshop search |
| Message Broker                  | Async event processing                                     | RabbitMQ for notifications, AI, and ingestion events                                                          |
| Cache / Rate Limiter            | Spike protection and fast reads                            | Redis token bucket and cache-aside for workshop reads and search                                              |
| Object Storage                  | PDFs and ingestion files                                   | S3-compatible storage for brochures and CSV drops                                                             |

PostgreSQL also stores in-app notification inbox records and workshop room layout references so client-facing metadata remains consistent with transactional state.

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
- **Event durability:** Spring Modulith Event Publication registry persists domain events transactionally and externalizes them to RabbitMQ, tracking retry attempts, last resubmission timestamps, and publication status for observability.
- **Payment chaos testing:** Stripe sandbox integration plus a `ChaosMockAdapter` to validate circuit breaker behavior and capacity compensation paths.
- **Offline-first safety:** Room/SQLite local persistence with server-side idempotent reconciliation.

> [!NOTE]
> The synchronous path is kept minimal: validate user, reserve seat, confirm payment, issue QR. Everything else is asynchronous and retry-safe.

## 2. C4 Diagram

### 2.1. Level 1 - System Context

<p align="center">
  <img src="./diagrams/c4_system_context.png" style="width:80%;" alt="System Context Diagram">
  <img src="./diagrams/c4_system_context_key.png" style="width:80%;" alt="System Context Diagram Key">
</p>

The system context view positions UniHub Workshop as the central platform coordinating all workshop lifecycle activities. Three primary personas interact with the system. **Students** browse schedules, register for sessions, and receive QR confirmations. **Organizers** use the system for operational control, including workshop creation, rescheduling, and analytics. **Check-in staff** rely on the system for attendance validation through the mobile app.

External dependencies shape the system boundary. The **Legacy Student Management System** provides nightly CSV exports for identity verification, which flow into UniHub without a return path. The **Payment Gateway** processes paid registrations and requires resilient integration to avoid service collapse during outages. The **AI Summarization Service** is invoked to produce concise workshop descriptions from uploaded PDFs. **Notification Providers** deliver immediate confirmations and alerts, and must be pluggable to enable future channels without core changes.

This context emphasizes clear trust boundaries: all users interact through secure HTTPS endpoints, while external systems are integrated through dedicated adapters. It reinforces that UniHub owns transactional integrity and availability, while external services are treated as potentially unreliable dependencies protected with circuit breakers and retry policies.

### 2.2. Level 2 - Container

<p align="center">
  <img src="./diagrams/c4_container.png" style="width:80%;" alt="Container Diagram">
  <img src="./diagrams/c4_container_key.png" style="width:80%;" alt="Container Diagram Key">
</p>

The container view decomposes UniHub into client apps, backend components, and infrastructure services aligned with the modular monolith strategy. The **React + TypeScript Web App** serves Students and Organizers with distinct RBAC-guarded experiences. The **Android Mobile App** is dedicated to check-in staff and includes a local database for offline-first scanning and later synchronization.

At the edge, an **API Gateway / Load Balancer** provides TLS termination, routing, and rate limiting to protect the backend during the 12,000+ user surge. The **Backend API** is a single Java deployable with strict internal modules (registration, payment, notifications, check-in, ingestion, admin). It performs transactional operations against **PostgreSQL**, enforcing seat allocation and payment consistency.

Asynchronous workloads run through a **Message Broker** and **Background Workers** to keep the critical path fast and reliable. Workers handle notifications, AI summarization, and CSV ingestion without blocking user requests. A **Cache / Rate Limiter** layer (Redis) supports burst control and fast availability reads. **Object Storage** holds workshop PDFs and nightly CSV exports, enabling reliable batch processing and auditability.

This structure ensures the system can scale horizontally under traffic spikes, maintain data integrity with PostgreSQL transactions, and support offline check-ins with eventual reconciliation while keeping operational overhead manageable with optimized operational resources.

## 3. High-Level Architecture Diagram

### System Architecture

This diagram shows the production topology: horizontally scaled Spring Boot app servers behind an API gateway, Redis for cache and token bucket rate limiting, PostgreSQL primary-replica for write safety and read scalability, and Spring Modulith event publication registry with dedicated workers for AI and CSV ingestion. External integrations are modeled explicitly.

<p align="center">
  <img src="./diagrams/system_architecture.png" style="width:100%;" alt="System Architecture">
</p>

- **Traffic distribution:** Nginx load balances external HTTPS requests across horizontally scaled Spring Boot app servers.
- **Cache and rate limiting:** App servers use cache-aside reads with Redis and enforce Token Bucket rate limiting at the edge.
- **Read/write separation:** App servers read from the PostgreSQL replica on cache misses and write domain data plus event publications to the primary.
- **Worker responsibilities:** Spring Modulith persists event publications and externalizes them to RabbitMQ, while workers write async results on the primary and read from the replica for batch validation workloads such as CSV ingestion.
- **Broker integration:** Spring Modulith externalizes published events to RabbitMQ, and workers consume tasks for AI summarization and notification delivery.

### Workshop Registration Flow

This sequence captures the end-to-end registration flow, including concurrency control, idempotent payment retries, and event publication-driven notifications.

```mermaid
sequenceDiagram
  actor Student
  participant Web as Web App
  participant GW as API Gateway
  participant Redis as Redis (Token Bucket)
  participant API as Backend API
  participant DB as PostgreSQL Primary
  participant Pay as Payment Gateway
  participant Sweeper as Payment Timeout Sweeper
  participant Registry as Spring Modulith Event Registry
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
    API->>DB: Lookup idempotency key
    alt Key exists
      API-->>Web: Return cached success response
    else New request
      API->>DB: Begin tx, lock workshop row
      API->>DB: Check capacity
      alt Workshop full
        API->>DB: Rollback
        API-->>Web: 409 Sold out
      else Seats available
        API->>DB: Decrement capacity
        API->>DB: Insert registration (PENDING)
        API->>DB: Commit
        alt Free workshop
          API->>DB: Begin tx
          API->>DB: Update registration (CONFIRMED) + event publication
          API->>DB: Commit
          API-->>Web: 201 + QR code
        else Paid workshop
          API->>Pay: Charge (idempotency key)
          alt Payment success
            Pay-->>API: Approved
            API->>DB: Begin tx
            API->>DB: Update registration (PAID) + event publication
            API->>DB: Commit
            API-->>Web: 201 + QR code
          else Timeout or failure
            Pay-->>API: Timeout/Error
            Note over API,Pay: Resilience4j circuit breaker trips
            API->>DB: Begin tx
            API->>DB: Update registration (FAILED)
            API->>DB: Increment capacity (compensating action)
            API->>DB: Commit
            API-->>Web: 502 or 202
          end
        end
      end
    end
  end

  par Event publication and notify
    Registry->>Broker: Externalize event
    Broker->>Notify: Deliver confirmation
  end

  Note over Sweeper,DB: Background sweep for expired PENDING > 15m
  Sweeper->>DB: Select PENDING registrations > 15m
  Sweeper->>DB: Mark FAILED + increment capacity (compensate)
```

- **Idempotency barrier:** The API checks the idempotency key before any reservation logic, returning a cached success response on retries to avoid double processing.
- **Short-lived pessimistic locking:** The capacity lock is held only inside the reservation transaction and released before any payment call, preventing long-held locks and connection pool exhaustion.
- **Compensating transactions:** Failed or timed-out payments transition the registration to `FAILED` and increment capacity, ensuring the final seat is not lost.
- **Circuit breaker protection:** Resilience4j guards the payment call path and surfaces controlled failures when the gateway degrades.
- **Event publication registry:** Confirmation notifications are persisted and externalized by Spring Modulith, decoupled from the synchronous user request.

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
  Mobile->>Local: Store check-in event (Pending)
  Note over Mobile,Local: Offline-first capture, no network required

  Mobile->>API: Sync batch when online
  loop For each record in Batch
    API->>DB: Validate and upsert attendance (idempotent key)
    alt Valid & New Attendance
      DB-->>API: Inserted
      API-->>Mobile: Ack (recorded)
      Mobile->>Local: Mark Synced
    else Duplicate (Idempotency)
      DB-->>API: Already recorded
      API-->>Mobile: Ack (existing)
      Mobile->>Local: Mark Synced
    else Invalid/Fake QR (Poison Pill Prevention)
      DB-->>API: Invalid workshop/student
      API-->>Mobile: Error (400/404)
      Mobile->>Local: Mark Failed/Rejected
    end
  end
```

- **Offline-first capture:** The mobile app records scans locally to keep check-in operational during network drops.
- **Batch synchronization:** Pending records are synced in batches when connectivity returns, reducing request overhead.
- **Idempotent conflict resolution:** Duplicate records return a success acknowledgment so the client can safely clear them, even if a prior response was lost.
- **Poison Pill prevention:** Invalid QR codes are deterministically rejected and marked failed locally, preventing infinite retry loops that waste bandwidth and battery.

### Async AI Summarization Flow

This sequence shows the asynchronous pipeline for PDF processing using the Spring Modulith event publication registry and brokered workers, including error handling when the AI service is unavailable.

```mermaid
sequenceDiagram
  actor Organizer
  participant Web as Web App
  participant API as Backend API
  participant DB as PostgreSQL Primary
  participant S3 as S3 Object Storage
  participant Registry as Spring Modulith Event Registry
  participant Broker as RabbitMQ
  participant Worker as AI Worker
  participant AI as AI Service
  participant DLQ as Dead Letter Queue

  Organizer->>Web: Upload brochure PDF
  Web->>API: POST /workshops/{id}/brochure
  API->>S3: Store PDF
  API->>DB: Create summary job + event publication
  API-->>Web: 202 Accepted

  Registry->>Broker: Externalize summarization event
  Broker->>Worker: Deliver job
  Worker->>S3: Fetch PDF
  Worker->>Worker: Extract text locally (PDFBox)
  Worker->>Worker: Clean and chunk text
  Worker->>AI: Summarize processed chunks
  alt Success
    AI-->>Worker: Summary
    Worker->>DB: Store summary result
  else Transient Error (Timeout/5xx)
    AI-->>Worker: Error/Timeout
    Worker-->>Broker: Retry with backoff
  else Max Retries Exceeded
    Worker-->>DLQ: Route failed job for review
  end
```

- **Decoupled processing:** The API persists the job and event publication entry, then returns 202 so heavy PDF processing runs asynchronously.
- **Local preprocessing:** The worker extracts, cleans, and chunks text before calling the AI service to avoid oversized payloads and token limit errors.
- **Fault tolerance:** Transient AI failures trigger backoff retries through the broker without blocking other jobs.
- **Dead Letter Queue (DLQ):** Jobs that exceed retry limits are routed to the DLQ for manual review, keeping the main queue healthy.

### Nightly Legacy CSV Ingestion Flow

This flowchart represents the nightly batch pipeline, showing fault-tolerant parsing, validation, and row-level quarantine without failing the entire job.

```mermaid
flowchart TD
  Scheduler[Scheduler] --> Job[Spring Batch Job]
  Legacy[Legacy Student System] --> S3[S3 CSV Drop]
  Job --> Fetch[Fetch CSV from S3 Drop]
  Fetch --> FileValidate{File-Level Validation}
  FileValidate -->|Invalid| Abort[Abort Job]
  Abort --> Alert[Publish Alert]
  FileValidate -->|Valid| Parse[Parse rows]

  Parse --> RowValidate{Validate row}
  RowValidate -->|Invalid| Quarantine[Quarantine row + error report]
  Quarantine --> Parse
  RowValidate -->|Valid| Dedup[Deduplicate]
  Dedup --> Chunk[Add to Chunk]
  Chunk -->|Chunk Full| BulkUpsert[Bulk Upsert to PostgreSQL]
  BulkUpsert --> Parse

  Parse -->|All rows processed| Archive[Archive file in S3]
  Archive --> Complete[Job complete with metrics]
  Complete --> Publish[Publish Job Summary to RabbitMQ]
  Publish --> Notify[Notify Admins]
```

- **File-level guardrails:** Header and size validation prevents corrupted or malformed uploads from crashing the batch job.
- **Chunk-oriented processing:** Valid rows are buffered and upserted in bulk to maximize throughput and avoid connection exhaustion.
- **Fault tolerance & quarantining:** Invalid rows are isolated into a quarantine report while the job continues.
- **Archiving strategy:** Processed files are moved to an archive folder to prevent accidental reprocessing.
- **Proactive alerting:** Job summaries (success and quarantined counts) are published to RabbitMQ to notify administrators.

### Workshop Lifecycle Management Flow

This sequence captures the admin-driven workshop lifecycle and the event-driven propagation of changes to downstream services.

```mermaid
sequenceDiagram
  actor Organizer
  participant Web as Admin Web App
  participant API as Backend API
  participant DB as PostgreSQL Primary
  participant Registry as Spring Modulith Event Registry
  participant Broker as RabbitMQ
  participant NotifWorker as Notification Worker
  participant Notify as Notification Providers

  Organizer->>Web: Create/Update/Reschedule/Cancel workshop
  Web->>API: POST/PUT/PATCH /workshops
  API->>DB: Begin tx
  API->>DB: Validate and persist lifecycle change
  alt Create
    API->>DB: Insert workshop record
    API->>DB: Insert event publication (WorkshopCreated)
  else Update
    API->>DB: Update workshop details
    API->>DB: Insert event publication (WorkshopUpdated)
  else Reschedule
    API->>DB: Update schedule/venue
    API->>DB: Insert event publication (WorkshopRescheduled)
  else Cancel
    API->>DB: Mark workshop as canceled
    API->>DB: Insert event publication (WorkshopCanceled)
  end
  API->>DB: Commit
  API-->>Web: 200/201 Response

  par Event publication fan-out
    Registry->>Broker: Externalize lifecycle event
    Broker->>NotifWorker: Consume lifecycle event
    NotifWorker->>DB: Fetch registered students (if Rescheduled/Canceled)
    NotifWorker->>Notify: Send bulk notifications (Email/SMS)
  end
```

- **Lifecycle coverage:** Admins can create, update, reschedule, or cancel workshops through the web portal.
- **Transactional event publication:** The lifecycle change and event publication are committed in the same transaction to guarantee consistent state and downstream notification delivery.
- **Asynchronous fan-out:** The notification worker consumes lifecycle events, fetches impacted students, and sends bulk emails without blocking the admin UI.

### Workshop Search & Cache-Aside Flow

```mermaid
sequenceDiagram
  actor Client
  participant API as Backend API
  participant Cache as Redis Cache
  participant Replica as PostgreSQL Replica

  Client->>API: GET /workshops/search?q=keyword
  API->>Cache: GET search:{keyword}
  alt Cache hit
    Cache-->>API: Cached results
    API-->>Client: 200 Results
  else Cache miss
    Cache-->>API: Miss
    API->>Replica: Native FTS query (to_tsvector)
    Replica-->>API: Result set
    API->>Cache: SET search:{keyword} (short TTL)
    API-->>Client: 200 Results
  end
```

## 4. Database Design

### Database Technology Stack

- **Core Data Stores (Server-Side):**
  - **PostgreSQL (Primary System of Record):** Provides strict ACID guarantees, pessimistic row-level locking for the 60-seat race condition, and durable storage for Idempotency and Spring Modulith Event Publication records.
  - **Redis (Cache & Rate Limiter):** High-throughput, low-latency storage for Token Bucket rate limiting and cache-aside workshop availability reads during the 12,000-user surge.
  - **S3-Compatible Object Storage (Blob Store):** Cost-effective, scalable persistence for unstructured data, specifically used for storing Organizer-uploaded PDF brochures and nightly CSV drops from the legacy system.
- **Mobile Data Store (Client-Side):**
  - **SQLite via Room:** Local cache for workshop details and an event queue (`PendingCheckIn`) to support offline-first check-in.
- **Database Tooling:**
  - **Flyway (Migration Tool):** Migration-first schema management with deterministic, version-controlled deployments; Hibernate `ddl-auto=update` is explicitly disabled.
  - **HikariCP (Connection Pool):** Efficient JDBC connection management to prevent connection pool exhaustion under burst traffic.

### Entity-Relationship Diagram (ERD)

```mermaid
erDiagram
  USERS {
    uuid id PK
    string email
    string full_name
    string role
    string status
    uuid organization_id FK
    boolean is_temporary_password
    datetime created_at
  }

  ORGANIZATIONS {
    uuid id PK
    string name
    uuid organizer_id FK
  }

  WORKSHOPS {
    uuid id PK
    string title
    string description
    string status
    int version
    int capacity_total
    int capacity_remaining
    datetime start_time
    datetime end_time
    uuid organizer_id FK
  }

  WORKSHOP_SPEAKERS {
    uuid id PK
    uuid workshop_id FK
    string speaker_name
    string speaker_bio
  }

  WORKSHOP_ASSIGNMENTS {
    uuid id PK
    uuid workshop_id FK
    uuid organization_id FK
  }

  REGISTRATIONS {
    uuid id PK
    uuid user_id FK
    uuid workshop_id FK
    string idempotency_key
    string status
    string ticket_type
    string qr_code
    datetime created_at
  }

  PAYMENTS {
    uuid id PK
    uuid registration_id FK
    string provider
    string provider_ref
    string status
    decimal amount
    datetime created_at
  }

  ATTENDANCE {
    uuid id PK
    uuid registration_id FK
    uuid workshop_id FK
    datetime checked_in_at
    string source
  }

  BROCHURES {
    uuid id PK
    uuid workshop_id FK
    string storage_key
    string status
    datetime uploaded_at
  }

  AI_SUMMARIES {
    uuid id PK
    uuid workshop_id FK
    text summary_text
    string status
    datetime updated_at
  }

  EVENT_PUBLICATION {
    uuid id PK
    datetime completion_date
    int completion_attempts
    string event_type
    string listener_id
    datetime publication_date
    datetime last_resubmission_date
    string serialized_event
    string status
  }

  IDEMPOTENCY_KEYS {
    uuid id PK
    string idempotency_key
    string request_hash
    string status
    datetime expires_at
  }

  STUDENT_IMPORT_BATCHES {
    uuid id PK
    string source_file
    string status
    int total_rows
    int quarantined_rows
    datetime created_at
  }

  STUDENT_IMPORT_ERRORS {
    uuid id PK
    uuid batch_id FK
    int row_number
    string error_reason
    string raw_row
  }

  USERS ||--o{ REGISTRATIONS : registers
  USERS ||--o{ WORKSHOPS : organizes
  USERS ||--o{ ORGANIZATIONS : manages
  ORGANIZATIONS ||--o{ USERS : "contains staff"
  WORKSHOPS ||--o{ REGISTRATIONS : has
  WORKSHOPS ||--o{ WORKSHOP_SPEAKERS : includes
  WORKSHOPS ||--o{ WORKSHOP_ASSIGNMENTS : "has assignments"
  ORGANIZATIONS ||--o{ WORKSHOP_ASSIGNMENTS : "is assigned to"
  WORKSHOPS ||--o{ BROCHURES : has
  WORKSHOPS ||--o{ AI_SUMMARIES : summarizes
  REGISTRATIONS ||--|| ATTENDANCE : results_in
  REGISTRATIONS ||--o| PAYMENTS : paid_by
  STUDENT_IMPORT_BATCHES ||--o{ STUDENT_IMPORT_ERRORS : contains
```

The schema separates core domain tables (users, workshops, registrations, payments, attendance) from technical pattern tables (event publications and idempotency keys) to keep business logic clean while enabling resilience patterns. Workshop content artifacts (brochures and AI summaries) are modeled as first-class entities for lifecycle tracking, while import batches and errors capture CSV ingestion outcomes without polluting the core domain. This separation supports high-concurrency workflows, reliable async processing, and offline check-in reconciliation without coupling operational metadata directly to transactional records.

### Core Schema & Data Dictionary

**USERS**

| Field Name              | Data Type (PostgreSQL) | Business/Technical Purpose                                      |
| :---------------------- | :--------------------- | :-------------------------------------------------------------- |
| `organization_id`       | UUID                   | Links staff users to their organization for check-in grouping.  |
| `is_temporary_password` | BOOLEAN                | Forces password change on first login for provisioned accounts. |

**ORGANIZATIONS**

| Field Name     | Data Type (PostgreSQL) | Business/Technical Purpose                      |
| :------------- | :--------------------- | :---------------------------------------------- |
| `name`         | TEXT                   | Human-readable organization or department name. |
| `organizer_id` | UUID                   | Organizer that manages the organization.        |

**WORKSHOP_ASSIGNMENTS**

| Field Name        | Data Type (PostgreSQL) | Business/Technical Purpose                        |
| :---------------- | :--------------------- | :------------------------------------------------ |
| `workshop_id`     | UUID                   | Workshop being assigned to a staff organization.  |
| `organization_id` | UUID                   | Organization responsible for check-in operations. |

**WORKSHOPS**

| Field Name           | Data Type (PostgreSQL) | Business/Technical Purpose                                               |
| :------------------- | :--------------------- | :----------------------------------------------------------------------- |
| `capacity_total`     | INTEGER                | Fixed seat capacity used for demand planning and validations.            |
| `capacity_remaining` | INTEGER                | Live seat counter updated under pessimistic lock to prevent overbooking. |
| `version`            | INTEGER                | Optimistic locking for non-capacity edits (title, schedule).             |
| `status`             | TEXT                   | Lifecycle state: `DRAFT`, `PUBLISHED`, `CANCELED`, `COMPLETED`.          |

**REGISTRATIONS**

| Field Name        | Data Type (PostgreSQL) | Business/Technical Purpose                                                                     |
| :---------------- | :--------------------- | :--------------------------------------------------------------------------------------------- |
| `idempotency_key` | TEXT                   | Correlates retries to the same request for safe deduplication.                                 |
| `status`          | TEXT                   | `PENDING`, `CONFIRMED`, `PAID`, `FAILED`, `CANCELED`; `FAILED` triggers capacity compensation. |

**PAYMENTS**

| Field Name | Data Type (PostgreSQL) | Business/Technical Purpose                                     |
| :--------- | :--------------------- | :------------------------------------------------------------- |
| `status`   | TEXT                   | Payment lifecycle: `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`. |

**EVENT_PUBLICATION**

| Field Name               | Data Type (PostgreSQL) | Business/Technical Purpose                                                       |
| :----------------------- | :--------------------- | :------------------------------------------------------------------------------- |
| `event_type`             | TEXT                   | Published event type recorded by Spring Modulith.                                |
| `listener_id`            | TEXT                   | Target listener identifier for publication routing.                              |
| `publication_date`       | TIMESTAMP              | When the event was registered for publication.                                   |
| `completion_date`        | TIMESTAMP              | When the publication completed for the listener (null if pending).               |
| `completion_attempts`    | INTEGER                | Tracks the number of retry attempts for the event publication.                   |
| `last_resubmission_date` | TIMESTAMP              | Timestamp of the last attempt to resubmit the event for publication.             |
| `serialized_event`       | TEXT                   | Serialized event payload persisted by the Event Publication registry.            |
| `status`                 | TEXT                   | Lifecycle status of the event publication (e.g., INCOMPLETE, COMPLETED, FAILED). |

**IDEMPOTENCY_KEYS**

| Field Name     | Data Type (PostgreSQL) | Business/Technical Purpose                                |
| :------------- | :--------------------- | :-------------------------------------------------------- |
| `request_hash` | TEXT                   | Unique fingerprint of the request payload for validation. |
| `expires_at`   | TIMESTAMP              | TTL cutoff for cleanup to prevent table bloat.            |

### Concurrency & Integrity Constraints

- **Capacity Protection (Race Condition Prevention):** Enforce `CHECK (capacity_remaining >= 0)` on `WORKSHOPS` and use `SELECT ... FOR UPDATE` to lock the workshop row during seat allocation to guarantee no overbooking.
- **Idempotency Barrier (Double-Charge Prevention):** Apply `UNIQUE (idempotency_key)` on both `IDEMPOTENCY_KEYS.idempotency_key` and `REGISTRATIONS.idempotency_key` to prevent duplicate processing on client retries.
- **Offline Sync Deduplication:** Add `UNIQUE (registration_id)` (or `UNIQUE (workshop_id, user_id)`) on `ATTENDANCE` to silently discard duplicate sync attempts from offline clients.
- **Data Consistency:** Enforce `FOREIGN KEY ... ON DELETE RESTRICT` to prevent deleting a workshop that has existing registrations.

### Indexing Strategy

| Table               | Indexed Columns        | Index Type                                       | Business/Performance Rationale                                 |
| :------------------ | :--------------------- | :----------------------------------------------- | :------------------------------------------------------------- |
| `IDEMPOTENCY_KEYS`  | `idempotency_key`      | B-Tree                                           | Fast lookup during registration surges to deduplicate retries. |
| `EVENT_PUBLICATION` | `completion_date`      | Partial B-Tree (`WHERE completion_date IS NULL`) | Speeds pending publication scans.                              |
| `WORKSHOPS`         | `status`, `start_time` | Composite B-Tree                                 | Accelerates schedule browsing and filtering.                   |
| `REGISTRATIONS`     | `user_id`              | B-Tree                                           | Efficient per-user registration history lookups.               |
| `REGISTRATIONS`     | `workshop_id`          | B-Tree                                           | Efficient workshop roster and capacity checks.                 |

### Mobile Local Database (Room SQLite Schema)

The mobile database is intentionally minimal and is not a replica of the server. It stores a read-only workshop cache for staff selection and a write-optimized event queue for offline check-ins.

```mermaid
erDiagram
  LOCAL_WORKSHOPS {
    string id PK
    string title
    datetime start_time
    string capacity_info
  }

  LOCAL_ATTENDEES {
    string registration_id PK
    string workshop_id
    string full_name
    string ticket_status
  }

  PENDING_CHECKINS {
    string id PK
    string workshop_id
    string qr_code_payload
    datetime scanned_at
    string sync_status
  }
```

- **LocalWorkshop (Read-Only Cache):** Minimal fields required for staff to select a workshop; refreshed when online.
- **LocalAttendee (Read-Only Cache):** Read-only cache for the attendee roster, enabling offline search and verification without network.
- **PendingCheckIn (Event Queue):** Offline scans with `sync_status` values `PENDING`, `SYNCED`, `FAILED`, or `REJECTED` to drive deterministic reconciliation.

## 5. Access Control Design

### RBAC Model & User Personas

We use **Role-Based Access Control (RBAC)** with role claims enforced at the API and UI layers.

| Role        | Access Rights                                                                                      |
| :---------- | :------------------------------------------------------------------------------------------------- |
| `STUDENT`   | View published workshops, register for workshops, view their own QR codes.                         |
| `ORGANIZER` | Create/update/cancel workshops, upload PDF brochures, view attendee lists for their own workshops. |
| `STAFF`     | View workshop details assigned to them and submit offline check-in sync batches.                   |

Database roles are stored as plain domain values; the authentication layer prepends `ROLE_` when issuing JWTs and constructing Spring Security authorities.

### Stateless JWT & Role Claims

The API Gateway and Backend Services do not query PostgreSQL for permissions on every request. They trust a cryptographically signed JWT and perform `O(1)` authorization checks using the embedded role claim.

**Example decoded JWT payload:**

```json
{
  "sub": "1b8f8c7b-2e4b-4f6c-9b3b-6b8d2f0c9a12",
  "role": "ROLE_ORGANIZER",
  "iat": 1767225600,
  "exp": 1767229200,
  "jti": "9c1f6c23-0f5d-4f8b-9f51-2d5f3a2f1d9e"
}
```

- **Client usage (React/Android):** The client decodes the `role` claim to drive the UI, such as hiding the "Create Workshop" action for `ROLE_STUDENT`.
- **Server usage (Spring Boot):** The API enforces access via the `role` claim (for example, `@PreAuthorize("hasRole('ORGANIZER')")`) and uses `sub` for row-level ownership checks.

### Multi-Layer Enforcement Points

- **Layer 1: UI/Client Layer (React & Mobile):** The client decodes the JWT to gate UI elements (hide menus, disable routes) for user experience. This improves UX but is not a security control.
- **Layer 2: API Gateway & Controller Layer (Endpoint Security):** Requests are intercepted, JWT signatures are verified, and endpoint access is enforced by role claims (for example, `@PreAuthorize`). This blocks a Student from calling Organizer endpoints directly.
- **Layer 3: Business/Data Layer (Data Ownership):** Services enforce ownership checks beyond role validation. For example, `PUT /workshops/{id}` verifies `ROLE_ORGANIZER` and confirms `workshops.organizer_id == jwt.sub` to prevent cross-tenant tampering.

### Authorization Sequence Diagram

This diagram illustrates the defense-in-depth strategy using an Organizer attempting to edit a workshop, showing how each layer prevents unauthorized access.

```mermaid
sequenceDiagram
  actor Organizer
  participant Web as Web App
  participant API as API Gateway / Spring Security
  participant Service as Workshop Service
  participant DB as PostgreSQL

  Organizer->>Web: Click "Edit Workshop"
  alt Role not ORGANIZER (Layer 1)
    Web-->>Organizer: Block action (UI error)
  else Role ORGANIZER
    Web->>API: PUT /workshops/{id} (JWT)
    alt JWT invalid or role not allowed (Layer 2)
      API-->>Web: 403 Forbidden
    else JWT valid and role allowed
      API->>Service: Forward request
      Service->>DB: Load workshop by id
      alt organizer_id != jwt.sub (Layer 3)
        Service-->>API: 403 Forbidden
        API-->>Web: 403 Forbidden
      else organizer_id == jwt.sub
        Service->>DB: Update workshop
        Service-->>API: 200 OK
        API-->>Web: 200 OK
      end
    end
  end
```

- **Layer 1 (UX/UI Gating):** Provides immediate feedback and hides restricted features from unauthorized users, but is cosmetic and can be bypassed.
- **Layer 2 (Endpoint Gating):** The API gateway and Spring Security boundary require a `ROLE_ORGANIZER` claim for `PUT /workshops/{id}`.
- **Layer 3 (Data Gating):** The service enforces ownership by matching `workshops.organizer_id` with `jwt.sub`, preventing IDOR attacks.

## 6. System Protection Mechanisms

### Traffic Spike Management

Registration demand peaks at 12,000 concurrent users within the first 10 minutes, with 60% concentrated in the initial 3-minute window. To keep the backend stable under that burst, the API Gateway enforces a distributed **Token Bucket** rate limiter using **Bucket4j** with **Redis** as the shared state store.

Each user or IP is assigned a bucket with a fixed capacity of tokens. Every `POST /registrations` request consumes a token, and tokens refill at a steady rate. This allows short bursts during the first 3 minutes while still capping sustained throughput, preventing the registration service and database from being overwhelmed.

When a bucket is empty, the gateway rejects the request with HTTP `429 Too Many Requests`. The frontend shows a simple "Please wait" message and retries with backoff, protecting the system while keeping the user informed.

```mermaid
flowchart TD
  Client[Client] --> Gateway[API Gateway: POST /registrations]
  Gateway --> Redis[Redis: Check Token Bucket]
  Redis --> Decision{Tokens available?}
  Decision -->|Yes| Consume[Consume token]
  Consume --> API[Backend API]
  API --> DB[Process DB transaction]
  DB --> Success[Return success]
  Decision -->|No| Drop[Drop request]
  Drop --> Throttle[Return 429 Too Many Requests]
```

### Third-Party Service Resilience

External payment gateways are inherently unreliable due to network latency, timeouts, and partial outages. If the backend waits on slow payment calls, request threads queue up and can exhaust the Spring Boot thread pool, causing a cascading failure across the UniHub system.

To prevent this, the payment integration uses a Circuit Breaker implemented with Resilience4j. The breaker continuously tracks failure rate and slow-call rate; when thresholds are exceeded, it opens and blocks further payment calls.

When the circuit is open, the system fails fast instead of hanging. It returns an immediate error or places the registration in a "Payment Pending" state so users can retry later, while free workshop registration and browsing remain fully operational.

```mermaid
stateDiagram-v2
  [*] --> CLOSED
  CLOSED --> OPEN: Failure/timeout threshold exceeded
  OPEN --> HALF_OPEN: Wait duration elapsed
  HALF_OPEN --> CLOSED: Test calls succeed
  HALF_OPEN --> OPEN: Test calls fail
```

### Transaction Idempotency

Client retries caused by network drops or impatient clicks can create duplicate `POST /registrations` requests, risking double-booking or double-charging. The system enforces exactly-once processing with an idempotency key strategy backed by PostgreSQL.

- **Key generation and transmission:** The React/Android client generates a UUIDv4 and sends it in the `Idempotency-Key` header.
- **Storage and duplicate detection:** The backend stores keys in `IDEMPOTENCY_KEYS` with a `UNIQUE` constraint, creating a database-level barrier against race conditions.
- **Workflow:** On the first attempt, the key is inserted, the payment is processed, and the final response payload is serialized and stored with the key. On a retry, the API finds the existing key and immediately returns the cached response, bypassing the payment gateway and write logic.
- **TTL configuration:** Each key includes an `expires_at` timestamp (for example, 24 hours), and a scheduled cleanup job deletes expired records to prevent table bloat.

```mermaid
sequenceDiagram
  actor Client
  participant GW as API Gateway
  participant DB as PostgreSQL
  participant Pay as Payment Gateway

  Client->>GW: POST /registrations (Idempotency-Key: A)
  GW->>DB: Lookup key A
  alt Initial Request
    DB-->>GW: Not found
    GW->>DB: Insert key A (lock)
    GW->>Pay: Charge payment
    Pay-->>GW: Approved
    GW->>DB: Store final response for key A
    GW-->>Client: 200 OK (success)
  else Retry Request (Network drop)
    DB-->>GW: Found key A
    GW->>DB: Load cached response
    GW-->>Client: 200 OK (cached)
  end
```

## 7. Architectural Decision Records

### Domain 1: Foundation

#### ADR 001: Backend Language and Framework

- **Context:** The backend must handle 12,000 concurrent users, enforce strict transactional integrity for seat allocation, and be delivered under rapid time-to-market requirements with optimized operational resources.
- **Decision:** Use Java with Spring Boot in a modular monolith architecture.
- **Rationale:** Java and Spring Boot provide mature concurrency support, strong ecosystem libraries for resilience patterns, and straightforward transaction management with PostgreSQL. Compared to Node.js, Java offers more predictable multi-threaded performance under sustained load and a larger set of battle-tested tooling for transactional workloads. Compared to Go, Spring Boot accelerates delivery due to richer scaffolding and familiarity while still meeting throughput requirements.
- **Consequences:** Requires JVM tuning and disciplined module boundaries to avoid monolith sprawl, but reduces operational complexity and time-to-delivery.

#### ADR 002: Web Frontend Framework

- **Context:** The web portal serves two roles (Students and Organizers) with distinct workflows and RBAC controls.
- **Decision:** Use React with TypeScript for the web application.
- **Rationale:** React provides strong component reuse for role-specific views and a broad ecosystem for routing and state management. TypeScript adds compile-time type safety and stronger IDE support, reducing runtime errors when handling complex API payloads and RBAC-driven UI states. Compared to plain React with JavaScript, TypeScript improves refactoring safety and long-term maintainability. Compared to Angular, React + TypeScript avoids the heavier framework surface while still delivering strong typing. Compared to Vue, it offers deeper enterprise tooling maturity and larger hiring pools.
- **Consequences:** Requires disciplined state management and UI architecture to avoid complexity in multi-role experiences.

#### ADR 003: Mobile App Technology

- **Context:** Check-in staff need fast camera scanning and reliable offline-first persistence under poor connectivity.
- **Decision:** Build the mobile app as Android Native using Java and XML.
- **Rationale:** Native Android provides direct access to camera APIs and fine-grained control of scanning performance. Compared to Flutter or React Native, native Java/XML avoids bridging overhead and offers deeper integration with offline storage and background sync constraints. This is essential for rapid QR scanning and deterministic offline behavior.
- **Consequences:** Limits cross-platform reuse, but delivers the highest reliability for the dedicated Android staff workflow.

#### ADR 004: API Design Style

- **Context:** Two clients (web and mobile) must integrate quickly and reliably under accelerated delivery requirements.
- **Decision:** Use RESTful APIs for core services.
- **Rationale:** REST offers clear resource modeling, easier caching, and simpler client integration. Compared to GraphQL, REST avoids additional schema management and resolver performance pitfalls, reducing delivery risk through streamlined development and operations. GraphQL can be introduced later if client flexibility becomes a stronger need.
- **Consequences:** May require multiple endpoints for complex UI screens, but simplifies initial delivery and observability.

### Domain 2: Security & Identity

#### ADR 005: Authentication Mechanism

- **Context:** The system must scale under traffic spikes and support multiple client platforms.
- **Decision:** Use stateless JWT-based authentication with role claims.
- **Rationale:** JWTs reduce server-side session storage and scale horizontally behind a load balancer. Compared to stateful sessions, JWTs avoid shared session stores and simplify API scaling during peak load. RBAC claims embedded in tokens support quick authorization decisions.
- **Consequences:** Requires token rotation and revocation strategy, and careful token lifetime management for security.

#### ADR 006: Authorization Model

- **Context:** The system must enforce clear permissions for three fixed personas across web, admin, and check-in workflows.
- **Decision:** Use Role-Based Access Control (RBAC) with role claims in stateless JWTs and row-level ownership checks in the API.
- **Rationale:** RBAC matches the fixed personas and enables O(1) permission checks during the 12,000-user surge. Compared to ABAC, RBAC avoids complex policy evaluation overhead while still supporting row-level ownership constraints for organizer-scoped data.
- **Consequences:** Less flexible than ABAC for fine-grained policy growth and requires careful role governance as permissions expand.

### Domain 3: Data Infrastructure

#### ADR 007: Primary Database

- **Context:** Seat allocation and payment confirmation must be ACID-compliant to prevent overbooking and double-charging.
- **Decision:** Use PostgreSQL hosted on Neon (Serverless Postgres) as the primary transactional database.
- **Rationale:** PostgreSQL provides strong ACID guarantees, row-level locking, and mature indexing, which are critical for the 60-seat race condition. Neon preserves these guarantees while eliminating local database setup friction and enabling a shared, always-available development database so team members can collaborate on a consistent state without manual synchronization. Compared to MongoDB, PostgreSQL offers more reliable transactional semantics and consistent constraints for concurrent seat allocation. This aligns with the requirement to prevent the final seat from being claimed by multiple users.
- **Consequences:** The team depends on managed Neon availability and must manage credentials and network access, but collaboration improves through a shared environment and reduced local setup burden. Schema evolution still requires migrations and careful modeling to preserve consistency under load.

#### ADR 008: Database Migration Tool

- **Context:** Relying on Hibernate's `ddl-auto=update` is dangerous for production environments, especially under high concurrency, as it can lock tables or drop columns unexpectedly. We need a deterministic way to manage database schema changes (Migration-First approach).
- **Decision:** Use Flyway for database migrations.
- **Rationale:** Flyway uses plain SQL scripts (`.sql`), which are transparent, easier to review, and universally understood by DBAs and developers. Compared to Liquibase, which uses abstracted XML/YAML/JSON formats, Flyway aligns better with our desire for clear, readable database initialization scripts.
- **Consequences:** Developers must write explicit SQL migration scripts (e.g., `V1__init_db.sql`). Hibernate ORM is restricted to `ddl-auto=validate` strictly for mapping data (DML), not modifying schemas (DDL).

#### ADR 009: Database Connection Pooling Strategy

- **Context:** High concurrency requires efficient database connection management to prevent saturation.
- **Decision:** Use HikariCP for JDBC connection pooling.
- **Rationale:** HikariCP offers low-latency pooling and strong observability. Compared to default or older pools, it provides better performance under bursty load, aligning with the 12,000-user surge window.
- **Consequences:** Requires careful tuning of pool sizes to avoid exhausting PostgreSQL resources.

#### ADR 010: Caching and Rate Limiting Storage

- **Context:** The system must absorb a 12,000-user traffic spike and enforce fair rate limiting.
- **Decision:** Use Redis for caching and rate-limiting counters.
- **Rationale:** Redis provides atomic operations, expiring keys, and high throughput for token bucket counters. Compared to Memcached, Redis offers richer data structures and persistence options that simplify rate limiting and short-lived availability caching.
- **Consequences:** Requires memory sizing and eviction policy tuning, but offers reliable performance under peak load.

#### ADR 011: Object Storage

- **Context:** The system must store PDF brochures and nightly CSV drops reliably and cheaply.
- **Decision:** Use S3-compatible object storage.
- **Rationale:** Object storage is cost-effective for large immutable files and integrates well with background processing. Compared to storing files in PostgreSQL, object storage avoids database bloat and improves IO performance for large files.
- **Consequences:** Requires access controls and lifecycle policies, but improves scalability and operational clarity.

#### ADR 012: Native Full-Text Search vs. External Search Engine

- **Context:** Workshop search is limited to titles and descriptions and must stay fast under read-heavy traffic while keeping operational overhead and memory footprint low.
- **Decision:** Use PostgreSQL native full-text search with `to_tsvector` and GIN indexes instead of Elasticsearch.
- **Rationale:** Native FTS avoids dual-write complexity, reduces operational cost, and keeps search results consistent with transactional data. It meets current scope for keyword search on workshop titles and descriptions while leveraging the existing primary-replica topology for read scaling.
- **Consequences:** Advanced relevance tuning and typo tolerance are more limited than Elasticsearch; if search scope expands significantly, we may revisit an external search service.

### Domain 4: Data Integrity & Concurrency

#### ADR 013: Database Locking Strategy

- **Context:** High-demand workshops can receive hundreds of simultaneous registration attempts for the final seats, and overbooking must be prevented.
- **Decision:** Use pessimistic row-level locking for seat allocation (for example, `SELECT ... FOR UPDATE` on the workshop capacity row).
- **Rationale:** Pessimistic locking serializes competing transactions for the same workshop, guaranteeing only one transaction can claim the final seat. Compared to optimistic locking with version checks, pessimistic locking avoids excessive retries during the 12,000-user spike where contention is expected to be high.
- **Consequences:** Increases lock contention under heavy load and requires careful transaction scoping to keep locks short-lived.

#### ADR 014: Idempotency Mechanism

- **Context:** Payment retries and gateway timeouts can cause duplicate charges without strict idempotency control.
- **Decision:** Use idempotency keys stored in PostgreSQL with a unique constraint and a TTL window (for example, 24 hours).
- **Rationale:** Persisting idempotency records in PostgreSQL ensures durability across restarts and aligns with transactional workflows. Compared to Redis-only storage, PostgreSQL avoids data loss that could lead to double-charging. Keys are hashed with request fingerprints and stored alongside status to safely replay responses.
- **Consequences:** Adds storage overhead and requires cleanup of expired keys, but guarantees exactly-once payment execution.

#### ADR 015: Database Replication Strategy

- **Context:** Read-heavy browse traffic during the surge can overload the primary database if reads and writes are co-located.
- **Decision:** Use a primary-replica PostgreSQL topology with read/write separation.
- **Rationale:** Read replicas offload workshop browsing and analytics queries from the primary, preserving write performance for registration and payments. Compared to a single primary, this reduces contention during traffic spikes. Replication lag is acceptable for browse views, while transactional writes remain on the primary.
- **Consequences:** Introduces eventual consistency on read replicas and requires routing logic to avoid stale reads for critical operations.

#### ADR 016: Caching Strategy Pattern

- **Context:** Workshop browsing and availability checks are read-heavy, especially during the initial surge.
- **Decision:** Use the cache-aside pattern with Redis for workshop listings and non-critical availability reads.
- **Rationale:** Cache-aside allows the backend to control freshness and avoids stale writes. Compared to write-through caching, cache-aside reduces write amplification and keeps the critical write path simple. Stale reads are mitigated with short TTLs and explicit invalidation after capacity updates.
- **Consequences:** Requires cache invalidation logic and accepts brief inconsistencies for non-critical reads.

### Domain 5: Async & Event-Driven

#### ADR 017: Message Broker

- **Context:** Notifications, AI summarization, and CSV ingestion must run asynchronously without blocking registration flows.
- **Decision:** Use RabbitMQ as the message broker.
- **Rationale:** RabbitMQ provides straightforward queue semantics, reliable delivery, and reduced operational overhead. Compared to Kafka, RabbitMQ has lower operational overhead and fits the task-queue model of notifications and ingestion. Kafka is more suitable for high-throughput event streaming, which is not the primary requirement here.
- **Consequences:** Lower throughput than Kafka for stream-heavy workloads, but adequate for async tasks and easier to operate.

#### ADR 018: Spring Modulith Event Publication Registry

- **Context:** Async processing must remain consistent with transactional writes to prevent lost events.
- **Decision:** Use Spring Modulith's Event Publication registry for transactional event persistence and externalization.
- **Rationale:** The registry ensures events are stored with the same transaction as domain changes and then automatically externalized to RabbitMQ. Compared to direct broker writes from request threads, it avoids inconsistencies between database commit and broker publish while reducing custom relay code.
- **Consequences:** Requires monitoring publication lag within the registry, but eliminates a bespoke relay service.

### Domain 6: Resilience & Traffic Management

#### ADR 019: Rate Limiting Algorithm

- **Context:** Registration can spike to 12,000 concurrent users within minutes, and the backend must ensure fair access while preventing overload.
- **Decision:** Use the Token Bucket algorithm implemented via the Bucket4j library in the Spring Boot backend, with distributed state stored in Redis.
- **Rationale:** Token Bucket smooths bursts while allowing controlled short-term spikes, which aligns with the initial surge profile. Compared to Fixed Window, it avoids boundary spikes, and compared to Sliding Window, it is simpler and cheaper to compute at high scale while still providing fairness. Using Bucket4j provides production-ready, thread-safe integration with Redis without writing custom Lua scripts.
- **Consequences:** Requires careful tuning of refill rates and burst sizes; if misconfigured, legitimate users may see throttling during peak demand.

#### ADR 020: Circuit Breaker Implementation

- **Context:** Payment gateways can fail or time out; the system must protect core functionality and prevent cascading failures during outages.
- **Decision:** Use Resilience4j for circuit breakers in the Java/Spring Boot backend.
- **Rationale:** Resilience4j is actively maintained, Spring Boot friendly, and supports Closed/Open/Half-Open states with metrics and fallback patterns. Compared to Netflix Hystrix (now in maintenance mode), Resilience4j offers lower overhead and modern integration without legacy constraints.
- **Consequences:** Adds dependency configuration and requires explicit fallback design; incorrect thresholds can cause premature open states or delayed recovery.

#### ADR 021: Stripe Integration & Chaos Engineering Strategy

- **Context:** Payment flows must be resilient under gateway failure scenarios, and the system needs a deterministic way to validate Resilience4j circuit breakers and capacity compensation logic during testing.
- **Decision:** Adopt Stripe (Sandbox) as the primary payment gateway and implement a ChaosMockAdapter that injects controlled failures, latency, and timeouts for resilience testing.
- **Rationale:** Stripe provides stable sandbox tooling and idempotency support, while the ChaosMockAdapter enables repeatable failure modes without relying on external outages. This allows targeted validation of circuit breakers, retries, and compensating transactions.
- **Consequences:** Requires adapter selection logic and test environment discipline; production must lock the adapter to Stripe to avoid accidental chaos injection.

### Domain 7: Specific Workflows

#### ADR 022: Mobile Local Database

- **Context:** Offline-first check-in requires durable local storage and deterministic sync.
- **Decision:** Use SQLite via Room persistence library.
- **Rationale:** Room offers compile-time schema validation and structured queries over SQLite, which is robust for offline records. Compared to Realm or custom file storage, Room provides predictable query behavior and easier conflict handling, aligning with the need for reliable reconciliation.
- **Consequences:** Requires schema migrations on mobile, but improves data integrity and maintainability.

#### ADR 023: Offline Sync Conflict Resolution

- **Context:** Offline check-in must preserve attendance records and reconcile safely when connectivity returns.
- **Decision:** Use server-authoritative reconciliation with idempotent check-in events keyed by workshop and student identifiers.
- **Rationale:** Server-authoritative reconciliation avoids conflicting outcomes when multiple offline devices sync the same attendee. Compared to client-wins merging, idempotent server-side upserts ensure no duplicate attendance is recorded and preserve auditability.
- **Consequences:** Requires careful event modeling and unique constraints, but guarantees consistent attendance records after offline periods.

#### ADR 024: Batch Processing Strategy

- **Context:** Nightly CSV ingestion must tolerate malformed rows and duplicates without failing the entire pipeline.
- **Decision:** Use Spring Batch with chunk processing, skip policies, and error quarantining.
- **Rationale:** Spring Batch provides structured retries, per-row validation, and fault-tolerant processing. Compared to ad-hoc scripts, it prevents a single bad row from crashing the job and enables reporting on skipped records for audit.
- **Consequences:** Adds configuration complexity and requires job monitoring, but delivers robust ingestion for the legacy system.

#### ADR 025: AI Summarization Integration

- **Context:** Workshop summaries must be generated from PDFs without blocking admin workflows.
- **Decision:** Use an async worker pipeline that extracts text, cleans it, and calls the AI service.
- **Rationale:** An async pipeline isolates AI latency and external failures from the user experience. Compared to synchronous in-request processing, it prevents timeouts and improves reliability under traffic spikes.
- **Consequences:** Introduces eventual consistency for summaries and requires status tracking for processing jobs.

#### ADR 026: Pluggable Notification Architecture

- **Context:** The system must support immediate notifications and allow future channels (for example, Telegram) without core changes.
- **Decision:** Use a provider abstraction with strategy-based adapters for notification channels.
- **Rationale:** A pluggable interface decouples core workflows from delivery mechanisms. Compared to hard-coded email logic, adapters allow new channels to be introduced with minimal changes and consistent retry behavior.
- **Consequences:** Requires adapter contracts and configuration management, but prevents vendor lock-in and reduces future integration cost.

#### ADR 027: Forced Password Change on First Login

- **Context:** Staff accounts are provisioned by organizers and must enforce a secure first-login flow for accountability.
- **Decision:** Use a temporary password with an `is_temporary_password` flag to require a password change on first login.
- **Consequences:** The API and UI must redirect users to Change Password and restrict normal access until the flag is cleared.

## 8. Project Structure & API Standards

### Project Directory Structure

#### Backend (Java 25 / Spring Boot, Modular Monolith)

```text
backend/
├── build.gradle
├── src/
│   ├── main/
│   │   ├── java/com/unihub/workshop/
│   │   │   ├── WorkshopApplication.java
│   │   │   │
│   │   │   ├── shared/                                    # Cross-cutting kernel
│   │   │   │   ├── events/                                # Event publication primitives and base event contracts
│   │   │   │   ├── security/                              # JWT filters, RBAC, crypto utilities
│   │   │   │   ├── exception/                             # Global error handling and problem details
│   │   │   │   ├── web/                                   # Shared HTTP utilities, response factories, filters
│   │   │   │   ├── observability/                         # Logging, audit, and request context
│   │   │   │   └── config/                                # Infra config (Redis, RabbitMQ, MinIO, SpringDoc)
│   │   │   │
│   │   │   ├── iam/                                       # Identity and access management (users, auth, orgs, admin)
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Identity aggregates and policies
│   │   │   │   │   ├── model/                             # Users, orgs, roles, credentials
│   │   │   │   │   ├── events/                            # Account and security lifecycle events
│   │   │   │   │   └── repository/                        # Ports for persistence and external services
│   │   │   │   ├── application/                           # Use cases (login, otp, provisioning)
│   │   │   │   │   ├── usecases/                          # Orchestrates IAM workflows
│   │   │   │   │   └── dto/                               # Request/response models
│   │   │   │   ├── infrastructure/                        # Adapters for DB, Redis, mail, JWT
│   │   │   │   │   ├── persistence/                       # JPA repositories and mappings
│   │   │   │   │   ├── security/                          # Token issuance, hashing, blocklists
│   │   │   │   │   └── event_publication/                 # Emits verification and reset events
│   │   │   │   └── presentation/                          # Controllers and API endpoints
│   │   │   │       └── controller/
│   │   │   │
│   │   │   ├── workshops/                                 # Workshop lifecycle, search, brochures, assignments
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Workshop aggregates and invariants
│   │   │   │   │   ├── model/                             # Workshops, speakers, assignments
│   │   │   │   │   ├── events/                            # Lifecycle and brochure events
│   │   │   │   │   └── repository/                        # Ports for persistence and storage
│   │   │   │   ├── application/                           # Use cases (draft, publish, search)
│   │   │   │   │   ├── usecases/                          # Orchestrates workshop flows
│   │   │   │   │   └── dto/                               # Request/response models
│   │   │   │   ├── infrastructure/                        # DB, FTS, S3, event publication adapters
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── storage/                           # MinIO client and brochure storage
│   │   │   │   │   ├── search/                            # Postgres FTS adapters
│   │   │   │   │   └── event_publication/
│   │   │   │   └── presentation/
│   │   │   │       └── controller/
│   │   │   │
│   │   │   ├── registration/                              # Seat allocation, payments, tickets, idempotency
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Registration aggregates and rules
│   │   │   │   │   ├── model/                             # Registrations, payments, idempotency
│   │   │   │   │   ├── events/                            # Registration lifecycle events
│   │   │   │   │   └── repository/                        # Ports for persistence and payment gateway
│   │   │   │   ├── application/                           # Use cases (register, cancel, qr)
│   │   │   │   │   ├── usecases/                          # Orchestrates payment and seat logic
│   │   │   │   │   └── dto/                               # Request/response models
│   │   │   │   ├── infrastructure/                        # DB, event publication, payment adapter
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── event_publication/
│   │   │   │   │   └── payment/                           # Stripe + ChaosMock adapter
│   │   │   │   └── presentation/
│   │   │   │       └── controller/
│   │   │   │
│   │   │   ├── attendance/                                # Check-in, offline sync, real-time metrics
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Attendance invariants and QR rules
│   │   │   │   │   ├── model/                             # Attendance records and scan events
│   │   │   │   │   ├── events/                            # Check-in lifecycle events
│   │   │   │   │   └── repository/                        # Ports for persistence and QR verification
│   │   │   │   ├── application/                           # Use cases (sync, roster, stats)
│   │   │   │   │   ├── usecases/
│   │   │   │   │   └── dto/
│   │   │   │   ├── infrastructure/                        # DB, QR verification, event publication
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── qr/
│   │   │   │   │   └── event_publication/
│   │   │   │   └── presentation/
│   │   │   │       └── controller/
│   │   │   │
│   │   │   ├── notifications/                             # Templates, in-app inbox, delivery orchestration
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Notification policies and templates
│   │   │   │   │   ├── model/
│   │   │   │   │   ├── events/
│   │   │   │   │   └── repository/
│   │   │   │   ├── application/                           # Use cases (send, retry, log)
│   │   │   │   │   ├── usecases/
│   │   │   │   │   └── dto/
│   │   │   │   ├── infrastructure/                        # Providers, templating, inbox storage
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── providers/                         # Email and future channels
│   │   │   │   │   └── event_publication/
│   │   │   │   └── presentation/
│   │   │   │       └── controller/
│   │   │   │
│   │   │   ├── ai/                                        # Async summarization pipeline
│   │   │   │   ├── contracts/                             # Public facades for other modules
│   │   │   │   ├── domain/                                # Summary job state and policies
│   │   │   │   │   ├── model/
│   │   │   │   │   ├── events/
│   │   │   │   │   └── repository/
│   │   │   │   ├── application/                           # Use cases (enqueue, poll, store)
│   │   │   │   │   ├── usecases/
│   │   │   │   │   └── dto/
│   │   │   │   ├── infrastructure/                        # AI client, storage, worker adapters
│   │   │   │   │   ├── persistence/
│   │   │   │   │   ├── ai_client/
│   │   │   │   │   └── event_publication/
│   │   │   │   └── presentation/
│   │   │   │       └── controller/
│   │   │   │
│   │   │   └── ingestion/                                 # CSV ingestion, quarantine, alerts
│   │   │       ├── contracts/                             # Public facades for other modules
│   │   │       ├── domain/                                # Batch state and validation rules
│   │   │       │   ├── model/
│   │   │       │   ├── events/
│   │   │       │   └── repository/
│   │   │       ├── application/                           # Use cases (trigger, report)
│   │   │       │   ├── usecases/
│   │   │       │   └── dto/
│   │   │       ├── infrastructure/                        # Spring Batch, S3, event publication adapters
│   │   │       │   ├── persistence/
│   │   │       │   ├── batch/
│   │   │       │   ├── storage/
│   │   │       │   └── event_publication/
│   │   │       └── presentation/
│   │   │           └── controller/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V1__init_schema.sql
│   └── test/
│       └── java/com/unihub/workshop/
│           └── ModulithArchitectureTest.java              # Spring Modulith module boundary tests
└── settings.gradle
```

```mermaid
flowchart TD
  subgraph IAM["Module: iam"]
    IAM_P["Presentation"] --> IAM_A["Application"]
    IAM_A --> IAM_D["Domain"]
    IAM_I["Infrastructure"] --> IAM_D
    IAM_C["contracts"]
  end

  subgraph WORK["Module: workshops"]
    WORK_P["Presentation"] --> WORK_A["Application"]
    WORK_A --> WORK_D["Domain"]
    WORK_I["Infrastructure"] --> WORK_D
    WORK_C["contracts"]
  end

  subgraph REG["Module: registration"]
    REG_P["Presentation"] --> REG_A["Application"]
    REG_A --> REG_D["Domain"]
    REG_I["Infrastructure"] --> REG_D
    REG_C["contracts"]
  end

  subgraph ATT["Module: attendance"]
    ATT_P["Presentation"] --> ATT_A["Application"]
    ATT_A --> ATT_D["Domain"]
    ATT_I["Infrastructure"] --> ATT_D
    ATT_C["contracts"]
  end

  subgraph NOTIF["Module: notifications"]
    NOTIF_P["Presentation"] --> NOTIF_A["Application"]
    NOTIF_A --> NOTIF_D["Domain"]
    NOTIF_I["Infrastructure"] --> NOTIF_D
    NOTIF_C["contracts"]
  end

  subgraph AI["Module: ai"]
    AI_P["Presentation"] --> AI_A["Application"]
    AI_A --> AI_D["Domain"]
    AI_I["Infrastructure"] --> AI_D
    AI_C["contracts"]
  end

  subgraph ING["Module: ingestion"]
    ING_P["Presentation"] --> ING_A["Application"]
    ING_A --> ING_D["Domain"]
    ING_I["Infrastructure"] --> ING_D
    ING_C["contracts"]
  end

  REG_A -->|sync via contracts| IAM_C
  REG_A -->|sync via contracts| WORK_C
  ATT_A -->|sync via contracts| REG_C
  ING_A -->|sync via contracts| IAM_C

  IAM_D --> Registry["Event Publication Registry"]
  WORK_D --> Registry
  REG_D --> Registry
  ING_D --> Registry
  Registry --> Broker["Message Broker"]

  Broker --> NOTIF_A
  Broker --> AI_A

  classDef contract fill:#eef7ff,stroke:#4a90e2,stroke-width:1px;
  classDef async fill:#fff7e6,stroke:#f5a623,stroke-width:1px;
  class IAM_C,WORK_C,REG_C,ATT_C,NOTIF_C,AI_C,ING_C contract;
  class Registry,Broker async;
```

- **Intra-module flow (Dependency Inversion):** Presentation calls Application, Application depends on Domain ports, and Infrastructure implements those ports. All source code dependencies point inward toward the Domain.
- **Inter-module flow (Strict boundaries):** Bounded contexts are consolidated into `iam`, `workshops`, `registration`, `attendance`, `notifications`, `ai`, and `ingestion`. Synchronous calls are allowed only from the application layer through `contracts/` facades, while async integration uses domain events routed through the Spring Modulith Event Publication registry and broker (consumed by notifications and ai). Payment is kept as an infrastructure adapter inside registration, and attendance validates QR and roster data through registration contracts only.

#### Web Frontend (React / TypeScript, Feature-Based)

```text
web/
├── src/
│   ├── app/
│   │   ├── routes/
│   │   └── providers/
│   ├── features/
│   │   ├── registration/                   # Workshop registration and ticketing
│   │   │   ├── components/                 # Feature-scoped UI to avoid global coupling
│   │   │   ├── hooks/                      # Local hooks keep registration state isolated
│   │   │   ├── schemas/                    # Zod schemas to validate form inputs before API calls
│   │   │   ├── api/                        # Feature API prevents global client sprawl
│   │   │   ├── state/                      # Encapsulated state avoids cross-feature leakage
│   │   │   └── pages/                      # Route-level composition for the feature
│   │   ├── admin_dashboard/                # Organizer admin overview and analytics
│   │   ├── organizations/                  # Organization and staff assignment
│   │   ├── workshops/                      # Workshop browse, detail, and management
│   │   ├── ai_summaries/                   # AI summary status and review
│   │   ├── ingestion/                      # CSV ingestion status and reports
│   │   ├── notifications/                  # In-app notifications and delivery status
│   │   ├── auth/                           # Login, register, and password flows
│   │   └── profile/                        # User profile and settings
│   ├── shared/
│   │   ├── ui/                             # Reusable primitives only; no feature logic here
│   │   ├── hooks/                          # Cross-cutting hooks shared by multiple features
│   │   ├── api/                            # Base API client + interceptors (no feature state)
│   │   ├── auth/                           # Global auth context and token handling
│   │   └── utils/                          # Pure helpers with no feature dependencies
│   └── assets/
└── package.json
```

```mermaid
flowchart TD
  App["App (Routing/Providers)"] --> FeatureA["Feature A"]
  App --> FeatureB["Feature B"]
  FeatureA --> Shared["Shared (UI, Utils, Base API)"]
  FeatureB --> Shared

  FeatureA -. "no cross-import" .- FeatureB

  subgraph FeatureDetail["Feature A internals"]
    Page["Page"] --> Components["Components"] --> Hooks["Hooks"] --> ApiState["API / State"]
  end
```

- **Unidirectional dependency:** Imports flow downward only (`app` -> `features` -> `shared`). The `shared` layer never imports from `features`.
- **Strict feature isolation:** Features are cohesive and loosely coupled; no feature can import another feature's internal components, hooks, or state.

#### Mobile (Android Native / Java, MVVM + Clean Architecture)

```text
mobile/
├── app/
│   ├── build.gradle
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/unihub/workshop/
│           │   ├── core/                     # Cross-cutting services (network, db, utils)
│           │   └── features/                 # Feature boundaries (MVVM + Clean Architecture)
│           │       ├── checkin/              # Offline-first check-in feature
│           │       │   ├── data/             # Repository implementations and data sources
│           │       │   │   ├── local/        # Room-backed local persistence (DAOs, Entities)
│           │       │   │   ├── remote/       # Retrofit API access and DTO mapping
│           │       │   │   └── repository/   # Orchestrates local-first read/write + sync
│           │       │   ├── domain/           # Pure business rules with no Android deps
│           │       │   │   ├── model/        # Domain entities and value objects
│           │       │   │   └── usecase/      # Use cases exposed to ViewModel
│           │       │   └── presentation/     # UI state and MVVM presentation logic
│           │       ├── workshops/
│           │       └── auth/
│           └── res/
│               ├── layout/
│               └── values/
├── build.gradle
└── settings.gradle
```

```mermaid
flowchart TD
  UI["Fragment/Activity (Presentation)"] --> VM["ViewModel (Presentation)"]
  VM --> UC["UseCase (Domain)"]
  UC --> Port["Repository Interface (Domain)"]
  Repo["RepositoryImpl (Data)"] --> Port
  Repo -->|"write/read first"| Room["Room DAO (Local DB)"]
  Repo -->|"background sync"| Api["Retrofit API (Remote Network)"]
```

- **MVVM + Clean Architecture flow:** UI -> ViewModel -> Domain use case -> Domain port -> Data implementation.
- **Offline-first repository pattern:** The Domain layer is unaware of SQLite or Retrofit; `RepositoryImpl` handles local persistence first, then syncs with the server in the background.

### API Design & Versioning Standards

This section defines a predictable, robust contract between the clients and the backend to keep integrations stable as the system evolves.

#### Versioning Strategy

We use URI path versioning (for example, `/api/v1/...`) because it is explicit, cache-friendly, and easily routable at the API Gateway compared to header-based versioning.

#### Pagination & Filtering

Collection endpoints use offset/limit pagination with explicit query parameters (for example, `?page=0&size=20`). Filters are passed as query parameters and are always optional.

#### HTTP Status Codes

Standard responses include: `200 OK`, `201 Created`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `409 Conflict`, `429 Too Many Requests`, and `500 Internal Server Error`.

#### Mandatory Headers

- `Authorization: Bearer <token>`
- `Idempotency-Key`
- `X-RateLimit-Remaining` and `X-RateLimit-Retry-After` are returned on Bucket4j-protected endpoints.

### Standard JSON Payloads

All REST responses must use a single, global JSON envelope with a strict, predictable schema. Transport and system metadata stays in the root `metadata` object, while all domain data (including pagination details) must live inside `data`.

```typescript
{
  "success": boolean,
  "message": string,
  "data": object | object[] | null,
  "metadata": {
    "statusCode": number,
    "processedBy": string,
    "timestamp": string
  },
  "error": object | null
}
```

- `success`: Boolean flag indicating overall request success.
- `message`: Human-readable summary intended for logs and UI messages.
- `data`: Domain payload (single object, list, or pagination container). `null` for errors.
- `metadata`: Transport/system metadata for observability and routing.
  - `metadata.statusCode`: HTTP status code returned by the backend for this response.
  - `metadata.processedBy`: Hostname or ID of the specific Spring Boot app server node (for example, `unihub-app-1`) that handled the request behind the Nginx round-robin load balancer.
  - `metadata.timestamp`: ISO 8601 timestamp when the response was generated.
- `error`: RFC 7807 Problem Details object on failures; `null` on success.

All fields must be present in every response. If a field is not applicable (for example, `data` on an error response or `error` on a success response), it must be set to `null` to avoid client parsing exceptions.

#### Success Response (Single Resource)

```json
{
  "success": true,
  "message": "Workshop retrieved",
  "data": {
    "id": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "title": "Career Sprint 101"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-03T12:00:00Z"
  },
  "error": null
}
```

#### Success Response (List / Pagination)

```json
{
  "success": true,
  "message": "Workshops page",
  "data": {
    "items": [
      {
        "id": "b2a3f0b8-22f9-4b6a-9a54-1a8d8d1f20f4",
        "title": "Design Systems"
      }
    ],
    "page": 0,
    "size": 20,
    "total": 120,
    "hasPrev": false,
    "hasNext": true
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-03T12:00:00Z"
  },
  "error": null
}
```

#### Error Response

```json
{
  "success": false,
  "message": "Invalid request payload",
  "data": null,
  "metadata": {
    "statusCode": 400,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-03T12:00:00Z"
  },
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Validation Error",
    "detail": "One or more fields failed validation.",
    "instance": "/api/v1/registrations",
    "validationErrors": [
      "email: must be a valid email address",
      "workshopId: is required"
    ]
  }
}
```

### Observability & Logging Strategy

To keep operational complexity low during initial launch, the system relies exclusively on structured console logging (stdout/stderr) rather than heavy external telemetry stacks.

#### API Request Logs

Every incoming API request is intercepted and logged on completion using a strict format: `[Method] [Endpoint] [Code] - [ResponseTime] - [Message]`.

```text
[INFO] POST /api/v1/registrations 201 - 45ms - Workshop registered successfully
[ERROR] POST /api/v1/registrations 409 - 120ms - Workshop capacity exceeded
```

#### System & Worker Logs

Background processes (Event Publication Registry, AI Summarization, CSV Ingestion) emit state changes directly to the console for traceability.

```text
[INFO] [EventPublicationRegistry] - Externalized 5 pending notification events
```
