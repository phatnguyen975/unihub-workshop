# Spec: Attendance Sync Reconciliation

> **Status:** APPROVED  
> **Domain:** 6 — On-Site Check-in & Attendance  
> **Owner/area:** `attendances` module — `apps/backend/`  
> **Last revised:** 2026-10-31 — first version

## 1. Overview

This feature implements the server-side reconciliation of batched attendance events that Check-in Staff recorded while their mobile device had no network connectivity. After the device reconnects, it submits every queued scan in one batch; the backend authoritatively decides, per entry, whether the scan is a genuine first check-in, a retry of a scan it already recorded, a ticket that must not be admitted, or a ticket that has already been used. Because offline devices cannot be trusted to arbitrate attendance authority, the server — never the client — owns that decision, and it does so in one workshop-scoped bulk read followed by one bulk write so the path stays stable during the on-site sync bursts. The feature is owned by the `attendances` module, is the second and final phase of the offline check-in workflow, and is the producer of the live attendance counter event consumed by the Live Attendance Monitoring dashboard.

## 2. Actors & Preconditions

| Actor                             | Precondition                                                                                                                                                                                                |
| :-------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Check-in Staff** (`STAFF` role) | Holds a valid, non-revoked JWT with `role = STAFF`, issued by the authentication feature (F-1.2), and the token's `sub` identifies the caller.                                                              |
| **Check-in Staff** (`STAFF` role) | Has been explicitly assigned to the target workshop through the staff assignment feature (F-1.6); an assignment record exists in `workshop_assignments` for `(workshopId, jwt.sub)`.                        |
| **Check-in Staff** (`STAFF` role) | The target workshop exists and is in `PUBLISHED` or `COMPLETED` status.                                                                                                                                     |
| **Check-in Staff** (`STAFF` role) | The mobile client already verified each queued ticket's QR signature locally against the cached platform public key (F-1.3) before queueing it, and assigned each queued scan a device-generated `sync_id`. |
| **Check-in Staff** (`STAFF` role) | The batch is non-empty and contains at most 500 entries, with no repeated `sync_id` or `registrationId` inside it.                                                                                          |
| **System**                        | The primary PostgreSQL database is reachable; all reads and writes of this flow are pinned to the primary instance.                                                                                         |
| **System**                        | Redis is reachable for request-level idempotency and rate limiting; the flow degrades to the durable database idempotency record when Redis is unavailable, and the rate limiter fails open.                |

## 3. Functional Requirements

### 3.1 Authentication & Authorization

- **FR-01:** The system SHALL reject the request with an unauthenticated error when the `Authorization: Bearer <token>` header is absent, malformed, expired, signature-invalid, or carries a revoked token identifier.
- **FR-02:** The system SHALL reject the request with a forbidden error when the JWT is valid but the caller's role is not `STAFF`.
- **FR-03:** The system SHALL reject the request with a not-found error when no workshop exists for the supplied workshop identifier.
- **FR-04:** The system SHALL reject the request with a forbidden error when the target workshop exists but the caller has no assignment record for it, regardless of the caller's organization.
- **FR-05:** The system SHALL attribute every attendance record created by this flow to the caller identified by `jwt.sub`, and SHALL NOT accept any caller-supplied actor identifier.

### 3.2 Request Validation

- **FR-06:** The system SHALL reject the request with a bad-request error when the `Idempotency-Key` header is absent, is not a UUIDv4 string, or exceeds 64 characters.
- **FR-07:** The system SHALL reject the request with an unprocessable-content error when the batch contains zero entries.
- **FR-08:** The system SHALL reject the request with an unprocessable-content error when the batch contains more than 500 entries.
- **FR-09:** The system SHALL reject the request with an unprocessable-content error when two or more entries in the same batch share the same `syncId`.
- **FR-10:** The system SHALL reject the request with an unprocessable-content error when two or more entries in the same batch share the same `registrationId`.
- **FR-11:** The system SHALL reject the request with an unprocessable-content error when any entry is missing `syncId`, `registrationId`, or `scannedAt`, or when any of those values is not a valid UUID / ISO-8601 UTC timestamp respectively.
- **FR-12:** The system SHALL accept an optional `deviceId`, SHALL persist it when supplied, and SHALL reject the request with an unprocessable-content error when it exceeds 100 characters.
- **FR-13:** The system SHALL reject the request with an unprocessable-content error when the target workshop's status is `DRAFT` or `CANCELED`.
- **FR-14:** The system SHALL accept the batch when the target workshop's status is `PUBLISHED` or `COMPLETED`, and SHALL NOT impose any time-window restriction relative to the workshop's start or end time.

