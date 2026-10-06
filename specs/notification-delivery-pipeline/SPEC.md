# Spec: Notification Delivery Pipeline

> **Status:** APPROVED  
> **Domain:** 7 — Notification System  
> **Owner/area:** `apps/workers/` — `notifications` worker module (Hexagonal Architecture)  
> **Last revised:** 2026-10-07 — first version

## 1. Overview

The Notification Delivery Pipeline is a background worker service that consumes domain integration events published to the `q.notifications` RabbitMQ queue and delivers notifications to affected users through three channels: in-app persistence, real-time Server-Sent Events (SSE), and email. It exists to decouple notification concerns from the backend monolith, which publishes events but has no knowledge of how or to whom they are delivered. The pipeline determines the correct recipient set for each event type (direct user, all active organizers in an organization, or all confirmed-registered students of a workshop), then fans out delivery across all applicable channels, recording a delivery audit log entry for external channel attempts (email). All processing is idempotent, resilience-wrapped, and failure-isolated so that a single channel's outage never blocks other channels or other event types.

## 2. Actors & Preconditions

| Actor                                          | Precondition                                                                                                                                                                                                                                        |
| :--------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **RabbitMQ broker** (`q.notifications`)        | Queue exists and is bound to the `unihub.events` exchange with routing keys `notifications`, `workshop.rescheduled`, and `workshop.canceled`. Dead-letter exchange (`unihub.dlx`) and dead-letter queue (`unihub.dlq`) are configured on the queue. |
| **Backend Monolith** (event publisher)         | Spring Modulith `@Externalized` events are being published to the exchange. Events carry a JSON body and an AMQP `__TypeId__` header identifying the canonical event type.                                                                          |
| **`batch_ingestion` Worker** (event publisher) | `FileCompletedEvent` and `FileRejectedEvent` are published to exchange `unihub.events` with routing key `notifications` and standard AMQP `__TypeId__` header (`FileCompletedEvent` / `FileRejectedEvent`).                                         |
| **SMTP Server**                                | A live SMTP server is reachable and configured via `JavaMailSender` properties.                                                                                                                                                                     |
| **Redis**                                      | Redis instance is reachable; used as the SSE publish channel for real-time delivery (`unihub:stream:user:{userId}`).                                                                                                                                |
| **PostgreSQL**                                 | Primary database is reachable; used to persist in-app notification records and external delivery audit logs, and to resolve recipients for fan-out events.                                                                                          |
| **Notification Worker process**                | The `apps/workers/` Spring Boot application is running and has registered its `q.notifications` listener.                                                                                                                                           |

## 3. Functional Requirements

### 3.1 Event Consumption & Routing

- **FR-01:** The system SHALL consume every message arriving on the `q.notifications` queue without acknowledging it until all delivery attempts for all applicable channels have been completed or exhausted.
- **FR-02:** The system SHALL route each consumed message to its matching event handler using the `__TypeId__` header present on the message properties.
- **FR-03:** The system SHALL reject and dead-letter a message to `unihub.dlq` when the `__TypeId__` header is missing or does not resolve to any registered event handler, logging the payload excerpt and reason.
- **FR-04:** The system SHALL support the addition of new event handler registrations without modifying core dispatch logic, following an open-closed event handler registry design.
- **FR-05:** The system SHALL define an extensible `ConsumedNotificationEvent` core domain abstraction where every concrete event DTO encapsulates its own domain-derived `idempotencyKey()`, ensuring generic deduplication across the pipeline.

### 3.2 Recipient Resolution

- **FR-06:** The system SHALL resolve the recipient as the single user identified by `userId` in the event payload, when the event carries a direct user reference (`OtpRequestedEvent`, `UserProvisionedEvent`, `UserStatusChangedEvent`, `StaffAssignedToWorkshopEvent`, `StaffRevokedFromWorkshopEvent`).
- **FR-07:** The system SHALL resolve recipients as all users where `role = ORGANIZER`, `is_active = true`, and `organization_id` matches the event's `organizationId`, when the event is `FileCompletedEvent` or `FileRejectedEvent`.
- **FR-08:** The system SHALL resolve recipients as all users who have a registration record with `status = CONFIRMED` for the workshop identified by the event's `workshopId`, when the event is `WorkshopRescheduledEvent` or `WorkshopCanceledEvent`.
- **FR-09:** The system SHALL emit zero notifications and log a warning when the recipient set for an event resolves to an empty list (not an error condition for fan-out events).

