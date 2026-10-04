# Implementation Plan: Workshop Lifecycle Sweeper

> **Spec:** `specs/workshop-lifecycle-sweeper/SPEC.md`  
> **Status:** APPROVED

## Overview

This plan implements the two background maintenance jobs for Feature 2.5 (`workshop-lifecycle-sweeper`) within the `workshops` module: **Job 1 (Lifecycle Sweeper)** and **Job 2 (Capacity Reconciliation Job)**. The implementation is broken down into two distinct, vertically sliced tasks ordered by capability dependency. Task 1 delivers the complete Lifecycle Sweeper capability end-to-end — distributed locking infrastructure, atomic database bulk transition of expired workshops, transactional outbox event publication, post-commit Redis availability counter eviction, structured logging, and scheduled execution. Task 2 delivers the complete Capacity Reconciliation capability end-to-end — reusing the lock infrastructure with a dedicated key, batch querying active workshops and registrations across module boundaries, ground-truth drift detection and repair, structured summary logging, and scheduled execution.

## Architecture Decisions for This Plan

- **T1 before T2:** Task 1 establishes the outbound distributed lock port (`WorkshopLockPort`) and its Redis adapter (`RedisWorkshopLockAdapter`). Task 2 reuses this lock capability with its own lock key (`lock:sweeper:capacity-reconciliation`). Slicing T1 first ensures the shared concurrency control infrastructure is built and verified with unit tests before T2 is executed.
- **Post-commit Cache Eviction via `@TransactionalEventListener`:** To satisfy BR-05 and Scenario 12 without coupling business transaction commit to cache mutation, cache eviction is implemented via a dedicated listener (`WorkshopLifecycleCacheEvictionListener`) listening to `WorkshopCompletedEvent` with `phase = TransactionPhase.AFTER_COMMIT`. If the database transaction rolls back, eviction never fires.
- **Atomic Bulk Update via Native SQL with `RETURNING`:** To satisfy FR-03, FR-04, and BR-03 with zero round-trip overhead and optimistic concurrency safety, `WorkshopRepositoryAdapter` executes a native SQL statement (`UPDATE workshops SET status = 'COMPLETED', updated_at = :now, version = version + 1 WHERE status = 'PUBLISHED' AND ends_at <= :now RETURNING id, organization_id, title, slug, starts_at, ends_at, updated_at`) via `EntityManager`, mapping the returned rows to a lightweight domain record `CompletedWorkshopDetails`.
- **Database Server Clock Authority:** Time comparison (`ends_at <= NOW()`) is evaluated in the database statement to ensure multi-node clock skew cannot cause inconsistent transitions across nodes.
- **Batch Processing for Active Registration Counts:** To satisfy BR-08 and eliminate N+1 queries, Task 2 calls `WorkshopRegistrationCountPort.countActiveRegistrationsBatch(List<UUID>)` (already implemented by the `registrations` module) to fetch all active registration counts in a single operation.
- **Dedicated Schedulers in Infrastructure:** Schedulers (`WorkshopLifecycleSweeperScheduler` and `WorkshopCapacityReconciliationScheduler`) reside in `workshops/infrastructure/scheduler/`, each wired with `@Scheduled(cron = "${...}")` and injecting its respective inbound use-case port. This preserves Clean Architecture boundaries (Presentation/Trigger → Inbound Port → Use Case).
- **No HTTP Layer or API Contract:** In accordance with the approved spec, neither job exposes REST endpoints, DTOs, or web controllers.

## Task List

### Phase 1: Lifecycle Sweeper (Job 1)

