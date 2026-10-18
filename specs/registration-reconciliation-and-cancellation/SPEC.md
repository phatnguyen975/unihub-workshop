# Spec: Registration Reconciliation & Cancellation

> **Status:** APPROVED  
> **Domain:** 4 — Workshop Registration  
> **Owner/area:** `registrations` module (sweeper + cancellation) in `apps/backend/`, with `payments` module (gateway status query / cancel-attempt / refund, via the registrations outbound SPI) and `notifications` worker in `apps/workers/` (cancellation delivery)  
> **Last revised:** 2026-10-18 — first version

## 1. Overview

This feature covers two complementary capabilities that close the registration lifecycle. (1) A **Dual-Case Reconciliation Sweeper**: a periodic background job that resolves `PENDING` paid registrations whose expiry has passed (webhook lost, or checkout abandoned) by asking the payment gateway for the real payment outcome — confirming the registration and issuing the QR ticket if the student actually paid (Delayed Webhook Recovery), or failing it and returning the reserved seat otherwise (Standard Cleanup). (2) A **unified registration cancellation** capability usable by both the owning `STUDENT` and an `ORGANIZER` of the workshop's organization: a `CONFIRMED` registration can be canceled before the workshop starts, the seat is returned to the pool, a paid registration is automatically refunded in full through the payment gateway, and the student is notified asynchronously. The sweeper is a fallback only; the payment webhook (Feature 5.1) remains the primary confirmation path (ADR-022).

## 2. Actors & Preconditions

| Actor                  | Precondition                                                                                                                                                                                                    |
| :--------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `STUDENT`              | Authenticated with a valid RS256 JWT with role `STUDENT`; is the owner of the target registration.                                                                                                              |
| `ORGANIZER`            | Authenticated with a valid RS256 JWT with role `ORGANIZER` whose organization matches the organization owning the registration's workshop.                                                                      |
| Reconciliation Sweeper | Scheduled system process; runs on every backend node, but only one node executes per cycle.                                                                                                                     |
| Payment Gateway        | Reachable for status query, payment-attempt cancellation, and refund. If unreachable, the affected operation fails safely (see FR-10, FR-25).                                                                   |
| System                 | Redis (capacity counter, distributed lock, idempotency L1), PostgreSQL primary, and RabbitMQ (outbox forwarding) are available. Redis/RabbitMQ outages after commit never roll back an already-committed state. |
| `notifications` worker | Consumes the cancellation event from the broker and delivers email / in-app / real-time notifications.                                                                                                          |

## 3. Functional Requirements

### 3.1 Reconciliation Sweeper

- **FR-01:** The system SHALL run the reconciliation sweeper periodically at a configurable schedule whose default is every 30 minutes.
- **FR-02:** The system SHALL ensure that, when multiple backend instances are running, at most one instance executes a given sweeper cycle.
- **FR-03:** The system SHALL select for reconciliation only registrations in `PENDING` status whose expiry timestamp is in the past, oldest expiry first, limited to a configurable maximum batch size per cycle (default 100); remaining registrations are picked up by the next cycle.
- **FR-04:** The system SHALL process each selected registration independently, such that a failure while processing one registration does not prevent the remaining registrations in the batch from being processed.
- **FR-05:** The system SHALL query the payment gateway for the real payment outcome of each selected registration that has a payment record, using the gateway payment identifier stored on that record, and SHALL NOT hold a database transaction open during this call.
- **FR-06 (Case A — Delayed Webhook Recovery):** The system SHALL, when the gateway reports the payment as succeeded, atomically in one transaction: transition the registration from `PENDING` to `CONFIRMED`, populate the signed QR ticket fields (`qr_payload`, `qr_signature`, `qr_issued_at`), transition the payment record to `COMPLETED` with its completion timestamp, record a registration-confirmed event in the transactional outbox, and mark the registration's idempotency key `COMPLETED`.
- **FR-07:** The system SHALL, after the Case A transaction commits, publish a `registration.updated` real-time event to the workshop channel and a `payment.confirmed` real-time event to the student's personal channel, identical in shape to those published by the webhook path.
- **FR-08 (Case B — Standard Cleanup):** The system SHALL, when the gateway reports the payment as failed, abandoned (awaiting a payment method or confirmation), canceled, or unknown to the gateway, first request the gateway to cancel the outstanding payment attempt, and then atomically in one transaction: transition the registration from `PENDING` to `FAILED`, transition the payment record to `FAILED` with a failure reason, and mark the registration's idempotency key `FAILED`.
- **FR-09:** The system SHALL, after the Case B transaction commits, return exactly one reserved seat to the workshop's Redis capacity counter.
- **FR-10:** The system SHALL leave a `PENDING` registration completely unchanged (no state change, no capacity change, no event) and SHALL log the condition when the gateway cannot be reached, its circuit breaker is open, or the gateway returns an error for that registration; the registration SHALL be re-evaluated on a later cycle.
- **FR-11:** The system SHALL leave a `PENDING` registration unchanged when the gateway reports the payment as still being processed, and SHALL re-evaluate it on a later cycle.
- **FR-12:** The system SHALL treat a payment-attempt cancellation that reveals the payment has meanwhile succeeded as Case A (FR-06, FR-07) instead of Case B.
- **FR-13:** The system SHALL treat an expired `PENDING` registration that has no payment record as Case B without querying the gateway (no payment attempt was ever created), and SHALL NOT create a payment record for it.
- **FR-14:** The system SHALL, immediately before applying any sweeper transition, verify the registration is still `PENDING`; if it has already left `PENDING` (for example, resolved concurrently by the webhook), the system SHALL apply no state change, no capacity change, and no event for it.
- **FR-15:** The system SHALL NOT emit a registration-confirmed event, QR ticket, or confirmation notification for registrations resolved as Case B.

