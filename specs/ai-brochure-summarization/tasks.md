# Implementation Plan: AI Brochure Summarization

> **Spec:** `specs/ai-brochure-summarization/SPEC.md`  
> **API Contract:** `specs/ai-brochure-summarization/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the complete asynchronous PDF brochure upload and AI summarization pipeline across two independently deployable apps. The work is sequenced in three phases:

- **Phase 1** delivers the core backend upload capability — the API surface a caller can immediately exercise and verify end-to-end (upload, validate, persist, enqueue, `202` response) along with the fallback polling endpoint.
- **Phase 2** delivers the worker's core processing pipeline — consuming the event, extracting PDF text, calling the AI provider, persisting results, and pushing real-time notifications — covering the nominal path plus the no-text fast-fail.
- **Phase 3** delivers the worker's resilience layer — retry logic, max-retry exhaustion, dead-letter routing, and the idempotency guard against duplicate message delivery.

Each task within a phase is a complete vertical slice through every layer its capability touches. No task is scoped to a single layer.

## Architecture Decisions for This Plan

- **T1 owns all shared domain models** (`WorkshopBrochure`, `AiSummaryJob`, `AiSummaryJobStatus`) as well as the two new JPA entities and the outbox event class, because these are the foundation the upload use case needs first and every later task builds on them. This follows the Handling Shared Foundations rule — first task that needs them builds them; later tasks declare a dependency on T1.
- **T2 (fallback polling) is separated from T1 (upload)** because it is a distinct, independently testable capability (a different HTTP verb, different business logic, different FR set, and its acceptance criteria can be verified independently). Combining them would produce an L-sized task.
- **Worker tasks split by functional concern, not by layer:** T3 (nominal path + no-text fast-fail) is split from T4 (retry/failure/DLQ) because they are distinct capabilities with distinct acceptance criteria and different infrastructure concerns (circuit breaker + retry vs. DLQ routing). Both are full vertical slices through the worker's hexagonal layers.
- **`HexagonalArchitectureTest` already lists `ai_processing`** in `WORKER_MODULES` — no update to the test file itself is needed. The new module's packages must simply comply with the existing rules (core isolation, no field injection, etc.).
- **No Flyway migration is needed** — `workshop_brochures` and `ai_summary_jobs` tables already exist in `V1__init_schema.sql`. This removes one common task (schema migration) that would otherwise appear first.
- **`app.ai` config section in `application.yml`** (workers) does not yet exist and must be added in T3 alongside the AI adapter wiring, as it is only needed when the AI provider adapter is created.

## Task List

### Phase 1: Backend Upload & Fallback Polling

- [x] **T1 — Brochure upload accepted: ORGANIZER can upload a valid PDF and receive `202 Accepted` with a job ID**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application (use case + ports), Infrastructure (persistence + storage + outbox event), Presentation (controller + DTOs + mapper)
  - **Files:**
    - `workshops/domain/model/WorkshopBrochure.java` _(new — domain record)_
    - `workshops/domain/model/AiSummaryJob.java` _(new — domain record)_
    - `workshops/domain/model/AiSummaryJobStatus.java` _(new — enum: PROCESSING, COMPLETED, FAILED)_
    - `workshops/application/ports/in/UploadBrochureUseCase.java` _(new — inbound port)_
    - `workshops/application/ports/in/UploadBrochureCommand.java` _(new — command record)_
    - `workshops/application/ports/out/WorkshopBrochureRepository.java` _(new — outbound port)_
    - `workshops/application/ports/out/AiSummaryJobRepository.java` _(new — outbound port)_
    - `workshops/application/ports/out/BrochureStoragePort.java` _(new — outbound port for MinIO)_
    - `workshops/application/usecases/UploadBrochureUseCaseImpl.java` _(new — use case impl)_
    - `workshops/infrastructure/persistence/WorkshopBrochureJpaEntity.java` _(new)_
    - `workshops/infrastructure/persistence/WorkshopBrochureJpaRepository.java` _(new)_
    - `workshops/infrastructure/persistence/WorkshopBrochureRepositoryAdapter.java` _(new)_
    - `workshops/infrastructure/persistence/AiSummaryJobJpaEntity.java` _(new)_
    - `workshops/infrastructure/persistence/AiSummaryJobJpaRepository.java` _(new)_
    - `workshops/infrastructure/persistence/AiSummaryJobRepositoryAdapter.java` _(new)_
    - `workshops/infrastructure/persistence/mapper/WorkshopBrochurePersistenceMapper.java` _(new)_
    - `workshops/infrastructure/persistence/mapper/AiSummaryJobPersistenceMapper.java` _(new)_
    - `workshops/infrastructure/storage/MinioBrochureStorageAdapter.java` _(new)_
    - `shared/events/BrochureSummarizationRequestedEvent.java` _(new — outbox event with @Externalized)_
    - `workshops/presentation/controllers/AdminWorkshopController.java` _(modified — add POST /brochure endpoint)_
    - `workshops/presentation/dto/BrochureUploadResponse.java` _(new)_
    - `workshops/presentation/mapper/BrochurePresentationMapper.java` _(new)_
  - **Acceptance Criteria:**
    - `POST /api/v1/admin/workshops/{id}/brochure` with a valid PDF (≤10 MB, MIME `application/pdf`, magic byte `%PDF-`) by an ORGANIZER whose `jwt.org` matches the workshop's `organization_id`, where the workshop is `DRAFT` or `PUBLISHED` and no `PROCESSING` job exists, returns `202 Accepted` with body `{ success: true, data: { jobId, brochureId, status: "PROCESSING", createdAt } }`. _(FR-17, Scenario 1)_
    - The same call causes a new `workshop_brochures` record with `is_active = true` and a new `ai_summary_jobs` record with `status = PROCESSING` to be persisted in the database. _(FR-14, FR-15)_
    - Any previously active `workshop_brochures` record for the same workshop has `is_active` set to `false` after the call. _(FR-13)_
    - The PDF binary is retrievable from MinIO at `workshops/{workshopId}/brochures/{brochureId}.pdf`. _(FR-12)_
    - A `BrochureSummarizationRequestedEvent` row is present in the `event_publication` table after the call. _(FR-16)_
    - The endpoint returns `401` when the JWT is missing/invalid. _(FR-01, Scenario 2)_
    - The endpoint returns `403` when the caller's role is not `ORGANIZER`. _(FR-02, Scenario 3)_
    - The endpoint returns `403` when `jwt.org` does not match `workshop.organization_id`. _(FR-03, Scenario 4)_
    - The endpoint returns `404` when the workshop does not exist. _(FR-04, Scenario 5)_
    - The endpoint returns `422` when the workshop is in `COMPLETED` or `CANCELED` status. _(FR-06, Scenario 6)_
    - The endpoint returns `409` when an existing `ai_summary_jobs` record for the workshop is in `PROCESSING` status. _(FR-07, Scenario 7)_
    - The endpoint returns `422` when the uploaded file's MIME type is not `application/pdf`. _(FR-08, Scenario 8)_
    - The endpoint returns `422` when the uploaded file does not begin with `%PDF-`. _(FR-09, Scenario 9)_
    - The endpoint returns `422` when the uploaded file exceeds 10 MB. _(FR-10, Scenario 10)_

- [x] **T2 — Brochure summary job polling: ORGANIZER can retrieve current job state via the fallback GET endpoint**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Domain (reuse T1 models), Application (use case + port), Infrastructure (persistence — reuse T1 adapters), Presentation (controller + DTO + mapper)
  - **Files:**
    - `workshops/application/ports/in/GetBrochureSummaryJobUseCase.java` _(new — inbound port)_
    - `workshops/application/ports/in/GetBrochureSummaryJobQuery.java` _(new — query record)_
    - `workshops/application/usecases/GetBrochureSummaryJobUseCaseImpl.java` _(new — use case impl)_
    - `workshops/presentation/controllers/AdminWorkshopController.java` _(modified — add GET /brochure/summary-job endpoint)_
    - `workshops/presentation/dto/BrochureSummaryJobResponse.java` _(new)_
    - `workshops/presentation/mapper/BrochurePresentationMapper.java` _(modified — add mapping for summary job response)_
  - **Acceptance Criteria:**
    - `GET /api/v1/admin/workshops/{id}/brochure/summary-job` by an authenticated ORGANIZER whose `jwt.org` matches the workshop, after a brochure has been uploaded, returns `200 OK` with `{ success: true, data: { jobId, brochureId, workshopId, status, resultText?, errorMessage?, attempts, completedAt?, createdAt } }` — null fields are absent from the JSON. _(FR-32, Scenario 14)_
    - The endpoint returns `404` when no workshop exists for the given `{id}`, or when the workshop exists but no brochure has ever been uploaded. _(FR-31, Scenario 15)_
    - The endpoint returns `401` when the JWT is missing/invalid. _(FR-01)_
    - The endpoint returns `403` when `jwt.org` does not match the workshop's `organization_id`. _(FR-05, Scenario 15)_

#### Checkpoint: Phase 1 — Backend API Complete

- [x] Both endpoints (`POST` and `GET`) return the correct responses for all acceptance criteria above when exercised against a running local stack (backend + MinIO + PostgreSQL).
- [x] All unit tests for T1 and T2 pass (use case logic, persistence adapters, controller mapping).
- [x] The `BrochureSummarizationRequestedEvent` is visible in the `event_publication` table when the upload succeeds.
- [x] Build is clean; `ModulithArchitectureTest` passes.

### Phase 2: Worker — Core Processing Pipeline

- [ ] **T3 — Worker processes brochure event: extracts text, calls AI, persists result, and pushes SSE notification (including no-text fast-fail path)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched (Worker Hexagonal):** Inbound (RabbitMQ listener + AMQP config), Core (use case + domain models + port interfaces), Outbound (MinIO PDF fetch adapter, AI summarization adapter — both real Gemini and mock, JPA job repository adapter, Redis real-time publisher adapter)
  - **Files:**
    - `ai_processing/inbound/AiSummarizationListener.java` _(new — @RabbitListener on q.ai.summarization)_
    - `ai_processing/inbound/AiSummarizationEventDto.java` _(new — deserialized event DTO)_
    - `ai_processing/core/models/BrochureSummarizationRequest.java` _(new — core domain record)_
    - `ai_processing/core/models/AiSummaryJobModel.java` _(new — core domain record mirroring DB state)_
    - `ai_processing/core/models/AiSummaryJobStatusModel.java` _(new — enum)_
    - `ai_processing/core/ports/in/ProcessBrochureSummarizationPort.java` _(new — inbound port)_
    - `ai_processing/core/ports/out/BrochureStoragePort.java` _(new — outbound port: fetch PDF bytes)_
    - `ai_processing/core/ports/out/AiSummarizationPort.java` _(new — outbound port: summarize text)_
    - `ai_processing/core/ports/out/AiSummaryJobRepositoryPort.java` _(new — outbound port: read/update job)_
    - `ai_processing/core/ports/out/RealtimePublisherPort.java` _(new — outbound port: push SSE event)_
    - `ai_processing/core/usecases/ProcessBrochureSummarizationUseCase.java` _(new — core orchestrator)_
    - `ai_processing/outbound/storage/MinioAiPdfStorageAdapter.java` _(new — fetches PDF from unihub-brochures bucket)_
    - `ai_processing/outbound/ai_providers/GeminiAiSummarizationAdapter.java` _(new — calls Google Gemini API)_
    - `ai_processing/outbound/ai_providers/MockAiSummarizationAdapter.java` _(new — returns canned response in test/dev)_
    - `ai_processing/outbound/persistence/AiSummaryJobJpaEntity.java` _(new — JPA entity for workers)_
    - `ai_processing/outbound/persistence/JpaAiSummaryJobRepository.java` _(new — Spring Data interface)_
    - `ai_processing/outbound/persistence/JpaAiSummaryJobRepositoryAdapter.java` _(new — implements AiSummaryJobRepositoryPort)_
    - `ai_processing/outbound/persistence/mapper/AiSummaryJobWorkerMapper.java` _(new)_
    - `ai_processing/outbound/messaging/RedisAiRealtimePublisherAdapter.java` _(new — publishes RealtimeEvent to Redis Pub/Sub)_
    - `ai_processing/outbound/config/AiProcessingAmqpConfig.java` _(new — declares q.ai.summarization queue + DLX binding)_
    - `ai_processing/outbound/config/AiProcessingProperties.java` _(new — @ConfigurationProperties for app.ai._)*
    - `workers/src/main/resources/application.yml` _(modified — add app.ai section)_
    - `workers/src/main/resources/application-dev.yml` _(modified — add AI_GEMINI_MODEL and AI_GEMINI_API_KEY defaults for dev/mock)_
  - **Acceptance Criteria:**
    - When a `BrochureSummarizationRequestedEvent` is consumed from `q.ai.summarization` and the PDF contains extractable text (≤50,000 chars), the worker calls the AI provider once, sets `ai_summary_jobs.status = COMPLETED` with `result_text` and `completed_at`, and publishes an `ai.summary.completed` `RealtimeEvent` to Redis Pub/Sub channel `unihub:stream:user:{organizerId}`. _(FR-18, FR-19, FR-21, FR-23, FR-27, FR-28, FR-30, Scenario 1)_
    - When the PDF text exceeds 50,000 characters, the worker chunks the text and calls the AI provider per chunk, then consolidates the summaries; `ai_summary_jobs.status = COMPLETED` with the consolidated `result_text`. _(FR-22)_
    - When the PDF yields no extractable text (image-only PDF), the worker sets `ai_summary_jobs.status = FAILED` with `error_message = "No extractable text found in PDF brochure"`, does **not** call the AI provider, publishes an `ai.summary.failed` `RealtimeEvent`, and ACKs the broker message (no retry, no DLQ). _(FR-20, Scenario 11)_
    - In the dev/test profile, the mock AI adapter is used instead of the real Gemini API; the mock returns a deterministic Markdown string without making any HTTP call. _(FR-24)_
    - The `RealtimeEvent` published to Redis Pub/Sub contains `eventId` (UUID), `eventType`, `timestamp` (ISO-8601 UTC), and `payload` as specified in the API contract. _(FR-30)_

#### Checkpoint: Phase 2 — Worker Core Pipeline Complete

- [ ] The full happy-path flow is exercisable locally: upload a real PDF via the backend `POST` endpoint → worker consumes the event → job transitions to `COMPLETED` → Redis Pub/Sub receives the `ai.summary.completed` event.
- [ ] The no-text fast-fail path is exercisable: upload an image-only PDF → worker marks job `FAILED` → Redis Pub/Sub receives `ai.summary.failed` → no DLQ message.
- [ ] All unit tests for T3 pass (listener, use case, all outbound adapters mocked).
- [ ] Build is clean; `HexagonalArchitectureTest` passes (all hexagonal boundary rules, including the new `ai_processing` module).

### Phase 3: Worker — Resilience, Retry & Dead-Letter

- [ ] **T4 — Worker retries transient AI errors and dead-letters after max attempts, with idempotency guard for duplicate delivery**
  - **Size:** M
  - **Depends on:** T3
  - **Layers touched (Worker Hexagonal):** Inbound (retry/nack logic in listener), Core (retry decision in use case — increments attempts, checks max), Outbound (persistence — update attempts + next_retry_at, AI adapter — circuit breaker wiring)
  - **Files:**
    - `ai_processing/core/usecases/ProcessBrochureSummarizationUseCase.java` _(modified — add retry counter check, max-retry terminal failure path, idempotency guard for COMPLETED/FAILED jobs)_
    - `ai_processing/inbound/AiSummarizationListener.java` _(modified — add nack-without-requeue on max retry, catch transient vs terminal exceptions, idempotency early-exit)_
    - `ai_processing/outbound/persistence/JpaAiSummaryJobRepositoryAdapter.java` _(modified — add updateAttempts, setNextRetryAt, setFailed methods)_
    - `ai_processing/outbound/ai_providers/GeminiAiSummarizationAdapter.java` _(modified — wrap with Resilience4j aiProvider circuit breaker)_
    - `ai_processing/outbound/config/AiProcessingProperties.java` _(modified — add max-attempts, circuit-breaker config binding)_
    - `workers/src/main/resources/application.yml` _(modified — add resilience4j.circuitbreaker.instances.aiProvider config)_
  - **Acceptance Criteria:**
    - When the AI provider returns a transient error (HTTP 429, 5xx, or timeout) on the first or second attempt, the worker increments `ai_summary_jobs.attempts`, sets `next_retry_at` to a future timestamp, and re-throws the exception so the broker re-queues the message for a subsequent attempt; `ai_summary_jobs.status` remains `PROCESSING`. _(FR-25, Scenario 12)_
    - When `ai_summary_jobs.attempts` reaches 3 (max retry exhaustion), the worker sets `ai_summary_jobs.status = FAILED` with `error_message` recording the last error, publishes an `ai.summary.failed` `RealtimeEvent` to Redis Pub/Sub, and rejects the message without requeue (dead-letters to `unihub.dlx::dlq`). _(FR-26, Scenario 13)_
    - When a re-delivered (duplicate) message arrives for a job that is already `COMPLETED` or `FAILED`, the worker detects the terminal state, skips all processing, and ACKs the message without overwriting the existing result. _(Scenario 16 — NFR: Worker Idempotency)_
    - The Resilience4j `aiProvider` circuit breaker is wired and opens after the configured failure rate threshold, returning `503`-equivalent failures without calling the external API while open. _(FR-23, NFR — Reliability)_

#### Checkpoint: Phase 3 — Resilience Complete

- [ ] Retry and DLQ paths are exercisable in dev (by injecting a failing mock that throws on first N attempts and succeeds on the Nth, or always fails).
- [ ] DLQ receives the dead-lettered message after 3 failed attempts; job is `FAILED` in DB; `ai.summary.failed` event was published.
- [ ] Duplicate message delivery (same event re-delivered after job is COMPLETED) results in a no-op ACK with no DB writes.
- [ ] All unit tests for T4 pass (retry decision logic, max-retry terminal path, idempotency guard, nack logic).
- [ ] Build is clean; `HexagonalArchitectureTest` passes.

#### Checkpoint: Feature Complete

- [ ] All tasks (T1–T4) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end.
- [ ] Unit tests pass for all directly changed files (per project testing policy — no full-suite run required; user will run the full suite and feed back failures).
- [ ] `ModulithArchitectureTest` passes (backend).
- [ ] `HexagonalArchitectureTest` passes (workers).
- [ ] Ready for `/review ai-brochure-summarization`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                     | Impact | Mitigation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Concurrent upload race at the DB level** — two `POST` requests for the same workshop arriving within milliseconds, both passing the `PROCESSING` job check before either commits, could produce two `PROCESSING` jobs. | High   | The `409` check must be implemented inside the same transaction as the `ai_summary_jobs` insert, relying on a database-level unique constraint or `SELECT FOR UPDATE` to make the check-and-insert atomic. Verify the DB schema has an appropriate constraint or index (the partial unique index on `workshop_brochures` (`workshop_id`) WHERE `is_active = true` only prevents duplicate active brochures, not duplicate PROCESSING jobs — a `SELECT FOR UPDATE` on the job row or an application-level constraint is needed). Flag for review during T1 implementation. |
| **MinIO orphan on transaction rollback** — PDF is uploaded to MinIO before the DB transaction commits; if the transaction rolls back (e.g., the `event_publication` insert fails), the MinIO object is never cleaned up. | Medium | Accepted as per SPEC.md §8 Out of Scope. Document with a code comment in `UploadBrochureUseCaseImpl` noting this known gap and that a periodic orphan-cleanup job would address it. No compensating action required within this feature.                                                                                                                                                                                                                                                                                                                                  |
| **Gemini API token limits / rate limiting** — the AI API may return 429 at peak, especially on the Free Tier.                                                                                                            | Medium | Retry with exponential backoff handles 429 (FR-25). Circuit breaker (`aiProvider`) prevents thundering-herd. In the worst case the job goes to `FAILED` + DLQ after 3 attempts, and the Organizer can re-upload. No additional mitigation needed here.                                                                                                                                                                                                                                                                                                                    |
| **PDFBox text extraction correctness** — some "valid" PDFs (e.g., malformed embedded fonts, encrypted PDFs) may cause PDFBox to throw an exception rather than return empty text.                                        | Medium | In T3, the use case must treat a PDFBox exception as equivalent to "no extractable text" — same fast-fail path as an image-only PDF (FR-20). Add a unit test for this branch in T3.                                                                                                                                                                                                                                                                                                                                                                                       |
| **Redis Pub/Sub publish failure** — if Redis is temporarily unavailable when the worker publishes the SSE event, the job state is already persisted as `COMPLETED`/`FAILED`, but the SSE push is lost.                   | Low    | Per SPEC.md NFR, SSE push is best-effort; the fallback `GET` endpoint is the recovery path. No additional mitigation needed in the worker. Log the failure at WARN level for observability.                                                                                                                                                                                                                                                                                                                                                                               |

## Open Questions

None. All ambiguities were resolved before the spec was approved (see SPEC.md §10 Open Questions / Assumptions). No human input is required before T1 begins.
