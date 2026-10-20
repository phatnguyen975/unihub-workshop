# Spec: Paid Workshop Registration

> **Status:** APPROVED  
> **Domain:** 4 — Workshop Registration  
> **Owner/area:** `registrations` module (registration flow) + `payments` module (webhook flow) — both in `apps/backend/`  
> **Last revised:** 2026-10-15 — first version

## 1. Overview

This feature extends the unified workshop registration endpoint (shared with Feature 4.1) to support paid workshops: a student who registers for a workshop with a price greater than zero receives a `PENDING` registration and a Stripe Payment Intent client secret, which the client uses to complete the payment on the frontend. Once Stripe confirms the payment, an HMAC-verified webhook event is delivered to the system; a dedicated webhook handler verifies the signature, records the payment, atomically transitions the registration to `CONFIRMED`, issues a signed QR ticket, and publishes the `WorkshopRegisteredEvent` so the notification worker delivers the confirmation email with the QR PNG attachment to the student — identical to the free-registration delivery path. The two sub-flows are a single end-to-end business capability: registration creation (Feature 4.2) is only observable as "done" once the webhook confirms the payment (Feature 5.1).

## 2. Actors & Preconditions

### 2.1 Paid Registration Flow

| Actor     | Precondition                                                                                                                                                                         |
| :-------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `STUDENT` | Authenticated with a valid RS256 JWT carrying role `STUDENT`.                                                                                                                        |
| `STUDENT` | The student's `student_record_id` is non-null (linked university record). Unlinked accounts cannot register.                                                                         |
| `STUDENT` | The target workshop exists, has status `PUBLISHED`, has not yet started (`starts_at > now()`), has `price > 0`, and has at least one available seat (`registered_count < capacity`). |
| `STUDENT` | The student does not already have an active (`PENDING` or `CONFIRMED`) registration for the same workshop.                                                                           |
| `STUDENT` | The student has no confirmed registration for another workshop whose time slot overlaps with the target workshop's time slot.                                                        |
| System    | Redis is reachable (required for capacity reservation via atomic DECR and idempotency locking).                                                                                      |
| System    | Stripe API is reachable (required to create the Payment Intent). If Stripe is unreachable, the circuit breaker is engaged and the registration is not created.                       |
| System    | RabbitMQ is reachable (required for outbox event forwarding after webhook commit).                                                                                                   |

### 2.2 Payment Webhook Flow

| Actor           | Precondition                                                                                                                             |
| :-------------- | :--------------------------------------------------------------------------------------------------------------------------------------- |
| Stripe (System) | The HTTP request carries a valid `Stripe-Signature` header containing an HMAC-SHA256 signature computed with the webhook signing secret. |
| Stripe (System) | A `PENDING` registration exists matching the `registration_id` embedded in the Payment Intent metadata.                                  |
| Stripe (System) | A `PENDING` payment record exists matching the `provider_payment_id` (Stripe Payment Intent ID) in the webhook payload.                  |

## 3. Functional Requirements

### 3.1 Paid Registration Creation