### 3.3 Request-Level Idempotency

- **FR-15:** The system SHALL return the previously stored response, without re-executing the reconciliation, when a request arrives with an `Idempotency-Key` that was already completed for the same caller and the same request body.
- **FR-16:** The system SHALL reject the request with a conflict error when an `Idempotency-Key` was previously used by the same caller with a different request body.
- **FR-17:** The system SHALL reject the request with a conflict error carrying a `Retry-After: 2` indication when an `Idempotency-Key` for the same caller is currently being processed.
- **FR-18:** The system SHALL scope request-level idempotency keys strictly to the pair (`jwt.sub`, `Idempotency-Key`), so two different staff members generating the same key value never collide.
- **FR-19:** The system SHALL record the request-level idempotency claim against the primary database before executing the reconciliation, releasing that claim only when the reconciliation fails for a transient reason so the client can retry.

### 3.4 Ticket Validation

- **FR-20:** The system SHALL resolve every distinct `registrationId` present in the batch using at most one bulk lookup, and SHALL NOT perform a per-entry lookup.
- **FR-21:** The system SHALL classify an entry as an invalid ticket when no registration exists for its `registrationId`.
- **FR-22:** The system SHALL classify an entry as an invalid ticket when its registration's status is anything other than `CONFIRMED`, including `PENDING`, `FAILED`, and `CANCELED`.
- **FR-23:** The system SHALL classify an entry as an invalid ticket when its registration belongs to a workshop other than the workshop identified in the request.
- **FR-24:** The system SHALL NOT re-verify the QR signature server-side; the server's authenticity decision is limited to registration existence, registration status, and workshop scope.

### 3.5 Attendance Classification

- **FR-25:** The system SHALL classify an entry as a successful check-in when no attendance record exists for the pair (`workshopId`, `registrationId`) and the corresponding insert is persisted.
- **FR-26:** The system SHALL classify an entry as a successful check-in when an attendance record exists for the pair (`workshopId`, `registrationId`) with status `VALID` and a `sync_id` equal to the entry's `syncId`, and SHALL NOT modify that record.
- **FR-27:** The system SHALL classify an entry as already-used when an attendance record exists for the pair (`workshopId`, `registrationId`) with status `VALID` and a `sync_id` different from the entry's `syncId`.
- **FR-28:** The system SHALL classify an entry as already-used when an attendance record exists for the pair (`workshopId`, `registrationId`) with status `INVALIDATED`, irrespective of whether its `sync_id` equals the entry's `syncId`, and SHALL NOT reactivate that record.
- **FR-29:** The system SHALL classify an entry as already-used when its insert is not among the records persisted by the bulk write, because a concurrent request won the race for the same registration.
- **FR-30:** The system SHALL apply the classification rules in this fixed precedence: (1) invalid ticket, (2) existing record with status `INVALIDATED`, (3) existing record with the same `sync_id`, (4) existing record with a different `sync_id`, (5) new insert.
- **FR-31:** The system SHALL classify an entry as already-used when its `syncId` collides with an existing attendance record of the same workshop that belongs to a different registration.

### 3.6 Persistence

- **FR-32:** The system SHALL persist all entries classified for insertion using a single bulk write, and SHALL NOT issue a per-entry write.
- **FR-33:** The system SHALL persist, for every newly created attendance record: the target workshop identifier, the entry's registration identifier, the caller as the checking-in user, the entry's `syncId`, the request's `deviceId` when supplied, the check-in method `OFFLINE_SYNC`, and status `VALID`.
- **FR-34:** The system SHALL set the attendance record's `checked_in_at` to the entry's `scannedAt`, clamped so that it is never earlier than the workshop's start time and never later than the server's current time.
- **FR-35:** The system SHALL set the attendance record's creation and last-update audit timestamps to the server's current time, and the creation and last-update audit actors to `jwt.sub`.
- **FR-36:** The system SHALL rely on the database-level uniqueness of (`workshop_id`, `registration_id`) and (`workshop_id`, `sync_id`) as the final authority, so that a retried scan never creates a second record and a double scan never overwrites the first.

### 3.7 Response

