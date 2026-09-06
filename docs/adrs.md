# UniHub Workshop — Architectural Decision Records

## 1. Foundation

### ADR 001: Backend Framework and Architecture

- **Context:** The backend must handle 12,000+ concurrent users, enforce strict transactional integrity for seat allocation and payments, and remain operationally simple under rapid delivery constraints.
- **Decision:** Use Java with Spring Boot in an Event-Driven Modular Monolith architecture powered by Spring Modulith.
- **Rationale:** Java and Spring Boot offer predictable multi-threaded performance, robust transaction management, and mature resilience libraries. Spring Modulith enforces module boundaries while preserving single-deploy simplicity, which reduces operational overhead compared to microservices and avoids cross-service consistency pitfalls during the registration surge.
- **Alternatives Considered:** Microservices were rejected because exactly-once registration/payment flows would require distributed transactions or compensating sagas across services, adding avoidable ops overhead for a university team. Go or Node.js were rejected because this project benefits more from Spring’s transaction, security, and batch ecosystem than from a different runtime.
- **Consequences:** Requires disciplined module boundaries and JVM tuning, but delivers strong consistency and simpler operations under high concurrency.

### ADR 002: Web Frontend Framework

- **Context:** The web portal serves `STUDENT` and `ORGANIZER` workflows with RBAC-controlled actions, strict data integrity, and rapid iteration requirements.
- **Decision:** Use React with TypeScript for the web application.
- **Rationale:** React enables modular UI composition for role-specific flows, while TypeScript improves refactor safety under evolving APIs. The ecosystem supports reliable state management and integration with REST endpoints, reducing UI defects during the 12,000-user surge.
- **Alternatives Considered:** Angular was rejected because its heavier framework model slows incremental delivery and does not improve sharing with React Native. Vue was rejected for the same reason as the project gains more from a single React/TypeScript component model across web and mobile.
- **Consequences:** Requires disciplined state architecture but accelerates delivery and long-term maintainability.

### ADR 003: Mobile App Framework

- **Context:** Check-in `STAFF` require offline-first scanning with fast QR processing and deterministic sync under poor connectivity.
- **Decision:** Build the mobile app with React Native (TypeScript).
- **Rationale:** React Native aligns the mobile stack with the web ecosystem, enabling shared TypeScript domain models and reducing duplicated logic. It still supports native camera integration and offline persistence while accelerating delivery and reducing long-term maintenance compared to separate native codebases.
- **Alternatives Considered:** Native iOS/Android apps were rejected because they would double the codebase and slow offline-sync feature parity. Flutter was rejected because it does not materially improve integration with the existing React/TypeScript web stack or shared domain model strategy.
- **Consequences:** Requires careful native module selection for camera and storage performance, but improves developer velocity and consistency across clients.

### ADR 004: API Design Style

- **Context:** Two clients (web and mobile) must integrate quickly and reliably during accelerated delivery.
- **Decision:** Use RESTful APIs for core services.
- **Rationale:** REST provides clear resource modeling, simpler observability, and predictable caching semantics. It reduces implementation risk compared to GraphQL while maintaining predictable performance during traffic spikes.
- **Alternatives Considered:** GraphQL was rejected because query flexibility would complicate authorization, caching, and mobile sync determinism under high concurrency. gRPC was rejected because it would add client complexity without improving the project’s browser-first and offline-first integration needs.
- **Consequences:** Complex screens may require multiple endpoints, but integration remains straightforward and debuggable.

## 2. Security & Identity

### ADR 005: Authentication Mechanism

- **Context:** The system must scale horizontally, support multiple clients, and avoid shared session state under peak load.
- **Decision:** Use stateless JWT authentication signed with asymmetric cryptography (RS256) and role claims, with revocation based on the `jti` claim stored in a Redis blacklist.
- **Rationale:** RS256 allows the Identity/Auth module to securely hold the private key while gateways and services verify tokens using the public key, reducing the blast radius of edge compromise. Storing `jti` in Redis enables immediate revocation and logout without server-side sessions, preserving throughput during 12,000-user spikes.
- **Alternatives Considered:** Server-side sessions were rejected because shared session state would become a horizontal-scaling bottleneck and complicate zero-trust edge hardening. Symmetric JWT signing (HS256) was rejected because every verifier would need the signing secret, increasing compromise impact and weakening key separation.
- **Consequences:** Requires key management, JWKS publishing, and Redis availability, but eliminates shared session storage and supports secure revocation.