### 3.3 In-App Notification Delivery

- **FR-10:** The system SHALL persist one in-app notification record per recipient per event when the channel matrix specifies in-app delivery for that event type.
- **FR-11:** The system SHALL populate the in-app notification record with: a unique identifier, the recipient's user ID, a stable event key, a structured JSON payload with event context data, `is_read = false`, `read_at = null`, and a standardized relative `action_url` mapped as follows:
  - `/workshops/{slug}` for registered students (`WorkshopRescheduledEvent`, `WorkshopCanceledEvent`)
  - `/workshops/{workshopId}` for assigned staff (`StaffAssignedToWorkshopEvent`)
  - `/workshops` for revoked staff (`StaffRevokedFromWorkshopEvent`)
  - `/admin/import` for organization organizers (`FileCompletedEvent`, `FileRejectedEvent`)
  - `null` for account-level events with no direct resource navigation (`UserProvisionedEvent`, `UserStatusChangedEvent`)
- **FR-12:** The system SHALL suppress duplicate in-app notification persistence when the event's `idempotencyKey` has already been recorded for that recipient.

### 3.4 Real-Time SSE Delivery

- **FR-13:** The system SHALL publish a real-time notification signal to the Redis SSE channel (`unihub:stream:user:{userId}`) for each recipient when the channel matrix specifies real-time delivery for that event type.
- **FR-14:** The real-time signal SHALL carry the event type discriminator `notification.new` and the recipient's user ID, so that the SSE layer can push the notification to any connected client session for that user.
- **FR-15:** The system SHALL treat a Redis publish failure as a non-fatal soft failure — it SHALL log a warning with context, increment a failure metric, and continue processing remaining channels and recipients without aborting the event.

### 3.5 Email Delivery

- **FR-16:** The system SHALL send one email per recipient per event when the channel matrix specifies email delivery for that event type.
- **FR-17:** The system SHALL render the email body from a per-event Thymeleaf HTML template, passing event-specific context variables into the template at render time.
- **FR-18:** The system SHALL send the rendered email via `JavaMailSender` to the recipient's email address.
- **FR-19:** The system SHALL apply a Resilience4j circuit breaker to all outbound email delivery calls, so that sustained SMTP failures open the breaker and prevent subsequent attempts from queuing up synchronously.
- **FR-20:** The system SHALL treat a failed email delivery (SMTP error, rendering error, or open circuit) as a non-fatal soft failure per recipient — it SHALL log the failure, record a `FAILED` delivery log entry, and continue processing remaining recipients and channels without aborting the whole event.
- **FR-21:** The system SHALL suppress duplicate email delivery to the same recipient when an audit log entry with `status = SENT` already exists for that `(idempotencyKey, recipient_address, channel = 'EMAIL')`.

### 3.6 Delivery Audit Logging

- **FR-22:** The system SHALL persist one delivery audit log entry in `notification_delivery_logs` per recipient per external delivery attempt (`channel = EMAIL`), recording `status = PENDING`, `SENT`, or `FAILED`.
- **FR-23:** Each delivery audit log entry SHALL record: a unique identifier, the recipient's user ID, `channel = EMAIL`, the recipient address (email address), the final status (`SENT` or `FAILED`), the provider message ID if available (SMTP message ID), the error reason if the attempt failed, and the attempt timestamp.
- **FR-24:** The system SHALL update an existing delivery audit log entry's status from `PENDING` to `SENT` or `FAILED` upon completion of the delivery attempt, rather than inserting a duplicate row on retry.

### 3.7 Retry and Dead-Letter Handling

- **FR-25:** The system SHALL allow RabbitMQ to redeliver a message (negative-acknowledge without requeue) when processing fails at the infrastructure level (database unreachable, Redis unreachable) before any delivery has been attempted, so that the message is retried up to the broker's configured retry limit.
- **FR-26:** The system SHALL dead-letter a message to `unihub.dlq` after all broker-level retry attempts are exhausted, preserving the original payload for manual inspection and replay.
- **FR-27:** The system SHALL NOT dead-letter a message solely because one soft-failure channel (SSE or email) failed — channel-level failures are handled within the pipeline and do not constitute a message-level processing failure.