- **FR-01:** The system SHALL create a `PENDING` registration record with `expires_at` set to current time plus the configured TTL (default 30 minutes, configurable via `app.registrations.pending-ttl-minutes`) when a `STUDENT` submits a registration request for a workshop with `price > 0`, all preconditions are satisfied, and no active registration exists for that workshop.
- **FR-02:** The system SHALL atomically decrement the workshop's available seat counter in Redis (DECR on `workshop:{workshopId}:capacity`) and increment `workshops.registered_count` in the database within the registration creation transaction. If the decrement results in a value below zero, the system SHALL compensate (INCR) and reject the request as a capacity conflict.
- **FR-03:** The system SHALL create a Stripe Payment Intent for the exact `price` of the workshop converted to the currency's smallest unit (e.g. cents for 2-decimal currencies like USD, or whole units for 0-decimal currencies like VND) via a dedicated currency unit converter, attaching the `registrationId` and `workshopId` as Payment Intent metadata, before the registration record is persisted.
- **FR-04:** The system SHALL persist a `PENDING` payment record in `payments` immediately after the `PENDING` registration is saved, capturing the Stripe `provider_payment_id` (`pi_xxx`) and workshop price. This record enables the Dual-Case Reconciliation Sweeper (Feature 4.3) to query Stripe using `provider_payment_id` for orphaned PENDING registrations. If an identical `provider_payment_id` already exists (retry race), the duplicate insert SHALL be silently swallowed (idempotent upsert via constraint catch).
- **FR-05:** The system SHALL forward the student's `Idempotency-Key` header value to Stripe as the Payment Intent creation idempotency key, ensuring that a retried Stripe API call for the same logical intent returns the same Payment Intent.
- **FR-06:** The system SHALL return a `201 Created` response containing the registration's `id`, `status: PENDING`, `priceAtRegistration`, `currency`, and the Stripe `paymentClientSecret` for the student's client to complete the payment on the frontend. QR fields (`qrPayload`, `qrSignature`, `qrIssuedAt`) SHALL be absent from this response (the registration is not yet confirmed).
- **FR-07:** The system SHALL reject the registration request with a `409 Conflict` when the workshop has zero remaining seats at the time of the capacity reservation attempt.
- **FR-08:** The system SHALL reject the registration request with a `409 Conflict` when the student already has an active (`PENDING` or `CONFIRMED`) registration for the same workshop.
- **FR-09:** The system SHALL reject the registration request with a `422 Unprocessable Entity` when the student has a `CONFIRMED` registration for another workshop whose scheduled time slot overlaps with the target workshop's time slot.
- **FR-10:** The system SHALL reject the registration request with a `404 Not Found` when no published workshop exists with the given `workshopId`.
- **FR-11:** The system SHALL reject the registration request with a `422 Unprocessable Entity` when the workshop exists but is not in `PUBLISHED` status, or has already started (i.e., `starts_at <= now()`).
- **FR-12:** The system SHALL reject the registration request with a `403 Forbidden` when the authenticated user's role is not `STUDENT` or when the student's `student_record_id` is `NULL`.
- **FR-13:** The system SHALL enforce the idempotency key lifecycle (L1 Redis lock + L2 DB `idempotency_keys` table) on the registration mutation to guarantee exactly-once semantics across client retries. A second request with the same `Idempotency-Key` while the first is in-flight SHALL return `409 Conflict` with `Retry-After: 2`. A second request after a successful first SHALL return the cached `201 Created` response without re-executing business logic.
- **FR-14:** The system SHALL apply Bucket4j rate limiting (5 requests / 10-second window / client IP) to the registration mutation endpoint. Requests exceeding the limit SHALL be rejected with `429 Too Many Requests`.
- **FR-15:** The system SHALL NOT open a database transaction during the Stripe API call. The Stripe Payment Intent creation must occur before the database transaction that persists the registration, so that no database connection is held while waiting for the Stripe network round-trip.
- **FR-16:** The system SHALL engage the Resilience4j circuit breaker around the Stripe Payment Intent creation call. When the circuit breaker is OPEN (due to repeated Stripe failures), the system SHALL reject the registration request with `503 Service Unavailable` and SHALL NOT create a registration record or decrement capacity.
- **FR-17:** The system SHALL store the workshop's `price` as `price_at_registration` on the registration record at the moment of creation, capturing the price snapshot independently of any future price changes to the workshop.
- **FR-18:** The system SHALL store the workshop's `currency` on the registration record at the moment of creation.

### 3.2 Payment Webhook Handling