- [ ] **T1 — Automated Lifecycle Sweeper for Expired Workshops**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:**
    - Domain & Shared Kernel (`WorkshopCompletedEvent`)
    - Application (`SweepWorkshopLifecycleUseCase`, `WorkshopLockPort`, `WorkshopAvailabilityPort`, `WorkshopRepository`, `CompletedWorkshopDetails`)
    - Infrastructure Persistence (`WorkshopRepositoryAdapter`)
    - Infrastructure Cache (`RedisWorkshopLockAdapter`, `RedisWorkshopAvailabilityAdapter`, `WorkshopLifecycleCacheEvictionListener`)
    - Infrastructure Scheduler (`WorkshopLifecycleSweeperScheduler`)
    - Configuration (`application.yml`)
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` _(modify — add `app.workshops.sweeper.cron` default `0 */5 * * * *`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopLockPort.java` _(create — `tryLock(String lockKey, Duration ttl)`, `releaseLock(String lockKey)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/cache/RedisWorkshopLockAdapter.java` _(create — implements `WorkshopLockPort` using `StringRedisTemplate`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/CompletedWorkshopDetails.java` _(create — record DTO for returned workshop data)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopRepository.java` _(modify — add `List<CompletedWorkshopDetails> completeExpiredWorkshops(Instant now)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(modify — implement `completeExpiredWorkshops` using native SQL `UPDATE ... RETURNING ...`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAvailabilityPort.java` _(modify — add `void deleteCapacity(UUID workshopId)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/cache/RedisWorkshopAvailabilityAdapter.java` _(modify — implement `deleteCapacity(UUID workshopId)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/cache/WorkshopLifecycleCacheEvictionListener.java` _(create — `@TransactionalEventListener(phase = AFTER_COMMIT)` listening to `WorkshopCompletedEvent`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/SweepWorkshopLifecycleUseCase.java` _(create — inbound port `void sweepLifecycle()`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/SweepWorkshopLifecycleUseCaseImpl.java` _(create — orchestrates lock acquisition, repository bulk update, outbox event publishing, lock release, and logging)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/scheduler/WorkshopLifecycleSweeperScheduler.java` _(create — `@Scheduled(cron = "${app.workshops.sweeper.cron:0 */5 * * * *}")` invoking use case)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/cache/RedisWorkshopLockAdapterTest.java` _(create — unit tests for tryLock and releaseLock)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/cache/WorkshopLifecycleCacheEvictionListenerTest.java` _(create — unit tests for post-commit cache eviction)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/SweepWorkshopLifecycleUseCaseImplTest.java` _(create — unit tests covering lock held skip, zero workshops swept, N workshops swept with event publication, DB failure lock release)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/scheduler/WorkshopLifecycleSweeperSchedulerTest.java` _(create — unit test for scheduled trigger invocation)_
  - **Acceptance Criteria:**
    - When `sweepLifecycle()` is invoked and `lock:sweeper:lifecycle` is already held, the use case skips execution, does not query the database, publishes no events, touches no Redis cache keys, and logs a DEBUG message. _(FR-01, Scenario 3)_
    - When `sweepLifecycle()` is invoked and the lock is acquired, the use case bulk transitions all `PUBLISHED` workshops with `ends_at <= now()` to `COMPLETED` and increments their `version` within a single database transaction. _(FR-03, FR-04, BR-03, Scenario 2)_
    - For each transitioned workshop, a `WorkshopCompletedEvent` is published via `ApplicationEventPublisher`, containing `workshopId`, `organizationId`, `title`, `slug`, `startsAt`, `endsAt`, and `completedAt`. _(FR-05, Scenario 2)_
    - After the database transaction commits, `WorkshopLifecycleCacheEvictionListener` deletes the Redis key `workshop:{workshopId}:capacity` for each completed workshop. If the transaction rolls back, eviction is NOT executed. _(FR-06, BR-05, Scenario 2, Scenario 12)_
    - When zero workshops are eligible for completion, the lock is released normally and a DEBUG message is logged. _(FR-07, Scenario 1)_
    - When the database update fails with an exception, the lock is released in a finally block and a WARN message is logged with exception details. _(FR-09, Scenario 4)_
    - Workshops in `DRAFT`, `COMPLETED`, or `CANCELED` states are untouched. _(BR-03, BR-04, Scenario 5)_
    - Cron schedule defaults to `0 */5 * * * *` and is overridable via `app.workshops.sweeper.cron`. _(FR-02)_

#### Checkpoint: Phase 1

- [ ] All Phase 1 unit tests pass (`RedisWorkshopLockAdapterTest`, `WorkshopLifecycleCacheEvictionListenerTest`, `SweepWorkshopLifecycleUseCaseImplTest`, `WorkshopLifecycleSweeperSchedulerTest`).
- [ ] Modulith architecture tests pass (`./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`).
- [ ] No regression in existing tests across `workshops` and `registrations`.

### Phase 2: Capacity Reconciliation (Job 2)

- [ ] **T2 — Capacity Reconciliation for Active Workshops**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:**
    - Application (`ReconcileWorkshopCapacityUseCase`, `ActiveWorkshopCapacity`, `WorkshopRepository`, `WorkshopAvailabilityPort`, `WorkshopRegistrationCountPort`)
    - Infrastructure Persistence (`WorkshopRepositoryAdapter`, `WorkshopJpaRepository`)
    - Infrastructure Cache (`RedisWorkshopAvailabilityAdapter`)
    - Infrastructure Scheduler (`WorkshopCapacityReconciliationScheduler`)
    - Configuration (`application.yml`)
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` _(modify — add `app.workshops.capacity-reconciliation.cron` default `0 */5 * * * *`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/ActiveWorkshopCapacity.java` _(create — record `UUID id, int capacity`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopRepository.java` _(modify — add `List<ActiveWorkshopCapacity> findActivePublishedWorkshops(Instant now)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopJpaRepository.java` _(modify — add query for published workshops with `endsAt > now`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/persistence/WorkshopRepositoryAdapter.java` _(modify — implement `findActivePublishedWorkshops`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/out/WorkshopAvailabilityPort.java` _(modify — add `Integer getCapacity(UUID workshopId)`, `void setCapacity(UUID workshopId, int capacity)`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/cache/RedisWorkshopAvailabilityAdapter.java` _(modify — implement `getCapacity` and `setCapacity`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/ports/in/ReconcileWorkshopCapacityUseCase.java` _(create — inbound port `void reconcileCapacity()`)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/application/usecases/ReconcileWorkshopCapacityUseCaseImpl.java` _(create — orchestrates lock acquisition, batch queries, drift calculation, cache repair, lock release, and summary logging)_
    - `apps/backend/src/main/java/com/unihub/workshop/workshops/infrastructure/scheduler/WorkshopCapacityReconciliationScheduler.java` _(create — `@Scheduled(cron = "${app.workshops.capacity-reconciliation.cron:0 */5 * * * *}")` invoking use case)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/application/usecases/ReconcileWorkshopCapacityUseCaseImplTest.java` _(create — unit tests for lock skip, drift detected and corrected, missing key set, no drift no-op, batch registration fetch verification, summary logging)_
    - `apps/backend/src/test/java/com/unihub/workshop/workshops/infrastructure/scheduler/WorkshopCapacityReconciliationSchedulerTest.java` _(create — unit test for scheduled trigger invocation)_
  - **Acceptance Criteria:**
    - When `reconcileCapacity()` is invoked and `lock:sweeper:capacity-reconciliation` is already held, execution is skipped without error and a DEBUG message is logged. _(FR-10, Scenario 9)_
    - When the lock is acquired, the use case retrieves all `PUBLISHED` workshops with `ends_at > now()`. _(FR-12, BR-07)_
    - Active registration counts (`PENDING` or `CONFIRMED`) for all active workshops are retrieved via `WorkshopRegistrationCountPort.countActiveRegistrationsBatch(workshopIds)` in a single batch call. _(FR-13, BR-08, Scenario 10)_
    - Ground-truth capacity is computed as `Math.max(0, totalCapacity - activeRegistrationCount)`. _(FR-14, BR-06)_
    - When the Redis counter for a workshop differs from ground truth, it is unconditionally overwritten and a WARN log tagged `[CAPACITY_DRIFT_DETECTED]` is emitted with workshopId, stale value, and corrected value. _(FR-16, Scenario 6)_
    - When the Redis counter is missing/absent, it is set to ground truth and a WARN log tagged `[CAPACITY_DRIFT_DETECTED]` is emitted indicating the counter was absent. _(FR-16, Scenario 7)_
    - When the Redis counter matches ground truth exactly, no write occurs and a DEBUG message is logged. _(FR-17, Scenario 8)_
    - When all workshops are evaluated, an INFO summary log is emitted containing total evaluated count, drift corrected count, and timestamp. _(FR-19, Scenario 11)_
    - The lock is always released upon completion (or failure) in a finally block. _(FR-18)_
    - Cron schedule defaults to `0 */5 * * * *` and is overridable via `app.workshops.capacity-reconciliation.cron`. _(FR-11)_

#### Checkpoint: Phase 2

- [ ] All Phase 2 unit tests pass (`ReconcileWorkshopCapacityUseCaseImplTest`, `WorkshopCapacityReconciliationSchedulerTest`).
- [ ] Modulith architecture tests pass (`./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`).
- [ ] No regression across the entire test suite.

### Checkpoint: Feature Complete

- [ ] All tasks above (T1, T2) are checked off.
- [ ] Every acceptance criterion across Scenarios 1–12 from `SPEC.md` has been verified with unit tests.
- [ ] Architecture regression test passes (`ModulithArchitectureTest`).
- [ ] Full backend test suite passes (`./gradlew test`).
- [ ] Ready for `/review workshop-lifecycle-sweeper`.

## Risks and Mitigations

| Risk                                                          | Impact | Mitigation                                                                                                                                                                                                    |
| :------------------------------------------------------------ | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Distributed lock TTL expiry during execution**              | Medium | Both jobs operate in batch mode (Job 1 is a single bulk update; Job 2 fetches counts in 1 round-trip), taking milliseconds. The 240-second TTL provides an extensive safety margin against premature release. |
| **Cache eviction executed on rolled-back transaction**        | High   | Post-commit cache eviction in Job 1 is decoupled via Spring `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. If the transaction rolls back, the listener method is never invoked.        |
| **N+1 queries when reconciling many workshops in Job 2**      | High   | Enforced batch retrieval via `WorkshopRegistrationCountPort.countActiveRegistrationsBatch(List<UUID>)`. At no point in Job 2 is a per-workshop database count query executed.                                 |
| **Multi-node clock skew during `ends_at` comparison**         | Low    | The bulk transition query in Job 1 relies on the database server timestamp (`ends_at <= NOW()` / `:now` evaluated consistently), preventing node-level clock drift from causing discrepancies.                |
| **Transient Redis write failure during Job 2 reconciliation** | Low    | Job 2 catches per-workshop Redis write exceptions, logs a warning, and continues reconciling remaining workshops rather than failing the whole batch. The next scheduled run (5 minutes later) will retry.    |

## Open Questions

None. All architectural and functional requirements were confirmed during context priming and spec approval.