### 3.2 Cancellation — Authentication & Authorization

- **FR-16:** The system SHALL allow an authenticated `STUDENT` or `ORGANIZER` to request cancellation of a single registration identified by its identifier.
- **FR-17:** The system SHALL reject a cancellation request without a valid, unexpired, non-revoked JWT with `401 Unauthorized`.
- **FR-18:** The system SHALL reject a cancellation request from any authenticated role other than `STUDENT` or `ORGANIZER` with `403 Forbidden`.
- **FR-19:** The system SHALL reject a cancellation request with `404 Not Found` when no registration exists with the supplied identifier.
- **FR-20:** The system SHALL reject a cancellation request from a `STUDENT` with `403 Forbidden` when the registration does not belong to that student.
- **FR-21:** The system SHALL reject a cancellation request from an `ORGANIZER` with `403 Forbidden` when the organization owning the registration's workshop differs from the organizer's own organization.
- **FR-22:** The system SHALL evaluate authorization (FR-20, FR-21) before any state or time validation (FR-24 – FR-27), so that a caller without rights over the registration learns nothing about its state.

### 3.3 Cancellation — Request Validation & Eligibility

- **FR-23:** The system SHALL accept an optional free-text `reason` in the cancellation request; after trimming it MUST NOT exceed 1000 characters (`422 Unprocessable Content` otherwise), and a blank or whitespace-only value SHALL be treated as absent.
- **FR-24:** The system SHALL reject cancellation with `422 Unprocessable Content` when the registration is not in `CONFIRMED` status, using a distinct reason for each of `CANCELED` ("already canceled"), `FAILED` ("cannot cancel a failed registration"), and `PENDING` ("only confirmed registrations can be canceled").
- **FR-25:** The system SHALL reject cancellation with `422 Unprocessable Content` when the registration's workshop is not in `PUBLISHED` status (including `CANCELED`, `COMPLETED`, `DRAFT`).
- **FR-26:** The system SHALL reject cancellation with `422 Unprocessable Content` when the workshop has already started or ended (`starts_at <= now()`).
- **FR-27:** The system SHALL require an `Idempotency-Key` on cancellation requests, rejecting a missing or malformed key with `422 Unprocessable Content`.
- **FR-28:** The system SHALL, when a cancellation request arrives with an `Idempotency-Key` that is already being processed, reject it with `409 Conflict` and a `Retry-After` hint; and when the key already completed successfully, return the original successful response without re-executing any side effect.
- **FR-29:** The system SHALL apply the same per-client rate limit as other registration mutation requests to cancellation requests, rejecting excess requests with `429 Too Many Requests`.

### 3.4 Cancellation — Refund