### ADR 006: Authorization Model

- **Context:** The platform must enforce permissions for `STUDENT`, `ORGANIZER`, and `STAFF` while supporting multi-tenant data isolation and concurrent admin edits.
- **Decision:** Use RBAC with logical multi-tenancy enforced by `organization_id` (for example, `workshop.organization_id == jwt.org`) and optimistic locking (`@Version`) for concurrent admin mutations.
- **Rationale:** RBAC enables `O(1)` checks under surge load, while logical multi-tenancy ensures any `ORGANIZER` within the same organization can manage its workshops without cross-tenant leakage. Optimistic locking prevents lost updates when multiple `ORGANIZERs` modify the same workshop, returning `409 Conflict` on version mismatch.
- **Alternatives Considered:** Pure ACLs were rejected because per-resource permission checks are harder to reason about and more expensive to maintain at scale. Database row-level security was rejected because it would push critical authorization logic deeper into the data layer and complicate application-level conflict handling.
- **Consequences:** Requires consistent org scoping in all data access paths and client handling of optimistic locking conflicts.

## 3. Data Infrastructure

### ADR 007: Primary Database

- **Context:** Seat allocation, payments, and attendance must be ACID-compliant and resistant to overbooking.
- **Decision:** Use PostgreSQL hosted on Neon as the primary transactional database with a primary-replica topology.
- **Rationale:** PostgreSQL provides strong transactional guarantees and mature indexing. Neon delivers managed availability while retaining strict consistency for writes, which is essential to exactly-once payment and registration flows.
- **Alternatives Considered:** MongoDB was rejected because its document model does not provide the same strict ACID guarantees and indexing consistency required for seat allocation, payments, and idempotency. MySQL was rejected because PostgreSQL better fits the project’s concurrency, indexing, and advanced transactional patterns.
- **Consequences:** Requires careful routing of read vs write queries and monitoring of replication lag.

### ADR 008: Database Migration Tool

- **Context:** Schema changes must be deterministic and safe under production load; tenant seeding must be dynamic and idempotent.
- **Decision:** Use Flyway strictly for schema (DDL) migrations. Seed root tenants and initial `ORGANIZER` accounts via JSON arrays in environment variables parsed by a Spring Boot `ApplicationRunner` during startup.
- **Rationale:** DDL-only migrations prevent version drift from mutable seed data. Environment-driven JSON seeding enables idempotent creation at startup, avoids migration failures, and supports adding new root tenants by updating configuration rather than rewriting SQL migrations.
- **Alternatives Considered:** Liquibase was rejected because the project does not need a heavier migration abstraction for this mostly DDL-driven schema lifecycle. Embedding seed data directly in migrations was rejected because mutable tenant bootstrap data should remain idempotent and environment-driven rather than tied to schema revision history.
- **Consequences:** Requires startup validation and operational controls around environment configuration, but reduces migration risk and preserves 12-factor principles.

### ADR 009: Database Connection Pooling Strategy

- **Context:** High concurrency requires efficient JDBC pooling to prevent saturation.
- **Decision:** Use HikariCP for connection pooling.
- **Rationale:** HikariCP offers low latency and predictable performance, reducing connection churn under the 12,000-user surge and preserving throughput for transactional workloads.
- **Alternatives Considered:** Apache DBCP2 and C3P0 were rejected because they are slower and less predictable under bursty traffic. Unpooled JDBC access was rejected because it would amplify connection churn and risk exhausting PostgreSQL during peak registration spikes.
- **Consequences:** Requires careful pool sizing and monitoring to avoid exhausting PostgreSQL resources.

### ADR 010: Caching and Rate Limiting Storage