- **FR-37:** The system SHALL return a multi-status response whenever the request passes authentication, request validation, and the workshop preconditions, including when every entry in the batch is classified as an invalid ticket or already-used.
- **FR-38:** The system SHALL return exactly one outcome object per submitted entry, in the same order as the submitted entries.
- **FR-39:** The system SHALL include only the entry's `syncId` and its classification in each outcome object.
- **FR-40:** The system SHALL use exactly three classification values in outcomes: `SUCCESS`, `ALREADY_USED`, and `INVALID_TICKET`.
- **FR-41:** The system SHALL NOT abort the batch, nor roll back successfully classified entries, because of a per-entry classification failure.

### 3.8 Real-Time Counter Side Effect

- **FR-42:** The system SHALL publish exactly one attendance-updated event for the workshop when at least one new attendance record was persisted by the request.
- **FR-43:** The system SHALL publish no attendance-updated event when the request persisted zero new attendance records, including when all entries were retries classified as successful.
- **FR-44:** The system SHALL publish the event only after the reconciliation transaction has committed, so that no event describes attendance state that was subsequently rolled back.
- **FR-45:** The system SHALL include, in the event payload: the workshop identifier, the current count of `VALID` attendance records for that workshop, the workshop's capacity, and the server timestamp at which the event was produced.
- **FR-46:** The system SHALL publish the event to the workshop-scoped real-time channel so that subscribers of the workshop — organizers monitoring the workshop and staff assigned to it — receive the updated count without polling.

## 4. Business Rules

- **BR-01 (Server-authoritative reconciliation):** Offline devices never decide attendance authority. The client's local verdict for an entry is irrelevant to the server's classification, and the server never trusts a client-supplied classification or attendance status. _(Source: ADR-024.)_
- **BR-02 (Primary database only):** Every read and write of this flow MUST be routed to the primary PostgreSQL instance. Reading existing attendance or registration state from a replica could miss a just-committed check-in and produce a duplicate. _(Source: ADR-015; `docs/database-schema.md` §Replication Lag & Read-After-Write Consistency.)_
- **BR-03 (Workshop boundary):** Every read and every write MUST be constrained by the workshop identifier from the request path. A valid ticket for one workshop MUST never be recorded against another workshop, and this MUST be enforced at the data-access level, not only at the endpoint level. _(Source: `docs/system-design.md` §4 Workshop Boundary Enforcement.)_
- **BR-04 (No per-entry database chatter):** The flow MUST perform at most one bulk registration lookup, one bulk attendance lookup, one staff-assignment existence check, one aggregate count for the real-time payload, and one bulk write. N+1 query shapes are forbidden. _(Source: `docs/feature-catalog.md` §6.2; ADR-024.)_
- **BR-05 (Database constraints are the final arbiter):** Uniqueness of (`workshop_id`, `registration_id`) and (`workshop_id`, `sync_id`) MUST be the last-resort guard. The flow MUST reconcile the identifiers actually persisted against the identifiers it attempted, rather than relying on exception-driven control flow. _(Source: ADR-024; `docs/system-design.md` §4 Pessimistic DB Constraints with Returned Identifiers.)_
- **BR-06 (Two-level idempotency):** The request-level idempotency key and the per-entry `sync_id` are complementary and both mandatory. The request-level key makes a whole-batch retry replay the stored response; the per-entry key makes an individual scan safe even when the batch is re-sent under a different request key. Neither replaces the other. _(Source: `docs/system-design.md` §6 Transaction Idempotency; `docs/api-standards.md` §Mandatory Headers.)_
- **BR-07 (`INVALIDATED` is terminal for this flow):** A check-in voided by an organizer MUST NOT be reactivated by a sync, even when the retried `sync_id` matches exactly. Reinstatement is an explicit organizer action outside this flow. _(Source: `docs/database-schema.md` `attendances.status`; human decision 2026-10-31.)_
- **BR-08 (Late sync is legitimate):** Offline batches may arrive hours after the session ends. The flow MUST accept `PUBLISHED` and `COMPLETED` workshops and MUST NOT apply a time-window restriction; `DRAFT` and `CANCELED` workshops MUST be rejected. _(Source: `docs/feature-catalog.md` §6.2; human decision 2026-10-31.)_
- **BR-09 (Server-authoritative timestamps with client scan provenance):** `checked_in_at` records when the scan actually happened, taken from the device and clamped to the workshop's start time and the server's current time; `created_at` records when the server received it. The clamp prevents a tampered or skewed device clock from fabricating future attendance. _(Source: `docs/database-schema.md` `attendances.checked_in_at`; human decision 2026-10-31.)_
- **BR-10 (Fail-closed classification):** When the system cannot prove an entry is a genuine first check-in, it MUST NOT report success. Ambiguity resolves to `ALREADY_USED` or `INVALID_TICKET`, never to `SUCCESS`. _(Source: ADR-024; human decision 2026-10-31.)_
- **BR-11 (Zero-trust at the edge, state-check at the server):** QR signature authenticity is verified by the mobile client before queueing (ADR-029). The server's contribution to anti-forgery is rejecting registrations that are absent, not `CONFIRMED`, or bound to another workshop — which is also how a ticket canceled after issue is rejected at sync time. _(Source: ADR-029; `specs/registration-reconciliation-and-cancellation/SPEC.md` §8.)_
- **BR-12 (No hard deletion):** This flow never deletes an attendance record. Corrections are expressed as status transitions owned by other capabilities. _(Source: `docs/database-schema.md` §No Hard Deletions.)_
- **BR-13 (Bounded batch):** A batch MUST contain between 1 and 500 entries inclusive. Duplicate identifiers inside one batch are a client defect, not a partial business outcome, and MUST be rejected before any database work. _(Source: human decision 2026-10-31 — net-new constraint, not present in prior documentation.)_
- **BR-14 (Attendance module is the sole writer):** Only the `attendances` module writes attendance records. Peer modules expose read-only facades; this flow reaches registration state and staff assignment through their public module surfaces only. _(Source: `AGENTS.md` §6; `docs/project-structure.md`.)_
- **BR-15 (Standard rate limiting):** The flow is governed by the default rate-limit tier (100 tokens per 60 seconds per `jwt.sub`), and the limiter fails open when Redis is unavailable. _(Source: `docs/system-design.md` §Rate Limiting Tiers; ADR-010.)_