### 3.8 Per-Event Channel Matrix Enforcement

- **FR-28:** The system SHALL deliver `OtpRequestedEvent` exclusively via email channel (no in-app, no SSE).
- **FR-29:** The system SHALL deliver `UserProvisionedEvent` via email channel (carrying temporary password) AND in-app channel (welcome notification); SSE is NOT applicable for this event.
- **FR-30:** The system SHALL deliver `UserStatusChangedEvent` via email channel AND in-app channel; SSE is NOT applicable for this event.
- **FR-31:** The system SHALL deliver `StaffAssignedToWorkshopEvent` via in-app channel, real-time SSE channel, AND email channel.
- **FR-32:** The system SHALL deliver `StaffRevokedFromWorkshopEvent` via in-app channel, real-time SSE channel, AND email channel.
- **FR-33:** The system SHALL deliver `WorkshopRescheduledEvent` to all confirmed-registered students via in-app channel, real-time SSE channel, AND email channel.
- **FR-34:** The system SHALL deliver `WorkshopCanceledEvent` to all confirmed-registered students via in-app channel, real-time SSE channel, AND email channel.
- **FR-35:** The system SHALL deliver `FileCompletedEvent` to all active organizers of the organization via in-app channel, real-time SSE channel, AND email channel.
- **FR-36:** The system SHALL deliver `FileRejectedEvent` to all active organizers of the organization via in-app channel, real-time SSE channel, AND email channel.

### 3.9 Modular Boundary & Architecture Compliance

- **FR-37:** The system SHALL maintain strict hexagonal architecture boundaries: the `core` layer SHALL contain zero Spring Framework, JPA, or AMQP annotations; all infrastructure concerns SHALL be confined to `inbound` and `outbound` adapters.
- **FR-38:** The system SHALL NOT access the internal packages of any other worker module (`batch_ingestion`, `search_sync`, `maintenance`, `ai_processing`) directly; cross-module data needed for recipient resolution SHALL be fetched from the shared database.

## 4. Business Rules

- **BR-01:** The channel matrix is fixed per event type and must not be determined dynamically at runtime from a database configuration table — it is part of the pipeline's core business logic and versioned with the code. _(Source: `docs/feature-catalog.md` Feature 7.1 channel matrix)_
- **BR-02:** An in-app notification record's `is_read` field defaults to `false` and may only be set to `true` by a subsequent user action (Feature 7.2 — Read Notification). This pipeline never marks a notification as read. _(Source: `docs/database-schema.md` `in_app_notifications` table)_
- **BR-03:** The `notification_delivery_logs` table records external delivery attempts (EMAIL) for audit and compliance. Entries must never be deleted by this pipeline. _(Source: `docs/database-schema.md` `notification_delivery_logs` table)_
- **BR-04:** Fan-out recipient resolution for `WorkshopRescheduled` and `WorkshopCanceled` must include only registrations with `status = CONFIRMED`. Registrations with `PENDING`, `CANCELED`, or `WAITLISTED` status must be excluded. _(Source: `docs/database-schema.md` `registrations` table, `docs/system-design.md` registration flow)_
- **BR-05:** Fan-out recipient resolution for `FileCompleted` and `FileRejected` must include only users with `role = ORGANIZER` and `is_active = true` within the event's `organizationId`. _(Source: `docs/database-schema.md` `users` table, `docs/feature-catalog.md` Feature 7.1)_
- **BR-06:** The email containing `temporaryPassword` (from `UserProvisionedEvent`) must be delivered exclusively to the provisioned user's email address. It must never be fanned out, cc'd, or logged in plaintext in application logs. _(Source: `docs/system-design.md` user lifecycle flow, `docs/adrs.md` ADR-010 security)_
- **BR-07:** A `FileCompletedEvent` or `FileRejectedEvent` that resolves to zero active organizers in the organization must be silently skipped (no error) — this is a valid edge case when an organization has no active organizers at the time of processing. _(Source: `docs/feature-catalog.md` Feature 7.1)_
- **BR-08:** The OTP value in `OtpRequestedEvent` must not appear in any delivery audit log entry, application log line, or in-app notification payload. It may only appear in the rendered email body sent directly to the user. _(Source: `docs/adrs.md` ADR-010 security, `docs/system-design.md` OTP flow)_
- **BR-09:** All writes to `in_app_notifications` and `notification_delivery_logs` must be routed to the primary PostgreSQL node, never to the read replica. _(Source: `docs/database-schema.md` Replication Lag & Read-After-Write Consistency section)_
- **BR-10:** Recipient resolution queries for fan-out events (`users` lookup, `registrations` lookup) may be routed to the read replica, since a small replication lag is acceptable for notification fan-out. _(Source: `docs/database-schema.md` Replication Lag & Read-After-Write Consistency section)_
- **BR-11:** The circuit breaker on the email delivery path must be configured with a dedicated instance name so that email circuit state is isolated from any other Resilience4j-wrapped call in the worker. _(Source: `docs/adrs.md` ADR-020 Resilience4j, `docs/system-design.md` system protection mechanisms)_
- **BR-12:** Messages that fail delivery for all channels due to a transient infrastructure outage must not be silently dropped. They must be negatively acknowledged so the broker can redeliver them, up to the queue's configured retry count, after which they land on `unihub.dlq`. _(Source: `docs/system-design.md` system protection mechanisms, `infra/rabbitmq/definitions.json.template`)_