- **Context:** The platform needs fast cache access and atomic counters for rate limiting and concurrency controls.
- **Decision:** Use Redis for cache-aside data, token bucket counters, and lock-free coordination primitives.
- **Rationale:** Redis provides atomic operations (for example, `DECR`), expiring keys, and high throughput for shared counters. It supports non-blocking refresh patterns and rate limiting at scale without overloading PostgreSQL.
- **Alternatives Considered:** Memcached was rejected because it lacks the durable atomic counter semantics needed for `DECR`-based coordination and lock-free control flow. In-memory local caches were rejected because they fragment state across nodes, making rate limiting and refresh coordination unsafe in a multi-instance deployment.
- **Consequences:** Requires memory sizing, eviction policy tuning, and Redis HA planning.
- **Failure Mode & Fallback:** Redis is a critical dependency for two mechanisms - Token Bucket rate limiting and lock-free capacity pre-checks. When Redis is unavailable, the system applies distinct fallback strategies per concern:
  - **Rate Limiting (Bucket4j):** Configured in **fail-open** mode. When Redis is unreachable, the filter skips bucket evaluation and allows the request through. This is acceptable because: (a) outages are expected to be brief, (b) the primary risk (overbooking) is still protected by the DB-level constraints, and (c) blocking all traffic during a Redis outage is a worse outcome for a university event system. All fail-open decisions must be logged and a `rate_limiter.redis_unavailable` metric emitted for alerting.
  - **Capacity DECR:** Configured in **fail-closed** mode. When Redis is unreachable, the API returns `503 Service Unavailable` with a `Retry-After: 30` header for registration attempts. This prevents capacity counter state from being bypassed entirely. Workshop browsing and other read paths remain fully operational and must not be gated on Redis availability.
  - **JTI Blacklist (Token Revocation):** Configured in **fail-closed** mode. If Redis is unreachable, revocation checks cannot be performed and the request is rejected with `503`. This is the safer choice for a security control - allowing potentially revoked tokens is a worse outcome than a brief service interruption.

### ADR 011: Object Storage

- **Context:** The system must store PDFs and nightly CSV drops without bloating the primary database.
- **Decision:** Use S3-compatible object storage (for example, MinIO in local environments).
- **Rationale:** Object storage is optimized for large immutable files and integrates cleanly with background workers and batch ingestion. It reduces database load and improves IO scalability under concurrent access.
- **Alternatives Considered:** Storing PDFs or CSVs directly in PostgreSQL was rejected because it would bloat the primary database and slow transactional workloads. Shared filesystem storage was rejected because it weakens durability and horizontal scalability for worker-driven batch processing.
- **Consequences:** Requires explicit access controls and lifecycle policies for retention.

### ADR 012: External Search Engine

- **Context:** Workshop search is read-heavy and must remain fast under surge traffic without impacting transactional workloads.
- **Decision:** Use Elasticsearch as a CQRS read model synchronized asynchronously from PostgreSQL.
- **Rationale:** Elasticsearch offloads CPU-intensive search from the primary database, protecting write throughput during the 12,000-user spike. Background workers synchronize data, keeping transactional correctness in PostgreSQL while enabling fast, relevance-tuned search.
- **Alternatives Considered:** PostgreSQL native full-text search was rejected because it would compete with transaction workloads for CPU and memory on the primary database during the spike window. Algolia was rejected due to cost and external dependency concerns for a high-read academic workload.
- **Consequences:** Requires dual-write synchronization and monitoring of index freshness.

## 4. Data Integrity & Concurrency

### ADR 013: Locking Strategy for Seat Allocation

- **Context:** High-demand workshops can receive hundreds of simultaneous registration attempts for the final seats.
- **Decision:** Use lock-free atomic pre-checks via Redis (`DECR`) for capacity enforcement instead of pessimistic database locking.
- **Rationale:** Redis processes atomic decrements sequentially in microseconds, preventing last-seat race conditions without holding database connections or blocking request threads. Pessimistic locking (`SELECT ... FOR UPDATE`) is an anti-pattern at 12,000-user scale due to connection pool exhaustion risk.
- **Alternatives Considered:** Pessimistic row locking was rejected because it would serialize hot-seat contention and exhaust the JDBC pool during the 3-minute surge. Application-side spin locks or retries were rejected because they waste threads and still cannot guarantee efficient last-seat handling under contention.
- **Consequences:** Requires compensating increments on rollback paths but preserves database availability during spikes.
- **Defense-in-Depth:** Redis DECR is the first-line guard. The database partial unique index on `(workshop_id, student_user_id) WHERE status IN ('PENDING', 'CONFIRMED')` is the last-resort safety net for any edge-case Redis inconsistency. The registration use case **must explicitly catch** the resulting unique constraint violation from the database and translate it into a `409 Conflict` response — not a `500 Internal Server Error`. This two-layer design ensures correctness is preserved even under Redis counter drift, while keeping the DB-level contention path rare enough to not impact throughput.
- **Periodic Reconciliation:** A Capacity Reconciliation Job runs every 5 minutes to detect and correct Redis-DB counter drift caused by missed compensating increments. See the Overbooking Prevention section of `design.md` for the full reconciliation flow.