## 5. State Transitions

This feature creates attendance records and never transitions an existing record out of `VALID`. It does not modify workshop or registration state — those lifecycles are owned by the `workshops` and `registrations` modules and are only read here.

| From State    | Event/Trigger                                                                             | To State                        | Valid? | Rejection Behavior (if invalid)                                             |
| :------------ | :---------------------------------------------------------------------------------------- | :------------------------------ | :----: | :-------------------------------------------------------------------------- |
| _(no record)_ | Sync entry with a `CONFIRMED` registration of this workshop                               | `VALID`                         |   ✅   | —                                                                           |
| _(no record)_ | Sync entry with an absent registration                                                    | _(no record)_                   |   ❌   | Entry outcome `INVALID_TICKET`; batch continues                             |
| _(no record)_ | Sync entry with a registration in `PENDING`, `FAILED`, or `CANCELED`                      | _(no record)_                   |   ❌   | Entry outcome `INVALID_TICKET`; batch continues                             |
| _(no record)_ | Sync entry with a `CONFIRMED` registration of a different workshop                        | _(no record)_                   |   ❌   | Entry outcome `INVALID_TICKET`; batch continues                             |
| _(no record)_ | Sync entry whose insert loses a concurrent race                                           | `VALID` (created by the winner) |   ❌   | Entry outcome `ALREADY_USED`; no second record                              |
| `VALID`       | Sync entry with the same `sync_id`                                                        | `VALID` (unchanged)             |   ✅   | Entry outcome `SUCCESS`; no write performed                                 |
| `VALID`       | Sync entry with a different `sync_id`                                                     | `VALID` (unchanged)             |   ❌   | Entry outcome `ALREADY_USED`; first record preserved                        |
| `VALID`       | Sync entry whose registration has since become `CANCELED`                                 | `VALID` (unchanged)             |   ❌   | Entry outcome `INVALID_TICKET`; existing record is not altered by this flow |
| `VALID`       | Sync entry reusing a `sync_id` already bound to another registration of the same workshop | `VALID` (unchanged)             |   ❌   | Entry outcome `ALREADY_USED`; no second record                              |
| `INVALIDATED` | Any sync entry for the same registration, same or different `sync_id`                     | `INVALIDATED` (unchanged)       |   ❌   | Entry outcome `ALREADY_USED`; record is never reactivated                   |

## 6. Non-Functional Requirements