## 5. State Transitions

### 5.1 Delivery Audit Log Entry (`notification_delivery_logs.status`)

Applies exclusively to external delivery channels tracked in `notification_delivery_logs` (`channel = EMAIL`).

| From State | Event/Trigger                                  | To State             | Valid? | Rejection Behavior (if invalid)                                                                                          |
| :--------- | :--------------------------------------------- | :------------------- | :----: | :----------------------------------------------------------------------------------------------------------------------- |
| _(new)_    | Delivery attempt begins                        | `PENDING`            |   ✅   | —                                                                                                                        |
| `PENDING`  | SMTP send succeeds                             | `SENT`               |   ✅   | —                                                                                                                        |
| `PENDING`  | SMTP error / circuit open / rendering error    | `FAILED`             |   ✅   | —                                                                                                                        |
| `SENT`     | Redelivered message (idempotency guard active) | `SENT` (no update)   |   ✅   | No duplicate row inserted; existing `SENT` entry is returned as-is                                                       |
| `FAILED`   | Redelivered message (idempotency guard active) | `FAILED` (no update) |   ✅   | No duplicate row inserted; existing `FAILED` entry is returned as-is                                                     |
| `SENT`     | Any further trigger                            | `FAILED`             |   ❌   | Rejected — a delivered notification cannot be un-delivered; pipeline skips re-delivery                                   |
| `FAILED`   | Any further trigger                            | `SENT`               |   ❌   | Rejected — a failed attempt is not retried by re-delivering to the same log entry; a new attempt creates a new log entry |

### 5.2 In-App Notification Record (`in_app_notifications.is_read`)

| From State        | Event/Trigger                                       | To State                     | Valid? | Rejection Behavior (if invalid)                                                                               |
| :---------------- | :-------------------------------------------------- | :--------------------------- | :----: | :------------------------------------------------------------------------------------------------------------ |
| _(new)_           | In-app notification persisted                       | `is_read = false`            |   ✅   | —                                                                                                             |
| `is_read = false` | User marks notification as read (Feature 7.2)       | `is_read = true`             |   ✅   | —                                                                                                             |
| `is_read = true`  | Any pipeline re-delivery (idempotency guard active) | `is_read = true` (no update) |   ✅   | No duplicate row inserted; pipeline skips insert                                                              |
| `is_read = true`  | Any attempt to set `is_read = false`                | ❌                           |   ❌   | Rejected — read state is monotonically increasing; this pipeline never changes `is_read` after initial insert |

## 6. Non-Functional Requirements

- **Performance:** The pipeline must sustain fan-out delivery for `WorkshopRescheduled` and `WorkshopCanceled` events where the registered student count may reach several thousand. Fan-out must not load all recipient rows into memory at once; it must use paginated or streaming queries to bound peak memory per message.
- **Concurrency:** Multiple concurrent listener threads may process different messages simultaneously. State for one message's processing must not bleed into another. Each message is processed in its own thread-isolated unit of work.
- **Idempotency:** Every in-app notification insert and every email delivery attempt must be idempotent with respect to message redelivery. Deduplication is governed generically by the event's domain `idempotencyKey`. Duplicate processing must be silently suppressed without errors.
- **Reliability / Failure modes:**
  - Channel-level failures (email down, Redis down) are soft failures — they do not abort processing of other channels or other recipients.
  - Infrastructure-level failures (database completely unreachable before any processing begins) cause a message-level NACK so the broker retries.
  - Exhausted broker retries → dead-letter to `unihub.dlq` for manual replay.
  - The Resilience4j circuit breaker on the email path opens after a configured threshold of consecutive SMTP failures, preventing unbounded synchronous wait time on subsequent messages.