- **FR-19:** The system SHALL verify the `Stripe-Signature` header against the raw request body using HMAC-SHA256 with the configured webhook signing secret before processing any webhook payload. Requests with an absent, malformed, or invalid signature SHALL be rejected with `400 Bad Request`.
- **FR-20:** The system SHALL respond with `200 OK` to Stripe for all webhook events it receives with a valid signature, regardless of whether the event type is processed or ignored. This prevents Stripe from retrying events that the system intentionally ignores.
- **FR-21:** The system SHALL process the `payment_intent.succeeded` event type: extract the `provider_payment_id` (Payment Intent ID) and `registration_id` from the event metadata, transition the existing `PENDING` payment record to `COMPLETED` (updating `status`, `amount`, `currency`, and `completed_at`), and atomically transition the registration from `PENDING` to `CONFIRMED`, generating and persisting the signed QR ticket fields (`qr_payload`, `qr_signature`, `qr_issued_at`) in the same database transaction. If no existing payment record is found (fallback path), a new `COMPLETED` record is inserted.
- **FR-22:** The system SHALL process the `payment_intent.payment_failed` event type: extract the `provider_payment_id` and `registration_id` from the event metadata, transition the existing `PENDING` payment record to `FAILED` (updating `status`, `failure_code`, `failure_reason`, and `failed_at`), atomically transition the registration from `PENDING` to `FAILED`, and compensate the workshop's available seat counter (INCR on `workshop:{workshopId}:capacity` in Redis and decrement `workshops.registered_count` in the database within the same transaction). If no existing payment record is found (fallback path), a new `FAILED` record is inserted.
- **FR-23:** The system SHALL guarantee exactly-once processing of each webhook event by checking the `payments.status` of the existing record before processing. If the `provider_payment_id` maps to a payment already in a **terminal state** (`COMPLETED`, `FAILED`, or `REFUNDED`), the webhook SHALL be acknowledged with `200 OK` without re-executing any side effects. A `PENDING` record indicates the normal first-delivery path and SHALL proceed to transition.
- **FR-24:** The system SHALL publish a `WorkshopRegisteredEvent` to the Spring Modulith outbox (within the same `PENDING → CONFIRMED` transition transaction) upon successful webhook confirmation. The notification worker SHALL consume this event to deliver a confirmation email containing the QR PNG attachment to the student.
- **FR-25:** The system SHALL publish real-time SSE events after the webhook transaction commits: a `payment.confirmed` event to the student's personal stream (`unihub:stream:user:{studentUserId}`) carrying the `registrationId`, `workshopId`, `qrPayload`, and `qrIssuedAt`; and a `registration.updated` event to the workshop's stream (`unihub:stream:workshop:{workshopId}`) with the updated seat counts.
- **FR-26:** The system SHALL NOT require authentication on the webhook endpoint. The `Stripe-Signature` HMAC verification (FR-19) is the sole authentication mechanism for this endpoint.
- **FR-27:** The system SHALL ignore and acknowledge (return `200 OK`) any Stripe event type other than `payment_intent.succeeded` and `payment_intent.payment_failed` without error.
- **FR-28:** The system SHALL log a structured warning and return `200 OK` (without failing) when a `payment_intent.succeeded` or `payment_intent.payment_failed` webhook arrives for a `registration_id` that does not exist or is not in `PENDING` status, to prevent Stripe from endlessly retrying stale events.

## 4. Business Rules