- **Performance:** A 500-entry batch MUST complete within 300 ms at p99, excluding network latency, on the primary database. The flow performs a bounded number of statements regardless of batch size (one assignment check, one registration lookup, one attendance lookup, one bulk write, one count).
- **Concurrency:** Two staff devices syncing overlapping registrations for the same workshop concurrently MUST produce exactly one `VALID` attendance record per registration. Under concurrent batches, at most one request persists a given (`workshop_id`, `registration_id`) pair; every loser is reported as `ALREADY_USED` rather than failing the request. No request may block another on a row lock for longer than the bulk write itself.
- **Idempotency:** A batch retried with the same request-level key and identical body MUST return the stored response without re-executing. A batch re-sent with a fresh request-level key but identical `sync_id` values MUST be a no-op that still reports `SUCCESS` for those entries.
- **Rate-limiting:** Default tier — 100 tokens per 60 seconds per `jwt.sub`, Bucket4j + Redis, fail-open on Redis unavailability.
- **Security:** JWT signature, expiry, and revocation are enforced by the shared security layer before this flow runs. Role and assignment checks are explicit (FR-02, FR-04) and MUST NOT be inferred from organization membership. The flow MUST NOT log QR payloads, signatures, or full request bodies.
- **Reliability / Failure modes:** If the primary database is unavailable, the flow MUST return a service-unavailable error and MUST NOT fall back to a replica. If Redis is unavailable, request-level idempotency MUST fall back to the durable database record rather than silently skipping deduplication, and the rate limiter MUST fail open. A failure after the bulk write commits but before the real-time event publishes MUST leave attendance data correct; a missing counter update is repaired by the next successful sync or by the summary endpoint.
- **Observability:** Each request MUST be logged with `user_id`, `workshopId`, entry count, per-classification counts, latency, and trace identifier. Request count, latency, per-classification counters, and bulk-write failure count MUST be emitted as Micrometer metrics for Prometheus ingestion.

## 7. Acceptance Criteria

### Scenario 1: A batch of new scans is recorded and reported as successful

```gherkin
Given a STAFF user with a valid non-revoked JWT
And the user is assigned to workshop "ws-1" which is PUBLISHED
And the user has no prior attendance records for "ws-1"
When the user submits a sync batch with 3 entries whose registrations are CONFIRMED for "ws-1"
Then the response status is 207
And the response contains 3 outcome objects in the submitted order
And every outcome has status "SUCCESS"
And 3 attendance records exist for "ws-1" with status VALID and check-in method OFFLINE_SYNC
```

_Satisfies: FR-04, FR-14, FR-25, FR-32, FR-33, FR-37, FR-38, FR-40, BR-02, BR-03_

### Scenario 2: A retried batch with identical sync identifiers is a no-op

```gherkin
Given a STAFF user who previously synced 3 entries successfully for workshop "ws-1"
When the user submits the same batch again with a new Idempotency-Key but identical syncId values
Then the response status is 207
And every outcome has status "SUCCESS"
And the attendance record count for "ws-1" is unchanged
And no attendance record's checked_in_at or updated_at is modified
```

_Satisfies: FR-26, FR-36, BR-05, BR-06_

### Scenario 3: The same batch replayed under the same Idempotency-Key returns the stored response

```gherkin
Given a STAFF user submitted a batch with Idempotency-Key "K" and received a 207 response
When the user submits the identical body again with Idempotency-Key "K"
Then the response status is 207
And the response body is identical to the first response
And the reconciliation is not executed a second time
And no additional attendance record is created
```

_Satisfies: FR-15, BR-06_

### Scenario 4: Reusing an Idempotency-Key with a different body is rejected

```gherkin
Given a STAFF user submitted a batch with Idempotency-Key "K"
When the user submits a different batch with the same Idempotency-Key "K"
Then the response status is 409
And the response follows the RFC 7807 error format with type "urn:unihub:error:conflict"
And no attendance record is created or modified
```

_Satisfies: FR-16, BR-06_

### Scenario 5: A concurrent request with the same Idempotency-Key is rejected

```gherkin
Given a STAFF user's batch with Idempotency-Key "K" is still being processed
When the user submits another request with Idempotency-Key "K"
Then the response status is 409
And the response carries a Retry-After indication of 2 seconds
```

_Satisfies: FR-17_

### Scenario 6: A missing Idempotency-Key is rejected