- **Security:**
  - OTP values must never appear in logs, audit records, or in-app payloads.
  - Temporary passwords (`temporaryPassword`) must never appear in logs, audit records, or in-app payloads.
  - Recipient resolution must be strictly scoped: a notification for organization A must never be delivered to any user of organization B, even if both are active organizers.
- **Observability:** The pipeline must emit structured log entries for every message consumed (event type, message ID, recipient count), every delivery success, every delivery failure (channel, recipient user ID, error class), and every dead-letter event. Metrics via Micrometer shall include: messages consumed counter, delivery success/failure counters per channel, circuit breaker state gauges.
- **Rate-limiting:** N/A — rate limiting is not applied at the worker level for this pipeline. The broker queue naturally throttles throughput; the circuit breaker handles SMTP saturation.
- **Extensibility:** Adding support for a new event type in the future must require only: (a) adding a new event DTO implementing `ConsumedNotificationEvent`, (b) adding a new handler implementing `NotificationEventHandler<T>`, and (c) adding a new email template if email channel is applicable. No changes to the core dispatch logic, recipient resolution framework, or delivery orchestration layer are required.

## 7. Acceptance Criteria

### Scenario 1: OTP email sent on OtpRequestedEvent (ACTIVATION purpose)

```gherkin
Given a user has requested OTP for ACTIVATION
And the backend has published an OtpRequestedEvent to q.notifications with userId, email, otp, purpose=ACTIVATION, and __TypeId__ header
When the notification pipeline consumes the message
Then one email is sent to the user's email address using the otp-activation template
And the email body contains the OTP value
And one delivery audit log entry is created with channel=EMAIL and status=SENT
And NO in-app notification record is created for this event
And the OTP value does NOT appear in any log line or audit record
```

_(Traces: FR-01, FR-02, FR-05, FR-06, FR-16, FR-17, FR-22, FR-23, FR-28, BR-08)_

### Scenario 2: UserProvisionedEvent triggers both email and in-app channels

```gherkin
Given a new organizer account has been provisioned
And the backend has published a UserProvisionedEvent with userId, email, fullName, role=ORGANIZER, temporaryPassword, and __TypeId__ header
When the notification pipeline consumes the message
Then one email is sent to the user's email address using the user-provisioned template containing the temporaryPassword
And one in-app notification record is created for the user with is_read=false, event_key=USER_PROVISIONED, and action_url=null
And exactly one delivery audit log entry is created for channel=EMAIL with status=SENT
And NO real-time SSE signal is published for this event
And temporaryPassword does NOT appear in any log line or audit record
```

_(Traces: FR-02, FR-05, FR-06, FR-10, FR-11, FR-16, FR-17, FR-22, FR-23, FR-29, BR-06)_

### Scenario 3: StaffAssignedToWorkshopEvent triggers all three channels

```gherkin
Given a staff member has been assigned to a workshop
And the backend has published a StaffAssignedToWorkshopEvent with staffUserId, workshopId, workshopTitle, startsAt, location, and __TypeId__ header
When the notification pipeline consumes the message
Then one in-app notification record is created for the staff user with action_url="/workshops/{workshopId}"
And one real-time SSE signal with type notification.new is published to the Redis channel for staffUserId
And one email is sent to the staff user's email address using the staff-assigned template
And exactly one delivery audit log entry is created for channel=EMAIL with status=SENT
```

_(Traces: FR-02, FR-05, FR-06, FR-10, FR-11, FR-13, FR-14, FR-16, FR-22, FR-31)_

### Scenario 4: WorkshopCanceledEvent fans out to all confirmed-registered students

