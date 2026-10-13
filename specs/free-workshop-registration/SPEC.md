# Spec: Free Workshop Registration

> **Status:** APPROVED  
> **Domain:** 4 — Workshop Registration  
> **Owner/area:** `registrations` module (backend) · `notification-worker` (workers)  
> **Last revised:** 2026-10-13 — first version

## 1. Overview

Feature 4.1 enables a student to register for a free (zero-price) workshop, view their own registration list, and retrieve a single registration's ticket details including a signed QR code. Upon successful registration the system publishes a domain event that triggers a notification worker: the worker delivers a confirmation email (with a rendered PNG QR code embedded inline), an in-app notification, and a real-time server-sent event to the student. This feature replaces the manual Google-Forms process and must remain correct and consistent even when thousands of students compete for the same seats within seconds of registration opening. The registration pipeline is designed using an extensible strategy pattern so that the paid workshop registration flow (Feature 4.2) can be added seamlessly by registering a new strategy without modifying the core validation, dispatch, or idempotency pipeline. The `registrations` module in the backend owns the write and read paths; the `notification-worker` owns delivery.

## 2. Actors & Preconditions

| Actor                               | Precondition                                                                                                                                                                                                                                                 |
| :---------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **STUDENT** (registration mutation) | Holds a valid, non-expired JWT with role `STUDENT`; has a confirmed `student_record_id` linkage (i.e. `student_record_id IS NOT NULL` returned via `IamApi.getStudentProfile`).                                                                              |
| **STUDENT** (read endpoints)        | Holds a valid, non-expired JWT with role `STUDENT`. No `student_record_id` linkage is required for reads.                                                                                                                                                    |
| **Workshop**                        | The target workshop must exist, have status `PUBLISHED`, must not have already started (`starts_at` is in the future), must not be in a terminal state (`CANCELED`, `COMPLETED`), and must have `price = 0` (for this feature's free registration strategy). |
| **Notification Worker**             | The `WorkshopRegisteredEvent` has been published to RabbitMQ (`unihub.events::workshop.registered` on queue `q.notifications`); the worker is connected to RabbitMQ and can reach the email provider, Redis Pub/Sub, and the in-app notification store.      |

## 3. Functional Requirements

### 3.1 Registration Mutation (POST)

- **FR-01:** The system SHALL reject a registration request with `429 Too Many Requests` when the caller has exhausted the `registration` Bucket4j Token Bucket tier (5 tokens / 10-second window per IP), before any business logic is executed.
- **FR-02:** The system SHALL query `IamApi.getStudentProfile(studentUserId)` and reject a registration request with `403 Forbidden` when the student user profile does not exist or has `studentRecordId == null` (unlinked university profile).
- **FR-03:** The system SHALL require an `Idempotency-Key` header (UUIDv4, max 64 characters) on every registration mutation; if the header is absent or malformed, the system SHALL return `422 Unprocessable Entity`.
- **FR-04:** The system SHALL return `409 Conflict` with `Retry-After: 2` when an in-flight request with the same `(studentId, Idempotency-Key)` composite is already being processed (L1 Redis lock indicates `PENDING`).
- **FR-05:** The system SHALL return `201 Created` with the original response body (replay) when a completed `(studentId, Idempotency-Key)` composite is submitted again (L1 Redis cache hit or L2 DB `idempotency_keys` table lookup indicates `COMPLETED`).
- **FR-06:** The system SHALL query `WorkshopApi.getWorkshopForRegistration(workshopId)` and return `404 Not Found` when the workshop does not exist.
- **FR-07:** The system SHALL return `422 Unprocessable Entity` when the target workshop's status is not `PUBLISHED`, or has already started (`starts_at ≤ now`), or is in a terminal state (`CANCELED` or `COMPLETED`).
- **FR-08:** The system SHALL dispatch registration processing using an extensible strategy mechanism based on workshop pricing:
  - If `workshop.price == 0`, the system SHALL execute the free registration strategy.
  - If `workshop.price > 0`, the system SHALL return `422 Unprocessable Entity` ("Paid workshop registration is not supported in this version") until Feature 4.2 registers the paid registration strategy.
- **FR-09:** The system SHALL query the Primary database and return `409 Conflict` when the student already has an active registration (status `PENDING` or `CONFIRMED`) for the same workshop.
- **FR-10:** The system SHALL query the Primary database and return `422 Unprocessable Entity` when the student has an active registration (status `PENDING` or `CONFIRMED`) for any other workshop whose time slot overlaps with the requested workshop's `[starts_at, ends_at]` range.
- **FR-11:** The system SHALL atomically decrement the available capacity counter in Redis (`workshop:{workshopId}:capacity`) and return `409 Conflict` (with no seat consumed and compensating INCR applied) when the counter reaches zero or below, preventing overbooking under high concurrency.
- **FR-12:** The system SHALL, within a single atomic database transaction on the Primary database:
  1. Insert the registration record with status `CONFIRMED`.
  2. Generate and persist `qr_payload`, `qr_signature`, and `qr_issued_at`.
  3. Increment `registered_count` on the workshop record.
  4. Persist an outbox `WorkshopRegisteredEvent` to the `event_publication` table.
  5. Record the idempotency record with status `COMPLETED` and response snapshot in `idempotency_keys`.
- **FR-13:** The system SHALL generate the QR payload as canonical JSON: `{"registrationId":"UUID","workshopId":"UUID","studentId":"UUID","issuedAt":"ISO-8601 UTC"}` and the QR signature as a Base64-encoded RSA-SHA256 digital signature over the raw UTF-8 bytes using the system's RSA private key.
- **FR-14:** The system SHALL, after the transaction commits:
  1. Cache the idempotency response in Redis with status `COMPLETED` (TTL 24 hours).
  2. Publish a `registration.updated` message to Redis Pub/Sub channel `unihub:stream:workshop:{workshopId}` to notify active viewers of the new seat count.
- **FR-15:** The system SHALL return `201 Created` with the full registration detail (including QR fields) in the standard API response envelope upon successful registration.

### 3.2 Student Registration List (GET /me)

- **FR-16:** The system SHALL return a paginated list of the authenticated student's own registrations, containing only records where `student_user_id == jwt.sub`.
- **FR-17:** The system SHALL support optional filtering by registration `status` (`PENDING`, `CONFIRMED`, `CANCELED`, `FAILED`) via query parameter; when omitted, all statuses are returned.
- **FR-18:** The system SHALL paginate results using keyset cursor pagination (`?cursor=` and `?size=`, default 20, max 50) ordered by `(created_at DESC, id ASC)`.
- **FR-19:** The system SHALL route read queries for `GET /registrations/me` to the Read Replica via `@Transactional(readOnly = true)` to offload the Primary database.
- **FR-20:** The system SHALL return `200 OK` with an empty `items` array and `cursor.hasMore = false` when no matching registrations exist.

### 3.3 Single Registration Detail (GET /{id})

- **FR-21:** The system SHALL return `404 Not Found` when the requested registration `id` does not exist.
- **FR-22:** The system SHALL return `403 Forbidden` when the authenticated student's JWT subject does not match the `student_user_id` of the requested registration (ownership check).
- **FR-23:** The system SHALL include the `qrPayload`, `qrSignature`, and `qrIssuedAt` fields in the response if and only if the registration's status is `CONFIRMED`.
- **FR-24:** The system SHALL route queries for `GET /registrations/{id}` to the Read Replica via `@Transactional(readOnly = true)`.

### 3.4 Notification Worker — WorkshopRegisteredEvent Handler

- **FR-25:** The notification worker SHALL consume `WorkshopRegisteredEvent` from `q.notifications` (routing key `workshop.registered`) as a fat, self-contained message without making remote sync calls back to Core Backend.
- **FR-26:** The notification worker SHALL send a confirmation email containing the workshop title, start/end time, location, and a PNG QR code image rendered from `{"payload": <qr_payload_json>, "signature": "<qr_signature>"}` using ZXing, embedded inline via CID (`cid:ticket-qr`).
- **FR-27:** The notification worker SHALL persist an in-app notification record for the student linking to `/workshops/{slug}`.
- **FR-28:** The notification worker SHALL publish an SSE notification to the student's personal stream channel (`unihub:stream:user:{studentId}`) via Redis Pub/Sub with event type `notification.new`.
- **FR-29:** The notification worker SHALL apply standard AMQP retry and DLQ handling; a notification failure SHALL NOT affect the registration's `CONFIRMED` status in the database.

## 4. Business Rules

- **BR-01 (Rate Limiting Tier):** The `registration` Bucket4j Token Bucket tier is 5 tokens per 10-second window per IP. Enforced at the application filter layer before any business logic or auth parsing. _(Ref: docs/system-design.md §6; docs/adrs.md ADR-014)_
- **BR-02 (Student Record Linkage):** A student must have an existing profile in IAM with `student_record_id IS NOT NULL`. Queried synchronously via `IamApi.getStudentProfile(studentUserId)`. If empty or null record ID, registration is rejected with `403 Forbidden`. _(Ref: docs/system-design.md §4)_
- **BR-03 (Idempotency Key Lifecycle):** Mandatory `Idempotency-Key` header on mutations. Lifecycle: `(absent) → PENDING (Redis lock + DB check) → COMPLETED (DB row + Redis cache TTL 24h)`. Scoped to `(studentId, key)`. `PENDING` returns `409 Conflict` + `Retry-After: 2`. `COMPLETED` returns the cached `201 Created` replay. Business errors (4xx) release the lock and do not write to `idempotency_keys`. _(Ref: docs/system-design.md §6; docs/adrs.md ADR-013)_
- **BR-04 (Exactly-Once Semantics):** No registration is created more than once for the same logical intent. L2 durability is guaranteed by the `idempotency_keys` table on Primary DB. _(Ref: docs/adrs.md ADR-013)_
- **BR-05 (Extensible Registration Pipeline & Free Strategy):** The registration application service acts as an orchestrator and delegates execution to a `RegistrationStrategy`:
  - `FreeRegistrationStrategy`: Activated when `workshop.price == 0`. Atomically acquires capacity, creates `CONFIRMED` registration directly with QR ticket, increments workshop count, and emits `WorkshopRegisteredEvent`.
  - `PaidRegistrationStrategy`: Reserved for Feature 4.2. Will activate when `workshop.price > 0`, create `PENDING` registration, and initialize Stripe payment intent.
  - In Feature 4.1, if `workshop.price > 0`, the orchestrator immediately rejects the request with `422 Unprocessable Entity`. _(Ref: docs/feature-catalog.md §4.1, §4.2)_
- **BR-06 (Duplicate Registration Prevention):** Database-level partial unique index `(workshop_id, student_user_id) WHERE status IN ('PENDING','CONFIRMED')` guarantees no duplicate active registrations exist for the same student on the same workshop. Pre-checked at application layer against Primary DB. _(Ref: docs/database-schema.md)_
- **BR-07 (Schedule Conflict Prevention):** A student cannot hold two active registrations (`PENDING` or `CONFIRMED`) whose time slots overlap (`[existing.starts_at, existing.ends_at]` intersects `[new.starts_at, new.ends_at]`). Evaluated against Primary DB before capacity decrement. _(Ref: docs/system-design.md §4)_
- **BR-08 (Capacity Control & Overbooking Defense):** Capacity is decremented in Redis via atomic `DECR workshop:{workshopId}:capacity`. If the result is `< 0`, an immediate compensating `INCR` is executed, and `409 Conflict` is returned. On cache miss, self-heals by loading `(capacity - registered_count)` from Primary DB. _(Ref: docs/system-design.md §6; docs/adrs.md ADR-017)_
- **BR-09 (Validation Pipeline Order):** Strict execution order:
  1. Rate Limit (Bucket4j)
  2. JWT & Student Linkage (`IamApi.getStudentProfile`)
  3. Idempotency Check (Redis L1 / DB L2)
  4. Workshop Status & Schedule (`WorkshopApi.getWorkshopForRegistration`)
  5. Price Check & Strategy Selection (`FreeRegistrationStrategy`)
  6. Duplicate & Schedule Conflict Check (Primary DB)
  7. Redis Capacity Atomic DECR
  8. Atomic DB Transaction (Insert CONFIRMED + QR + Outbox Event + Complete Idempotency)
  9. Post-Commit Hooks (Redis L1 Cache + Redis Pub/Sub seat broadcast)
- **BR-10 (QR Signature Algorithm):** The QR signature uses `SHA256withRSA` over the canonical UTF-8 JSON payload. Private key is loaded from `RsaKeyProperties`. Payload JSON keys: `registrationId`, `workshopId`, `studentId`, `issuedAt`. _(Ref: docs/adrs.md ADR-029)_
- **BR-11 (QR Code Rendering in Email):** The notification worker formats the barcode payload as `{"payload": <qr_payload_json_string>, "signature": "<base64_signature>"}`, generates a PNG image via ZXing, and attaches it inline with `Content-ID: ticket-qr`. _(Ref: docs/system-design.md §4)_
- **BR-12 (PENDING Expiry & Sweeper Out of Scope):** Free registrations skip `PENDING` and enter `CONFIRMED` immediately; `expires_at` is left `NULL`. The 30-minute reconciliation sweeper is implemented in Feature 4.3. _(Ref: docs/database-schema.md; docs/feature-catalog.md §4.3)_
- **BR-13 (Transactional Outbox Pattern):** The `WorkshopRegisteredEvent` is persisted to `event_publication` within the same database transaction as the registration record. Forwarded to RabbitMQ only after commit. _(Ref: docs/system-design.md §4; docs/adrs.md ADR-018, ADR-019)_
- **BR-14 (Eventual Consistency for Notifications):** Delivery failure (email bounce, SSE disconnect) does not roll back or invalidate the `CONFIRMED` registration. Retries and DLQ isolate notification issues. _(Ref: docs/system-design.md §4)_
- **BR-15 (Primary vs Replica Routing):**
  - All writes and critical pre-checks (duplicate check, schedule conflict, idempotency) execute on the Primary DB.
  - Read endpoints (`GET /registrations/me`, `GET /registrations/{id}`) execute with `@Transactional(readOnly = true)`, automatically routed to the Read Replica by `TransactionAwareRoutingDataSource`. _(Ref: docs/adrs.md ADR-015; docs/database-schema.md §4)_
- **BR-16 (Ownership Isolation):** A student can only view their own registrations (`student_user_id == jwt.sub`). Attempting to read another student's registration via `GET /{id}` returns `403 Forbidden`. _(Ref: docs/feature-catalog.md §4.1)_
- **BR-17 (Keyset Cursor Pagination):** `GET /me` uses keyset cursor pagination with opaque Base64-encoded cursor `{id, sortField: "createdAt", primaryValue, secondaryValue: null}`. Stable under concurrent writes. _(Ref: docs/api-standards.md)_
- **BR-18 (WorkshopApi Cross-Module Facade):** The `registrations` module communicates with `workshops` exclusively via `com.unihub.workshop.workshops.api.WorkshopApi`:
  ```java
  public interface WorkshopApi {
      Optional<WorkshopRegistrationDetailsDto> getWorkshopForRegistration(UUID workshopId);
  }
  ```
  No internal repository or entity of `workshops` may be imported. _(Ref: docs/project-structure.md; docs/adrs.md ADR-031)_
- **BR-19 (IamApi Cross-Module Facade):** The `registrations` module communicates with `iam` exclusively via `com.unihub.workshop.iam.api.IamApi`:
  ```java
  Optional<StudentProfileDto> getStudentProfile(UUID studentUserId);
  ```
  Returns `StudentProfileDto(UUID userId, UUID studentRecordId, String fullName, String email)`. _(Ref: docs/project-structure.md)_
- **BR-20 (Self-Contained Fat Event):** `WorkshopRegisteredEvent` in `shared/events/` is a self-contained fat event annotated with `@Externalized("unihub.events::workshop.registered")`, carrying student and workshop snapshot data so workers require zero synchronous calls back to backend. _(Ref: docs/adrs.md ADR-019, ADR-027)_

## 5. State Transitions

The `registration.status` field lifecycle for this feature:

| From State  | Event/Trigger                                                | To State                 | Valid? | Rejection Behavior (if invalid)                                             |
| :---------- | :----------------------------------------------------------- | :----------------------- | :----: | :-------------------------------------------------------------------------- |
| _(none)_    | Student submits free registration request                    | `CONFIRMED`              |   ✅   | —                                                                           |
| _(none)_    | Student submits free registration request, capacity is 0     | _(not created)_          |   ✅   | `409 Conflict` — no record created, Redis INCR compensation applied         |
| _(none)_    | Student submits free registration request, duplicate active  | _(not created)_          |   ✅   | `409 Conflict` — no record created                                          |
| _(none)_    | Student submits free registration request, schedule conflict | _(not created)_          |   ✅   | `422 Unprocessable Entity` — no record created                              |
| `CONFIRMED` | Student reads registration via GET /{id}                     | `CONFIRMED` (read-only)  |   ✅   | —                                                                           |
| `CONFIRMED` | Student re-registers for same workshop                       | _(no transition)_        |   ❌   | `409 Conflict` — duplicate active registration                              |
| `PENDING`   | Free registration mutation                                   | _(not applicable)_       |   ❌   | `PENDING` is reserved for paid flow (F-4.2); free flow skips to `CONFIRMED` |
| `CANCELED`  | Re-registration for same workshop                            | `CONFIRMED` (new record) |   ✅   | Allowed — prior canceled record is not active                               |
| `FAILED`    | Re-registration for same workshop                            | `CONFIRMED` (new record) |   ✅   | Allowed — prior failed record is not active                                 |

## 6. Non-Functional Requirements

- **Performance:** End-to-end registration mutation p99 latency < 500ms under standard load. Read endpoints p99 < 200ms.
- **Concurrency:** Zero overbooking and zero duplicate registrations under 12,000 concurrent users (60% arriving within 3 minutes). Guarded by Redis atomic DECR + DB partial unique index.
- **Idempotency:** Exactly-once registration semantics per `Idempotency-Key`. Durable across Redis restarts via `idempotency_keys` table.
- **Rate Limiting:** 5 requests / 10 seconds / IP enforced at application filter layer via Bucket4j.
- **Security:** RS256 JWT authentication. Ownership isolation strictly enforced. RSA private key never leaves secure backend configuration.
- **Reliability:** Decoupled asynchronous notifications via outbox pattern. Redis cache failure falls back to DB. Worker failures route to DLQ without affecting registration status.
- **Observability:** Metrics emitted: `registrations.created`, `registrations.rejected_capacity`, `registrations.rejected_duplicate`, `registrations.rejected_conflict` tagged by `workshopId`. Worker metrics: `notifications.email.sent`, `notifications.inapp.created`, `notifications.sse.published`.

## 7. Acceptance Criteria

### Scenario 1: Successful free registration

```gherkin
Given a STUDENT with a valid JWT and an active profile with confirmed student_record_id
And a PUBLISHED workshop with price = 0 and available capacity >= 1
And the student has no active registration for this workshop and no schedule conflict
And the student provides a valid UUIDv4 Idempotency-Key header
When the student submits a POST registration request for the workshop
Then the system returns 201 Created
And the response body contains a registration with status CONFIRMED
And the response body contains qrPayload, qrSignature, and qrIssuedAt
And the workshop available capacity in Redis is decremented by 1
And a WorkshopRegisteredEvent is placed in the outbox
```

_(Satisfies: FR-08, FR-11, FR-12, FR-13, FR-14, FR-15)_

### Scenario 2: Idempotent replay — completed key

```gherkin
Given a STUDENT has successfully registered for a workshop with Idempotency-Key "K1"
When the student submits the same POST request again with Idempotency-Key "K1"
Then the system returns 201 Created with the exact same response body as the original request
And no new registration record is created in the database
And no new WorkshopRegisteredEvent is published
And the workshop available capacity is not decremented again
```

_(Satisfies: FR-05, BR-04)_

### Scenario 3: In-flight duplicate — PENDING key

```gherkin
Given a STUDENT registration request with Idempotency-Key "K2" is currently in-flight
When the student submits another POST request with Idempotency-Key "K2"
Then the system returns 409 Conflict
And the response includes Retry-After: 2
```

_(Satisfies: FR-04, BR-03)_

### Scenario 4: Missing or invalid Idempotency-Key header

```gherkin
Given a STUDENT with a valid JWT
When the student submits a POST registration request without an Idempotency-Key header
Then the system returns 422 Unprocessable Entity
And the error body follows RFC 7807 format with type "urn:unihub:error:validation"
```

_(Satisfies: FR-03)_

### Scenario 5: Registration rejected — capacity exhausted

```gherkin
Given a PUBLISHED workshop with price = 0 and available capacity = 0
And a STUDENT with a valid JWT, a linked student_record_id, and a valid Idempotency-Key
When the student submits a POST registration request
Then the system returns 409 Conflict
And no registration record is created in the database
And the workshop capacity in Redis remains 0 (compensating INCR applied)
```

_(Satisfies: FR-11, BR-08)_

### Scenario 6: Registration rejected — unlinked student record

```gherkin
Given a STUDENT with a valid JWT but IamApi returns studentRecordId = null
When the student submits a POST registration request
Then the system returns 403 Forbidden
And no capacity is consumed and no registration record is created
```

_(Satisfies: FR-02, BR-02)_

### Scenario 7: Registration rejected — paid workshop (extensibility guard)

```gherkin
Given a PUBLISHED workshop with price > 0
And a STUDENT with a valid JWT, a linked student_record_id, and a valid Idempotency-Key
When the student submits a POST registration request for this paid workshop
Then the system returns 422 Unprocessable Entity with detail indicating paid registration is handled separately
And no capacity is consumed
```

_(Satisfies: FR-08, BR-05)_

### Scenario 8: Registration rejected — duplicate active registration

```gherkin
Given a STUDENT already has a CONFIRMED registration for workshop W
When the student submits another POST registration request for workshop W with a new Idempotency-Key
Then the system returns 409 Conflict
And no new registration record is created
```

_(Satisfies: FR-09, BR-06)_

### Scenario 9: Registration rejected — schedule conflict

```gherkin
Given a STUDENT has a CONFIRMED registration for workshop A running 10:00-12:00
And workshop B runs 11:00-13:00 (overlapping time range)
When the student submits a POST registration request for workshop B
Then the system returns 422 Unprocessable Entity
And no capacity is consumed for workshop B
```

_(Satisfies: FR-10, BR-07)_

### Scenario 10: Rate limit exceeded

```gherkin
Given a STUDENT has sent 5 registration requests within 10 seconds from the same IP
When the student sends a 6th registration request from the same IP
Then the system returns 429 Too Many Requests before any business logic executes
And the response includes an X-RateLimit-Retry-After header
```

_(Satisfies: FR-01, BR-01)_

### Scenario 11: Read own registration list — paginated

```gherkin
Given a STUDENT has 25 registrations (all CONFIRMED)
When the student calls GET /registrations/me with no cursor and default size
Then the system returns 200 OK
And the response items contain 20 items ordered by created_at DESC
And cursor.hasMore = true
And cursor.nextCursor is present
```

_(Satisfies: FR-16, FR-18, FR-19, BR-17)_

### Scenario 12: Read own registration list — status filter

```gherkin
Given a STUDENT has 5 CONFIRMED and 3 CANCELED registrations
When the student calls GET /registrations/me?status=CONFIRMED
Then the system returns 200 OK
And the response items contain exactly 5 items all with status CONFIRMED
```

_(Satisfies: FR-17)_

### Scenario 13: Read single registration detail — success

```gherkin
Given a STUDENT has a CONFIRMED registration with ID "R1"
When the student calls GET /registrations/R1
Then the system returns 200 OK
And the response body contains qrPayload, qrSignature, and qrIssuedAt
```

_(Satisfies: FR-23, FR-24)_

### Scenario 14: Read single registration — ownership violation

```gherkin
Given student A has a CONFIRMED registration with ID "R1"
And student B is authenticated with a valid JWT
When student B calls GET /registrations/R1
Then the system returns 403 Forbidden
```

_(Satisfies: FR-22, BR-16)_

### Scenario 15: Read single registration — not found

```gherkin
Given no registration exists with ID "R-unknown"
When an authenticated STUDENT calls GET /registrations/R-unknown
Then the system returns 404 Not Found
```

_(Satisfies: FR-21)_

### Scenario 16: Notification delivery — confirmation email with inline QR

```gherkin
Given a WorkshopRegisteredEvent has been published to the q.notifications queue
When the notification worker consumes the event
Then the worker sends a confirmation email to the student email address
And the email contains the workshop title, start/end time, and location
And the email contains an inline PNG QR code image with CID "ticket-qr"
And the QR code image encodes the JSON string {"payload": <qr_payload_json>, "signature": "<base64_signature>"}
```

_(Satisfies: FR-25, FR-26, BR-11, BR-20)_

### Scenario 17: Notification delivery — in-app and SSE

```gherkin
Given a WorkshopRegisteredEvent has been published to the q.notifications queue
When the notification worker consumes the event
Then the worker creates an in-app notification for the student linking to /workshops/{slug}
And the worker publishes an SSE notification to the student personal stream channel
```

_(Satisfies: FR-25, FR-27, FR-28)_

### Scenario 18: Notification failure does not affect registration

```gherkin
Given a WorkshopRegisteredEvent has been published
And the email provider is temporarily unavailable
When the notification worker attempts delivery
Then the worker retries according to queue retry policy
And the registration record remains CONFIRMED in the database
```

_(Satisfies: FR-29, BR-14)_

### Scenario 19: Last-seat concurrency — single winner under contention

```gherkin
Given a PUBLISHED workshop with price = 0 and available capacity = 1
And 100 STUDENTS simultaneously submit valid POST registration requests
When the system processes all concurrent requests
Then exactly 1 registration record with status CONFIRMED is created
And 99 requests receive 409 Conflict
And the workshop capacity in Redis is 0 (never negative)
```

_(Satisfies: FR-11, BR-08)_

## 8. Out of Scope

- **Paid workshop registration (feature 4.2):** Workshops with `price > 0`, Stripe payment intent creation, webhook processing, `PENDING` status lifecycle for paid checkout. The core pipeline delegates to strategies, but only `FreeRegistrationStrategy` is implemented in 4.1.
- **Registration cancellation and expiry sweeper (feature 4.3):** Student cancellation API, seat release logic, 30-minute background sweeper for expired pending registrations.
- **Organizer roster / attendance monitoring (feature 4.4):** Organizer dashboard endpoints for viewing attendee rosters.
- **On-site staff check-in (feature 5.x):** Offline QR scanning and reconciliation.
- **Waitlist management:** Full workshops reject registration with 409; no queuing mechanism.
- **Email template authoring:** Underlying SMTP configuration and thymeleaf templates belong to the notification delivery pipeline.

## 9. Linked References

| Document                    | Section                                                                                                                                                                                                                               |
| :-------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md`   | §4.1 free-workshop-registration, §4.2, §4.3, Appendix A                                                                                                                                                                               |
| `docs/system-design.md`     | §4 — Workshop Registration Flow; §6 — System Protection Mechanisms; §4 — Student Read Endpoints; §4 — Visual QR Email Rendering; §4 — Cross-Module API Facades                                                                        |
| `docs/database-schema.md`   | `registrations`, `idempotency_keys`, `event_publication` tables; §4 Replication Lag & Read-After-Write Consistency                                                                                                                    |
| `docs/api-standards.md`     | Keyset Cursor Pagination, RFC 7807 problem details, standard 2xx envelope, HTTP status code table                                                                                                                                     |
| `docs/adrs.md`              | ADR-013 (Idempotency), ADR-014 (Rate Limiting), ADR-015 (Primary-Replica Routing), ADR-017 (Redis Capacity), ADR-018 (Outbox), ADR-019 (Modulith Events), ADR-027 (Notifications), ADR-029 (QR Signing), ADR-031 (Modular Boundaries) |
| `docs/project-structure.md` | Backend Clean Architecture 4-layer layout; Hexagonal Architecture for workers; Spring Modulith module boundaries                                                                                                                      |