```gherkin
Given a STAFF user with a valid non-revoked JWT
When the user submits a valid batch without an Idempotency-Key header
Then the response status is 400
And the response follows the RFC 7807 error format with type "urn:unihub:error:bad-request"
And no attendance record is created
```

_Satisfies: FR-06_

### Scenario 7: A non-STAFF role is forbidden

```gherkin
Given a user with role ORGANIZER and a valid non-revoked JWT
When the user submits a sync batch for workshop "ws-1"
Then the response status is 403
And the response follows the RFC 7807 error format with type "urn:unihub:error:forbidden"
And no attendance record is created
```

_Satisfies: FR-02_

### Scenario 8: A STAFF user not assigned to the workshop is forbidden

```gherkin
Given a STAFF user with a valid non-revoked JWT
And the user has no assignment for workshop "ws-1"
And workshop "ws-1" exists and is PUBLISHED
When the user submits a sync batch for "ws-1"
Then the response status is 403
And no attendance record is created
```

_Satisfies: FR-04, BR-14_

### Scenario 9: An unknown workshop is rejected

```gherkin
Given a STAFF user with a valid non-revoked JWT
When the user submits a sync batch for a workshop identifier that does not exist
Then the response status is 404
And the response follows the RFC 7807 error format with type "urn:unihub:error:not-found"
```

_Satisfies: FR-03_

### Scenario 10: A canceled workshop is rejected

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And workshop "ws-1" has status CANCELED
When the user submits a sync batch for "ws-1"
Then the response status is 422
And the response follows the RFC 7807 error format with type "urn:unihub:error:validation"
And no attendance record is created
```

_Satisfies: FR-13, BR-08_

### Scenario 11: A batch synced after the workshop ended is accepted

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And workshop "ws-1" has status COMPLETED and ended 6 hours ago
When the user submits a batch of scans recorded before the workshop ended
Then the response status is 207
And every entry with a CONFIRMED registration is reported as "SUCCESS"
```

_Satisfies: FR-14, BR-08_

### Scenario 12: Invalid tickets are reported per entry without aborting the batch

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And the batch contains 4 entries: one CONFIRMED registration of "ws-1", one PENDING registration, one CANCELED registration, and one registration belonging to workshop "ws-2"
When the user submits the batch
Then the response status is 207
And the first outcome has status "SUCCESS"
And the remaining outcomes have status "INVALID_TICKET"
And exactly 1 attendance record is created
```

_Satisfies: FR-21, FR-22, FR-23, FR-37, FR-40, FR-41, BR-11_

### Scenario 13: A double scan is reported as already used and the first record is preserved

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And an attendance record exists for registration "reg-1" with sync_id "sync-A" and status VALID
When the user submits a batch containing registration "reg-1" with a different sync_id "sync-B"
Then the response status is 207
And the outcome for "sync-B" has status "ALREADY_USED"
And the attendance record for "reg-1" still has sync_id "sync-A"
```

_Satisfies: FR-27, FR-36, BR-05_

### Scenario 14: An invalidated check-in is never reactivated, even on an exact retry

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And an attendance record exists for registration "reg-1" with sync_id "sync-A" and status INVALIDATED
When the user submits a batch containing registration "reg-1" with the same sync_id "sync-A"
Then the response status is 207
And the outcome for "sync-A" has status "ALREADY_USED"
And the attendance record remains INVALIDATED
```

_Satisfies: FR-28, FR-30, BR-07, BR-10_

### Scenario 15: Concurrent syncs of the same registration produce one record

```gherkin
Given two STAFF users both assigned to workshop "ws-1"
And both submit a batch containing the same CONFIRMED registration "reg-1" with different sync_id values at the same time
When both requests complete
Then exactly 1 attendance record exists for registration "reg-1"
And exactly one request reports "SUCCESS" for "reg-1"
And the other request reports "ALREADY_USED" for "reg-1"
And neither request returns an error status
```

_Satisfies: FR-29, FR-36, BR-05, BR-10_

### Scenario 16: A reused sync identifier from another registration is rejected

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And an attendance record exists for registration "reg-1" with sync_id "sync-A"
When the user submits a batch containing registration "reg-2" with the same sync_id "sync-A"
Then the response status is 207
And the outcome for "sync-A" has status "ALREADY_USED"
And no attendance record exists for "reg-2"
```

_Satisfies: FR-31_