- **FR-30:** The system SHALL cancel a free registration (`price_at_registration = 0`) without any interaction with the payment gateway.
- **FR-31:** The system SHALL, for a paid registration (`price_at_registration > 0`), request a refund of 100% of the amount paid from the payment gateway **before** changing any local state, and SHALL NOT hold a database transaction open during the gateway call.
- **FR-32:** The system SHALL ensure the gateway refund request is deduplicated across retries of the same cancellation, so a student is never refunded twice for the same registration.
- **FR-33:** The system SHALL reject the cancellation with `503 Service Unavailable` (with a retry hint) when the gateway refund fails, times out, or its circuit breaker is open, and in that case SHALL leave the registration, the payment record, the capacity counter, and the outbox unchanged and SHALL release the idempotency key for retry.
- **FR-34:** The system SHALL refuse to cancel a paid `CONFIRMED` registration that has no payment record in `COMPLETED` status, leaving all state unchanged and reporting an internal error.

### 3.5 Cancellation — Execution & Side Effects

- **FR-35:** The system SHALL, once eligibility (and, for paid registrations, the refund) has succeeded, atomically in one transaction: transition the registration from `CONFIRMED` to `CANCELED` recording the caller as the actor who last updated it; for paid registrations transition the payment record to `REFUNDED`; record a registration-canceled event in the transactional outbox; and mark the idempotency key `COMPLETED` with the response snapshot.
- **FR-36:** The system SHALL include in the registration-canceled event the registration, workshop and student identifiers and snapshots needed for notification (student email and name, workshop title, slug, schedule, location), the optional `reason`, the role of the actor who canceled (`STUDENT` or `ORGANIZER`), and — for paid registrations — the refunded amount and currency. The `reason` is NOT persisted in any table.
- **FR-37:** The system SHALL, after the cancellation transaction commits, return exactly one reserved seat to the workshop's Redis capacity counter; a Redis failure at this point SHALL NOT fail the request or roll back the cancellation.
- **FR-38:** The system SHALL, after the cancellation transaction commits, publish a `registration.updated` real-time event to the workshop channel.
- **FR-39:** The system SHALL respond to a successful cancellation with `200 OK` and the registration representation showing status `CANCELED`; QR fields SHALL be omitted, and for paid registrations the response SHALL include the refunded amount and currency.
- **FR-40:** The system SHALL permit exactly one of several concurrent cancellation requests for the same registration to succeed; the others SHALL be rejected as in FR-24 and SHALL NOT trigger a second refund, a second capacity return, or a second event.

### 3.6 Cancellation — Notification Delivery (Worker)

- **FR-41:** The `notifications` worker SHALL, on consuming a registration-canceled event, create an in-app notification for the student, send a cancellation email to the student (including the reason when present, who canceled, and refund details for paid registrations), and emit a real-time event to the student's personal channel.
- **FR-42:** The `notifications` worker SHALL skip with a logged warning, without failing or blocking the queue, any registration-canceled event missing the registration or student identifier, and SHALL tolerate redelivery of the same event without duplicating the in-app notification or email.

## 4. Business Rules