```gherkin
Given a workshop has been canceled
And there are 500 students with CONFIRMED registrations for that workshop
And 20 students have PENDING registrations and 5 have CANCELED registrations
And the backend has published a WorkshopCanceledEvent with __TypeId__ header
When the notification pipeline consumes the WorkshopCanceledEvent
Then exactly 500 in-app notification records are created with action_url="/workshops/{slug}" (one per confirmed student)
And exactly 500 real-time SSE signals are published (one per confirmed student)
And exactly 500 emails are sent (one per confirmed student)
And the 20 PENDING and 5 CANCELED students receive zero notifications
And exactly 500 delivery audit log entries are created for channel=EMAIL (one per confirmed student)
```

_(Traces: FR-02, FR-05, FR-08, FR-10, FR-11, FR-13, FR-16, FR-22, FR-34, BR-04)_

### Scenario 5: FileCompletedEvent fans out to active organizers of the organization

```gherkin
Given a batch ingestion file has been processed successfully for organizationId=ORG-123
And organizationId=ORG-123 has 3 active organizers and 1 inactive organizer
And the batch_ingestion worker has published a FileCompletedEvent with __TypeId__ header "FileCompletedEvent"
When the notification pipeline consumes the message
Then the event is routed to the FileCompleted handler via __TypeId__ header
And exactly 3 in-app notification records are created with action_url="/admin/import" (one per active organizer)
And exactly 3 real-time SSE signals are published
And exactly 3 emails are sent using the ingestion-completed template
And the inactive organizer receives zero notifications
And exactly 3 delivery audit log entries are created for channel=EMAIL
```

_(Traces: FR-02, FR-05, FR-07, FR-10, FR-11, FR-13, FR-16, FR-22, FR-35, BR-05)_

### Scenario 6: Fan-out event with zero recipients is silently skipped

```gherkin
Given a WorkshopRescheduledEvent is published for a workshop with zero CONFIRMED registrations
When the notification pipeline consumes the message
Then zero in-app notifications are created
And zero SSE signals are published
And zero emails are sent
And zero delivery audit log entries are created
And a warning log entry is emitted indicating zero recipients resolved
And the message is positively acknowledged (not dead-lettered)
```

_(Traces: FR-08, FR-09, FR-33)_

### Scenario 7: Email channel failure does not abort in-app and SSE delivery

```gherkin
Given a StaffRevokedFromWorkshopEvent is published for staffUserId=U-001
And the SMTP server is returning errors for all send attempts
When the notification pipeline consumes the message
Then one in-app notification record IS created for U-001 with action_url="/workshops"
And one real-time SSE signal IS published for U-001
And the email delivery attempt fails
And a delivery audit log entry is created for channel=EMAIL with status=FAILED and error_reason recorded
And the message is positively acknowledged (not dead-lettered, because in-app and SSE succeeded)
```

_(Traces: FR-11, FR-20, FR-22, FR-23, FR-27, FR-32, BR-12)_

### Scenario 8: Idempotency — duplicate message delivery does not create duplicate records

```gherkin
Given an OtpRequestedEvent was already successfully processed with idempotencyKey="OTP:U-001:1700000000"
And a delivery audit log entry exists for (idempotencyKey="OTP:U-001:1700000000", recipient_address="student@hcmus.edu.vn", channel=EMAIL, status=SENT)
When the broker redelivers the same message
Then no duplicate email is sent
And no duplicate delivery audit log entry is created
And the message is positively acknowledged
```

_(Traces: FR-05, FR-12, FR-21, FR-24, BR-12)_

### Scenario 9: Infrastructure failure causes NACK for broker retry

```gherkin
Given the PostgreSQL database is completely unreachable
And a UserStatusChangedEvent arrives on q.notifications
When the notification pipeline attempts to process the message
Then the pipeline negatively acknowledges the message without requeue
And the broker redelivers the message up to its configured retry limit
And after retry exhaustion the message is moved to unihub.dlq
And the message is not silently dropped
```

_(Traces: FR-25, FR-26, BR-12)_

### Scenario 10: Missing or unknown event type is dead-lettered

```gherkin
Given a message arrives on q.notifications with a missing or unmapped __TypeId__ header
When the notification pipeline processes the message
Then the message is dead-lettered to unihub.dlq
And a structured log entry is written containing the raw payload excerpt and the reason for rejection
And no in-app notification, SSE signal, or email is sent
```

_(Traces: FR-03)_

### Scenario 11: WorkshopRescheduledEvent fan-out uses paginated recipient resolution