### Scenario 17: The stored check-in time is the device scan time, clamped to the workshop window

```gherkin
Given a STAFF user assigned to workshop "ws-1" that started at 2026-11-15T09:00:00Z
And the batch contains one entry with scannedAt "2026-11-15T08:40:00Z"
And another entry with scannedAt "2030-01-01T00:00:00Z"
When the user submits the batch at 2026-11-15T10:00:00Z
Then the first attendance record's checked_in_at is "2026-11-15T09:00:00Z"
And the second attendance record's checked_in_at is "2026-11-15T10:00:00Z"
And both records' created_at is "2026-11-15T10:00:00Z"
```

_Satisfies: FR-34, FR-35, BR-09_

### Scenario 18: A batch with duplicate identifiers inside it is rejected before any write

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And the batch contains 2 entries with the same registrationId
When the user submits the batch
Then the response status is 422
And the response follows the RFC 7807 error format with a field-level validation error
And no attendance record is created
```

_Satisfies: FR-10, BR-13_

### Scenario 19: An empty batch is rejected

```gherkin
Given a STAFF user assigned to workshop "ws-1"
When the user submits a batch with an empty entries array
Then the response status is 422
And no attendance record is created
```

_Satisfies: FR-07, BR-13_

### Scenario 20: A batch exceeding the entry cap is rejected

```gherkin
Given a STAFF user assigned to workshop "ws-1"
When the user submits a batch containing 501 entries
Then the response status is 422
And no attendance record is created
```

_Satisfies: FR-08, BR-13_

### Scenario 21: The attendance counter event is published only for new check-ins

```gherkin
Given a STAFF user assigned to workshop "ws-1" with capacity 100
And the batch contains 2 new scans and 1 retry of an already-recorded scan
When the user submits the batch and the transaction commits
Then exactly 1 attendance-updated event is published for "ws-1"
And the event payload contains workshopId "ws-1", checkedInCount equal to the current VALID attendance count, capacity 100, and a lastSyncedAt equal to the server time of the sync
```

_Satisfies: FR-42, FR-45, FR-46_

### Scenario 22: No counter event is published for a batch of pure retries

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And every entry in the batch matches an existing VALID attendance record with the same sync_id
When the user submits the batch
Then the response status is 207
And every outcome has status "SUCCESS"
And no attendance-updated event is published for "ws-1"
```

_Satisfies: FR-43_