- **BR-01 (Cancellation window):** A registration may be canceled only while the workshop has not started. Once `starts_at <= now()`, cancellation is closed for both roles. _(Source: feature-catalog §4.3 — human-confirmed 2026-10-18.)_
- **BR-02 (Only `CONFIRMED` is cancelable):** `PENDING` registrations are never canceled by this capability; they resolve through payment outcome or the sweeper. `FAILED` and `CANCELED` are terminal. _(Source: feature-catalog §4.3 — human-confirmed.)_
- **BR-03 (Full refund, no policy tiers):** A paid cancellation always refunds 100% of the amount paid, regardless of caller role or proximity to the start time. No partial refund, fee, or manual-processing mode exists. _(Source: human decision 2026-10-18.)_
- **BR-04 (Refund before state change; gateway outside transaction):** The gateway is called before the cancellation transaction and never inside a database transaction; a gateway failure leaves local state untouched. _(Source: ADR-020; spec `paid-workshop-registration` BR-02.)_
- **BR-05 (Refund exactly-once):** Retrying a cancellation after a partial failure (for example refund succeeded, local commit failed) must converge on a single refund and a single `CANCELED` transition. _(Source: ADR-014; `docs/system-design.md` §6 Transaction Idempotency.)_
- **BR-06 (Capacity returned exactly once per transition):** Each transition into `FAILED` (sweeper) or `CANCELED` (cancellation) returns exactly one seat, after commit. Capacity drift caused by a post-commit Redis failure is repaired by the existing Capacity Reconciliation Job (Feature 2.5). _(Source: feature-catalog §4.3; ADR-013.)_
- **BR-07 (Reason is transient):** `reason` is carried only in the outbox event, never stored. The registrations data model has no column for it. _(Source: `docs/database-schema.md` `registrations`; spec `workshop-cancellation` BR-03 precedent.)_
- **BR-08 (Tenant isolation for organizers):** An organizer may act only on registrations of workshops of their own organization. _(Source: ADR-006.)_
- **BR-09 (Attendance guard replaced by time guard):** Because cancellation is closed once the workshop starts (BR-01) and check-in cannot meaningfully precede the start, no attendance lookup is performed. The attendance module does not yet exist and registrations must not depend on it. _(Source: human decision 2026-10-18.)_
- **BR-10 (Re-registration allowed):** After cancellation the student no longer holds an active registration and may register again for the same workshop (new idempotency key), subject to all normal registration rules. _(Source: `docs/database-schema.md` partial unique index on `PENDING`/`CONFIRMED` only.)_
- **BR-11 (Sweeper is fallback-only):** The sweeper acts only on registrations past their expiry and never preempts the webhook path. Interval and batch size are configuration, not constants. _(Source: ADR-022; human decision — default 30, configurable.)_
- **BR-12 (Sweeper decides on gateway truth):** An expired `PENDING` registration is never failed solely because time passed when the gateway has a live or succeeded payment; the gateway outcome decides. _(Source: `docs/system-design.md` §4 Dual-Case Reconciliation Sweeper.)_
- **BR-13 (No late-charge orphan):** A payment attempt that the sweeper releases (Case B) must no longer be completable by the student, so that a seat released by the sweeper cannot be followed by a charge with no registration.
- **BR-14 (Single-writer for lifecycle):** Payment `REFUNDED` and registration `CANCELED` transitions are owned exclusively by this feature; `COMPLETED`/`FAILED` for webhook-driven flows remain owned by Feature 5.1. _(Source: feature-catalog §5.2.)_

## 5. State Transitions

### 5.1 Registration (`registrations.status`)

| From State  | Event / Trigger                                                              | To State     |   Valid?   | Rejection Behavior (if invalid)                           |
| :---------- | :--------------------------------------------------------------------------- | :----------- | :--------: | :-------------------------------------------------------- |
| `PENDING`   | Sweeper, expired, gateway reports payment succeeded (Case A)                 | `CONFIRMED`  |     ✅     | N/A — QR set atomically, event in outbox                  |
| `PENDING`   | Sweeper, expired, gateway reports failed/abandoned/canceled/unknown (Case B) | `FAILED`     |     ✅     | N/A — seat returned after commit                          |
| `PENDING`   | Sweeper, expired, no payment record (Case B)                                 | `FAILED`     |     ✅     | N/A — seat returned after commit                          |
| `PENDING`   | Sweeper, gateway still processing / unreachable / errored                    | `PENDING`    | ✅ (no-op) | Left unchanged; retried next cycle                        |
| `PENDING`   | Sweeper, not yet expired                                                     | `PENDING`    | ✅ (no-op) | Not selected                                              |
| `PENDING`   | Cancellation requested                                                       | _(rejected)_ |     ❌     | `422` — only confirmed registrations can be canceled      |
| `CONFIRMED` | Cancellation by owning `STUDENT`, workshop `PUBLISHED`, not started          | `CANCELED`   |     ✅     | N/A — refund first (paid), seat returned, event in outbox |
| `CONFIRMED` | Cancellation by `ORGANIZER` of the workshop's organization, same conditions  | `CANCELED`   |     ✅     | N/A — same as above                                       |
| `CONFIRMED` | Cancellation by non-owning `STUDENT` or other-org `ORGANIZER`                | _(rejected)_ |     ❌     | `403 Forbidden`                                           |
| `CONFIRMED` | Cancellation when `starts_at <= now()`                                       | _(rejected)_ |     ❌     | `422` — workshop already started or ended                 |
| `CONFIRMED` | Cancellation when workshop not `PUBLISHED`                                   | _(rejected)_ |     ❌     | `422` — workshop not in a cancelable state                |
| `CONFIRMED` | Cancellation, paid, gateway refund fails / circuit open                      | _(rejected)_ |     ❌     | `503` — no state change                                   |
| `CONFIRMED` | Sweeper                                                                      | `CONFIRMED`  | ✅ (no-op) | Not selected (not `PENDING`)                              |
| `FAILED`    | Cancellation requested                                                       | _(rejected)_ |     ❌     | `422` — cannot cancel a failed registration               |
| `FAILED`    | Any further transition                                                       | _(rejected)_ |     ❌     | Terminal; no change                                       |
| `CANCELED`  | Cancellation requested again (different idempotency key)                     | _(rejected)_ |     ❌     | `422` — already canceled                                  |
| `CANCELED`  | Any further transition                                                       | _(rejected)_ |     ❌     | Terminal; no change                                       |