### ADR 014: Idempotency Mechanism

- **Context:** Payment retries and network drops can cause duplicate charges without strict idempotency control.
- **Decision:** Implement a 2-Tier Cache-Aside Idempotency Strategy combining Redis (L1 distributed in-memory cache) for sub-millisecond deduplication and cached response replay with Primary PostgreSQL (`idempotency_keys` table, L2 durable source-of-truth) for atomic insert-on-conflict claims, cache-aside backfill, and cache invalidation on failure.
- **Rationale:** An atomic database insert (`ON CONFLICT DO NOTHING`) eliminates check-then-act race conditions across horizontally-scaled backend nodes. L1 Redis provides instant deduplication and cached replay for the 12,000-user surge. On an L1 cache miss, the system queries the primary database (L2 Hit) and backfills the L1 cache while replaying the stored response. On transient transaction failures or rollbacks, the application explicitly invalidates the L1 Redis key (`DEL`) and clears the pending database claim, ensuring clients are not locked out for subsequent retries. A Background Sweeper reconciles orphaned `PENDING` states (Delayed Webhook Recovery) for asynchronous payment flows.
- **Alternatives Considered:** Redis-only idempotency was rejected because cache loss, node restart, or memory eviction would permanently destroy exact replay semantics for financial transactions. Database-only idempotency without Redis was rejected because it would place unnecessary read/write contention on PostgreSQL during high-volume registration surges. Application-memory deduplication was rejected because it cannot survive multi-node concurrency behind Nginx.
- **Consequences:** Requires maintaining consistent response serialization and explicit cache invalidation logic across layers, but guarantees resilient, exactly-once mutation semantics without duplicate charges or duplicate registrations.

### ADR 015: Primary-Replica Routing Strategy

- **Context:** Replication lag in a primary-replica topology can cause stale reads during critical transactional operations (e.g., idempotency checks, admin data mutations, or immediate read-after-write scenarios).
- **Decision:** Route all writes and highly consistent read operations (such as idempotency verification, pre-transaction validations, and admin mutation flows) strictly to the primary database. Route eventually consistent, non-critical reads (like public workshop browsing) to replicas.
- **Rationale:** Critical flows require strict "Read-Your-Writes" consistency to prevent replay anomalies, concurrency conflicts, or lost updates. Forcing primary routing for these specific operations eliminates replication lag risks while still preserving read replica scalability for the vast majority of public traffic.
- **Alternatives Considered:** Replica reads for idempotency and admin mutations were rejected because replication lag can cause false negatives, replay anomalies, and lost-update races. A single-node primary-only model was rejected because it would unnecessarily sacrifice read scalability for non-critical browsing paths.
- **Consequences:** Requires explicit routing logic at the application layer (e.g., relying on `@Transactional` routing mechanisms) and strict code reviews to ensure critical reads never hit replicas.

### ADR 016: Stale-While-Revalidate (SWR) Cache-Aside

- **Context:** Search and listing endpoints are read-heavy and must remain responsive during surge traffic.
- **Decision:** Use an SWR cache-aside strategy with Redis, dual TTLs (soft/hard), and non-blocking refresh with `tryLock`.
- **Rationale:** SWR returns cached content immediately while refreshing in the background, preventing thread blocking and cache stampedes. Dual TTLs allow controlled staleness without serving expired data indefinitely. A non-blocking refresh lock prevents concurrent refresh storms.
- **Alternatives Considered:** Pure cache-aside with synchronous refresh was rejected because it would block request threads and create stampedes during the 3-minute traffic spike. Local per-node caches were rejected because they would diverge across instances and break search coherence.
- **Consequences:** Requires cache invalidation discipline and observability on refresh behavior.

## 5. Async & Event-Driven

### ADR 017: Message Broker