### Scenario 23: A rolled-back reconciliation publishes no counter event

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And the bulk write fails after the classification step
When the request is processed
Then the response status is 503
And no attendance record is created
And no attendance-updated event is published for "ws-1"
```

_Satisfies: FR-44, BR-02_

### Scenario 24: A cross-workshop ticket is rejected

```gherkin
Given a STAFF user assigned to workshop "ws-1"
And registration "reg-9" is CONFIRMED for workshop "ws-2"
When the user submits a batch for "ws-1" containing "reg-9"
Then the response status is 207
And the outcome for "reg-9" has status "INVALID_TICKET"
And no attendance record exists for "reg-9"
```

_Satisfies: FR-23, BR-03_

## 8. Out of Scope

- **Live attendance summary and dashboard (F-6.3):** the summary read model, check-in percentage, and recent-scan listing belong to `live-attendance-monitoring`, which is blocked on this feature.
- **Real-time transport implementation (F-10.5):** the SSE endpoint, emitter registry, Redis subscriber, and reconnection semantics are already delivered by the Real-Time Event Streaming Hub. This feature only produces the event.
- **QR code generation and signing:** ticket generation belongs to the registration features; this feature never signs or issues a ticket.
- **On-device QR signature verification (F-6.1 / ADR-029):** performed by the mobile client before queueing; not repeated server-side.
- **Online real-time check-in (`REALTIME_API` method):** a separate, future capability. This feature only writes records with `check_in_method = OFFLINE_SYNC`.
- **Invalidating or reinstating an attendance record:** organizer correction tooling is not part of this feature; this flow never transitions a record to `INVALIDATED` and never reactivates one.
- **Mobile SQLite queue, retry scheduling, and client-side conflict resolution:** the React Native client is out of scope.
- **Listing, paginating, filtering, or exporting attendance records:** no collection read endpoint is introduced here.
- **Registration lifecycle changes:** cancellation, refunds, and reconciliation sweepers remain owned by `registrations` and `payments`.
- **Workshop lifecycle changes:** publishing, rescheduling, canceling, and completing a workshop remain owned by `workshops`.
- **Rate-limiter implementation and tier configuration:** delivered by `distributed-rate-limiting`.
- **Batch sizes above 500 entries:** the client must split larger offline queues into multiple batches.

## 9. Linked References

| Document                                                     | Section                                        | Relevance                                                                                                                          |
| :----------------------------------------------------------- | :--------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                                    | §6.2 `attendance-sync-reconciliation`          | Feature entry, module ownership, classification rules, key constraints                                                             |
| `docs/feature-catalog.md`                                    | §6.3 `live-attendance-monitoring`              | Downstream consumer of the event produced here                                                                                     |
| `docs/feature-catalog.md`                                    | Appendix A — Feature Dependency Map            | Dependencies: `mobile-offline-checkin`, `free-workshop-registration`, `workshop-staff-assignment`, `real-time-event-streaming-hub` |
| `docs/system-design.md`                                      | §4 — Offline Check-in & Synchronization Flow   | End-to-end authorized branch, classification loop, bulk upsert, 207 response, live broadcasting                                    |
| `docs/system-design.md`                                      | §6 — Transaction Idempotency                   | Mandatory `Idempotency-Key`, 2-tier lifecycle, payload-hash mismatch, in-flight conflict                                           |
| `docs/system-design.md`                                      | §6 — Rate Limiting Tiers                       | Default tier (100/60s), fail-open behavior                                                                                         |
| `docs/system-design.md`                                      | §6 — Real-Time Event Distribution Architecture | Workshop channel, event fan-out                                                                                                    |
| `docs/database-schema.md`                                    | `attendances` table                            | Columns, `VALID`/`INVALIDATED` status, unique constraints, timestamp semantics                                                     |
| `docs/database-schema.md`                                    | `registrations` table                          | `CONFIRMED`/`PENDING`/`FAILED`/`CANCELED` status values                                                                            |
| `docs/database-schema.md`                                    | `workshop_assignments` table                   | Assignment record used for authorization                                                                                           |
| `docs/database-schema.md`                                    | Replication Lag & Read-After-Write Consistency | Justification for primary-only routing (BR-02)                                                                                     |
| `docs/database-schema.md`                                    | Mobile SQLite schema — `mobile_sync_queue`     | `sync_id`, `scanned_at`, and local `sync_status` values the client reconciles against                                              |
| `docs/api-standards.md`                                      | §HTTP Status Codes                             | 207, 400, 403, 404, 409, 422, 429 semantics                                                                                        |
| `docs/api-standards.md`                                      | §Mandatory Headers                             | `Idempotency-Key` requirement                                                                                                      |
| `docs/api-standards.md`                                      | §Success Response (207 Multi-Status)           | Outcome object shape                                                                                                               |
| `docs/api-standards.md`                                      | §Error Response                                | RFC 7807 wrapped in the standard envelope                                                                                          |
| `docs/adrs.md`                                               | ADR-014 — Idempotency Mechanism                | Two-tier idempotency basis (FR-15 to FR-19)                                                                                        |
| `docs/adrs.md`                                               | ADR-015 — Primary-Replica Routing Strategy     | Basis for BR-02                                                                                                                    |
| `docs/adrs.md`                                               | ADR-024 — Offline Sync Conflict Resolution     | Basis for the whole classification model                                                                                           |
| `docs/adrs.md`                                               | ADR-029 — Offline QR Verification Cryptography | Basis for BR-11 and FR-24                                                                                                          |
| `docs/adrs.md`                                               | ADR-031 — Real-Time Event Streaming            | Basis for FR-46                                                                                                                    |
| `docs/project-structure.md`                                  | Module layout & cross-module access            | Basis for BR-14                                                                                                                    |
| `specs/mobile-offline-checkin/SPEC.md`                       | Full spec                                      | Prerequisite phase 1; same authorization model                                                                                     |
| `specs/workshop-staff-assignment/SPEC.md`                    | Assignment rules                               | Source of the assignment precondition                                                                                              |
| `specs/registration-reconciliation-and-cancellation/SPEC.md` | §8 Out of Scope                                | Explicit requirement that canceled tickets be rejected at sync time                                                                |
| `specs/real-time-event-streaming-hub/SPEC.md`                | Workshop channel contract                      | Consumer of `attendance.updated`                                                                                                   |