### 5.2 Payment (`payments.status`)

| From State  | Event / Trigger                                    | To State     |   Valid?   | Rejection Behavior (if invalid)                            |
| :---------- | :------------------------------------------------- | :----------- | :--------: | :--------------------------------------------------------- |
| `PENDING`   | Sweeper Case A                                     | `COMPLETED`  |     ✅     | N/A                                                        |
| `PENDING`   | Sweeper Case B                                     | `FAILED`     |     ✅     | N/A — failure reason recorded                              |
| `COMPLETED` | Registration canceled and gateway refund succeeded | `REFUNDED`   |     ✅     | N/A                                                        |
| `COMPLETED` | Cancellation, gateway refund failed                | `COMPLETED`  | ✅ (no-op) | Unchanged; `503`                                           |
| `PENDING`   | Cancellation of a paid registration                | _(rejected)_ |     ❌     | Not reachable: only `CONFIRMED` registrations are canceled |
| `FAILED`    | Any further transition                             | _(rejected)_ |     ❌     | Terminal                                                   |
| `REFUNDED`  | Any further transition                             | _(rejected)_ |     ❌     | Terminal                                                   |

### 5.3 Idempotency key (`idempotency_keys.status`)

| From State  | Event / Trigger                                | To State    |   Valid?   | Rejection Behavior (if invalid) |
| :---------- | :--------------------------------------------- | :---------- | :--------: | :------------------------------ |
| `PENDING`   | Sweeper Case A on the registration's own key   | `COMPLETED` |     ✅     | N/A                             |
| `PENDING`   | Sweeper Case B on the registration's own key   | `FAILED`    |     ✅     | N/A                             |
| _(none)_    | Cancellation request claims a new key          | `PENDING`   |     ✅     | In-flight duplicate → `409`     |
| `PENDING`   | Cancellation succeeds                          | `COMPLETED` |     ✅     | N/A                             |
| `PENDING`   | Cancellation fails with `503`/unexpected error | `FAILED`    |     ✅     | Key released for retry          |
| `COMPLETED` | Same key replayed                              | `COMPLETED` | ✅ (no-op) | Cached `200` returned           |

## 6. Non-Functional Requirements

- **Performance:** Cancellation of a free registration involves no external call; for a paid registration the gateway refund call dominates latency and is bounded by the gateway client timeout. A sweeper cycle is bounded by the batch size (FR-03) so one cycle cannot run unbounded; the sweeper query must use the existing partial index on expired `PENDING` rows.
- **Concurrency:** Concurrent cancellations of the same registration (student + organizer, double click) produce exactly one `CANCELED` transition, one refund, one capacity return, one event (FR-40). A sweeper cycle racing the webhook on the same registration produces exactly one outcome (FR-14). Two backend nodes never run the same sweeper cycle (FR-02).
- **Idempotency:** Cancellation requires `Idempotency-Key` (two-tier: Redis L1 + DB L2), plus a gateway-level refund dedup keyed to the registration (FR-32, BR-05). Sweeper steps are safe to re-run: a transition is applied only if the registration is still `PENDING` (FR-14).
- **Rate-limiting:** Cancellation is covered by the Registration Tier token bucket (5 tokens, refill 5 per 10 s, per client IP), enforced before authentication-dependent business logic.
- **Security:** Authorization is explicit (FR-17 – FR-22); organizers are tenant-scoped; `reason` is length-limited and treated as untrusted text (HTML-escaped in email); no gateway identifiers, secrets, or internal error text are returned to clients; the gateway is never called before authorization and eligibility pass.
- **Reliability / Failure modes:**
  - Gateway down / circuit open during cancellation → `503`, zero state change (FR-33).
  - Refund succeeds but local commit fails → retry with the same key converges without a second refund (BR-05); the client receives a retryable error.
  - Redis down after commit → request still succeeds; seat counter is repaired by the existing capacity reconciliation job (BR-06).
  - Redis down before commit (idempotency L1) → `503`, no state change.
  - Broker down → events stay in the outbox and are published on recovery.
  - Sweeper gateway failure for one item → item untouched, batch continues (FR-04, FR-10).
