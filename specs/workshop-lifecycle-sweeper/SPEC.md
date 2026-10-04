# Spec: Workshop Lifecycle Sweeper

> **Status:** APPROVED  
> **Domain:** 2 — Workshop Lifecycle Management  
> **Owner/area:** `workshops` module (backend)  
> **Last revised:** 2026-10-05 — first version

## 1. Overview

The Workshop Lifecycle Sweeper is a pair of system-level background jobs running inside the `workshops` module. They require no human trigger and expose no HTTP endpoints. **Job 1 — Lifecycle Sweeper** detects workshops whose scheduled end-time has passed and whose status is still `PUBLISHED`, atomically transitions them to `COMPLETED`, publishes a domain event per completed workshop so downstream consumers (e.g., the search-sync worker) can react, and post-commit evicts the Redis availability counter for each completed workshop. **Job 2 — Capacity Reconciliation Job** periodically re-derives the ground-truth available-seat count for every currently `PUBLISHED` workshop from the database and reconciles it against the Redis counter, correcting any drift that accumulated due to network errors, crashes, or redelivered messages. Both jobs are multi-node-safe via distributed locking, configurable via `application.yml`, and produce structured log output only — no user-facing responses and no metrics collection.

## 2. Actors & Preconditions

| Actor                                                  | Precondition                                                                                                                                                                                                                                                                                                                   |
| :----------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| System scheduler (Job 1 — Lifecycle Sweeper)           | Scheduled task execution is enabled in the application. Redis is reachable (for the distributed lock). PostgreSQL primary is reachable (for the bulk update). The application has been running long enough for at least one scheduled trigger to fire.                                                                         |
| System scheduler (Job 2 — Capacity Reconciliation Job) | Scheduled task execution is enabled in the application. Redis is reachable (for the distributed lock and counter reads/writes). PostgreSQL replica (or primary when replica unavailable) is reachable for reading active workshops and registration counts. The cross-module registration count port is wired and operational. |

## 3. Functional Requirements

### Job 1 — Lifecycle Sweeper

