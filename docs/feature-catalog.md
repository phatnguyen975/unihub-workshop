# UniHub Workshop — Feature Catalog

> **Document Purpose:** This is the authoritative, production-grade feature catalog for the UniHub Workshop platform. It enumerates every feature required to deliver the full system described in `docs/overview.md`, maps each feature to the backend modules defined in `docs/project-structure.md`, and references the relevant system flows from `docs/system-design.md`. This document must be the starting point for any `/spec` invocation — the feature name supplied to `/spec` must be one of the kebab-case names listed here.

## Document Conventions

- **Feature Name:** kebab-case identifier used as the `<feature-name>` argument to `/spec`, `/plan`, `/build`, and `/review`.
- **Backend Modules:** Spring Modulith modules in `apps/backend/src/main/java/com/unihub/workshop/` (see `docs/project-structure.md §3`).
- **Worker Modules:** Background worker sub-modules in `apps/workers/`.
- **System Flow Ref:** Cross-reference to the corresponding flow in `docs/system-design.md §4` or `§5 Access Control Design`.
- **ADR Refs:** Relevant Architectural Decision Records from `docs/adrs.md`.
- **Clients:** `Web` (React), `Mobile` (React Native), or both.

## Domain Index

| #   | Domain                                                                        | Feature Count |
| :-- | :---------------------------------------------------------------------------- | :------------ |
| 1   | [Identity & Access Management](#1-identity--access-management)                | 6             |
| 2   | [Workshop Lifecycle Management](#2-workshop-lifecycle-management)             | 5             |
| 3   | [Workshop Discovery & Search](#3-workshop-discovery--search)                  | 2             |
| 4   | [Workshop Registration](#4-workshop-registration)                             | 3             |
| 5   | [Payment Processing](#5-payment-processing)                                   | 2             |
| 6   | [On-Site Check-in & Attendance](#6-on-site-check-in--attendance)              | 2             |
| 7   | [Notification System](#7-notification-system)                                 | 2             |
| 8   | [AI-Powered Brochure Summarization](#8-ai-powered-brochure-summarization)     | 1             |
| 9   | [Student Data Synchronization](#9-student-data-synchronization)               | 1             |
| 10  | [System Reliability & Infrastructure](#10-system-reliability--infrastructure) | 4             |

**Total: 28 Features**

## 1. Identity & Access Management

> **Domain Description:** Covers all authentication, authorization, user lifecycle, and tenant provisioning concerns. Every other domain depends on the identity contracts (`iam/contracts`) exposed by this module. Corresponds to the `iam` Spring Modulith module in the Core Backend and the multi-layer RBAC enforcement described in `docs/system-design.md §5`.

### 1.1 `user-registration-and-identity-verification`

**Description:** Allows a new `STUDENT` to self-register on the platform. Upon registration, the system validates the submitted email against the `student_directory` table (populated by the nightly CSV ingestion pipeline) to establish a verified identity link (`users.student_record_id`). Students whose email cannot be matched to a `student_directory` record are created as accounts but are blocked from registering for workshops until verified. This is the only self-service account creation flow — `ORGANIZER` and `STAFF` accounts are exclusively created by delegated provisioning (see feature `staff-and-organizer-provisioning`).

| Attribute           | Detail                                                                                                                                         |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `iam` (primary — user creation, identity verification, `student_record_id` link), `shared/security` (password hashing)                         |
| **Worker Modules**  | None                                                                                                                                           |
| **System Flow Ref** | `docs/system-design.md §5 — Account Provisioning & Security`                                                                                   |
| **ADR Refs**        | ADR-005 (JWT RS256), ADR-006 (RBAC), ADR-028 (Zero-Trust Provisioning)                                                                         |
| **DB Tables**       | `users`, `student_directory`, `organizations`                                                                                                  |
| **Clients**         | Web                                                                                                                                            |
| **Key Constraints** | Password hashed with Argon2/Bcrypt. `student_record_id` is nullable; `NULL` = workshop registration blocked. Email unique via `CITEXT UNIQUE`. |

### 1.2 `authentication-and-session-management`

**Description:** Implements the complete authentication session lifecycle: login with email/password returning a short-lived RS256-signed JWT access token and a long-lived refresh token; access token refresh using the refresh token (with rotation on each use); and secure logout that revokes the JWT's `jti` claim in the Redis blacklist and invalidates the refresh token in the database. Also enforces the mandatory first-login password change for accounts where `is_temporary_password = true` (provisioned `STAFF`/`ORGANIZER` accounts). The JWKS endpoint (`/.well-known/jwks.json`) exposes the public key for token verification by downstream services.

| Attribute           | Detail                                                                                                                                                                                                |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `iam` (primary — auth use cases, token generation), `shared/security` (JWT RS256 signing/verification, Redis JTI blacklist, JWKS endpoint)                                                            |
| **Worker Modules**  | None                                                                                                                                                                                                  |
| **System Flow Ref** | `docs/system-design.md §5 — RBAC Model & Multi-Layer Enforcement`                                                                                                                                     |
| **ADR Refs**        | ADR-005 (JWT RS256 + Redis JTI revocation), ADR-010 (Redis fail-closed for JTI checks)                                                                                                                |
| **DB Tables**       | `users`, `refresh_tokens`                                                                                                                                                                             |
| **Clients**         | Web, Mobile                                                                                                                                                                                           |
| **Key Constraints** | Access token: short-lived (e.g., 1 hour). Refresh token: stored as `token_hash` only, rotated on each use. JTI blacklist is **fail-closed** — Redis unavailability returns `503 Service Unavailable`. |

### 1.3 `password-reset`

**Description:** Provides a self-service password reset flow for users who have forgotten their credentials. A user submits their email; the system generates a cryptographically random, single-use reset token, stores its hash in `password_reset_tokens` with an expiry TTL, and sends the reset link via the notification worker. On link consumption, the token is validated (non-expired, non-used), the password is updated and hashed, the token is marked as `used_at`, and all active refresh tokens for the user are revoked.

| Attribute           | Detail                                                                                                                                                          |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `iam` (primary — token generation and validation), `notifications` (event publication for reset email)                                                          |
| **Worker Modules**  | `notifications` (email delivery)                                                                                                                                |
| **System Flow Ref** | `docs/system-design.md §5 — Account Provisioning & Security`                                                                                                    |
| **ADR Refs**        | ADR-027 (Pluggable Notification Architecture), ADR-028 (Zero-Trust Provisioning)                                                                                |
| **DB Tables**       | `users`, `password_reset_tokens`, `refresh_tokens`                                                                                                              |
| **Clients**         | Web                                                                                                                                                             |
| **Key Constraints** | Tokens are single-use; `used_at` set on first valid consumption. Expired tokens purged by the TTL cleanup job every 6 hours (per `docs/database-schema.md §1`). |

### 1.4 `staff-and-organizer-provisioning`

**Description:** Enables `ORGANIZER` users to create `STAFF` accounts and invite additional `ORGANIZER` accounts within their own organization. Following the Zero-Trust provisioning model (ADR-028), the creating organizer cannot set passwords. The system generates a secure temporary password, emails it directly to the new user, and sets `is_temporary_password = true` to force a mandatory reset on first login. All provisioned accounts are scoped to the same `organization_id` as the creating `ORGANIZER`. `STAFF` users created here have no check-in privileges until explicitly assigned to a workshop via `staff-assignment-management`.

| Attribute           | Detail                                                                                                                                        |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `iam` (primary — user creation, organization scoping), `notifications` (event publication for welcome email)                                  |
| **Worker Modules**  | `notifications` (email delivery with temporary password)                                                                                      |
| **System Flow Ref** | `docs/system-design.md §5 — Account Provisioning & Security`                                                                                  |
| **ADR Refs**        | ADR-006 (RBAC + org scoping), ADR-028 (Zero-Trust Provisioning + delegated creation)                                                          |
| **DB Tables**       | `users`, `organizations`                                                                                                                      |
| **Clients**         | Web                                                                                                                                           |
| **Key Constraints** | `ORGANIZER` can only create accounts within `jwt.org`. Cannot create `STUDENT` accounts. `is_temporary_password = true` enforced at creation. |

### 1.5 `staff-assignment-management`

**Description:** Enables `ORGANIZER` users to assign `STAFF` members to specific workshops, granting them the ability to perform QR code scanning and attendance synchronization for those workshops. Assignments are tracked in the `workshop_assignments` bridging table. An `ORGANIZER` can also revoke assignments. `STAFF` members can only check in at workshops they are explicitly assigned to — even within the same organization, an unassigned `STAFF` user receives `403 Forbidden` at the check-in endpoint. Organizers can view the current list of staff assigned to a given workshop.

| Attribute           | Detail                                                                                                                                                               |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `iam` (provides `StaffQueryPort` via `contracts/`), `workshops` (primary — assignment CRUD, validation that target user is STAFF within same org)                    |
| **Worker Modules**  | None                                                                                                                                                                 |
| **System Flow Ref** | `docs/system-design.md §5 — RBAC Model, Explicit Staff Assignment`                                                                                                   |
| **ADR Refs**        | ADR-006 (RBAC), ADR-028 (Explicit assignment)                                                                                                                        |
| **DB Tables**       | `workshop_assignments`, `users`, `workshops`                                                                                                                         |
| **Clients**         | Web                                                                                                                                                                  |
| **Key Constraints** | Unique constraint on `(workshop_id, staff_user_id)`. Validation: target user must be `STAFF` role and share the same `organization_id` as the assigning `ORGANIZER`. |

### 1.6 `user-profile-management`

**Description:** Allows authenticated users to view and update their own profile information (e.g., `full_name`, `phone_number`, `avatar_url`). Password changes are handled separately via a dedicated flow requiring the current password for verification. Role-specific fields (`organization_id`, `role`, `email`) are immutable through this endpoint. `is_active` status management (enabling/disabling accounts) is an organizer-level action scoped to their organization.

| Attribute           | Detail                                                                                                  |
| :------------------ | :------------------------------------------------------------------------------------------------------ |
| **Backend Modules** | `iam` (primary — profile read/update)                                                                   |
| **Worker Modules**  | None                                                                                                    |
| **System Flow Ref** | `docs/system-design.md §5 — RBAC Model`                                                                 |
| **ADR Refs**        | ADR-006 (RBAC)                                                                                          |
| **DB Tables**       | `users`                                                                                                 |
| **Clients**         | Web, Mobile                                                                                             |
| **Key Constraints** | Users can only update their own profile. `email`, `role`, and `organization_id` are strictly immutable. |

## 2. Workshop Lifecycle Management

> **Domain Description:** Covers the entire server-side management of workshops from creation through terminal states. This is the most complex domain in the system, involving strict state machine enforcement (`DRAFT → PUBLISHED → COMPLETED/CANCELED`), multi-tenant ownership checks, optimistic locking for concurrent edits, automated sweeper-based transitions, and the transactional outbox pattern for reliable event fan-out to Elasticsearch and notification workers. Corresponds to the `workshops` Spring Modulith module and the **Workshop Lifecycle Management Flow** in `docs/system-design.md §4`.

### 2.1 `workshop-creation-and-draft-management`

**Description:** Enables authenticated `ORGANIZER` users to create a new workshop in `DRAFT` status. In `DRAFT`, all fields are fully editable with no restrictions. The workshop is not visible to students, is not indexed in Elasticsearch, and generates no external domain events until it is published. Organizers can update (full field set), preview, and delete `DRAFT` workshops. Deletion of `DRAFT` workshops is the only hard-delete permitted in the system (no registered students, no financial records exist yet).

| Attribute           | Detail                                                                                                                                                            |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (primary — CRUD use cases, domain entity, `WorkshopSpeakerRepository`), `iam/contracts` (org + role validation)                                       |
| **Worker Modules**  | None (DRAFT events never externalized)                                                                                                                            |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Lifecycle Management Flow (CREATE OPERATION)`                                                                                |
| **ADR Refs**        | ADR-006 (RBAC + org scoping), ADR-008 (Flyway schema migrations)                                                                                                  |
| **DB Tables**       | `workshops`, `workshop_speakers`, `organizations`                                                                                                                 |
| **Clients**         | Web                                                                                                                                                               |
| **Key Constraints** | `status = DRAFT` on creation. `organization_id` set from `jwt.org`. `version = 0` initialized for optimistic locking. No outbox events emitted for DRAFT changes. |

### 2.2 `workshop-publishing-and-update`

**Description:** Manages the `DRAFT → PUBLISHED` state transition (publishing) and the rules governing updates to a `PUBLISHED` workshop. Publishing triggers a `WorkshopPublishedEvent` into the transactional outbox, causing background workers to index the workshop in Elasticsearch. Once published, core immutable fields (e.g., `price`, `currency`) cannot be changed, and `capacity` cannot be reduced below the current `CONFIRMED + PENDING` registration count. Non-core fields (e.g., `description`, `cover_image_url`) remain editable and emit a `WorkshopUpdatedEvent` for Elasticsearch re-synchronization. Optimistic locking (`@Version`) prevents lost updates from concurrent organizer edits, returning `409 Conflict` on version mismatch.

| Attribute           | Detail                                                                                                                                                                    |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Backend Modules** | `workshops` (primary — state machine, optimistic locking, outbox publication), `shared/events` (outbox infrastructure)                                                    |
| **Worker Modules**  | `search_sync` (consumes `WorkshopPublishedEvent`, `WorkshopUpdatedEvent` to upsert Elasticsearch index)                                                                   |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Lifecycle Management Flow (PUBLISH OPERATION, UPDATE OPERATION)`                                                                     |
| **ADR Refs**        | ADR-006 (ownership validation `jwt.org == workshop.organization_id`), ADR-013 (optimistic locking via `@Version`), ADR-018 (Transactional Outbox)                         |
| **DB Tables**       | `workshops`, `event_publication`                                                                                                                                          |
| **Clients**         | Web                                                                                                                                                                       |
| **Key Constraints** | `409 Conflict` on `@Version` mismatch. `422 Unprocessable Entity` for immutable field changes or invalid state transitions. Only `DRAFT → PUBLISHED` is valid transition. |

### 2.3 `workshop-rescheduling`

**Description:** Allows `ORGANIZER` users to reschedule a `PUBLISHED` workshop to a new future time slot. The reschedule endpoint is explicitly restricted to `PUBLISHED` workshops — `DRAFT`, `COMPLETED`, and `CANCELED` states return `422`. The system validates that the new time range is valid (start < end, both in the future) and performs a PostgreSQL-level resource collision check to ensure the target location and any assigned speakers are not already committed to another workshop in the proposed time window. On success, a `WorkshopRescheduledEvent` is emitted via the outbox, triggering Elasticsearch re-sync and bulk notification to all registered students.

| Attribute           | Detail                                                                                                                                                                |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (primary — reschedule use case, collision check query, outbox), `registrations/contracts` (read registered count for capacity guard)                      |
| **Worker Modules**  | `search_sync` (ES update), `notifications` (bulk email/push to registered students)                                                                                   |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Lifecycle Management Flow (RESCHEDULE OPERATION, ASYNCHRONOUS EVENT FAN-OUT)`                                                    |
| **ADR Refs**        | ADR-006, ADR-013 (optimistic locking), ADR-018 (outbox), ADR-027 (notification fan-out)                                                                               |
| **DB Tables**       | `workshops`, `registrations`, `event_publication`, `in_app_notifications`, `notification_delivery_logs`                                                               |
| **Clients**         | Web                                                                                                                                                                   |
| **Key Constraints** | Collision check queries for overlapping `(starts_at, ends_at)` intervals in `workshops` matching same `location` and speaker `email`. Returns `422` on any collision. |

### 2.4 `workshop-cancellation`

**Description:** Enables `ORGANIZER` users to cancel a `DRAFT` or `PUBLISHED` workshop. Terminal states (`COMPLETED`, `CANCELED`) are immutable and cannot be re-canceled. On cancellation, a `WorkshopCanceledEvent` is written to the transactional outbox, which asynchronously removes the workshop from the Elasticsearch index and triggers bulk notifications to all affected registered students. The Redis capacity counter key for the workshop is cleaned up. Registrations in `PENDING` or `CONFIRMED` state are transitioned to `CANCELED` as a downstream consequence triggered by the event.

| Attribute           | Detail                                                                                                                                  |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (primary — cancellation use case, outbox), `registrations/contracts` (bulk status update triggered by event)                |
| **Worker Modules**  | `search_sync` (remove from ES index), `notifications` (bulk email/push to affected students)                                            |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Lifecycle Management Flow (CANCEL OPERATION, ASYNCHRONOUS EVENT FAN-OUT)`                          |
| **ADR Refs**        | ADR-006, ADR-013, ADR-018 (outbox), ADR-027 (notifications)                                                                             |
| **DB Tables**       | `workshops`, `registrations`, `event_publication`, `in_app_notifications`, `notification_delivery_logs`                                 |
| **Clients**         | Web                                                                                                                                     |
| **Key Constraints** | `422 Unprocessable Entity` for already-terminal workshops. Redis capacity key must be zeroed on cancellation to prevent stale counters. |

### 2.5 `workshop-lifecycle-sweeper`

**Description:** Implements two automated background jobs: (1) **Lifecycle Sweeper** — a cron running hourly that detects `PUBLISHED` workshops whose `ends_at` timestamp has passed and bulk-transitions them to `COMPLETED`, writing `WorkshopCompletedEvent` entries into the transactional outbox for Elasticsearch de-listing. (2) **Capacity Reconciliation Job** — a cron running every 5 minutes that detects and corrects Redis counter drift versus ground-truth database capacity counts (`capacity - COUNT(CONFIRMED + PENDING)`), resetting the Redis counter and emitting a `capacity.drift.detected` metric via Micrometer whenever drift is detected. Neither job has a user-facing endpoint.

| Attribute           | Detail                                                                                                                                                                                                                                                   |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (primary — sweeper scheduled task, outbox bulk insert), `shared/observability` (drift metric emission via Micrometer)                                                                                                                        |
| **Worker Modules**  | `search_sync` (consumes `WorkshopCompletedEvent` to remove from ES)                                                                                                                                                                                      |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Lifecycle Management Flow (AUTOMATED LIFECYCLE SWEEPER)`, `docs/system-design.md §6 — Overbooking Prevention (Redis-DB Capacity Reconciliation Job)`                                                                |
| **ADR Refs**        | ADR-013 (Redis DECR + DB reconciliation), ADR-018 (outbox)                                                                                                                                                                                               |
| **DB Tables**       | `workshops`, `event_publication`                                                                                                                                                                                                                         |
| **Clients**         | None (backend-only)                                                                                                                                                                                                                                      |
| **Key Constraints** | Lifecycle sweeper uses single bulk `UPDATE ... WHERE status = 'PUBLISHED' AND ends_at < NOW()`. Capacity reconciliation: Redis `SET` is unconditional (DB value is always authoritative). Emits `capacity.drift.detected` counter metric per correction. |

## 3. Workshop Discovery & Search

> **Domain Description:** Covers the public-facing workshop catalog and full-text search capabilities. Implements the CQRS read model backed by Elasticsearch and the Stale-While-Revalidate (SWR) cache-aside pattern backed by Redis. The `search` Spring Modulith module serves as the CQRS read-side, while background `search_sync` workers maintain the Elasticsearch index. References the **Workshop Search & Cache-Aside Flow** in `docs/system-design.md §4`.

### 3.1 `workshop-catalog-browsing`

**Description:** Provides authenticated students and unauthenticated public users with paginated browsing of `PUBLISHED` workshops, with filtering by date range, location, and price range (free/paid). Catalog results are served from the PostgreSQL replica under eventual consistency semantics (non-critical browsing path per ADR-015). Individual workshop detail pages include full metadata: title, description, speakers (bios, avatars, LinkedIn), schedule, location, capacity, available seats (derived from Redis atomic counter with DB fallback on cache miss), and price.

| Attribute           | Detail                                                                                                                                                                                                           |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (primary — catalog and detail read use cases), `search` (listing queries), `shared/config` (Redis)                                                                                                   |
| **Worker Modules**  | None                                                                                                                                                                                                             |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Search & Cache-Aside Flow`                                                                                                                                                  |
| **ADR Refs**        | ADR-012 (Elasticsearch CQRS), ADR-015 (Replica routing for browsing), ADR-016 (SWR cache-aside)                                                                                                                  |
| **DB Tables**       | `workshops`, `workshop_speakers` (read from replica)                                                                                                                                                             |
| **Clients**         | Web                                                                                                                                                                                                              |
| **Key Constraints** | Cursor-based pagination (keyset) per `docs/api-standards.md`. Default page size 20, hard cap 100. Available seats from Redis counter; fallback to `capacity - COUNT(CONFIRMED + PENDING)` from DB on cache miss. |

### 3.2 `workshop-full-text-search`

**Description:** Provides a dedicated full-text search endpoint (`GET /api/v1/workshops/search?q={keyword}`) that queries the Elasticsearch CQRS read model. Implements the full Stale-While-Revalidate (SWR) cache-aside strategy with dual TTLs (soft TTL: ~5 min, hard TTL: ~24 h). Stale cache hits return immediately and trigger a non-blocking async refresh via Redis `tryLock`. Cache misses perform a synchronous Elasticsearch query wrapped in a Resilience4j Circuit Breaker — on circuit open or ES failure, the endpoint returns a safe empty result set (`200 OK`) rather than a `5xx` error. Telemetry instruments cache hit/stale/miss ratios, lock acquisition rate, ES response latency, and circuit breaker state transitions.

| Attribute           | Detail                                                                                                                                                                                       |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `search` (primary — SWR cache logic, ES query, circuit breaker), `shared/config` (Redis tryLock, Resilience4j bean), `shared/observability` (Micrometer)                                     |
| **Worker Modules**  | `search_sync` (maintains ES index from workshop lifecycle events)                                                                                                                            |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Search & Cache-Aside Flow (complete SWR sequence diagram)`                                                                                              |
| **ADR Refs**        | ADR-012 (Elasticsearch CQRS), ADR-016 (SWR dual TTL + non-blocking tryLock), ADR-020 (Resilience4j circuit breaker)                                                                          |
| **DB Tables**       | None (reads from Elasticsearch only; Redis for cache layer)                                                                                                                                  |
| **Clients**         | Web                                                                                                                                                                                          |
| **Key Constraints** | Non-blocking `tryLock` — only the lock holder queries ES; others no-op. Circuit breaker wraps ES call. Cache key: `search:q:{keyword}`. Always returns `200 OK` — never `503` on ES failure. |

## 4. Workshop Registration

> **Domain Description:** Covers the high-concurrency workshop registration lifecycle from initial request through confirmation or failure, including distributed rate limiting, atomic capacity enforcement, schedule overlap detection, idempotency, QR code generation, and automated reconciliation. This is the most performance-critical domain in the system, designed to handle 12,000+ concurrent users within a 10-minute window. References the **Workshop Registration Flow** in `docs/system-design.md §4` and the protection mechanisms in `§6`.

### 4.1 `free-workshop-registration`

**Description:** Handles the end-to-end registration flow for free (`price = 0`) workshops. The flow: (1) enforces distributed Token Bucket rate limiting (Bucket4j + Redis); (2) atomically inserts the idempotency key (PENDING) — concurrent retries return the cached response immediately; (3) detects schedule overlap (existing `CONFIRMED`/`PENDING` registrations for the same student in the same time window via primary DB query); (4) performs atomic Redis `DECR` capacity pre-check (lock-free overbooking prevention, returns `409` if counter goes negative); (5) inserts the registration with `status = CONFIRMED` and `qr_payload`/`qr_signature`/`qr_issued_at` atomically in a single database transaction with the outbox event; (6) updates the idempotency key to `COMPLETED`. Returns `201 Created` with the QR payload. The `Idempotency-Key` header (UUIDv4) is mandatory.

| Attribute           | Detail                                                                                                                                                                                                                                                                                                                                                   |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `registrations` (primary — registration use case, QR generation), `shared/idempotency` (key lifecycle), `workshops/contracts` (capacity + status read), `iam/contracts` (student identity check)                                                                                                                                                         |
| **Worker Modules**  | `notifications` (confirmation email/push triggered by `WorkshopRegisteredEvent` from outbox)                                                                                                                                                                                                                                                             |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Registration Flow (Free workshop branch)`, `§6 — Traffic Spike Management`, `§6 — Overbooking Prevention`                                                                                                                                                                                                           |
| **ADR Refs**        | ADR-013 (Redis DECR lock-free + DB unique index defense-in-depth), ADR-014 (Idempotency keys), ADR-015 (Primary DB routing), ADR-018 (Transactional Outbox), ADR-019 (Bucket4j Token Bucket)                                                                                                                                                             |
| **DB Tables**       | `registrations`, `idempotency_keys`, `event_publication`                                                                                                                                                                                                                                                                                                 |
| **Clients**         | Web                                                                                                                                                                                                                                                                                                                                                      |
| **Key Constraints** | Rate limit enforced first (`429`). Redis `DECR` before any DB transaction (`409` on capacity = 0). DB partial unique index on `(workshop_id, student_user_id) WHERE status IN ('PENDING','CONFIRMED')` as last-resort guard — must be caught and translated to `409` not `500`. QR fields populated only on `CONFIRMED` transition — never at `PENDING`. |

### 4.2 `paid-workshop-registration`

**Description:** Handles the registration flow for paid (`price > 0`) workshops. Follows the same rate limiting and idempotency steps as free registration, but after the Redis capacity is reserved, inserts the registration with `status = PENDING` and calls the Stripe payment gateway (wrapped in a Resilience4j Circuit Breaker with the same `Idempotency-Key` forwarded to Stripe). On synchronous payment approval, the registration transitions `PENDING → CONFIRMED` and QR is generated in the same atomic transaction. On payment timeout or circuit-breaker trip, the registration is marked `FAILED`, Redis capacity is restored via `INCR` (compensating action), and `503 Service Unavailable` is returned. The webhook-driven async path (see `payment-webhook-handling`) is the primary confirmation path; the synchronous response handles only immediate API-level approvals.

| Attribute           | Detail                                                                                                                                                                                                                                     |
| :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `registrations` (primary — registration use case), `payments` (Stripe client, payment intent creation), `shared/idempotency`, `workshops/contracts`, `iam/contracts`                                                                       |
| **Worker Modules**  | `notifications` (triggered on `WorkshopRegisteredEvent` via outbox)                                                                                                                                                                        |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Registration Flow (Paid workshop branch)`, `§6 — Third-Party Service Resilience`, `§6 — Transaction Idempotency`                                                                                      |
| **ADR Refs**        | ADR-013 (Redis DECR), ADR-014 (Idempotency), ADR-019 (Rate limiting), ADR-020 (Circuit breaker), ADR-021 (ChaosMockAdapter), ADR-022 (Webhook as primary path)                                                                             |
| **DB Tables**       | `registrations`, `payments`, `idempotency_keys`, `event_publication`                                                                                                                                                                       |
| **Clients**         | Web                                                                                                                                                                                                                                        |
| **Key Constraints** | `status = PENDING` on initial insert. QR fields **never** populated at `PENDING` — only on `→ CONFIRMED` transition. Resilience4j circuit breaker wraps Stripe call. On circuit open: `FAILED` + Redis `INCR` compensating action + `503`. |

### 4.3 `registration-reconciliation-and-cancellation`

**Description:** Covers two complementary concerns: (1) **Dual-Case Reconciliation Sweeper** — a background cron running every 30 minutes that resolves orphaned `PENDING` registrations by querying the payment gateway. If payment succeeded (delayed webhook recovery), the registration is confirmed and QR generated. If payment failed or was never initiated, the registration is marked `FAILED` and Redis capacity is restored via `INCR`. (2) **Student-initiated cancellation** — allows a `STUDENT` to cancel their own `CONFIRMED` registration before the workshop starts, transitioning it to `CANCELED` and restoring the Redis capacity counter. An `ORGANIZER` can also force-cancel a student's registration from the admin panel (scoped to their org's workshops).

| Attribute           | Detail                                                                                                                                                                                        |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `registrations` (primary — sweeper + cancellation use case), `payments/contracts` (payment status query), `shared/idempotency`                                                                |
| **Worker Modules**  | `notifications` (cancellation confirmation to student)                                                                                                                                        |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Registration Flow (Dual-Case Reconciliation Sweeper loop)`, `§6 — Transaction Idempotency`                                                               |
| **ADR Refs**        | ADR-014 (sweeper as idempotency fallback, Delayed Webhook Recovery), ADR-022 (sweeper as fallback-only, 30-min extended timeout)                                                              |
| **DB Tables**       | `registrations`, `payments`, `idempotency_keys`                                                                                                                                               |
| **Clients**         | Web                                                                                                                                                                                           |
| **Key Constraints** | Sweeper queries Stripe only for `PENDING` registrations older than 30 minutes (per ADR-022). Redis `INCR` on every `FAILED`/`CANCELED` transition. Student can only cancel own registrations. |

## 5. Payment Processing

> **Domain Description:** Covers the Stripe payment gateway integration and the webhook-driven asynchronous payment confirmation flow. This domain is responsible for financial data integrity and exactly-once payment state transitions. It corresponds to the `payments` Spring Modulith module and references the **Payment Webhook Handling Flow** in `docs/system-design.md §4`. QR generation on payment confirmation is owned by the `registrations` module.

### 5.1 `payment-webhook-handling`

**Description:** Implements a dedicated, publicly accessible (unauthenticated) `POST /api/v1/payments/webhook` endpoint that receives asynchronous payment outcome events from the Stripe gateway (e.g., `payment_intent.succeeded`, `payment_intent.payment_failed`). Authentication is performed exclusively via **HMAC-SHA256 webhook signature verification** using the Stripe signing secret stored in environment variables — never JWT. For `payment_intent.succeeded`, the handler atomically updates `payments` to `COMPLETED`, transitions `registrations` to `CONFIRMED`, sets `qr_payload`/`qr_signature`/`qr_issued_at`, inserts a `WorkshopRegisteredEvent` into the outbox, and updates the idempotency key to `COMPLETED`. For `payment_intent.payment_failed`, both records are set to `FAILED` and Redis capacity is restored. The endpoint **always returns `200 OK`** to prevent gateway retry floods; internal errors are logged to DLQ.

| Attribute           | Detail                                                                                                                                                                                                                              |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `payments` (primary — webhook handler, HMAC signature verification, state transitions), `registrations/contracts` (QR generation, status update), `shared/idempotency`                                                              |
| **Worker Modules**  | `notifications` (triggered by `WorkshopRegisteredEvent` for confirmation delivery)                                                                                                                                                  |
| **System Flow Ref** | `docs/system-design.md §4 — Payment Webhook Handling Flow (complete sequence diagram)`                                                                                                                                              |
| **ADR Refs**        | ADR-014 (idempotency on `provider_payment_id` UNIQUE constraint), ADR-022 (webhook as primary path, HMAC verification, always return `200 OK`)                                                                                      |
| **DB Tables**       | `payments`, `registrations`, `idempotency_keys`, `event_publication`                                                                                                                                                                |
| **Clients**         | None (called by Stripe, not by user clients)                                                                                                                                                                                        |
| **Key Constraints** | No JWT — HMAC signature verification only. Idempotent by `provider_payment_id` UNIQUE constraint: already-terminal records return `200 OK` immediately without reprocessing. Signing secret: env var only, never in source control. |

### 5.2 `payment-history-and-status`

**Description:** Provides authenticated users with read-only access to their payment history and individual payment status. `STUDENT` users can view their own payment records (amount, status, provider reference, timestamps) linked to their registrations. `ORGANIZER` users can view payment summaries across all registrations for workshops within their organization for operational reporting. All payment state transitions are exclusively handled by `payment-webhook-handling` and `registration-reconciliation-and-cancellation`.

| Attribute           | Detail                                                                                                                                                                  |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `payments` (primary — payment query use cases), `registrations/contracts`                                                                                               |
| **Worker Modules**  | None                                                                                                                                                                    |
| **System Flow Ref** | N/A (read-only reporting path)                                                                                                                                          |
| **ADR Refs**        | ADR-006 (RBAC), ADR-015 (Replica routing for non-critical reads)                                                                                                        |
| **DB Tables**       | `payments`, `registrations` (read from replica for non-critical queries)                                                                                                |
| **Clients**         | Web                                                                                                                                                                     |
| **Key Constraints** | `STUDENT` can only view own payments. `ORGANIZER` scoped to `jwt.org` workshops. Cursor-based pagination. Read from replica acceptable (non-critical path per ADR-015). |

## 6. On-Site Check-in & Attendance

> **Domain Description:** Covers the mobile-first offline check-in experience for `STAFF` users and the server-side attendance reconciliation. This domain operates under a **Zero-Trust offline model**: the mobile app verifies QR signatures locally using a cached RSA public key (PKI), queues scans in SQLite, and synchronizes batches to the server when connectivity is restored. Server-side reconciliation uses workshop-scoped bulk upserts to prevent N+1 queries and database-level unique constraints to deterministically handle concurrent double-scans. Corresponds to the `attendances` Spring Modulith module (backend) and the React Native Mobile App. References the **Offline Check-in & Synchronization Flow** in `docs/system-design.md §4` and **Offline PKI & Cryptography** in `§5`.

### 6.1 `mobile-offline-checkin`

**Description:** Implements the complete React Native Mobile App experience for Check-in Staff: (1) **Online data download** — on login, the app fetches and caches assigned workshops and the current RSA public key into local SQLite (`mobile_assigned_workshops`, `mobile_public_keys`). (2) **Offline QR scanning** — the staff member selects an assigned workshop, scans student QR codes; the app verifies the RSA signature offline using the cached public key, generates a `sync_id` (UUIDv4 on device), and persists the record to `mobile_sync_queue` with `sync_status = PENDING`. Forged or invalid QR codes are dropped immediately with a local "Invalid Ticket" error and never written to the queue. (3) **Sync on connectivity** — when connectivity returns, the app POSTs the batch to the server and updates each SQLite record's `sync_status` from the `207 Multi-Status` response.

| Attribute                     | Detail                                                                                                                                                                                                                        |
| :---------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules**           | `attendances` (server-side sync endpoint), `iam/contracts` (staff assignment validation), `shared/security` (public key distribution endpoint on login)                                                                       |
| **Worker Modules**            | None                                                                                                                                                                                                                          |
| **System Flow Ref**           | `docs/system-design.md §4 — Offline Check-in & Synchronization Flow`, `§5 — Offline PKI & Cryptography`                                                                                                                       |
| **ADR Refs**                  | ADR-023 (JSI-based SQLite for mobile), ADR-024 (server-authoritative reconciliation, 207 Multi-Status), ADR-029 (PKI QR signing + offline verification)                                                                       |
| **DB Tables (Mobile SQLite)** | `mobile_assigned_workshops`, `mobile_public_keys`, `mobile_sync_queue`                                                                                                                                                        |
| **Clients**                   | Mobile only                                                                                                                                                                                                                   |
| **Key Constraints**           | QR signature verified locally before any SQLite write. `sync_id` is device-generated UUIDv4. Sync must be retried on network failure without data loss (SQLite durability). Batch size bounded to prevent oversized payloads. |

### 6.2 `attendance-sync-reconciliation`

**Description:** Implements the server-side `POST /api/v1/workshops/{workshopId}/attendances/sync` endpoint that authoritatively reconciles batched attendance events from the mobile app. The endpoint: (1) validates the `STAFF` JWT and confirms the caller is assigned to `{workshopId}` via `workshop_assignments`; (2) extracts all `registration_id` values from the batch payload and performs a single **workshop-scoped bulk fetch** of existing attendance records from the primary DB; (3) classifies each entry in-memory as `SUCCESS` (new scan), `ALREADY_USED` (same `registration_id`, different `sync_id`), or `INVALID_TICKET` (registration not found or not `CONFIRMED` for this workshop); (4) performs a **single bulk upsert** using DB-level unique constraints on `(workshop_id, registration_id)` and `(workshop_id, sync_id)` to resolve microsecond races; (5) returns `207 Multi-Status` with per-entry `{ sync_id, status }` objects.

| Attribute           | Detail                                                                                                                                                                                                                                            |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Backend Modules** | `attendances` (primary — sync endpoint, in-memory classification, bulk upsert), `iam/contracts` (staff role + assignment validation), `registrations/contracts` (ticket validity check)                                                           |
| **Worker Modules**  | None                                                                                                                                                                                                                                              |
| **System Flow Ref** | `docs/system-design.md §4 — Offline Check-in & Synchronization Flow (Authorized branch: bulk fetch → classify → bulk upsert → 207 response)`                                                                                                      |
| **ADR Refs**        | ADR-024 (server-authoritative, bulk ops, 207 Multi-Status response), ADR-015 (primary DB routing for all sync reads and writes)                                                                                                                   |
| **DB Tables**       | `attendances`, `workshop_assignments`, `registrations` (primary DB only)                                                                                                                                                                          |
| **Clients**         | Mobile (caller), Web (organizer views attendance reports)                                                                                                                                                                                         |
| **Key Constraints** | All reads and writes strictly routed to primary DB (per ADR-015 — sync is a check-then-act operation). N+1 query pattern strictly forbidden: single bulk fetch + single bulk upsert only. `207` always returned; never abort on partial failures. |

## 7. Notification System

> **Domain Description:** Covers the delivery of user-facing notifications across in-app (web/mobile) and email channels. This domain follows the pluggable provider abstraction (ADR-027) with strategy-based adapters, ensuring new channels can be added without modifying core business logic. Corresponds to the `notifications` Spring Modulith module (backend REST API for reading notifications) and the `notifications` background worker module (async delivery fan-out). Delivery is always asynchronous — triggered by domain events from the transactional outbox via RabbitMQ.

### 7.1 `notification-delivery-pipeline`

**Description:** Implements the asynchronous background worker pipeline that consumes domain events from RabbitMQ and fans out notifications to registered channels (Email and In-App). The worker listens for: `WorkshopRegisteredEvent` (registration confirmation to student), `WorkshopRescheduledEvent` (schedule change alert to all registered students), and `WorkshopCanceledEvent` (cancellation alert to all registered students). For each event, the worker: (1) resolves the target user list; (2) renders the notification payload from the `event_key` and structured template; (3) persists an `in_app_notifications` record per recipient; (4) dispatches the email via the Email Provider adapter; (5) logs delivery status in `notification_delivery_logs`. Failed deliveries retry with exponential backoff; persistent failures route to DLQ.

| Attribute           | Detail                                                                                                                                                                                                                                                 |
| :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `notifications` (internal event listener + `in_app_notifications` persistence)                                                                                                                                                                         |
| **Worker Modules**  | `notifications` (primary — event consumer, email dispatch, delivery logging, retry + DLQ routing)                                                                                                                                                      |
| **System Flow Ref** | `docs/system-design.md §4 — Workshop Registration Flow (async notification par block)`, `§4 — Workshop Lifecycle Management Flow (ASYNCHRONOUS EVENT FAN-OUT)`                                                                                         |
| **ADR Refs**        | ADR-017 (RabbitMQ broker), ADR-018 (Transactional Outbox as event source), ADR-027 (Pluggable Notification Architecture with provider adapters)                                                                                                        |
| **DB Tables**       | `in_app_notifications`, `notification_delivery_logs`                                                                                                                                                                                                   |
| **Clients**         | None (backend worker, no direct client interaction)                                                                                                                                                                                                    |
| **Key Constraints** | Email provider adapter must implement a common `NotificationPort` interface for pluggability. Retry with exponential backoff. DLQ for max-retry exhaustion. `notification_delivery_logs` records written even on failed deliveries for audit purposes. |

### 7.2 `in-app-notification-management`

**Description:** Provides the REST API for authenticated users to manage their in-app notifications: `GET /api/v1/notifications` (paginated list with unread count, optimized by composite index on `(user_id, is_read)`), `PATCH /api/v1/notifications/{id}/read` (mark single notification as read), and `PATCH /api/v1/notifications/read-all` (bulk mark-as-read). This feature provides the data contract for the web and mobile notification bell/badge UI elements driven by the `in_app_notifications.is_read` field.

| Attribute           | Detail                                                                                                                                                                                            |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Backend Modules** | `notifications` (primary — notification read/mark-read use cases and REST controllers)                                                                                                            |
| **Worker Modules**  | None                                                                                                                                                                                              |
| **System Flow Ref** | N/A (read/update path for user-facing notification state)                                                                                                                                         |
| **ADR Refs**        | ADR-006 (RBAC — users see only own notifications), ADR-015 (replica routing acceptable for reads)                                                                                                 |
| **DB Tables**       | `in_app_notifications`                                                                                                                                                                            |
| **Clients**         | Web, Mobile                                                                                                                                                                                       |
| **Key Constraints** | `user_id` filter mandatory on every query — users can never access other users' notifications. Cursor-based pagination. Composite index `(user_id, is_read)` must be used for unread count query. |

## 8. AI-Powered Brochure Summarization

> **Domain Description:** Covers the complete pipeline for uploading workshop PDF brochures and asynchronously generating AI-written summaries. This domain is fully decoupled from synchronous API flows via the transactional outbox and RabbitMQ — the core API returns `202 Accepted` immediately, and all heavy processing happens in the `ai_processing` background worker module. Corresponds to the `workshops` module (brochure upload endpoints and job-status polling) and the `ai_processing` worker module (full hexagonal pipeline). References the **Async AI Summarization Flow** in `docs/system-design.md §4`.

### 8.1 `ai-brochure-summarization`

**Description:** Implements the full PDF brochure upload-to-summary pipeline: **(1) Upload** — `ORGANIZER` uploads a PDF via `POST /api/v1/workshops/{id}/brochure` (multipart); the backend validates MIME type and file size, stores the PDF in MinIO/S3, creates a `workshop_brochures` record, creates an `ai_summary_jobs` record with `status = PROCESSING`, and writes a `BrochureSummarizationRequestedEvent` to the transactional outbox — returning `202 Accepted` with the job ID. **(2) Worker Processing** — the `ai_processing` worker fetches the PDF from S3, extracts and chunks text locally using PDFBox (to bound payload size and prevent AI token-limit errors), and submits chunks to the external AI Summarization Service. On success, updates `ai_summary_jobs` to `COMPLETED` with `result_text`. On transient errors (HTTP 429, 5xx, timeout), retries with broker-backed exponential backoff. On max-retry exhaustion, marks the job `FAILED` and routes to DLQ (prevents zombie `PROCESSING` states). **(3) Status polling** — Organizer polls `GET /api/v1/workshops/{id}/brochure/summary-job` for job status and retrieves the summary on completion.

| Attribute           | Detail                                                                                                                                                                                                                                                           |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `workshops` (brochure upload endpoint, job-status endpoint, outbox publication), `shared/config` (MinIO/S3 client)                                                                                                                                               |
| **Worker Modules**  | `ai_processing` (primary — PDF fetch from S3, PDFBox text extraction, chunking, AI API call, DB result persistence, retry + DLQ handling)                                                                                                                        |
| **System Flow Ref** | `docs/system-design.md §4 — Async AI Summarization Flow (complete sequence diagram)`                                                                                                                                                                             |
| **ADR Refs**        | ADR-011 (MinIO object storage), ADR-017 (RabbitMQ), ADR-018 (Transactional Outbox), ADR-026 (async worker pipeline + PDFBox chunking)                                                                                                                            |
| **DB Tables**       | `workshop_brochures`, `ai_summary_jobs`, `event_publication`                                                                                                                                                                                                     |
| **Clients**         | Web                                                                                                                                                                                                                                                              |
| **Key Constraints** | Only one active brochure per workshop (partial unique index on `workshop_id WHERE is_active = true`). PDF size validation on upload. Worker chunks text before AI API call (ADR-026). Job status transitions driven exclusively by worker — never by web client. |

## 9. Student Data Synchronization

> **Domain Description:** Covers the nightly batch ingestion of student data from the legacy Student Management System via CSV files dropped into MinIO/S3. This domain populates the `student_directory` table used by `user-registration-and-identity-verification` to verify student identity during account creation. The pipeline is implemented exclusively in the `batch_ingestion` background worker module using Spring Batch with fault-tolerant streaming semantics. References the **Nightly Legacy CSV Ingestion Flow** in `docs/system-design.md §4`.

### 9.1 `nightly-student-csv-ingestion`

**Description:** Implements the complete nightly Spring Batch pipeline for ingesting student CSV exports from S3: **(1) Scheduler** — triggers the Spring Batch job nightly via a cron expression. **(2) File-level validation** — validates CSV headers, required columns (`student_number`, `email`, `full_name`, `faculty`), and file size before streaming; aborts immediately and publishes an alert to RabbitMQ on validation failure. **(3) Streaming** — reads the CSV from S3 via `Object InputStream` to maintain a flat memory footprint, preventing OOM on large files. **(4) ItemReader/Processor/Writer** — `ItemReader` streams rows; `ItemProcessor` validates fields and performs intra-chunk deduplication; invalid rows are appended to a local `/tmp/rejects.csv`; valid rows are committed in chunks via bulk upsert to `student_directory` using composite upsert keys `(organization_id, student_number)` and `(organization_id, email)`. Chunk commits update the `JobRepository` for restartability. **(5) Post-job** — reject file is uploaded to S3; original CSV is archived; a job summary (including S3 reject file link) is published to RabbitMQ for admin notification.

| Attribute           | Detail                                                                                                                                                                                                                                                                                                                                          |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | None (entirely worker-side)                                                                                                                                                                                                                                                                                                                     |
| **Worker Modules**  | `batch_ingestion` (primary — Spring Batch job: `ItemReader`, `ItemProcessor`, `ItemWriter`, local reject staging, S3 archiving, admin notification pub)                                                                                                                                                                                         |
| **System Flow Ref** | `docs/system-design.md §4 — Nightly Legacy CSV Ingestion Flow (complete flowchart)`                                                                                                                                                                                                                                                             |
| **ADR Refs**        | ADR-011 (MinIO S3 drop bucket), ADR-017 (RabbitMQ for job summary notification), ADR-025 (Spring Batch streaming + chunk commits + reject staging pattern)                                                                                                                                                                                      |
| **DB Tables**       | `student_directory` (writes to primary), `batch_*` (Spring Batch `JobRepository` tables)                                                                                                                                                                                                                                                        |
| **Clients**         | None (backend-only scheduled job)                                                                                                                                                                                                                                                                                                               |
| **Key Constraints** | CSV must NOT contain `organization_id` or `status` — injected by the batch job (org derived from tenant context, status defaults to `ACTIVE`). Streaming prevents OOM. Chunk-level restartability via `JobRepository` (restart resumes at chunk N+1 after crash). A bad row never aborts the entire job — it is quarantined to the reject file. |

## 10. System Reliability & Infrastructure

> **Domain Description:** Covers the cross-cutting infrastructure features that enable the platform to meet its non-functional requirements: rate limiting to absorb traffic spikes, circuit breakers to isolate third-party failures, observability to detect and diagnose incidents, and the infrastructure bootstrap that seeds initial data and configures the runtime environment. These features are primarily implemented in `shared/` packages within the Core Backend and the Docker `infra/` configuration, rather than in domain modules.

### 10.1 `distributed-rate-limiting`

**Description:** Implements the application-level distributed Token Bucket rate limiter using **Bucket4j + Redis** in the Core Backend Monolith's HTTP filter layer. Each authenticated user is assigned a bucket keyed by `jwt.sub`; unauthenticated endpoints use IP as the bucket key. Registration endpoints have a tighter refill rate than browsing endpoints. When a bucket is exhausted, the filter returns `429 Too Many Requests` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` response headers (per `docs/api-standards.md`). **Fail-open** mode when Redis is unavailable: the filter skips bucket evaluation, allows the request through, logs the event, and emits a `rate_limiter.redis_unavailable` Micrometer counter metric for alerting (per ADR-010). Nginx acts as a pure forwarding proxy and performs no rate limiting.

| Attribute           | Detail                                                                                                                                                                                                                                                           |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `shared/web` (primary — HTTP filter interceptor), `shared/config` (Bucket4j + Redis bean configuration), `shared/observability` (metrics)                                                                                                                        |
| **Worker Modules**  | None                                                                                                                                                                                                                                                             |
| **System Flow Ref** | `docs/system-design.md §6 — Traffic Spike Management (Token Bucket sequence diagram)`, `§4 — Workshop Registration Flow (rate limiting step)`                                                                                                                    |
| **ADR Refs**        | ADR-010 (Redis fail-open for rate limiting), ADR-019 (Token Bucket algorithm, per-user buckets, backend filter placement — Nginx excluded)                                                                                                                       |
| **DB Tables**       | None (Redis-only state)                                                                                                                                                                                                                                          |
| **Clients**         | All (transparent middleware)                                                                                                                                                                                                                                     |
| **Key Constraints** | Nginx must NOT be configured with rate limiting rules. Per-user bucket keyed by `jwt.sub`. Fail-open must emit `rate_limiter.redis_unavailable` counter metric and log the decision. `X-RateLimit-*` headers must be returned on all bucket-protected endpoints. |

### 10.2 `circuit-breaker-and-resilience`

**Description:** Implements Resilience4j Circuit Breakers around all external service integrations: the Stripe payment gateway (in `payments` module), the AI Summarization Service (in `ai_processing` worker), and the Email Notification Provider (in `notifications` worker). Each circuit breaker independently tracks failure rate and slow-call rate, transitioning through `CLOSED → OPEN → HALF_OPEN → CLOSED` states. When a circuit is `OPEN`, calls fail fast with pre-defined fallbacks (e.g., `503` for payment, DLQ routing for notifications, `FAILED` job state for AI). Includes the **ChaosMockAdapter** for Stripe — a `dev`/`test`-profile-only adapter that injects configurable latency, timeouts, and error rates to validate circuit breaker thresholds and idempotency behavior without relying on Stripe sandbox outages.

| Attribute           | Detail                                                                                                                                                                                                                      |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `payments` (Stripe circuit breaker), `shared/config` (Resilience4j bean configuration)                                                                                                                                      |
| **Worker Modules**  | `ai_processing` (AI Service circuit breaker), `notifications` (Email Provider circuit breaker)                                                                                                                              |
| **System Flow Ref** | `docs/system-design.md §6 — Third-Party Service Resilience (circuit breaker state diagram)`, `§4 — Workshop Registration Flow (payment timeout + circuit breaker branch)`                                                   |
| **ADR Refs**        | ADR-020 (Resilience4j Closed/Open/Half-Open), ADR-021 (ChaosMockAdapter for controlled failure injection)                                                                                                                   |
| **DB Tables**       | None (circuit state is in-memory / Resilience4j-managed)                                                                                                                                                                    |
| **Clients**         | None (transparent middleware)                                                                                                                                                                                               |
| **Key Constraints** | `ChaosMockAdapter` must only activate under `dev` or `test` Spring profile — never `prod`. Resilience4j state transitions (`CLOSED/OPEN/HALF_OPEN`) must be emitted as Micrometer metrics for Grafana dashboard visibility. |

### 10.3 `system-observability-and-telemetry`

**Description:** Implements the complete observability stack for the Core Backend and Background Workers. Includes: **Micrometer** metric instrumentation for JVM health, Tomcat thread pool saturation, HikariCP connection pool, Redis/Bucket4j rate limit drop rate, Resilience4j circuit breaker states, RabbitMQ queue depth + DLQ signals, and `capacity.drift.detected` counter. **Prometheus** pull-based scraping via `/actuator/prometheus` on both backend and worker instances. **Grafana** dashboards provisioned as code (IaC) covering all six dashboard JSON templates in `infra/grafana/dashboards/`: JVM + Spring Boot, HikariCP, Redis/Bucket4j, RabbitMQ, Resilience4j circuit breaker, and MinIO storage health. Also covers the structured JSON request logging filter (described in `docs/api-standards.md`) with `traceId`, `correlationId`, `processedBy`, and full MDC field emission.

| Attribute           | Detail                                                                                                                                                                                                                                                                                                                                                                                 |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `shared/observability` (primary — Micrometer metric emission, request logging filter, traceId MDC propagation), `shared/config` (Actuator + Prometheus exposure)                                                                                                                                                                                                                       |
| **Worker Modules**  | `shared/observability` (worker-side Micrometer metric emission)                                                                                                                                                                                                                                                                                                                        |
| **System Flow Ref** | `docs/system-design.md §6 — System Telemetry & Core Metrics to Track`                                                                                                                                                                                                                                                                                                                  |
| **ADR Refs**        | ADR-030 (Prometheus + Grafana stack, Micrometer instrumentation, pull-model rationale)                                                                                                                                                                                                                                                                                                 |
| **DB Tables**       | None                                                                                                                                                                                                                                                                                                                                                                                   |
| **Clients**         | None (infrastructure dashboards for DevOps)                                                                                                                                                                                                                                                                                                                                            |
| **Key Constraints** | The following metrics are **mandatory** per `docs/system-design.md §6`: HikariCP active connections + acquisition latency, `429` drop rate (Bucket4j), `409` conflict frequency (Redis + DB optimistic locking), Resilience4j state transitions, RabbitMQ queue depth + processing lag. All 6 Grafana dashboard JSON files must be provisioned via IaC in `infra/grafana/dashboards/`. |

### 10.4 `infrastructure-bootstrap-and-seed-data`

**Description:** Implements the idempotent startup bootstrap process and ongoing maintenance jobs: **(1) Organization & Root Organizer Seeding** — a Spring Boot `ApplicationRunner` reads root organization definitions and initial `ORGANIZER` accounts from JSON arrays in environment variables (`.env`), checks the database for existing records, and creates only missing entries (idempotent — safe on every restart). **(2) RSA Key Pair Setup** — `private.pem` and `public.pem` files in `resources/security/` are loaded at startup for JWT signing and JWKS endpoint exposure. **(3) Database Migration** — Flyway applies `V1__init_schema.sql` and all subsequent migrations strictly on startup, before any application beans initialize. **(4) TTL Cleanup Scheduler** — a scheduled job runs every 6 hours to purge expired records from `idempotency_keys`, `refresh_tokens`, and `password_reset_tokens` to prevent table bloat (per `docs/database-schema.md §1`).

| Attribute           | Detail                                                                                                                                                                                                                                                               |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Backend Modules** | `shared/config` (primary — `ApplicationRunner` seeding, TTL cleanup scheduler, health checks), `iam` (org + root organizer entity creation), `shared/security` (RSA key loading, JWKS endpoint)                                                                      |
| **Worker Modules**  | None                                                                                                                                                                                                                                                                 |
| **System Flow Ref** | `docs/system-design.md §5 — Account Provisioning & Security (Bootstrapping)`                                                                                                                                                                                         |
| **ADR Refs**        | ADR-008 (Flyway for DDL migrations + environment-driven seed data for idempotency), ADR-005 (RSA key pair, JWKS endpoint exposure)                                                                                                                                   |
| **DB Tables**       | `organizations`, `users`, `idempotency_keys`, `refresh_tokens`, `password_reset_tokens`                                                                                                                                                                              |
| **Clients**         | None (startup process + scheduled background task)                                                                                                                                                                                                                   |
| **Key Constraints** | Seeding must be **idempotent** — running on any restart must not create duplicate records. Flyway must run before `ApplicationRunner`. TTL cleanup must use `DELETE WHERE expires_at < NOW()` in bounded batches to avoid long-running transactions and table locks. |

## Appendix A: Feature Dependency Map

Shows which features must exist before another can be built or meaningfully tested end-to-end. Informational for `/plan` ordering — formal task ordering is determined per feature during the `/plan` phase.

```
infrastructure-bootstrap-and-seed-data
    └── authentication-and-session-management
            ├── user-registration-and-identity-verification
            ├──password-reset
            ├── staff-and-organizer-provisioning
            │       └── staff-assignment-management
            └── user-profile-management

nightly-student-csv-ingestion           (populates student_directory for identity verification)
    └── user-registration-and-identity-verification

distributed-rate-limiting               (cross-cutting — must be in place before registration endpoints)
circuit-breaker-and-resilience          (cross-cutting — must be in place before paid registration)
system-observability-and-telemetry      (cross-cutting — deploy early alongside other features)

workshop-creation-and-draft-management
    └── workshop-publishing-and-update
            ├── workshop-rescheduling
            ├── workshop-cancellation
            ├── workshop-lifecycle-sweeper
            ├── workshop-catalog-browsing
            └── workshop-full-text-search

free-workshop-registration              (depends on: auth, workshop published, rate limiting)
paid-workshop-registration              (depends on: free-registration, circuit-breaker)
    ├── payment-webhook-handling
    ├── registration-reconciliation-and-cancellation
    └── payment-history-and-status

notification-delivery-pipeline          (depends on: registration events + workshop lifecycle events)
    └── in-app-notification-management

ai-brochure-summarization               (depends on: workshop created, MinIO configured)

mobile-offline-checkin                  (depends on: auth on Mobile, staff-assignment-management)
    └── attendance-sync-reconciliation
```

## Appendix B: Module-to-Feature Cross-Index

| Module                        | Features Touched                                                                                                                                                                                                                                    |
| :---------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `iam`                         | `user-registration-and-identity-verification`, `authentication-and-session-management`, `password-reset`, `staff-and-organizer-provisioning`, `user-profile-management`                                                                             |
| `workshops`                   | `workshop-creation-and-draft-management`, `workshop-publishing-and-update`, `workshop-rescheduling`, `workshop-cancellation`, `workshop-lifecycle-sweeper`, `staff-assignment-management`, `ai-brochure-summarization`, `workshop-catalog-browsing` |
| `registrations`               | `free-workshop-registration`, `paid-workshop-registration`, `registration-reconciliation-and-cancellation`, `payment-webhook-handling`                                                                                                              |
| `payments`                    | `paid-workshop-registration`, `payment-webhook-handling`, `payment-history-and-status`, `circuit-breaker-and-resilience`                                                                                                                            |
| `attendances`                 | `attendance-sync-reconciliation`                                                                                                                                                                                                                    |
| `notifications`               | `notification-delivery-pipeline`, `in-app-notification-management`, `password-reset`, `staff-and-organizer-provisioning`                                                                                                                            |
| `search`                      | `workshop-full-text-search`, `workshop-catalog-browsing`                                                                                                                                                                                            |
| `shared/security`             | `authentication-and-session-management`, `user-registration-and-identity-verification`, `infrastructure-bootstrap-and-seed-data`, `mobile-offline-checkin`                                                                                          |
| `shared/idempotency`          | `free-workshop-registration`, `paid-workshop-registration`, `payment-webhook-handling`, `registration-reconciliation-and-cancellation`                                                                                                              |
| `shared/web`                  | `distributed-rate-limiting`                                                                                                                                                                                                                         |
| `shared/observability`        | `system-observability-and-telemetry`, `distributed-rate-limiting`, `circuit-breaker-and-resilience`, `workshop-lifecycle-sweeper`                                                                                                                   |
| `shared/config`               | `distributed-rate-limiting`, `circuit-breaker-and-resilience`, `infrastructure-bootstrap-and-seed-data`, `system-observability-and-telemetry`, `ai-brochure-summarization`                                                                          |
| **Worker: `ai_processing`**   | `ai-brochure-summarization`, `circuit-breaker-and-resilience`                                                                                                                                                                                       |
| **Worker: `notifications`**   | `notification-delivery-pipeline`, `password-reset`, `staff-and-organizer-provisioning`, `circuit-breaker-and-resilience`                                                                                                                            |
| **Worker: `search_sync`**     | `workshop-publishing-and-update`, `workshop-rescheduling`, `workshop-cancellation`, `workshop-lifecycle-sweeper`                                                                                                                                    |
| **Worker: `batch_ingestion`** | `nightly-student-csv-ingestion`                                                                                                                                                                                                                     |
| **Mobile App**                | `mobile-offline-checkin`, `authentication-and-session-management`, `in-app-notification-management`, `user-profile-management`                                                                                                                      |
| **Web App**                   | All features except `mobile-offline-checkin` (mobile-only) and `attendance-sync-reconciliation` (Mobile as caller)                                                                                                                                  |