- **BR-01: Seat reservation before Stripe call.** The Redis capacity DECR occurs before the Stripe Payment Intent is created. If Stripe fails or the circuit breaker is OPEN, the DECR is compensated (INCR) and no registration record is created. This ensures capacity is never over-reserved due to Stripe errors. _(Source: `docs/system-design.md §Paid Registration Flow`)_
- **BR-02: No DB transaction during Stripe call.** The Stripe API call is made outside any database transaction. The registration is persisted only after Stripe responds successfully. This prevents long-lived DB connections during network-latency-dependent external calls. _(Source: `docs/adrs.md §ADR-020`, refactoring applied pre-spec)_
- **BR-03: Price snapshot at registration time.** `price_at_registration` and `currency` are captured from the workshop at the moment of registration creation, not read from the workshop record later. This decouples the financial record from any subsequent organizer edits to workshop pricing. _(Source: `docs/database-schema.md §registrations`)_
- **BR-04: QR ticket deferred to CONFIRMED.** `qr_payload`, `qr_signature`, and `qr_issued_at` are `NULL` for `PENDING` registrations and are set atomically only inside the `PENDING → CONFIRMED` transition transaction (on webhook confirmation). They are never set at initial registration creation for paid workshops. _(Source: `docs/database-schema.md §registrations`)_
- **BR-05: Webhook HMAC verification is mandatory.** Every request to the webhook endpoint must pass HMAC-SHA256 signature verification before any business logic executes. The raw request body (not a parsed/deserialized body) must be used for verification. _(Source: `docs/system-design.md §Payment Webhook Handling Flow`, `docs/adrs.md §ADR-021`)_
- **BR-06: Webhook always-200 contract.** The system must return `200 OK` to Stripe for any webhook with a valid signature, including events for unknown types or already-processed intents. Returning non-2xx causes Stripe to retry, which can lead to duplicate processing. The only valid non-200 response is `400 Bad Request` when signature verification fails. _(Source: `docs/system-design.md §Payment Webhook Handling Flow`)_
- **BR-07: Duplicate webhook idempotency via terminal-state check.** Before processing any webhook event, the system queries `payments` by `provider_payment_id`. If the record is already in a terminal state (`COMPLETED`, `FAILED`, `REFUNDED`), the system returns `200 OK` immediately without reprocessing any side effects (no state transition, no outbox event, no SSE). A `PENDING` record indicates the normal first-delivery path and proceeds with the state transition. The `payments.provider_payment_id` UNIQUE constraint remains as a concurrent-race safety net for duplicate PENDING inserts, not the primary idempotency mechanism. _(Source: `docs/database-schema.md §payments`, `docs/system-design.md §Payment Webhook Handling Flow`)_
- **BR-08: Capacity compensation on payment failure.** When `payment_intent.payment_failed` is received, the Redis capacity counter is incremented (INCR) and `workshops.registered_count` is decremented within the same `PENDING → FAILED` transition transaction. This returns the seat to the pool so other students can register. _(Source: `docs/system-design.md §Payment Webhook Handling Flow`)_
- **BR-09: PENDING registration expiry.** `registrations.expires_at` is populated at `PENDING` creation time with `now() + TTL`, where TTL defaults to 30 minutes and is configurable via `app.registrations.pending-ttl-minutes`. The Dual-Case Reconciliation Sweeper (a separate scheduled job, out of scope for this spec) relies on this column to expire abandoned checkouts and compensate capacity. _(Source: `docs/database-schema.md §registrations`, `docs/system-design.md §Payment Webhook Handling Flow`, `docs/adrs.md §ADR-022`)_
- **BR-10: Single active registration per workshop per student.** The partial unique index on `(workshop_id, student_user_id) WHERE status IN ('PENDING','CONFIRMED')` enforces this at the DB level. The application layer also checks before attempting the insert and returns `409 Conflict`. _(Source: `docs/database-schema.md §registrations`)_
- **BR-11: Time-slot conflict check.** A student cannot have two `CONFIRMED` registrations for workshops with overlapping time slots. PENDING registrations do not block overlap checks (since payment may fail). The check is on `CONFIRMED` status only. _(Source: `docs/feature-catalog.md §4.2`)_
- **BR-12: Stripe Payment Intent currency and amount conversion.** The Payment Intent must be created with the workshop's `currency` in lowercase (e.g. `"usd"`), and the amount must be converted from `workshops.price` into the smallest currency unit via a dedicated currency unit converter (e.g. multiplied by 100 for 2-decimal currencies such as USD, or factor 1 for 0-decimal currencies such as VND) to prevent floating-point rounding errors and ensure forward compatibility across currencies. _(Source: `docs/adrs.md §ADR-013`, Stripe API Specifications)_
- **BR-13: Circuit breaker on Stripe.** The Resilience4j circuit breaker wraps the Stripe Payment Intent creation call. When OPEN, the endpoint returns `503 Service Unavailable` immediately without calling Stripe. _(Source: `docs/system-design.md §System Protection Mechanisms`, `docs/adrs.md §ADR-019`)_
- **BR-14: PENDING payment record for sweeper reconciliation.** Immediately after the `PENDING` registration is persisted, a `PENDING` payment record with `provider_payment_id = pi_xxx` and the workshop `price` is inserted by `PaidRegistrationStrategy` via `RegistrationPaymentPort.recordPendingPayment()`. This record is essential for the Dual-Case Reconciliation Sweeper (Feature 4.3), which reads `payments.provider_payment_id` to query Stripe for orphaned PENDING registrations whose webhooks were lost. If the insert races with a concurrent retry and violates the `payments.provider_payment_id` UNIQUE constraint, the resulting `DataIntegrityViolationException` is swallowed as an idempotent no-op. _(Source: `docs/feature-catalog.md §4.2`, `docs/system-design.md §Paid Registration Flow`)_

## 5. State Transitions

### 5.1 Registration Entity (`registrations.status`)

| From State  | Event / Trigger                                                                  | To State     |      Valid?       | Rejection Behavior (if invalid)                                                             |
| :---------- | :------------------------------------------------------------------------------- | :----------- | :---------------: | :------------------------------------------------------------------------------------------ |
| _(none)_    | Student submits paid registration; all preconditions met; Stripe succeeds        | `PENDING`    |        ✅         | N/A                                                                                         |
| _(none)_    | Student submits paid registration; capacity = 0                                  | _(rejected)_ |        ❌         | `409 Conflict` — "No seats remaining for this workshop."                                    |
| _(none)_    | Student submits paid registration; duplicate active registration                 | _(rejected)_ |        ❌         | `409 Conflict` — "Student is already registered for this workshop."                         |
| _(none)_    | Student submits paid registration; time-slot overlap with confirmed registration | _(rejected)_ |        ❌         | `422 Unprocessable Entity` — time-slot overlap error.                                       |
| _(none)_    | Student submits paid registration; workshop not PUBLISHED or already started     | _(rejected)_ |        ❌         | `422 Unprocessable Entity` — workshop not eligible.                                         |
| _(none)_    | Student submits paid registration; Stripe circuit breaker OPEN                   | _(rejected)_ |        ❌         | `503 Service Unavailable` — registration not created; capacity not reserved.                |
| `PENDING`   | Stripe webhook `payment_intent.succeeded` received with valid signature          | `CONFIRMED`  |        ✅         | N/A — QR fields set atomically, outbox event published                                      |
| `PENDING`   | Stripe webhook `payment_intent.payment_failed` received with valid signature     | `FAILED`     |        ✅         | N/A — capacity compensated, registration marked FAILED                                      |
| `CONFIRMED` | Any webhook for the same `provider_payment_id` arrives again (duplicate)         | `CONFIRMED`  |    ✅ (no-op)     | Detected via DB unique constraint; return `200 OK` without re-executing transition          |
| `CONFIRMED` | Attempt to transition to any other state (e.g., from another webhook)            | _(rejected)_ |        ❌         | Registration is already in terminal state; no state change; log warning; `200 OK` to Stripe |
| `FAILED`    | Any further state transition attempt                                             | _(rejected)_ |        ❌         | `FAILED` is terminal; no state change; log warning; `200 OK` to Stripe                      |
| `PENDING`   | Reconciliation sweeper expires PENDING registration                              | `FAILED`     | ✅ (out of scope) | Handled by sweeper (not this feature); capacity compensated.                                |
| `CANCELED`  | (Not applicable in this feature — no cancellation)                               | N/A          |        N/A        | Out of scope.                                                                               |