- **Observability:** Sweeper cycles log counts of selected / confirmed / failed / skipped; each Case A recovery is logged at WARN (it indicates a lost webhook); each refund logs registration id and amount (no secrets or card data); cancellation logs actor role.

## 7. Acceptance Criteria

### Scenario 1: Sweeper recovers a payment whose webhook was lost (FR-01, FR-03, FR-05, FR-06, FR-07)

```gherkin
Given a PENDING paid registration whose expiry has passed
And the payment gateway reports its payment as succeeded
When the sweeper runs
Then the registration becomes CONFIRMED with QR payload, signature and issued-at populated
And the payment becomes COMPLETED
And a registration-confirmed event is recorded in the outbox
And the registration's idempotency key becomes COMPLETED
And registration.updated and payment.confirmed real-time events are published after commit
```

### Scenario 2: Sweeper cleans up an abandoned checkout (FR-08, FR-09)

```gherkin
Given a PENDING paid registration whose expiry has passed
And the gateway reports the payment as awaiting a payment method
When the sweeper runs
Then the gateway is asked to cancel the payment attempt
And the registration becomes FAILED and the payment becomes FAILED
And the idempotency key becomes FAILED
And exactly one seat is returned to the workshop's capacity counter
And no registration-confirmed event is recorded
```

### Scenario 3: Gateway cancel reveals a late success (FR-12)

```gherkin
Given an expired PENDING paid registration the gateway reports as abandoned
And the payment succeeds between the status query and the cancel request
When the sweeper runs
Then the registration is handled as Case A and becomes CONFIRMED
And no seat is returned
```

### Scenario 4: Gateway still processing (FR-11)

```gherkin
Given an expired PENDING paid registration whose payment the gateway reports as processing
When the sweeper runs
Then the registration remains PENDING and no capacity or event change occurs
```

### Scenario 5: Gateway unavailable for one registration (FR-04, FR-10)

```gherkin
Given three expired PENDING registrations
And the gateway fails for the first one only
When the sweeper runs
Then the first remains PENDING unchanged
And the other two are resolved normally
```

### Scenario 6: Expired PENDING with no payment record (FR-13)

```gherkin
Given an expired PENDING registration with no payment record
When the sweeper runs
Then the gateway is not queried
And the registration becomes FAILED and one seat is returned
And no payment record is created
```

### Scenario 7: Webhook wins the race (FR-14)

```gherkin
Given an expired PENDING registration selected by the sweeper
And the webhook confirms it before the sweeper applies a transition
When the sweeper attempts the transition
Then no state, capacity, or event change is applied by the sweeper
```

### Scenario 8: Only one node sweeps (FR-02)

```gherkin
Given two backend nodes trigger the sweeper at the same moment
When both attempt to start a cycle
Then exactly one executes the cycle and the other skips it
```

### Scenario 9: Sweeper is bounded and configurable (FR-01, FR-03)

```gherkin
Given more expired PENDING registrations than the configured batch size
When the sweeper runs
Then only the configured number, oldest expiry first, are processed
And the remainder are processed in subsequent cycles
```

### Scenario 10: Student cancels own free registration (FR-16, FR-30, FR-35 – FR-39)

