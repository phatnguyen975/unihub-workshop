# Implementation Plan: Nightly Student CSV Ingestion

> **Spec:** `specs/nightly-student-csv-ingestion/SPEC.md`  
> **Status:** APPROVED

## Overview

This plan delivers the worker in three independently verifiable capabilities: safe discovery and rejection of unusable source files, restart-safe clean-file synchronization, and row-level quarantine with organizer reporting. Each task keeps the `batch_ingestion` module hexagonal: cron scheduling and Spring Batch job triggers are inbound, orchestration and ports are core, and MinIO, PostgreSQL, and RabbitMQ are outbound adapters.

## Architecture Decisions for This Plan

- Build the file-level rejection path first — it establishes the trusted organization scope, MinIO object lifecycle, and organization-scoped event publication that every successful path requires.
- Deliver clean-file synchronization before invalid-row handling — a valid export becomes a complete, observable ingestion capability before the implementation adds quarantine complexity.
- Add row quarantine as a separate capability — it changes a successful run's artifacts and summary without changing the clean-file contract.
- Establish restartable Spring Batch chunk processing in the clean-file synchronization slice, because later quarantine behavior must preserve the same committed-chunk semantics.

## Task List

### Phase 1: Safe File Admission

- [ ] **T1 — Reject invalid source files without changing student data and publish the affected organization’s result**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Inbound scheduler, Core use case and ports, Outbound MinIO, PostgreSQL, and RabbitMQ adapters, Configuration, Unit tests
  - **Files:** `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/models/SourceFile.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/models/IngestionJobReport.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/in/RunFileAdmission.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/IngestionObjectStore.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/OrganizationDirectory.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/IngestionJobReportWriter.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/IngestionEventPublisher.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/usecases/FileAdmissionService.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/inbound/NightlyIngestionScheduler.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/storage/S3IngestionObjectStore.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/persistence/PostgresOrganizationDirectory.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/persistence/PostgresIngestionJobReportWriter.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/messaging/RabbitMqIngestionEventPublisher.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/config/BatchIngestionProperties.java`, `apps/workers/src/main/resources/application.yml`, corresponding unit tests under `apps/workers/src/test/java/com/unihub/workshop/workers/batch_ingestion/`
  - **Acceptance Criteria:**
    - When the nightly trigger finds inbox files for one organization slug, it evaluates them in ascending object-creation-time order, derives that slug only from the trusted inbox prefix, resolves its seeded organization ID and name through the organization registry, and admits it only when that name appears in the Workers-only `INGESTION_EXPECTED_FACULTIES` allow-list. _(FR-01–FR-04; Scenario 1, BR-02)_
    - When a file exceeds 100 MB or has an invalid header, no student-directory write is attempted; the source file moves from that organization's inbox to `rejected/<organization-slug>/source-files/`; and an organization-scoped failure event is published. _(FR-05–FR-07; Scenarios 2–3)_
    - A file-level rejection records its organization ID, source key, final outcome metrics, and its moved source-file key—but no row-report key—in an application-owned ingestion-job report. _(FR-26, BR-14; Scenario 11)_
    - The event contains the target organization and source-file identity, but the worker does not attempt to send in-app or email notifications directly. _(BR-07, BR-13)_

#### Checkpoint: Safe File Admission

- [ ] The invalid-file path has unit coverage across core, storage-adapter mapping, messaging-adapter mapping, and scheduler delegation; no invalid file can reach student-directory persistence.

### Phase 2: Restart-Safe Clean Export Synchronization

- [ ] **T2 — Synchronize a valid clean CSV in restart-safe chunks and archive its source file**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Inbound Spring Batch job configuration, Core pipeline and ports, Outbound PostgreSQL and MinIO adapters, Unit tests
  - **Files:** `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/models/StudentDirectoryRow.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/in/IngestStudentCsv.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/StudentDirectoryWriter.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/IngestionCheckpointStore.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/usecases/CleanCsvIngestionService.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/pipeline/StreamingStudentCsvReader.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/inbound/StudentCsvBatchJobConfiguration.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/persistence/PostgresStudentDirectoryWriter.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/persistence/SpringBatchCheckpointStore.java`, corresponding unit tests under `apps/workers/src/test/java/com/unihub/workshop/workers/batch_ingestion/`
  - **Acceptance Criteria:**
    - When an admitted CSV contains only valid rows, the worker streams it without retaining the whole file in memory and bulk creates or updates the one record represented by each deterministic organization, student-number, and email pair. _(FR-08, FR-14; Scenario 4)_
    - Each created record has `ACTIVE` status; each created or updated record records the source file key and import time. _(FR-15–FR-16; Scenario 4)_
    - A valid source row missing from a later export is not deleted, deactivated, or otherwise changed because of that absence. _(FR-17; Scenario 5)_
    - When execution stops after one or more chunk commits, the same source file resumes at the next uncommitted chunk and does not reapply committed chunks. _(FR-18; Scenario 9)_
    - Restart progress is bound to the ingestion attempt and source key, so a different slug-scoped inbox file cannot resume the wrong execution; all restart tests mock Spring Batch and external collaborators. _(FR-18, BR-08; Project testing policy)_
    - After a clean attempt completes, the source moves to `archive/`; its persisted ingestion-job report and summary event have processed-row metrics, `rejectCount: 0`, and no rejection key. _(FR-22–FR-24, FR-26; Scenarios 11–12)_