- **Context:** Notifications, AI summarization, and CSV ingestion must run asynchronously without blocking registration flows.
- **Decision:** Use RabbitMQ as the message broker.
- **Rationale:** RabbitMQ provides reliable task-queue semantics and straightforward retry/DLQ handling, which fits the background worker model and keeps core request threads free during the registration surge.
- **Alternatives Considered:** Apache Kafka was rejected because its operational footprint and stream-processing model are excessive for simple task routing and DLQ-driven worker retries. Synchronous execution or in-process queues were rejected because they would tie async latency to request threads and undermine scalability.
- **Consequences:** Requires broker monitoring and retry policies, but reduces coupling between synchronous and async workloads.

### ADR 018: Transactional Outbox

- **Context:** Domain events must not be lost when database commits succeed but broker publishes fail.
- **Decision:** Use Spring Modulith Event Publication registry (transactional outbox) for event persistence and externalization.
- **Rationale:** Storing events in the same transaction as domain writes guarantees exactly-once publication semantics and prevents inconsistencies during high traffic and retries.
- **Alternatives Considered:** Direct broker publishing from request handlers was rejected because commit/publish failures would create inconsistent state and duplicate event risk. Database polling of domain tables was rejected because it weakens event fidelity and adds unnecessary read load to the primary database.
- **Consequences:** Requires monitoring of publication lag and backlog.

## 6. Resilience & Traffic Management

### ADR 019: Rate Limiting Algorithm

- **Context:** Registrations can spike to 12,000 concurrent users within minutes.
- **Decision:** Use Bucket4j Token Bucket rate limiting in the Core Backend Monolith filter layer with Redis-backed counters. Nginx acts as a pure forwarding proxy and does not perform rate limiting.
- **Rationale:** Token Bucket smooths bursts while allowing controlled short-term spikes. Placing enforcement in the backend filter layer preserves a dumb gateway and centralizes logic under consistent authentication and telemetry. Redis-backed shared counters ensure consistent enforcement across all horizontally scaled backend nodes (Node 1 and Node 2).
- **Alternatives Considered:** Leaky bucket and fixed-window counters were rejected because they either smooth too aggressively or allow burst edges that are too coarse for the 3-minute spike window. Gateway-only enforcement was rejected because the core backend needs first-class control and observability for authenticated traffic shaping. Nginx-level rate limiting was rejected because it cannot distinguish authenticated users and lacks access to JWT claims required for per-user bucket keying.
- **Consequences:** Requires tuning refill rates and burst sizes per endpoint category (registration endpoints vs general browsing). When Redis is unavailable, the rate limiter operates in fail-open mode (see ADR-010 failure mode) — all fail-open decisions are logged and emitted as metrics for alerting.

### ADR 020: Circuit Breaker Implementation

- **Context:** Payment gateways can fail or time out, risking cascading failures.
- **Decision:** Use Resilience4j circuit breakers around external integrations.
- **Rationale:** Resilience4j provides Closed/Open/Half-Open states with metrics and failure thresholds, allowing fast-fail behavior and protecting core flows during outages.
- **Alternatives Considered:** Custom circuit breaker logic was rejected because it would duplicate state-machine and metrics concerns that Resilience4j already solves reliably. Bulkhead-only isolation was rejected because it does not address downstream failure propagation or fast-fail behavior for payment integrations.
- **Consequences:** Requires explicit fallback design and careful threshold configuration.

### ADR 021: Payment Gateway Testing Strategy

- **Context:** The system must prove resilience to payment outages and timeouts before production.
- **Decision:** Use Stripe in sandbox mode with a `ChaosMockAdapter` that injects controlled failures and latency.
- **Rationale:** A deterministic chaos adapter validates circuit breaker thresholds, idempotency, and compensating actions without relying on external outages, ensuring exactly-once payment behavior under stress tests.
- **Alternatives Considered:** Manual sandbox testing alone was rejected because it cannot systematically reproduce timeout, retry, and duplicate-charge edge cases. Ad hoc mock libraries were rejected because they do not provide the controlled failure injection needed to validate production-grade resilience policies.
- **Consequences:** Requires strict environment controls so chaos testing cannot reach production.

### ADR 022: Payment Webhook Handling Strategy