### 5.2 Payment Entity (`payments.status`)

| From State  | Event / Trigger                                                                | To State            |      Valid?       | Rejection Behavior (if invalid)                                                      |
| :---------- | :----------------------------------------------------------------------------- | :------------------ | :---------------: | :----------------------------------------------------------------------------------- |
| _(none)_    | `PaidRegistrationStrategy` persists payment after Stripe PaymentIntent created | `PENDING`           |        ✅         | N/A — idempotent; duplicate insert swallowed via `DataIntegrityViolationException`   |
| `PENDING`   | Stripe webhook `payment_intent.succeeded` processed                            | `COMPLETED`         |        ✅         | N/A — `completed_at` set atomically                                                  |
| `PENDING`   | Stripe webhook `payment_intent.payment_failed` processed                       | `FAILED`            |        ✅         | N/A — `failed_at`, `failure_code`, `failure_reason` set atomically                   |
| `COMPLETED` | Duplicate webhook for same `provider_payment_id` (terminal-state check)        | `COMPLETED` (no-op) |    ✅ (no-op)     | Terminal-state detected; no transition; no outbox event; `200 OK` returned to Stripe |
| `COMPLETED` | Attempt to transition to `FAILED`                                              | _(rejected)_        |        ❌         | Not permitted — `COMPLETED` is terminal; log warning; `200 OK` to Stripe             |
| `FAILED`    | Attempt to transition to any other state                                       | _(rejected)_        |        ❌         | Not permitted — `FAILED` is terminal; log warning; `200 OK` to Stripe                |
| `COMPLETED` | Refund initiated (future feature)                                              | `REFUNDED`          | ✅ (out of scope) | Out of scope for this spec.                                                          |

## 6. Non-Functional Requirements