- **FR-01:** The system SHALL attempt to acquire a distributed lock (non-blocking, single attempt) before executing any lifecycle sweep work. When the lock is already held by another node, the system SHALL skip the current invocation entirely without error, log a structured DEBUG-level message indicating the sweep was skipped due to a concurrently held lock, and take no further action for that invocation.
- **FR-02:** The system SHALL execute the Lifecycle Sweeper on a configurable schedule, defaulting to every 5 minutes (`0 */5 * * * *`). The schedule expression SHALL be overridable via the `app.workshops.sweeper.cron` configuration key.
- **FR-03:** The system SHALL atomically identify all workshops whose persisted status is `PUBLISHED` and whose scheduled end time is less than or equal to the current wall-clock time, and transition their status to `COMPLETED` in a single database operation that returns the full row details (identifier, organization identifier, title, slug, start time, end time, and completion timestamp) for each affected row.
- **FR-04:** The system SHALL, within the same database transaction as FR-03, increment the version counter on every transitioned workshop to satisfy optimistic-concurrency requirements.
- **FR-05:** The system SHALL, for each workshop transitioned to `COMPLETED`, publish a domain event carrying the workshop's identifier, organization identifier, title, slug, start time, end time, and completion timestamp, so that downstream consumers receive exactly-once notification of the transition. Event publication SHALL participate in the transactional outbox — the event is durably stored in the database as part of the same transaction before any broker delivery occurs.
- **FR-06:** The system SHALL, after the database transaction for FR-03 commits successfully, remove the Redis availability counter for each workshop that was transitioned to `COMPLETED`. This eviction SHALL occur only post-commit — it must not execute if the transaction is rolled back.
- **FR-07:** The system SHALL release the distributed lock (or rely on the lock's time-to-live expiry) upon completion of all sweep work. When no workshops are eligible for transition, the system SHALL still release the lock normally and log a DEBUG-level message indicating zero workshops were swept.
- **FR-08:** The system SHALL log, at INFO level, a structured message for each successfully completed workshop, including its identifier and the sweep's invocation timestamp.
- **FR-09:** The system SHALL log a WARN-level message when the database operation in FR-03 fails, including the exception details, and SHALL ensure the distributed lock is released regardless of whether the database operation succeeded or failed.

### Job 2 — Capacity Reconciliation Job

- **FR-10:** The system SHALL attempt to acquire a separate distributed lock (non-blocking, single attempt) before executing any capacity reconciliation work. When the lock is already held by another node, the system SHALL skip the current invocation entirely without error, log a structured DEBUG-level message indicating the reconciliation was skipped, and take no further action for that invocation.
- **FR-11:** The system SHALL execute the Capacity Reconciliation Job on a configurable schedule, defaulting to every 5 minutes (`0 */5 * * * *`). The schedule expression SHALL be overridable via the `app.workshops.capacity-reconciliation.cron` configuration key.
- **FR-12:** The system SHALL retrieve all currently `PUBLISHED` workshops whose scheduled end time is strictly greater than the current wall-clock time from the database, fetching at minimum their identifier, total capacity, and published status.
- **FR-13:** The system SHALL retrieve the active registration count — registrations whose status is `PENDING` or `CONFIRMED` — for each workshop identified in FR-12, using the cross-module registration count capability, in a single batched query rather than one query per workshop.
- **FR-14:** The system SHALL compute the ground-truth available seat count for each workshop as: `max(0, totalCapacity − activeRegistrationCount)`.
- **FR-15:** The system SHALL read the current value of each workshop's availability counter from the Redis cache.
- **FR-16:** The system SHALL, when the Redis availability counter for a workshop is absent or differs from the ground-truth value computed in FR-14, unconditionally overwrite the counter with the ground-truth value and emit a WARN-level structured log entry tagged with the marker `[CAPACITY_DRIFT_DETECTED]`, including the workshop identifier, the stale value (or "absent"), and the corrected value.
- **FR-17:** The system SHALL, when the Redis availability counter for a workshop matches the ground-truth value exactly, take no corrective action and emit a DEBUG-level log entry confirming consistency for that workshop.
- **FR-18:** The system SHALL release the distributed lock (or rely on its time-to-live expiry) upon completion of all reconciliation work, whether or not any drift was detected and corrected.
- **FR-19:** The system SHALL log, at INFO level, a structured summary at the end of each reconciliation run, including the total number of active workshops evaluated, the number where drift was detected and corrected, and the invocation timestamp.

## 4. Business Rules

- **BR-01:** The Lifecycle Sweeper distributed lock key is `lock:sweeper:lifecycle`. The lock SHALL be acquired with a time-to-live of 240 seconds using a set-if-absent (NX) operation to guarantee mutual exclusion across all horizontally scaled application nodes. _(Source: `docs/feature-catalog.md` §Feature 2.5 Key Constraints; `docs/system-design.md` §Automated Lifecycle Sweeper)_
- **BR-02:** The Capacity Reconciliation Job distributed lock key is `lock:sweeper:capacity-reconciliation`. The lock SHALL be acquired with a time-to-live of 240 seconds using a set-if-absent (NX) operation. _(Source: `docs/feature-catalog.md` §Feature 2.5 Key Constraints; `docs/system-design.md` §Capacity Reconciliation Job)_
- **BR-03:** The atomic status transition in Job 1 (FR-03) must target only rows where `status = 'PUBLISHED'` AND `ends_at <= NOW()`. Any workshop in a state other than `PUBLISHED` (i.e., `DRAFT`, `COMPLETED`, or `CANCELED`) SHALL NOT be touched by the Lifecycle Sweeper. _(Source: `docs/database-schema.md` §workshops table; `docs/system-design.md` §Automated Lifecycle Sweeper)_
- **BR-04:** The `COMPLETED` status is a terminal state. A workshop that has been transitioned to `COMPLETED` by the Lifecycle Sweeper cannot be transitioned back to `PUBLISHED`, `DRAFT`, or any other status by this sweeper. Idempotency is guaranteed by the `WHERE status = 'PUBLISHED'` predicate — repeated runs will not re-process already-completed workshops. _(Source: `docs/system-design.md` §Workshop Lifecycle Flow)_
- **BR-05:** Redis availability counter eviction in Job 1 (FR-06) must occur strictly after the database transaction commits. If the transaction is rolled back, the eviction must NOT execute. This prevents serving stale data from a counter whose associated workshop was never actually completed. _(Source: confirmed user decision — Option B; `docs/system-design.md` §Automated Lifecycle Sweeper)_
- **BR-06:** The ground-truth available seat count for the Capacity Reconciliation Job (FR-14) is defined as `max(0, totalCapacity − activeRegistrationCount)`. The floor of zero prevents the counter from being set to a negative value. Active registrations are those with status `PENDING` or `CONFIRMED`. _(Source: `docs/feature-catalog.md` §Feature 2.5; `docs/database-schema.md` §registrations table; ADR-013)_
- **BR-07:** The Capacity Reconciliation Job (FR-12, FR-13) operates only on `PUBLISHED` workshops with `ends_at > NOW()`. Workshops already completed or in a terminal state do not participate in reconciliation — their Redis counters, if any, are cleaned up by Job 1 (FR-06). _(Source: `docs/feature-catalog.md` §Feature 2.5)_
- **BR-08:** The cross-module registration count used by the Capacity Reconciliation Job (FR-13) must be fetched in a single batched operation, not one round-trip per workshop, to avoid N+1 query behavior at scale. _(Source: `docs/feature-catalog.md` §Feature 2.5 Key Constraints; `docs/adrs.md` ADR-013)_
- **BR-09:** Both jobs are background system processes with no associated HTTP session, user identity, or RBAC check. Neither job accepts any externally supplied input at runtime. _(Source: `docs/feature-catalog.md` §Feature 2.5; `docs/system-design.md` §Automated Lifecycle Sweeper)_
- **BR-10:** Both jobs' cron expressions are configurable at deployment time via `application.yml`. Hard-coding the schedule in source is not permitted. _(Source: confirmed user decision; `docs/feature-catalog.md` §Feature 2.5)_
- **BR-11:** Domain events published by the Lifecycle Sweeper (FR-05) participate in the transactional outbox pattern — they are durably recorded in the database within the same transaction as the status update, before any message broker delivery occurs. This ensures that a crash between DB commit and broker publish does not silently drop events. _(Source: `docs/adrs.md` ADR-017, ADR-018; `docs/system-design.md` §Event-Driven Flows)_
- **BR-12:** A single Redis availability counter key follows the pattern `workshop:{workshopId}:capacity`. The Lifecycle Sweeper deletes this key (FR-06); the Capacity Reconciliation Job reads and, where necessary, overwrites this key (FR-15, FR-16). No other format is permitted. _(Source: `docs/feature-catalog.md` §Feature 2.5; `docs/system-design.md` §Capacity Reconciliation Job; ADR-013)_

## 5. State Transitions

The Lifecycle Sweeper directly modifies the `status` field of the `Workshop` entity. Only the transition it owns is listed below. All other transitions belong to other features and are listed for completeness as invalid for this feature.

| From State  | Event/Trigger                                | To State    | Valid? | Rejection Behavior (if invalid)                                                      |
| :---------- | :------------------------------------------- | :---------- | :----: | :----------------------------------------------------------------------------------- |
| `PUBLISHED` | Scheduled sweep fires AND `ends_at <= NOW()` | `COMPLETED` | ✅ Yes | —                                                                                    |
| `DRAFT`     | Scheduled sweep fires                        | `COMPLETED` | ❌ No  | Row is excluded by `WHERE status = 'PUBLISHED'` predicate; no action taken, no error |
| `COMPLETED` | Scheduled sweep fires                        | `COMPLETED` | ❌ No  | Row is excluded by `WHERE status = 'PUBLISHED'` predicate; idempotent, no error      |
| `CANCELED`  | Scheduled sweep fires                        | `COMPLETED` | ❌ No  | Row is excluded by `WHERE status = 'PUBLISHED'` predicate; no action taken, no error |
| `PUBLISHED` | Scheduled sweep fires AND `ends_at > NOW()`  | `COMPLETED` | ❌ No  | Row does not satisfy `ends_at <= NOW()` predicate; no action taken, no error         |

> [!NOTE]
> The Capacity Reconciliation Job does **not** modify the `status` field. It only reads `PUBLISHED` workshops and adjusts the Redis counter. No state transition is involved in Job 2.

## 6. Non-Functional Requirements

- **Performance:** Job 1 must transition all eligible workshops in a single batched database statement rather than one statement per workshop. Job 2 must fetch registration counts in a single batched query (BR-08). Neither job may issue per-workshop round-trips to the database for its primary read or write path.
- **Concurrency / Multi-node safety:** At any point in time, at most one instance of Job 1 and at most one instance of Job 2 may be executing concurrently across all horizontally scaled application nodes. This is guaranteed by the distinct Redis distributed locks with 240-second TTL (BR-01, BR-02). A node that cannot acquire its lock must skip silently rather than queue or block.
- **Idempotency:** Job 1 is idempotent by construction: re-running the sweep against already-`COMPLETED` workshops produces zero rows affected. Job 2 is idempotent by construction: re-running reconciliation when the Redis counter already matches the ground truth produces zero writes and a DEBUG log only.
- **Rate-limiting:** N/A — both jobs are internal scheduled processes. No external rate limiter applies. The distributed lock prevents pile-up across nodes.
- **Security:** Both jobs operate with system-level authority. They accept no externally-sourced input at runtime and require no RBAC enforcement (BR-09). Lock keys must not be externally overridable via configuration.
- **Reliability / Failure modes:**
  - If the distributed lock cannot be acquired (another node holds it), the current invocation is skipped without raising an alert. This is the expected steady-state behavior in a multi-node deployment.
  - If the database operation in Job 1 fails, the distributed lock must still be released. The failure must be logged at WARN level (FR-09).
  - If a Redis counter SET fails in Job 2, the reconciliation job must log the failure and continue processing remaining workshops rather than aborting the entire run.
  - If Job 1's post-commit cache eviction fails (transient Redis error), the stale counter will be corrected on the next run of Job 2. This is acceptable because the two jobs together form a self-healing pair.
  - If no `PUBLISHED` workshops exist at sweep time, both jobs complete successfully and produce zero-work log entries.
- **Observability:** Both jobs emit structured SLF4J log entries only — no Micrometer metrics, no Prometheus counters, no Grafana dashboards are in scope. Required log entries are described in FR-07, FR-08, FR-09, FR-16, FR-17, FR-19.

## 7. Acceptance Criteria

### Scenario 1: Lifecycle sweep with no eligible workshops

```gherkin
Given the application scheduler is active
And no workshop has status "PUBLISHED" with ends_at <= NOW()
And the distributed lock "lock:sweeper:lifecycle" is not held
When the Lifecycle Sweeper fires
Then the distributed lock is acquired
And zero workshops are transitioned to "COMPLETED"
And zero domain events are published
And zero Redis availability counters are evicted
And the distributed lock is released
And a DEBUG-level log entry is emitted indicating zero workshops were swept
```

_Traces to: FR-02, FR-03, FR-05, FR-06, FR-07_

### Scenario 2: Lifecycle sweep successfully completes N expired workshops

```gherkin
Given the application scheduler is active
And N workshops have status "PUBLISHED" with ends_at <= NOW() (N >= 1)
And the distributed lock "lock:sweeper:lifecycle" is not held
When the Lifecycle Sweeper fires
Then the distributed lock is acquired
And all N workshops are transitioned to status "COMPLETED" in a single atomic database operation
And each of the N workshops has its version counter incremented
And one domain event is published per completed workshop, containing workshopId, organizationId, title, slug, startsAt, endsAt, and completedAt
And all N events are stored in the transactional outbox within the same database transaction as the status transition
And after the database transaction commits, the Redis availability counter key for each of the N workshops is deleted
And an INFO-level log entry is emitted per completed workshop
And the distributed lock is released
```

_Traces to: FR-01, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08_

### Scenario 3: Lifecycle sweep skipped because lock is already held

```gherkin
Given the application scheduler is active
And another application node currently holds the distributed lock "lock:sweeper:lifecycle"
When the Lifecycle Sweeper fires on the current node
Then no database query is executed
And no workshops are transitioned
And no domain events are published
And no Redis keys are touched
And a DEBUG-level log entry is emitted indicating the sweep was skipped due to a concurrently held lock
```

_Traces to: FR-01_

### Scenario 4: Lifecycle sweep database failure releases lock

```gherkin
Given the application scheduler is active
And the distributed lock "lock:sweeper:lifecycle" is acquired successfully
And the database operation to transition workshops to COMPLETED fails with an exception
When the exception propagates
Then a WARN-level log entry is emitted including the exception details
And the distributed lock is released
And no domain events are published
And no Redis keys are touched
```

_Traces to: FR-09_

### Scenario 5: Lifecycle sweep does not process non-PUBLISHED workshops

```gherkin
Given a workshop exists with status "DRAFT" and ends_at <= NOW()
And a workshop exists with status "CANCELED" and ends_at <= NOW()
And a workshop exists with status "COMPLETED" and ends_at <= NOW()
And the distributed lock "lock:sweeper:lifecycle" is not held
When the Lifecycle Sweeper fires
Then none of the three workshops are updated
And no domain events are published for those workshops
And no Redis keys are deleted for those workshops
```

_Traces to: FR-03, BR-03, BR-04 — State Transitions_

### Scenario 6: Capacity reconciliation detects and corrects drift

```gherkin
Given the application scheduler is active
And at least one workshop W exists with status "PUBLISHED" and ends_at > NOW()
And W has totalCapacity = 100 and 60 active registrations (PENDING or CONFIRMED)
And the Redis availability counter for W holds the value 30 (instead of the correct 40)
And the distributed lock "lock:sweeper:capacity-reconciliation" is not held
When the Capacity Reconciliation Job fires
Then the distributed lock is acquired
And the ground-truth available count for W is computed as max(0, 100 - 60) = 40
And the Redis availability counter for W is overwritten with the value 40
And a WARN-level log entry tagged [CAPACITY_DRIFT_DETECTED] is emitted for W, including the stale value (30) and corrected value (40)
And the distributed lock is released
```

_Traces to: FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-18_

### Scenario 7: Capacity reconciliation corrects missing Redis counter

```gherkin
Given a workshop W exists with status "PUBLISHED" and ends_at > NOW()
And W has totalCapacity = 50 and 20 active registrations
And no Redis availability counter key exists for W
And the distributed lock "lock:sweeper:capacity-reconciliation" is not held
When the Capacity Reconciliation Job fires
Then the ground-truth available count for W is computed as max(0, 50 - 20) = 30
And the Redis availability counter for W is set to 30
And a WARN-level log entry tagged [CAPACITY_DRIFT_DETECTED] is emitted for W, indicating the counter was absent
```

_Traces to: FR-12, FR-13, FR-14, FR-15, FR-16_

### Scenario 8: Capacity reconciliation finds no drift — no write

```gherkin
Given a workshop W exists with status "PUBLISHED" and ends_at > NOW()
And W has totalCapacity = 80 and 30 active registrations
And the Redis availability counter for W holds the value 50 (which equals max(0, 80 - 30))
And the distributed lock "lock:sweeper:capacity-reconciliation" is not held
When the Capacity Reconciliation Job fires
Then the Redis availability counter for W is NOT overwritten
And a DEBUG-level log entry is emitted confirming consistency for W
And no WARN-level entries are emitted for W
```

_Traces to: FR-14, FR-15, FR-17_

### Scenario 9: Capacity reconciliation skipped because lock is already held

```gherkin
Given another application node currently holds the distributed lock "lock:sweeper:capacity-reconciliation"
When the Capacity Reconciliation Job fires on the current node
Then no database queries are executed
And no Redis counters are read or written
And a DEBUG-level log entry is emitted indicating the reconciliation was skipped
```

_Traces to: FR-10_

### Scenario 10: Capacity reconciliation fetches registration counts in a single batch

```gherkin
Given M workshops exist with status "PUBLISHED" and ends_at > NOW() (M >= 2)
And the distributed lock "lock:sweeper:capacity-reconciliation" is not held
When the Capacity Reconciliation Job fires
Then the registration counts for all M workshops are retrieved in a single batched query, not M separate queries
```

_Traces to: FR-13, BR-08_

### Scenario 11: Capacity reconciliation summary log is emitted

```gherkin
Given M workshops exist with status "PUBLISHED" and ends_at > NOW()
And K of them have drifted Redis counters (K <= M)
And the distributed lock "lock:sweeper:capacity-reconciliation" is not held
When the Capacity Reconciliation Job fires and completes
Then an INFO-level structured log entry is emitted containing: total workshops evaluated (M), workshops with drift corrected (K), and the invocation timestamp
And the distributed lock is released
```

_Traces to: FR-18, FR-19_

### Scenario 12: Post-commit cache eviction is NOT executed on transaction rollback

```gherkin
Given a workshop W has status "PUBLISHED" and ends_at <= NOW()
And a Redis availability counter exists for W
And the distributed lock "lock:sweeper:lifecycle" is acquired
When the Lifecycle Sweeper fires but the database transaction is rolled back
Then the Redis availability counter for W is NOT deleted
And W's status remains "PUBLISHED" in the database
```

_Traces to: FR-06, BR-05_

## 8. Out of Scope

- **No HTTP endpoints.** Neither job exposes a REST endpoint, webhook, or any other network interface. There is no way for an external client or human operator to manually trigger a sweep run via HTTP. No `API_CONTRACT.md` is produced for this feature.
- **No manual trigger mechanism.** Administrative or manual triggering of either job is not in scope. If such a capability is needed in future, it is a separate feature.
- **No Micrometer metrics.** Prometheus counters, Grafana dashboards, and any metric emission are explicitly excluded. Observability is limited to structured SLF4J log output (FR-08, FR-09, FR-16, FR-17, FR-19).
- **No registration cancellation.** When a workshop is completed by Job 1, existing registrations are not cancelled, refunded, or modified by this feature. Any such downstream consequence is handled by a separate feature or worker.
- **No search-sync worker changes.** The search-sync worker already handles `WorkshopCompletedEvent` (routes `"workshop.completed"` to delete the index entry and invalidate the search cache). This feature does not modify the worker.
- **No email or notification delivery.** No attendee or organizer notification is triggered directly by the Lifecycle Sweeper. Downstream notification is the responsibility of separate features triggered by the domain event.
- **No UI client scope.** No web or mobile UI changes are required or produced.
- **No payment-related actions.** The Lifecycle Sweeper does not initiate refunds, charge corrections, or any payment operations.
- **No `PUBLISHED` workshop deactivation by the Capacity Reconciliation Job.** The reconciliation job only corrects Redis counters; it does not cancel registrations, change workshop status, or take any action when a capacity counter is found to be negative or zero.
- **No direct writes to the `registrations` table.** Both jobs are read-only with respect to the `registrations` table; the Capacity Reconciliation Job uses the cross-module count capability (read-only) and never writes to registration rows.

## 9. Linked References

| Source Document             | Section(s) Referenced                                                                                                                                                         |
| :-------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | Feature 2.5 — `workshop-lifecycle-sweeper` (full entry: description, module, DB tables, ADR refs, client scope, Key Constraints)                                              |
| `docs/system-design.md`     | §Automated Lifecycle Sweeper (sequence diagram + prose); §Capacity Reconciliation Job (sequence diagram + prose)                                                              |
| `docs/database-schema.md`   | §workshops table (columns: `status`, `ends_at`, `version`, `capacity`); §registrations table (column: `status`; valid values: `PENDING`, `CONFIRMED`, `CANCELED`, `REFUNDED`) |
| `docs/adrs.md`              | ADR-013 (Redis DECR + reconciliation strategy); ADR-017 (RabbitMQ as message broker); ADR-018 (Transactional Outbox pattern)                                                  |
| `docs/project-structure.md` | §Clean Architecture internal layout (workshops module); §SPI Cross-Module pattern (WorkshopRegistrationCountPort as API-layer SPI)                                            |
| `docs/api-standards.md`     | Confirmed N/A — no HTTP endpoints are introduced                                                                                                                              |