- **Context:** After the synchronous payment API call, the platform must receive payment outcome notifications from the external payment gateway (e.g., Stripe `payment_intent.succeeded`, `payment_intent.payment_failed`). Without a reception path for these events, the system must poll or wait, creating unacceptable UX delays for students waiting for registration confirmation.
- **Decision:** Implement a dedicated, unauthenticated `POST /api/v1/payments/webhook` endpoint that receives gateway-pushed events, verifies the payload signature using the provider's HMAC signing secret, and processes the payment outcome idempotently. The Dual-Case Reconciliation Sweeper (defined in ADR-014) is retained as a fallback-only mechanism for genuinely lost webhooks, with its timeout extended to 30 minutes.
- **Rationale:** Webhook-driven confirmation is the industry-standard pattern for asynchronous payment flows. It reduces the student's registration confirmation latency from up to 15 minutes (sweeper-driven) to seconds (webhook-driven), which is critical during a high-concurrency event where students expect near-instant feedback. Idempotency on `provider_payment_id` prevents double-processing if the gateway retries the webhook.
- **Alternatives Considered:** Sweeper-only reconciliation was rejected as the primary path because the 15-minute (or longer) latency creates a degraded UX during the peak registration window — students are left with a `PENDING` registration with no confirmation. Pure synchronous payment confirmation was rejected because payment gateways are inherently asynchronous and synchronous calls block request threads during the 3-minute spike window.
- **Consequences:** Requires secure storage of the webhook signing secret as an environment variable. The endpoint must always return `200 OK` to the gateway (even on internal processing errors) to prevent retry floods. An internal DLQ or error log must capture events that could not be processed for manual review.

## 7. Specific Workflows

### ADR 023: Mobile Local Database

- **Context:** Offline check-in requires durable local storage and deterministic sync.
- **Decision:** Use a modern JSI-based SQLite library for React Native (e.g., `expo-sqlite` or `react-native-quick-sqlite`) as the mobile persistence layer.
- **Rationale:** SQLite provides predictable transactional behavior and supports offline-first queues that can be reconciled reliably. It aligns with the React Native stack and supports high-volume scan buffering without data loss. Using modern JSI bindings ensures synchronous, high-speed inserts without the React Native Bridge overhead, which is critical for maintaining fast QR scanning speeds at the door.
- **Alternatives Considered:** AsyncStorage was rejected because it lacks transactional querying and cannot support deterministic offline reconciliation at scan volume. WatermelonDB was rejected because the project benefits more from a simpler, broadly supported SQLite layer for controlled sync semantics.
- **Consequences:** Requires schema migrations on mobile, but delivers durable offline behavior.

### ADR 024: Offline Sync Conflict Resolution

- **Context:** Multiple devices can scan the same attendee while offline and later sync concurrently.
- **Decision:** Use server-authoritative reconciliation with workshop-scoped bulk fetch and bulk upsert that returns an HTTP 207 Multi-Status response with per-entry resolution codes (e.g., `SUCCESS`, `ALREADY_USED`, `INVALID_TICKET`).
- **Rationale:** Server-authoritative reconciliation prevents duplicate attendance, preserves auditability, and scales under batch sync. Bulk operations minimize DB chatter and avoid per-row contention during offline sync bursts. Using HTTP 207 avoids exception-based control flow for partial batch failures, allowing the SQLite mobile client to deterministically update local records without failing the entire sync batch.
- **Alternatives Considered:** Client-side merge or last-write-wins reconciliation was rejected because offline devices cannot safely decide attendance authority without risking double booking. Exception-driven batch failure was rejected because a single bad row would abort the entire sync and make deterministic mobile recovery impossible.
- **Consequences:** Requires unique constraints and deterministic response handling on the client.

### ADR 025: Batch Processing Strategy

- **Context:** Nightly CSV ingestion must tolerate malformed rows and duplicates without failing the entire pipeline.
- **Decision:** Use Spring Batch with streaming ItemReader/ItemProcessor/ItemWriter, chunk commits, and local reject staging uploaded to S3 on completion.
- **Rationale:** Streaming prevents OOM for large files, chunk commits provide restartability via `JobRepository`, and local reject staging avoids unsupported S3 append operations while preserving audit artifacts.
- **Alternatives Considered:** Loading the whole CSV into memory was rejected because large nightly drops would risk OOM and long GC pauses. Direct S3 append or per-row uploads were rejected because object storage is immutable and those patterns would increase API chatter, latency, and operational fragility.
- **Consequences:** Requires job monitoring and local ephemeral storage on workers.