```gherkin
Given a WorkshopRescheduledEvent is published for a workshop with 3000 CONFIRMED registrations
When the notification pipeline processes the message
Then recipients are resolved in pages (not all 3000 loaded into memory simultaneously)
And all 3000 students receive in-app, SSE, and email notifications
And memory usage during processing remains bounded
```

_(Traces: FR-08, FR-33, NFR-Performance)_

## 8. Out of Scope

- **Feature 7.2 (Notification Read API):** The REST endpoint that marks in-app notifications as read, and the API to list a user's notifications, are defined in Feature 7.2 and are NOT implemented by this pipeline.
- **Push notifications (FCM/APNs) and SMS:** The `notification_delivery_logs` table has columns for `PUSH` and `SMS` channels. This pipeline does NOT implement push or SMS delivery. Only `EMAIL` and `IN_APP` (+ SSE signal) are in scope.
- **User preference / opt-out:** There is no notification preference or unsubscribe mechanism in scope. All users in the recipient set receive all channels defined in the matrix, always.
- **Registration cancellation / refund notifications:** Events related to student self-cancellation of paid registrations and any associated refund notifications are not in scope for this feature.
- **Backend Monolith code changes:** This feature adds zero new code to `apps/backend/`. The backend already publishes all required events. No new `@Externalized` events, no new endpoints, no new modules are added to the monolith.
- **Email bounce and delivery receipt tracking:** Parsing SMTP delivery receipts, bounce notifications, or unsubscribe headers is out of scope. The pipeline records delivery attempt outcomes only.
- **Notification batching / digest:** All notifications are delivered individually and in real time. Batching multiple events into a single digest email is out of scope.
- **Admin UI for dead-letter queue:** Manual replay of messages from `unihub.dlq` is an operational concern outside this feature's scope.
- **AI-generated notification summaries:** Feature 7.1 does not involve the `ai_processing` worker module. Any AI-driven summarization is a separate feature.

## 9. Linked References

| Document                                                                                                                         | Section(s) consulted                                                                                                                                                      |
| :------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `docs/feature-catalog.md`                                                                                                        | Feature 7.1 entry, Feature 7.2 entry, Appendix A Feature Dependency Map                                                                                                   |
| `docs/system-design.md`                                                                                                          | Core System Flows (Registration, Workshop Lifecycle, Offline Sync), System Protection Mechanisms (circuit breaker, retry/DLQ, idempotency), RBAC sequence diagrams        |
| `docs/database-schema.md`                                                                                                        | `in_app_notifications` table, `notification_delivery_logs` table, `users` table, `registrations` table, Replication Lag & Read-After-Write Consistency section            |
| `docs/adrs.md`                                                                                                                   | ADR-010 (Security & Auth), ADR-020 (Resilience4j), ADR-021 (RabbitMQ / event-driven), ADR-025 (Spring Modulith @Externalized)                                             |
| `docs/project-structure.md`                                                                                                      | Hexagonal Architecture layout for `apps/workers/`, module isolation rules, `HexagonalArchitectureTest` enforcement                                                        |
| `docs/system-architecture.md`                                                                                                    | Component diagram — RabbitMQ, Redis, PostgreSQL, SMTP, worker process connections                                                                                         |
| `docs/api-standards.md`                                                                                                          | N/A — no REST endpoints in this feature                                                                                                                                   |
| `infra/rabbitmq/definitions.json.template`                                                                                       | `q.notifications` queue definition, `unihub.dlx`/`unihub.dlq` DLX config, exchange bindings for routing keys `notifications`, `workshop.rescheduled`, `workshop.canceled` |
| `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/inbound/SearchSyncListener.java`                             | Reference listener pattern (AMQP listener, NACK strategy)                                                                                                                 |
| `apps/workers/src/main/java/com/unihub/workshop/workers/search_sync/outbound/config/SearchSyncConfig.java`                       | Reference AMQP configuration pattern                                                                                                                                      |
| `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/messaging/RabbitMqIngestionEventPublisher.java` | Batch ingestion publisher to align with standard `__TypeId__` header                                                                                                      |
| `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/infrastructure/RedisRealtimePublisherAdapter.java`               | Reference Redis SSE publish pattern                                                                                                                                       |
| `apps/backend/src/main/java/com/unihub/workshop/shared/realtime/api/RealtimeEventType.java`                                      | Confirmed `NOTIFICATION_NEW("notification.new")` event type string                                                                                                        |