- **Performance:** The Stripe Payment Intent creation call (FR-03) is the dominant latency contributor. The registration creation transaction (persist PENDING registration) must not hold the database connection during this call (BR-02). P99 for the full registration endpoint including Stripe call should be under 3 seconds under normal load.
- **Concurrency:** The system must remain correct under 12,000+ concurrent registration attempts within a 10-minute window (project constraint). Redis atomic DECR (FR-02) is the primary concurrency guard for seat reservation. The DB partial unique index on `(workshop_id, student_user_id) WHERE status IN ('PENDING','CONFIRMED')` prevents duplicate active registrations at the DB level as a secondary guard.
- **Idempotency:** The `Idempotency-Key` header is mandatory on the registration endpoint (FR-13). The two-tier mechanism (L1 Redis + L2 DB `idempotency_keys`) guarantees exactly-once semantics for registration creation across client retries and server restarts. Webhook idempotency is guaranteed by a terminal-state check on the existing `payments` record (FR-23, BR-07); the `payments.provider_payment_id` UNIQUE constraint acts as a concurrent-race safety net for duplicate PENDING inserts.
- **Rate-limiting:** Bucket4j Token Bucket at 5 requests / 10-second window / client IP for the registration mutation endpoint (FR-14). No rate limiting on the webhook endpoint (Stripe's delivery is already controlled).
- **Security:** The webhook endpoint is unauthenticated (no JWT required). It MUST verify the `Stripe-Signature` HMAC before processing any event. The raw byte body must be preserved for verification — any body parsing/deserialization before verification invalidates the HMAC. The `Stripe-Webhook-Secret` must be loaded from environment variable (`STRIPE_WEBHOOK_SECRET`), never hardcoded.
- **Reliability / Failure modes:**
  - If Stripe is unreachable and the circuit breaker trips OPEN → `503 Service Unavailable`; capacity is not reserved; no registration created.
  - If Redis is unreachable during capacity DECR → `503 Service Unavailable`; Stripe is not called; no registration created.
  - If the DB transaction fails after Stripe returns a Payment Intent → the Stripe Payment Intent exists orphaned; the student will not receive a `paymentClientSecret`; they must retry (idempotency key returns cached response if the first request created the DB record, or re-runs full logic if the DB commit failed). The reconciliation sweeper (out of scope) handles orphaned PENDING registrations via `expires_at`.
  - If the webhook transaction fails after the DB `CONFIRMED` write → the outbox event and SSE publish are post-commit side effects, so they will not fire. The Spring Modulith outbox guarantees at-least-once delivery of the `WorkshopRegisteredEvent` on the next outbox poll; SSE is fire-and-forget (the student can poll `GET /api/v1/registrations/{id}` to observe the confirmed state).
- **Observability:** All registration and payment state transitions must be logged at `INFO` level with `traceId`, `registrationId`, and `workshopId`. All Stripe call failures must be logged at `WARN` or `ERROR` with the Stripe error code. Duplicate webhook receipts must be logged at `INFO` level. Micrometer metrics should capture circuit breaker state, Stripe call latency, and webhook processing latency.

## 7. Acceptance Criteria

### Scenario 1: Successful paid registration — PENDING created with client secret

```gherkin
Given a STUDENT is authenticated and has a linked student_record_id
And a workshop exists with status PUBLISHED, price = 50.00 USD, available seats > 0, starts_at in the future
And the student has no existing registration for this workshop
When the student submits POST /api/v1/registrations with workshopId and a valid Idempotency-Key
Then the response status is 201 Created
And the response body contains status = "PENDING"
And the response body contains priceAtRegistration = 50.00
And the response body contains currency = "USD"
And the response body contains a non-null paymentClientSecret
And the response body does NOT contain qrPayload, qrSignature, or qrIssuedAt
And a PENDING registration record exists in the database
And the workshop's available seat counter in Redis is decremented by 1
And the Stripe Payment Intent was created with the registrationId in its metadata
```

_Traces to: FR-01, FR-02, FR-03, FR-06, FR-17, FR-18_

### Scenario 2: Idempotent retry — second request with same Idempotency-Key returns cached 201

```gherkin
Given a STUDENT previously submitted a registration request that succeeded (201 Created)
And the idempotency key is cached in Redis as COMPLETED
When the student submits POST /api/v1/registrations again with the same workshopId and the same Idempotency-Key
Then the response status is 201 Created
And the response body is identical to the first response
And no new registration record is created
And Stripe is not called again
```

_Traces to: FR-13_

### Scenario 3: In-flight idempotency conflict — 409 while first request is processing

```gherkin
Given a STUDENT submitted a registration request and the Idempotency-Key is in Redis with status PENDING (first request still in-flight)
When the student submits a second POST /api/v1/registrations with the same Idempotency-Key
Then the response status is 409 Conflict
And the response includes Retry-After: 2
And the detail message indicates "A request with this Idempotency-Key is already in progress."
```

_Traces to: FR-13_

### Scenario 4: Capacity exhausted — 409 Conflict

```gherkin
Given a STUDENT is authenticated
And a workshop exists with status PUBLISHED, price > 0, and zero available seats in Redis
When the student submits POST /api/v1/registrations for that workshop
Then the response status is 409 Conflict
And the detail message indicates "No seats remaining for this workshop."
And no registration record is created
And the Redis capacity counter is not decremented below zero (compensated)
```

_Traces to: FR-07, BR-01_

### Scenario 5: Duplicate active registration — 409 Conflict

```gherkin
Given a STUDENT already has a PENDING or CONFIRMED registration for a workshop
When the student submits POST /api/v1/registrations for the same workshopId
Then the response status is 409 Conflict
And the detail message indicates "Student is already registered for this workshop."
And no new registration record is created
```

_Traces to: FR-08, BR-10_

### Scenario 6: Time-slot overlap with a CONFIRMED registration — 422 Unprocessable Entity

```gherkin
Given a STUDENT has a CONFIRMED registration for Workshop A (09:00–11:00 on the same day)
And Workshop B is a paid workshop with time slot 10:00–12:00 on the same day (overlapping with Workshop A)
When the student submits POST /api/v1/registrations for Workshop B
Then the response status is 422 Unprocessable Entity
And the detail message indicates a time-slot overlap
And no registration record is created for Workshop B
```

_Traces to: FR-09, BR-11_

### Scenario 7: Stripe circuit breaker OPEN — 503 Service Unavailable

```gherkin
Given the Resilience4j circuit breaker for Stripe is in OPEN state
When a STUDENT submits POST /api/v1/registrations for a paid workshop
Then the response status is 503 Service Unavailable
And no registration record is created
And the Redis capacity counter is NOT decremented
And the Stripe API is NOT called
```

_Traces to: FR-16, BR-13_

### Scenario 8: Rate limit exceeded — 429 Too Many Requests

```gherkin
Given a client IP has submitted 5 registration requests within the last 10 seconds
When the same IP submits a 6th POST /api/v1/registrations within the same window
Then the response status is 429 Too Many Requests
And the response includes X-RateLimit-Retry-After
```

_Traces to: FR-14_

### Scenario 9: Webhook payment_intent.succeeded — registration confirmed, QR issued, notification sent

```gherkin
Given a PENDING registration exists with registrationId "R1" and price_at_registration = 50.00 USD
And a Stripe webhook payload of type payment_intent.succeeded arrives for the Payment Intent linked to "R1"
And the Stripe-Signature header is valid
When the webhook is processed
Then the response status is 200 OK
And a COMPLETED payment record is persisted with the provider_payment_id
And the registration "R1" status transitions to CONFIRMED
And qr_payload, qr_signature, and qr_issued_at are set atomically on registration "R1"
And a WorkshopRegisteredEvent is published to the outbox within the same transaction
And after commit, a payment.confirmed SSE event is published to unihub:stream:user:{studentId}
And after commit, a registration.updated SSE event is published to unihub:stream:workshop:{workshopId}
And the notification worker delivers a confirmation email with the QR PNG attachment to the student
```

_Traces to: FR-21, FR-23, FR-24, FR-25, BR-04, BR-06_

### Scenario 10: Webhook payment_intent.payment_failed — registration failed, capacity restored

```gherkin
Given a PENDING registration "R1" for a paid workshop with 1 reserved seat
And a Stripe webhook payload of type payment_intent.payment_failed arrives for the Payment Intent linked to "R1"
And the Stripe-Signature header is valid
When the webhook is processed
Then the response status is 200 OK
And a FAILED payment record is persisted
And the registration "R1" status transitions to FAILED
And the workshop's available seat counter in Redis is incremented by 1 (capacity restored)
And workshops.registered_count is decremented by 1 in the same transaction
And NO WorkshopRegisteredEvent is published
```

_Traces to: FR-22, BR-08_

### Scenario 11: Duplicate webhook delivery — idempotent, no double-processing

```gherkin
Given a payment_intent.succeeded webhook has already been successfully processed for Payment Intent "PI-1"
And registration "R1" is already in CONFIRMED status
And a payment record for "PI-1" already exists with status COMPLETED
When the same payment_intent.succeeded webhook is delivered again (Stripe retry)
Then the response status is 200 OK
And no new payment record is created (DB unique constraint prevents duplicate)
And no new CONFIRMED transition is triggered
And no additional WorkshopRegisteredEvent is published
And the student is NOT sent a duplicate confirmation email
```

_Traces to: FR-23, BR-07_

### Scenario 12: Webhook with invalid Stripe-Signature — 400 Bad Request

```gherkin
Given a POST request to POST /api/v1/payments/webhook with a forged or missing Stripe-Signature header
When the system attempts HMAC verification
Then the response status is 400 Bad Request
And no event processing occurs
And no database records are created or modified
```

_Traces to: FR-19, BR-05_

### Scenario 13: Webhook for unknown event type — acknowledged, ignored

```gherkin
Given a Stripe webhook payload of type customer.subscription.created arrives
And the Stripe-Signature header is valid
When the webhook is processed
Then the response status is 200 OK
And no business logic is executed
And no database records are created or modified
```

_Traces to: FR-20, FR-27, BR-06_

### Scenario 14: Webhook for registration that is not in PENDING status — graceful no-op

```gherkin
Given a payment_intent.succeeded webhook arrives for a registration "R1" that is already CONFIRMED (stale event)
And the Stripe-Signature is valid
When the webhook is processed
Then the response status is 200 OK
And a structured warning is logged with registrationId and the unexpected state
And no state transition occurs
And no duplicate outbox event is published
```

_Traces to: FR-28, BR-06_

### Scenario 15: Student with unlinked profile — 403 Forbidden

```gherkin
Given a STUDENT is authenticated but has student_record_id = NULL
When the student submits POST /api/v1/registrations for a paid workshop
Then the response status is 403 Forbidden
And the detail message indicates the profile is not linked
And no registration record is created
```

_Traces to: FR-12_

### Scenario 16: Workshop not PUBLISHED — 422 Unprocessable Entity

```gherkin
Given a workshop exists with status DRAFT (or CANCELED, or COMPLETED)
When a STUDENT submits POST /api/v1/registrations for that workshop
Then the response status is 422 Unprocessable Entity
And no registration record is created
```

_Traces to: FR-11_

### Scenario 17: Workshop already started — 422 Unprocessable Entity

```gherkin
Given a PUBLISHED workshop whose starts_at is in the past
When a STUDENT submits POST /api/v1/registrations for that workshop
Then the response status is 422 Unprocessable Entity
And no registration record is created
```

_Traces to: FR-11_

## 8. Out of Scope

- **Dual-Case Reconciliation Sweeper:** The scheduled background job that expires `PENDING` registrations after `expires_at` and compensates capacity. Referenced in BR-09 and `docs/database-schema.md`, but its implementation is a separate feature.
- **Payment cancellation / refunds:** The `REFUNDED` payment status exists in the DB schema but the cancellation and refund flows are separate features.
- **Payment history API:** Querying a student's payment records via a REST endpoint is out of scope for this feature.
- **Organizer payment dashboard:** Viewing payment status per workshop registration from the organizer side is out of scope.
- **Stripe webhook event types beyond `payment_intent.succeeded` and `payment_intent.payment_failed`:** Other webhook event types are acknowledged (always-200) but not processed.
- **ChaosMockAdapter for Stripe resilience testing:** The `ChaosMockAdapter` interface exists in `shared/resilience/` and can be wired to the Stripe adapter during testing; defining its configuration for Stripe is not a deliverable of this spec.
- **Mobile offline check-in:** Handled by a separate feature. This feature only ensures the QR is issued and included in the email.
- **Free workshop registration changes:** Feature 4.1 behavior is unchanged; this spec is additive only.

## 9. Linked References

| Document                                           | Section                         | Relevance                                                                              |
| :------------------------------------------------- | :------------------------------ | :------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                          | §4.2 Paid Workshop Registration | Feature scope, module ownership, DB tables, status `READY`, merge note with §5.1       |
| `docs/feature-catalog.md`                          | §5.1 Payment Webhook Handling   | Feature scope, merge note with §4.2                                                    |
| `docs/system-design.md`                            | §Paid Registration Flow         | End-to-end flow: capacity reservation → Stripe call → PENDING insert                   |
| `docs/system-design.md`                            | §Payment Webhook Handling Flow  | End-to-end flow: HMAC verify → payment record → CONFIRMED transition → outbox → SSE    |
| `docs/system-design.md`                            | §System Protection Mechanisms   | Rate limiting, circuit breaker, idempotency, overbooking prevention                    |
| `docs/database-schema.md`                          | §registrations                  | Column constraints, partial unique index, QR fields, expires_at, price_at_registration |
| `docs/database-schema.md`                          | §payments                       | Column constraints, provider_payment_id UNIQUE, status enum                            |
| `docs/adrs.md`                                     | ADR-013                         | Stripe as payment gateway                                                              |
| `docs/adrs.md`                                     | ADR-014                         | Stripe Payment Intents API (client-side confirmation)                                  |
| `docs/adrs.md`                                     | ADR-017                         | Redis atomic DECR for seat reservation                                                 |
| `docs/adrs.md`                                     | ADR-018                         | Two-tier idempotency (Redis + DB)                                                      |
| `docs/adrs.md`                                     | ADR-019                         | Resilience4j circuit breaker for external calls                                        |
| `docs/adrs.md`                                     | ADR-020                         | Transaction scope — no DB connection held during Stripe call                           |
| `docs/adrs.md`                                     | ADR-021                         | Stripe webhook HMAC verification                                                       |
| `docs/adrs.md`                                     | ADR-022                         | Always-200 webhook acknowledgment pattern                                              |
| `docs/api-standards.md`                            | §HTTP Status Codes              | Status code assignments                                                                |
| `docs/api-standards.md`                            | §Mandatory Headers              | Idempotency-Key, Stripe-Signature requirements                                         |
| `docs/api-standards.md`                            | §SSE Unified Event Catalog      | `payment.confirmed` and `registration.updated` event contracts                         |
| `specs/free-workshop-registration/SPEC.md`         | All                             | Dependency pattern: strategy selection, capacity guard, idempotency lifecycle          |
| `specs/free-workshop-registration/API_CONTRACT.md` | §POST /api/v1/registrations     | Shared endpoint — additive extension for paid branch                                   |