### ADR 026: AI Summarization Integration

- **Context:** PDF summarization must not block admin workflows and must be resilient to external AI failures.
- **Decision:** Use an async worker pipeline triggered via transactional outbox events and brokered execution with DLQ handling.
- **Rationale:** Asynchronous processing isolates AI latency and external failures from the core API, while DLQ handling preserves failed jobs for inspection and retry. Workers perform local PDF extraction and text chunking (for example, via PDFBox) prior to invoking external AI APIs; this bounds payload size to prevent external token-limit exhaustion (manifesting as HTTP 413/429 errors) and reduces external compute and inference costs.
- **Alternatives Considered:** Calling the AI API synchronously from the admin request was rejected because it would couple user latency to external inference variability and break resilience under spikes. Sending raw PDFs directly was rejected because payload size, token limits, and external compute costs make pre-chunking necessary for predictable operation.
- **Consequences:** Introduces eventual consistency and requires job status tracking.

### ADR 027: Pluggable Notification Architecture

- **Context:** The system must support email and in-app notifications, with future channel expansion.
- **Decision:** Use a provider abstraction with strategy-based adapters and brokered delivery.
- **Rationale:** Adapters decouple core workflows from delivery channels, allowing new channels without schema changes and preserving reliability through retry semantics.
- **Alternatives Considered:** A monolithic notification service was rejected because channel-specific failures and release cadence would become tightly coupled. Direct provider calls from business workflows were rejected because they would spread vendor logic across the codebase and weaken retry isolation.
- **Consequences:** Requires adapter contracts and configuration management but avoids vendor lock-in.

### ADR 028: Delegated Creation & Zero-Trust Provisioning

- **Context:** `ORGANIZERs` provision `STAFF` and other `ORGANIZER` accounts without a shared admin console, and passwords must not be exposed to the inviter.
- **Decision:** Use delegated creation with system-generated temporary passwords, mandatory first-login password change, and explicit workshop assignment for `STAFF`.
- **Rationale:** Zero-trust provisioning prevents password exposure and ensures new accounts are secured at first login. Explicit assignment prevents `STAFF` from scanning unassigned workshops even within the same organization.
- **Alternatives Considered:** Shared initial passwords were rejected because they expose credentials to the inviter and violate zero-trust principles. Self-service account creation was rejected because `STAFF` and delegated `ORGANIZER` provisioning must remain scoped, auditable, and organization-bound.
- **Consequences:** Requires email delivery reliability and account-state enforcement on first login.

### ADR 029: Offline QR Verification Cryptography

- **Context:** QR codes could be forged offline, and `STAFF` devices must verify authenticity without network access.
- **Decision:** Use PKI-based asymmetric cryptography for QR payload signing and verification.
- **Rationale:** The backend signs QR payloads with a private key; the mobile app stores the current public key in SQLite and verifies signatures offline. This supports key rotation to invalidate compromised keys globally.
- **Alternatives Considered:** Symmetric encryption or HMAC were rejected because the mobile client would need to hold the secret key, which is unacceptable if the app is decompiled. Online-only verification was rejected because offline check-in must work without network access.
- **Consequences:** Requires secure key management and periodic public key refresh on login.

## 8. Observability

### ADR 030: Core Observability and Monitoring Stack

- **Context:** High-concurrency surges can cause silent failures, connection pool starvation, or cache stampedes. We need real-time telemetry without introducing performance overhead to request threads.
- **Decision:** Use Spring Boot Actuator with Micrometer to expose core metrics at `/actuator/prometheus`. Deploy Prometheus as a pull-based Time Series Database (TSDB) to scrape these metrics, and Grafana for centralized visualization dashboarding.
- **Rationale:** Micrometer provides out-of-the-box, low-overhead instrumentation for JVM, Tomcat threads, HikariCP, Redis, and Resilience4j components. The pull-model of Prometheus ensures that backend instances are not burdened with pushing telemetry data during traffic spikes.
- **Consequences:** Adds two lightweight infrastructure containers (Prometheus & Grafana) to the Docker environment. Requires metrics storage provisioning.