```gherkin
Given a CONFIRMED registration for a free PUBLISHED workshop that has not started
And the caller is the owning STUDENT with a valid Idempotency-Key
When the student cancels it
Then the response is 200 with status CANCELED and no QR fields
And no payment gateway call is made
And one seat is returned after commit
And a registration-canceled event is recorded in the outbox
And registration.updated is published to the workshop channel
```

### Scenario 11: Student cancels own paid registration with full refund (FR-31, FR-32, FR-35, FR-39)

```gherkin
Given a CONFIRMED registration for a paid PUBLISHED workshop that has not started
And its payment is COMPLETED
When the owning student cancels it
Then a refund of 100% of the amount paid is requested from the gateway before any state change
And the registration becomes CANCELED and the payment becomes REFUNDED
And the response includes the refunded amount and currency
And the event carries the refunded amount and currency
```

### Scenario 12: Organizer force-cancels within own organization (FR-16, FR-21, FR-36)

```gherkin
Given a CONFIRMED registration for a workshop owned by organization O
And the caller is an ORGANIZER of organization O
When the organizer cancels it with reason "Room unavailable for this student"
Then the registration becomes CANCELED
And the event records the canceling role ORGANIZER and the reason
And the reason is not stored in any table
```

### Scenario 13: Organizer of another organization (FR-21, FR-22)

```gherkin
Given a registration for a workshop owned by organization O
And the caller is an ORGANIZER of a different organization
When the organizer attempts to cancel it
Then the response is 403 Forbidden
And nothing changes and the gateway is not called
```

### Scenario 14: Student cancels someone else's registration (FR-20, FR-22)

```gherkin
Given a registration owned by student A
When student B attempts to cancel it
Then the response is 403 Forbidden and nothing changes
```

### Scenario 15: Unauthenticated, wrong role, unknown id (FR-17, FR-18, FR-19)

```gherkin
Given no JWT, or a STAFF JWT, or a valid STUDENT JWT and a non-existent registration id
When cancellation is requested
Then the responses are respectively 401, 403 and 404
```

### Scenario 16: Workshop has started (FR-26)

```gherkin
Given a CONFIRMED registration whose workshop has starts_at in the past
When the owner attempts to cancel
Then the response is 422 and nothing changes
```

### Scenario 17: Workshop not PUBLISHED (FR-25)

```gherkin
Given a CONFIRMED registration whose workshop is CANCELED or COMPLETED
When the owner attempts to cancel
Then the response is 422 and nothing changes
```

### Scenario 18: Registration not in CONFIRMED (FR-24)

```gherkin
Given registrations in status PENDING, FAILED and CANCELED
When the owner attempts to cancel each
Then each response is 422 with its own distinct reason
And nothing changes
```

### Scenario 19: Reason validation (FR-23)

```gherkin
Given a cancelable registration
When the request carries a reason longer than 1000 characters after trimming
Then the response is 422 and nothing changes
When the request carries a whitespace-only reason
Then the cancellation succeeds and the event carries no reason
```

### Scenario 20: Gateway refund failure (FR-33)

```gherkin
Given a CONFIRMED paid registration that is cancelable
And the gateway times out or its circuit breaker is open
When the owner cancels it
Then the response is 503 with a retry hint
And the registration stays CONFIRMED and the payment stays COMPLETED
And no seat is returned and no event is recorded
And retrying later with the same Idempotency-Key is allowed
```

### Scenario 21: Retry after partial failure (FR-32, BR-05)

```gherkin
Given a refund succeeded at the gateway but the local transaction then failed
When the client retries the cancellation with the same Idempotency-Key
Then no second refund is created
And the registration becomes CANCELED once
```

### Scenario 22: Idempotent replay and in-flight duplicate (FR-27, FR-28)

```gherkin
Given a cancellation completed with Idempotency-Key K
When the same request is replayed with K
Then the original 200 response is returned and no side effect is repeated
Given a cancellation with key K is still in progress
When another request with K arrives
Then the response is 409 with a Retry-After hint
Given the Idempotency-Key header is missing or not a valid UUIDv4
When cancellation is requested
Then the response is 422
```

### Scenario 23: Concurrent cancellations (FR-40)

```gherkin
Given the owning student and an organizer cancel the same registration simultaneously
When both requests are processed
Then exactly one succeeds
And the other receives 422
And only one refund, one seat return and one event result
```