#### Checkpoint: Restart-Safe Clean Export Synchronization

- [ ] A clean organization-scoped export can be admitted, restart-safely synchronized, archived, and summarized with unit tests covering core behavior and outbound query/object mapping.

### Phase 3: Quarantine and Organizer Reporting

- [ ] **T3 — Continue a valid export after bad rows, preserve a unique reject artifact, and publish the organizer-facing result**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Core row-validation pipeline and ports, Outbound MinIO and RabbitMQ adapters, Inbound scheduler delegation, Unit tests
  - **Files:** `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/models/RejectedStudentRow.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/pipeline/StudentRowValidator.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/pipeline/ChunkDuplicateResolver.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/ports/out/RejectArtifactStore.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/core/usecases/CsvIngestionWithQuarantineService.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/storage/S3RejectArtifactStore.java`, `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/messaging/RabbitMqIngestionEventPublisher.java`, `apps/workers/src/test/java/com/unihub/workshop/workers/batch_ingestion/core/pipeline/StudentRowValidatorTest.java`, `apps/workers/src/test/java/com/unihub/workshop/workers/batch_ingestion/core/usecases/CsvIngestionWithQuarantineServiceTest.java`, `apps/workers/src/test/java/com/unihub/workshop/workers/batch_ingestion/outbound/storage/S3RejectArtifactStoreTest.java`
  - **Acceptance Criteria:**
    - Blank required fields, student identifiers outside the `23` plus six-digit format, emails not exactly matching `<student_number>@student.hcmus.edu.vn`, overlength fields, and faculty values that do not match the resolved expected faculty are quarantined with reasons while valid rows from the same file are synchronized. _(FR-09–FR-12, FR-19; Scenarios 6–7)_
    - Within one chunk, the first duplicate row is retained and every later duplicate is quarantined with a duplicate reason. _(FR-13, FR-19; Scenario 8)_
    - When at least one row is quarantined, the worker uploads one row report at `rejected/<organization-slug>/row-reports/<job-execution-id>.csv` before archiving the source, then persists and publishes an organization-scoped summary containing that key and outcome metrics. _(FR-20–FR-23, FR-26; Scenarios 10–11)_
    - The completion event identifies the organization for the future notifications worker’s in-app/email broadcast; the publisher contains the documented deferred-fan-out code comment and does not directly deliver either channel. _(FR-25, BR-13; Scenario 13)_

#### Checkpoint: Quarantine and Organizer Reporting

- [ ] All documented invalid-row conditions continue processing valid rows; reject artifacts are unique and only created when needed; notification-event behavior is unit-tested with the publishing port mocked.

#### Checkpoint: Feature Complete

- [ ] All tasks above are checked off.
- [ ] Every acceptance criterion across all tasks has been verified with unit tests.
- [ ] The full workers unit-test suite passes, including static architecture checks where applicable.
- [ ] Ready for `/review`.

## Risks and Mitigations

| Risk                                                                       | Impact | Mitigation                                                                                                                                                               |
| :------------------------------------------------------------------------- | :----- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Moving an object or publishing an event fails after student chunks commit. | High   | Keep source files in `inbox/` until the successful lifecycle operation completes; use attempt-specific checkpoint state and idempotent object/event operations on retry. |
| Notification fan-out is not implemented when this worker is built.         | Medium | Publish an organization-scoped event only, retain the required code comment at that boundary, and mock the publisher in this feature’s tests.                            |
| The report table is unavailable when the new worker is deployed.           | High   | Deploy the additive Flyway migration first; it neither rewrites nor changes existing tables, and a failed deployment rolls forward with a corrective migration.          |
| Large nightly files increase memory pressure or database contention.       | Medium | Stream rows, use bounded chunks and bulk upserts, and keep all writes on the primary database.                                                                           |

## Open Questions

None.