### Scenario 24: Redis failure after commit (FR-37)

```gherkin
Given Redis becomes unavailable immediately after the cancellation commits
When the cancellation response is produced
Then it is still 200 CANCELED
And the seat counter is later repaired by capacity reconciliation
```

### Scenario 25: Rate limit (FR-29)

```gherkin
Given a client exceeds the registration-tier rate limit
When it sends another cancellation request
Then the response is 429
```

### Scenario 26: Re-registration after cancellation (BR-10)

```gherkin
Given a student canceled a registration for a workshop that has not started and has seats
When the student registers again with a new Idempotency-Key
Then the registration is accepted under the normal rules
```

### Scenario 27: Worker delivers cancellation notification (FR-41)

```gherkin
Given a registration-canceled event reaches the notifications worker
When it is processed
Then the student receives an in-app notification, an email with reason, actor and refund details, and a real-time event
```

### Scenario 28: Worker tolerates bad or duplicate events (FR-42)

```gherkin
Given an event missing the student identifier, or the same event delivered twice
When the worker processes it
Then the invalid event is skipped with a warning and the queue is not blocked
And the duplicate does not create a second in-app notification or email
```

### Scenario 29: Paid CONFIRMED registration without a completed payment (FR-34)

```gherkin
Given a paid CONFIRMED registration whose payment record is missing or not COMPLETED
When the owner attempts to cancel
Then the response is a 500 and nothing changes
```

## 8. Out of Scope

- **Bulk cancellation / refund when an entire workshop is canceled** (Feature 2.4 emits the event; the downstream consumers described in `docs/system-design.md` are not part of this feature and are not yet catalogued).
- Partial refunds, cancellation fees, refund deadlines or tiers, manual refund queues, and organizer-chosen refund amounts.
- Canceling `PENDING` registrations on request (they resolve via payment outcome or the sweeper).
- Handling Stripe refund-related webhooks (`charge.refunded`, `refund.failed`) and asynchronous refund failure recovery.
- Attendance/check-in lookups and invalidating an already-issued QR on the offline mobile device: a canceled ticket's signature remains cryptographically valid offline until the staff device syncs, where server reconciliation (Feature 6.2) must reject it.
- Read endpoints for organizers to view a single registration (`GET /registrations/{id}` stays student-only), the roster (Feature 4.4), and payment history (Feature 5.2).
- Changing the webhook handler to reconcile a late `payment_intent.succeeded` for an already-`FAILED` registration (Feature 5.1).
- Capacity Reconciliation Job (Feature 2.5) changes; it is relied on, not modified.
- The 15-minute vs 30-minute sweeper timing text in the webhook section of `docs/system-design.md` (documentation cleanup, handled outside `/spec`).

## 9. Linked References

- `docs/feature-catalog.md` — §4.3 (this feature), §4.1, §4.2, §4.4, §5.1, §5.2, §2.4, §2.5, §6.2, Appendix A.
- `docs/system-design.md` — §4 Workshop Registration Flow (Dual-Case Reconciliation Sweeper, Unified Registration Cancellation & Automated Refund Flow, Live Registration Broadcasting, Cross-Module API Facades), §4 Payment Webhook Handling Flow, §4 Workshop Lifecycle Management Flow, §6 Transaction Idempotency, §6 Overbooking Prevention, §6 Third-Party Service Resilience.
- `docs/adrs.md` — ADR-006, ADR-013, ADR-014, ADR-015, ADR-018, ADR-020, ADR-022, ADR-031.
- `docs/database-schema.md` — `registrations` (status check, expiry column, sweeper partial index, QR invariant), `payments` (statuses, unique registration/provider ids), `idempotency_keys`, `event_publication`; Replication Lag section (mutations on primary).
- `docs/api-standards.md` — status codes, mandatory headers, envelope, RFC 7807, SSE event catalog (`registration.updated`, `payment.confirmed`).
- `specs/paid-workshop-registration/SPEC.md` — PENDING lifecycle, `expires_at` TTL, payment record purpose (BR-09, BR-14).
- `specs/workshop-cancellation/SPEC.md` — transient `reason` precedent (BR-03), outbox pattern.
- `AGENTS.md` §1 (exactly-once, offline check-in), §6 (module boundaries).
