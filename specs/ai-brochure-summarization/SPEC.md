# Spec: AI Brochure Summarization

> **Status:** APPROVED  
> **Domain:** AI-Powered Brochure Summarization  
> **Owner/area:** `workshops` module (Core Backend — upload, fallback polling, outbox) + `ai_processing` worker module (Workers — full hexagonal pipeline)  
> **Last revised:** 2026-10-12 — first version

## 1. Overview

The AI Brochure Summarization feature enables Organizers to upload a PDF brochure for a workshop and receive an AI-generated Markdown summary asynchronously. The backend accepts the upload synchronously, validates the file, stores it in object storage, deactivates any previously active brochure, creates a summary job record, and publishes a processing event via the transactional outbox — returning `202 Accepted` immediately. The background `ai_processing` worker then consumes the event from the message broker, downloads the PDF, extracts text using Apache PDFBox, and calls the Google Gemini AI API to produce a structured Markdown summary (covering Objectives, Target Audience, Key Highlights/Agenda, and Prerequisites/Speaker info). Once complete or failed, the worker updates the job record and pushes a real-time notification to the Organizer's active SSE stream. A dedicated fallback polling endpoint is provided for clients that reconnect after a disconnection. This feature replaces any manual process of writing workshop descriptions by hand, enabling richer and more consistent workshop discovery content at scale.

## 2. Actors & Preconditions

| Actor                                          | Precondition                                                                                                                                                                                                                                                                                                 |
| :--------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ORGANIZER** (authenticated, primary trigger) | Must hold a valid JWT with role `ORGANIZER`. The JWT claim `organization_id` (`jwt.org`) must match the `organization_id` of the target workshop. The target workshop must exist and be in `DRAFT` or `PUBLISHED` status. No existing summary job for this workshop may currently be in `PROCESSING` status. |
| **Background Worker** (`ai_processing`)        | A `BrochureSummarizationRequestedEvent` must be present on the RabbitMQ queue `q.ai.summarization`. The referenced `ai_summary_jobs` record must exist with `status = PROCESSING`. The referenced PDF must be retrievable from MinIO bucket `unihub-brochures` at the recorded storage key.                  |
| **ORGANIZER** (fallback polling trigger)       | Must hold a valid JWT with role `ORGANIZER`. The JWT claim `organization_id` must match the `organization_id` of the target workshop. The target workshop must exist and have at least one brochure ever uploaded.                                                                                           |

## 3. Functional Requirements

### 3.1 — Authorization & Tenant Scoping

- **FR-01:** The system SHALL reject the upload request with `401 Unauthorized` when the `Authorization` header is absent or contains an invalid, expired, or revoked JWT.
- **FR-02:** The system SHALL reject the upload request with `403 Forbidden` when the authenticated caller does not hold the `ORGANIZER` role.
- **FR-03:** The system SHALL reject the upload request with `403 Forbidden` when `workshop.organization_id` does not equal `jwt.org` (the `organization_id` claim from the JWT), even when the workshop exists.
- **FR-04:** The system SHALL reject the upload request with `404 Not Found` when no workshop exists for the supplied `{id}`.
- **FR-05:** The system SHALL apply the same `jwt.org` scoping rule to the fallback polling endpoint: reject with `403 Forbidden` when `workshop.organization_id` does not equal `jwt.org`.

### 3.2 — Workshop State Guard

- **FR-06:** The system SHALL reject the upload request with `422 Unprocessable Entity` when the target workshop is in `COMPLETED` or `CANCELED` status.

### 3.3 — Concurrent Job Guard

- **FR-07:** The system SHALL reject the upload request with `409 Conflict` when an `ai_summary_jobs` record for the same workshop already has `status = PROCESSING`, to prevent concurrent duplicate AI inference.

### 3.4 — File Validation

- **FR-08:** The system SHALL reject the upload with `422 Unprocessable Entity` when the uploaded file's declared MIME type is not `application/pdf`.
- **FR-09:** The system SHALL reject the upload with `422 Unprocessable Entity` when the uploaded file does not begin with the magic byte sequence `%PDF-`, regardless of the declared MIME type.
- **FR-10:** The system SHALL reject the upload with `422 Unprocessable Entity` when the uploaded file size exceeds 10 MB.

### 3.5 — Storage & Persistence (Upload Path)

- **FR-11:** The system SHALL compute the SHA-256 checksum of the uploaded PDF and persist it on the `workshop_brochures` record.
- **FR-12:** The system SHALL store the uploaded PDF in the MinIO bucket `unihub-brochures` under the storage key `workshops/{workshopId}/brochures/{brochureId}.pdf`, where `{brochureId}` is a newly generated UUID.
- **FR-13:** The system SHALL set the `is_active` flag to `false` on any previously active `workshop_brochures` record for the same workshop before persisting the new brochure record (historical records are retained; only one may be active at a time).
- **FR-14:** The system SHALL persist a new `workshop_brochures` record with `is_active = true`, capturing the file name, content type (`application/pdf`), byte size, SHA-256 checksum, and storage key.
- **FR-15:** The system SHALL persist a new `ai_summary_jobs` record with `status = PROCESSING`, linked to the new brochure and workshop.
- **FR-16:** The system SHALL write a `BrochureSummarizationRequestedEvent` (containing `jobId`, `workshopId`, `brochureId`, `storageKey`, and `organizerId`) to the transactional outbox, routed to `unihub.events::ai.summarization`, within the same database transaction as FR-14 and FR-15.
- **FR-17:** The system SHALL return `202 Accepted` with a response body containing `jobId`, `brochureId`, `status = "PROCESSING"`, and `createdAt` (ISO-8601 UTC) when the upload and all persistence steps succeed.

### 3.6 — Worker: Message Consumption & PDF Processing

- **FR-18:** The worker SHALL consume the `BrochureSummarizationRequestedEvent` from the RabbitMQ queue `q.ai.summarization` and fetch the PDF from the MinIO bucket `unihub-brochures` using the `storageKey` from the event payload.
- **FR-19:** The worker SHALL extract and clean text from the PDF using the Apache PDFBox library locally (no external OCR service).
- **FR-20:** The worker SHALL immediately transition the `ai_summary_jobs` record to `status = FAILED` with `error_message = "No extractable text found in PDF brochure"`, publish an `ai.summary.failed` real-time event, and acknowledge (ACK) the broker message — without calling the AI provider — when the PDF yields no extractable text (e.g., a scanned image-only PDF). This failure is non-retryable.

### 3.7 — Worker: AI Summarization

- **FR-21:** The worker SHALL invoke the AI provider with a single structured prompt requesting a standardized Markdown summary (sections: Objectives, Target Audience, Key Highlights/Agenda, Prerequisites/Speaker info) when the extracted text length is ≤ 50,000 characters.
- **FR-22:** The worker SHALL split the extracted text into chunks before summarization when the extracted text length exceeds 50,000 characters, then consolidate the chunk results into a single Markdown summary.
- **FR-23:** The worker SHALL use the Google Gemini API (Flash series model, configured via `AI_GEMINI_MODEL` environment variable, e.g., `gemini-3.8-flash` via Google AI Studio Free Tier) as the primary AI provider, protected by a Resilience4j circuit breaker (`aiProvider`).
- **FR-24:** The worker SHALL use a mock AI adapter in local and test environments in place of the real Gemini API.

### 3.8 — Worker: Retry, Failure & Dead-Letter Handling

- **FR-25:** The worker SHALL increment the `attempts` counter and set `next_retry_at` on the `ai_summary_jobs` record, then re-enqueue the message with broker-backed exponential backoff, when a transient error occurs (HTTP 429, HTTP 5xx, or a network/timeout error from the AI provider). This retry applies only when `attempts < 3`.
- **FR-26:** The worker SHALL transition the `ai_summary_jobs` record to `status = FAILED` with a descriptive `error_message`, publish an `ai.summary.failed` real-time event, and reject the message without requeue (dead-letter to DLQ `unihub.dlx::dlq`) when `attempts` reaches 3 (max retry exhaustion).

### 3.9 — Worker: Job Completion

- **FR-27:** The worker SHALL update the `ai_summary_jobs` record with `status = COMPLETED`, `result_text = <summary>`, and `completed_at = now()` upon a successful AI summarization response.

### 3.10 — Real-Time Push (Worker)

- **FR-28:** The worker SHALL publish a `RealtimeEvent` JSON envelope with `eventType = "ai.summary.completed"` and payload `{ jobId, workshopId, resultText }` to the Redis Pub/Sub channel `unihub:stream:user:{organizerId}` upon successful job completion (FR-27).
- **FR-29:** The worker SHALL publish a `RealtimeEvent` JSON envelope with `eventType = "ai.summary.failed"` and payload `{ jobId, workshopId, errorMessage }` to the Redis Pub/Sub channel `unihub:stream:user:{organizerId}` upon any terminal job failure (FR-20 and FR-26).
- **FR-30:** The real-time `RealtimeEvent` envelope SHALL contain: `eventId` (UUIDv4), `eventType` (string), `timestamp` (ISO-8601 UTC), and `payload` (domain object).

### 3.11 — Fallback Polling Endpoint

- **FR-31:** The system SHALL return `404 Not Found` from the fallback polling endpoint when no workshop exists for the supplied `{id}`, or when no brochure has ever been uploaded for that workshop.
- **FR-32:** The system SHALL return `200 OK` from the fallback polling endpoint with the latest summary job for the workshop's currently active brochure, including fields: `jobId`, `brochureId`, `workshopId`, `status`, `resultText` (null if not yet available), `errorMessage` (null if not failed), `attempts`, `completedAt` (null if incomplete), and `createdAt`.

## 4. Business Rules

- **BR-01:** Only users with role `ORGANIZER` may upload brochures or poll summary job status. No other role (`STUDENT`, `STAFF`) has access to these endpoints. _(Source: docs/feature-catalog.md §8.1 Key Constraints; docs/overview.md RBAC; ADR-006)_
- **BR-02:** Tenant isolation is enforced by comparing `workshop.organization_id` with the `organization_id` claim in the JWT. An Organizer from Organization A cannot upload to or query a workshop owned by Organization B. _(Source: docs/feature-catalog.md §8.1 Key Constraints; ADR-006)_
- **BR-03:** A workshop must be in `DRAFT` or `PUBLISHED` status to receive a brochure upload. `COMPLETED` and `CANCELED` workshops are immutable from a content perspective. _(Source: docs/feature-catalog.md §8.1 Key Constraints)_
- **BR-04:** At most one `ai_summary_jobs` record per workshop may be in `PROCESSING` status at any given time. A new upload is rejected with `409 Conflict` if one is already in `PROCESSING`. _(Source: docs/feature-catalog.md §8.1 Key Constraints — prevents race conditions and duplicate AI inference costs)_
- **BR-05:** At most one `workshop_brochures` record per workshop may have `is_active = true` at any given time (enforced by a partial unique index on `(workshop_id) WHERE is_active = true`). A new upload deactivates the previous active brochure before activating the new one. _(Source: docs/database-schema.md `workshop_brochures` table)_
- **BR-06:** A PDF brochure file must be validated by both declared MIME type (`application/pdf`) and magic byte (`%PDF-`) to prevent MIME spoofing. Maximum file size is 10 MB. _(Source: docs/api-standards.md §Content Types; docs/feature-catalog.md §8.1 Key Constraints)_
- **BR-07:** The SHA-256 checksum of the PDF must be computed by the system (not trusted from the client) and stored for integrity verification purposes. _(Source: docs/database-schema.md `workshop_brochures.checksum_sha256`)_
- **BR-08:** Brochure storage key follows the deterministic pattern `workshops/{workshopId}/brochures/{brochureId}.pdf` in the `unihub-brochures` MinIO bucket. _(Source: docs/feature-catalog.md §8.1 Description — Upload & Validation step; ADR-011)_
- **BR-09:** All database writes triggered by the upload (new brochure record, previous brochure deactivation, new job record, outbox event) must occur atomically in a single database transaction. If any step fails, the entire transaction is rolled back and no partial state is persisted. _(Source: ADR-018 Transactional Outbox; docs/system-design.md §4 Async AI Summarization Flow)_
- **BR-10:** A scanned image-only PDF (no extractable text layer) results in an immediate non-retryable `FAILED` terminal outcome. The broker message is acknowledged without retry or DLQ routing to avoid polluting the dead-letter queue with unrecoverable data errors. _(Source: docs/feature-catalog.md §8.1 Description — Worker Processing step; docs/adrs.md ADR-026; resolved ambiguity confirmed in prior session)_
- **BR-11:** Transient errors (HTTP 429, HTTP 5xx, or timeout from the AI provider) are retried up to a maximum of 3 total attempts with broker-backed exponential backoff. Upon exhausting 3 attempts, the job is set to `FAILED` and the message is dead-lettered without requeue. _(Source: docs/feature-catalog.md §8.1 Description; resolved ambiguity confirmed in prior session)_
- **BR-12:** Text ≤ 50,000 characters is processed in a single AI prompt. Text exceeding 50,000 characters is chunked before summarization. _(Source: docs/feature-catalog.md §8.1 Description; docs/adrs.md ADR-026 Text Preprocessing & Summarization Strategy; resolved ambiguity confirmed in prior session)_
- **BR-13:** Job state transitions are driven exclusively by the background worker — never by web client input. The client may only observe the current state. _(Source: docs/feature-catalog.md §8.1 Key Constraints)_
- **BR-14:** Real-time events are published to the Redis Pub/Sub channel `unihub:stream:user:{organizerId}` as a full `RealtimeEvent` JSON envelope (not a bare payload), matching the format consumed by the existing `RedisRealtimeSubscriber`. _(Source: docs/api-standards.md §Unified Event Catalog; docs/system-design.md §6 Real-Time Event Distribution Architecture; ADR-031; confirmed in prior session)_
- **BR-15:** The fallback polling endpoint returns `404` when the workshop does not exist OR when no brochure has ever been uploaded for that workshop — it does not distinguish between these two cases in the HTTP response. _(Source: docs/feature-catalog.md §8.1 Description — Real-Time Push & Polling Fallback step; resolved ambiguity confirmed in prior session)_

## 5. State Transitions

The `ai_summary_jobs.status` field is a lifecycle field for this feature.

| From State   | Event / Trigger                                                   | To State             | Valid? | Rejection Behavior (if invalid)                                                                                                                                                           |
| :----------- | :---------------------------------------------------------------- | :------------------- | :----: | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| _(none)_     | New brochure upload accepted (FR-15 — backend creates the record) | `PROCESSING`         |   ✅   | —                                                                                                                                                                                         |
| `PROCESSING` | Worker: AI summarization succeeds (FR-27)                         | `COMPLETED`          |   ✅   | —                                                                                                                                                                                         |
| `PROCESSING` | Worker: PDF has no extractable text (FR-20)                       | `FAILED`             |   ✅   | —                                                                                                                                                                                         |
| `PROCESSING` | Worker: Transient AI error, `attempts < 3` (FR-25)                | `PROCESSING` (retry) |   ✅   | —                                                                                                                                                                                         |
| `PROCESSING` | Worker: `attempts` reaches 3 (FR-26)                              | `FAILED`             |   ✅   | —                                                                                                                                                                                         |
| `COMPLETED`  | Any transition attempt                                            | _(unchanged)_        |   ❌   | Terminal state; no further transitions are possible. Worker must not re-process a completed job.                                                                                          |
| `FAILED`     | Any transition attempt                                            | _(unchanged)_        |   ❌   | Terminal state; no further transitions are possible. A new brochure upload (if the workshop status allows) creates a new `ai_summary_jobs` record — it does not reopen the failed record. |
| `PROCESSING` | Web client attempts to force-transition                           | _(rejected)_         |   ❌   | Client has no endpoint to change job state; only background worker drives state transitions (BR-13).                                                                                      |

**Note on the `409 Conflict` guard:** A new upload request (which would create a new `PROCESSING` record) is blocked by FR-07 when an existing `PROCESSING` job already exists for the same workshop. A new job can only be created when no `PROCESSING` job exists (i.e., when no prior job exists at all, or the most recent job is `COMPLETED` or `FAILED`).

## 6. Non-Functional Requirements

- **Performance:** The upload endpoint must respond within 3 seconds under normal conditions (this is the backend acceptance step only — not the full AI processing time). MinIO upload and all synchronous DB writes must complete within this window. The AI summarization pipeline is fully async and its latency is governed by the worker, not the upload endpoint response time.
- **Concurrency:** The partial unique index on `(workshop_id) WHERE is_active = true` (on `workshop_brochures`) provides database-level enforcement of the single-active-brochure constraint. The `409 Conflict` guard for duplicate `PROCESSING` jobs (FR-07) must be implemented such that concurrent upload requests for the same workshop arriving within milliseconds of each other result in exactly one accepted upload and one rejected with `409`. This requires an atomic check-and-create at the database level (leveraging the DB constraint and the transactional boundary in BR-09), not a read-then-write race.
- **Idempotency:** The upload endpoint is not idempotent by design — each call creates a new brochure record and a new job (subject to the `PROCESSING` guard). The `Idempotency-Key` header is therefore **not required** for this endpoint. The worker's message-consumption pipeline must be idempotent: if the same event is re-delivered by RabbitMQ (due to network issues before ACK), the worker must detect the existing job state and not re-process a `COMPLETED` or `FAILED` job.
- **Rate Limiting:** The upload endpoint is protected by the standard Bucket4j token-bucket rate limiter. Specific rate-limit parameters (token refill rate, bucket capacity) are configured at the infrastructure level; the endpoint must return `429 Too Many Requests` with the `Retry-After` header when the limit is exceeded.
- **Security:**
  - Authorization is enforced per FR-01 through FR-05 (JWT validation, RBAC role check, tenant isolation).
  - File upload must validate MIME type and magic bytes server-side (not trusting client-declared content type) per BR-06.
  - The AI provider API key must be stored as a secret (environment variable `AI_GEMINI_API_KEY`), never logged or returned in any response or error body.
  - Internal MinIO storage keys must not be returned in any client-facing response payload (only the `storageKey` is internal; clients receive `brochureId` only).
  - Error responses must never reveal internal infrastructure details (stack traces, internal hostnames, S3 paths).
- **Reliability / Failure modes:**
  - If MinIO is unavailable during the upload, the backend must propagate a `503 Service Unavailable` (circuit breaker open or connection failure) and not persist any partial records.
  - If the outbox event fails to write within the transaction, the entire transaction rolls back (BR-09) — the PDF already uploaded to MinIO will become an orphan. The outbox relay will handle subsequent delivery; no compensating delete of the MinIO object is required by the upload endpoint itself.
  - If the AI provider is unavailable, the worker retries up to 3 times with exponential backoff before moving the job to `FAILED` and dead-lettering the message.
  - If the Redis Pub/Sub publish fails (worker side), the job state update still persists — the SSE push is best-effort, not blocking. The fallback polling endpoint serves as the recovery mechanism for missed push events.
- **Observability:**
  - The upload endpoint must log `traceId`, `correlationId` (from `X-Correlation-ID`), `workshopId`, `brochureId`, `jobId`, and file size for every accepted upload.
  - The worker must log `jobId`, `workshopId`, `attempts`, and outcome (`COMPLETED` / `FAILED` / `RETRY`) for each processing cycle.
  - Micrometer metrics must count: `ai.summarization.jobs.started`, `ai.summarization.jobs.completed`, `ai.summarization.jobs.failed`, and `ai.summarization.jobs.retried` for Prometheus/Grafana.

## 7. Acceptance Criteria

### Scenario 1: Successful Upload and AI Summarization (Happy Path)

```gherkin
Given an authenticated ORGANIZER whose JWT organization_id matches the workshop's organization_id
And the target workshop is in DRAFT or PUBLISHED status
And no PROCESSING summary job exists for the target workshop
When the ORGANIZER uploads a valid PDF (≤ 10 MB, MIME application/pdf, begins with %PDF-) to the brochure upload endpoint
Then the system stores the PDF in MinIO under workshops/{workshopId}/brochures/{brochureId}.pdf
And the system persists a new workshop_brochures record with is_active = true
And any previously active brochure for the workshop has is_active = false
And the system persists a new ai_summary_jobs record with status = PROCESSING
And the system writes a BrochureSummarizationRequestedEvent to the transactional outbox
And the system returns 202 Accepted with { jobId, brochureId, status: "PROCESSING", createdAt }
And eventually the worker processes the event and calls the AI provider
And the worker updates ai_summary_jobs with status = COMPLETED, result_text, completed_at
And the worker publishes an ai.summary.completed RealtimeEvent to Redis Pub/Sub channel unihub:stream:user:{organizerId}
```

_(Satisfies: FR-01..FR-04, FR-06..FR-17, FR-19, FR-21 or FR-22, FR-23, FR-27, FR-28, FR-30)_

### Scenario 2: Upload Rejected — Unauthenticated Request

```gherkin
Given a request with no Authorization header or with an expired/invalid JWT
When the request is submitted to the brochure upload endpoint
Then the system returns 401 Unauthorized
```

_(Satisfies: FR-01)_

### Scenario 3: Upload Rejected — Wrong Role

```gherkin
Given an authenticated user with role STUDENT or STAFF
When the user submits a brochure upload request
Then the system returns 403 Forbidden
```

_(Satisfies: FR-02)_

### Scenario 4: Upload Rejected — Cross-Tenant Access

```gherkin
Given an authenticated ORGANIZER with jwt.org = "org-A"
And a workshop exists with organization_id = "org-B"
When the ORGANIZER submits a brochure upload for that workshop
Then the system returns 403 Forbidden
```

_(Satisfies: FR-03)_

### Scenario 5: Upload Rejected — Workshop Not Found

```gherkin
Given an authenticated ORGANIZER with a valid jwt.org
When the ORGANIZER submits a brochure upload with a non-existent workshop ID
Then the system returns 404 Not Found
```

_(Satisfies: FR-04)_

### Scenario 6: Upload Rejected — Workshop in Terminal Status

```gherkin
Given an authenticated ORGANIZER whose JWT matches the workshop's organization_id
And the target workshop is in COMPLETED or CANCELED status
When the ORGANIZER submits a brochure upload
Then the system returns 422 Unprocessable Entity
```

_(Satisfies: FR-06)_

### Scenario 7: Upload Rejected — Duplicate PROCESSING Job

```gherkin
Given an authenticated ORGANIZER whose JWT matches the workshop's organization_id
And the target workshop is in DRAFT or PUBLISHED status
And an ai_summary_jobs record with status = PROCESSING already exists for this workshop
When the ORGANIZER submits a brochure upload
Then the system returns 409 Conflict
And no new brochure record or job record is created
```

_(Satisfies: FR-07)_

### Scenario 8: Upload Rejected — Invalid MIME Type

```gherkin
Given an authenticated ORGANIZER with a valid matching jwt.org
And the workshop is in DRAFT or PUBLISHED status with no PROCESSING job
When the ORGANIZER uploads a file with a declared MIME type other than application/pdf
Then the system returns 422 Unprocessable Entity
And no brochure record is stored in MinIO or the database
```

_(Satisfies: FR-08)_

### Scenario 9: Upload Rejected — Magic Byte Mismatch (MIME Spoofing)

```gherkin
Given an authenticated ORGANIZER with a valid matching jwt.org
And the workshop is in DRAFT or PUBLISHED status with no PROCESSING job
When the ORGANIZER uploads a file declared as application/pdf but not starting with %PDF-
Then the system returns 422 Unprocessable Entity
```

_(Satisfies: FR-09)_

### Scenario 10: Upload Rejected — File Too Large

```gherkin
Given an authenticated ORGANIZER with a valid matching jwt.org
And the workshop is in DRAFT or PUBLISHED status with no PROCESSING job
When the ORGANIZER uploads a PDF file exceeding 10 MB
Then the system returns 422 Unprocessable Entity
```

_(Satisfies: FR-10)_

### Scenario 11: Worker — Scanned Image-Only PDF (Non-Retryable Failure)

```gherkin
Given a BrochureSummarizationRequestedEvent is consumed by the worker
And the fetched PDF contains no extractable text layer (scanned image-only)
When the worker runs Apache PDFBox text extraction
Then the worker sets ai_summary_jobs.status = FAILED
And ai_summary_jobs.error_message = "No extractable text found in PDF brochure"
And the worker does NOT call the AI provider
And the worker publishes an ai.summary.failed RealtimeEvent to Redis Pub/Sub
And the worker ACKs the broker message (no retry, no DLQ routing)
```

_(Satisfies: FR-19, FR-20, FR-29, FR-30)_

### Scenario 12: Worker — Transient AI Error, Retry Within Limit

```gherkin
Given a BrochureSummarizationRequestedEvent is consumed by the worker
And the PDF has extractable text
And the AI provider returns HTTP 429 or 5xx or a timeout on the first or second attempt
When the worker handles the error with attempts < 3
Then the worker increments ai_summary_jobs.attempts
And the worker sets ai_summary_jobs.next_retry_at to a future time (exponential backoff)
And the message is re-enqueued via the broker for a subsequent attempt
And ai_summary_jobs.status remains PROCESSING
```

_(Satisfies: FR-25)_

### Scenario 13: Worker — Max Retry Exhaustion (Dead-Letter)

```gherkin
Given a BrochureSummarizationRequestedEvent has been attempted 3 times
And all 3 attempts resulted in transient AI provider errors
When the worker processes the third failed attempt
Then the worker sets ai_summary_jobs.status = FAILED
And ai_summary_jobs.attempts = 3
And ai_summary_jobs.error_message records the last error
And the worker publishes an ai.summary.failed RealtimeEvent to Redis Pub/Sub
And the message is rejected without requeue and dead-lettered to unihub.dlx::dlq
```

_(Satisfies: FR-26, FR-29, FR-30)_

### Scenario 14: Fallback Polling — Job Data Returned

```gherkin
Given an authenticated ORGANIZER whose jwt.org matches the workshop's organization_id
And the workshop has at least one brochure ever uploaded
When the ORGANIZER calls the fallback polling endpoint
Then the system returns 200 OK with { jobId, brochureId, workshopId, status, resultText, errorMessage, attempts, completedAt, createdAt }
And null fields are omitted from the response per NON_NULL serialization rules
```

_(Satisfies: FR-05, FR-32)_

### Scenario 15: Fallback Polling — Workshop Not Found or No Brochure

```gherkin
Given an authenticated ORGANIZER with a valid jwt.org
When the ORGANIZER calls the fallback polling endpoint for a workshop that does not exist, or for a workshop that has never had a brochure uploaded
Then the system returns 404 Not Found
```

_(Satisfies: FR-31)_

### Scenario 16: Worker Idempotency — Already Completed Job

```gherkin
Given a BrochureSummarizationRequestedEvent is re-delivered by RabbitMQ (duplicate delivery)
And the referenced ai_summary_jobs record already has status = COMPLETED
When the worker receives the re-delivered message
Then the worker detects the terminal state and does not re-call the AI provider
And the worker does not overwrite the existing COMPLETED result
And the worker ACKs the message (discards the duplicate without error)
```

_(Satisfies: FR-18; derived from NFR — Idempotency)_

## 8. Out of Scope

- **Manual summary editing:** This feature only generates the AI summary and delivers it via SSE/polling. There is no endpoint for the Organizer to edit, approve, or apply the AI summary to the workshop's description field. That is a separate feature (if desired).
- **OCR processing:** Scanned image-only PDFs are fast-failed with a `FAILED` status. There is no OCR pipeline to extract text from image-based PDFs.
- **Multiple file formats:** Only PDF (`.pdf`) is supported. Word documents, PowerPoint files, and other formats are out of scope.
- **Organizer-initiated retry:** There is no API endpoint for an Organizer to manually retry a `FAILED` summary job. To retry, the Organizer must upload a new (or the same) PDF, which creates a new job (subject to the status guards in BR-03 and BR-04).
- **Summary versioning / history:** Only the latest `ai_summary_jobs` record per active brochure is surfaced by the fallback polling endpoint. Historical job records for previously deactivated brochures are retained in the database but are not exposed by any API.
- **Automatic periodic re-summarization:** The system does not automatically re-run summarization when a workshop's details change. Summarization is only triggered by a new brochure upload.
- **Email / in-app notification of summary completion:** The real-time delivery mechanism is SSE push only (with polling fallback). No email notification or in-app notification record is created when summarization completes or fails. (The `notifications` module is not involved in this feature.)
- **Mobile client support:** Per the feature catalog, the client scope for this feature is Web only. The mobile app does not have access to the brochure upload or summary job endpoints.
- **PDF content moderation:** No content moderation or malware scanning of the uploaded PDF is performed within this feature.
- **Custom AI prompt configuration:** The prompt template is fixed in the implementation. There is no API for Organizers to customize the AI prompt or summary structure.
- **Storage cleanup on upload failure:** If a PDF is uploaded to MinIO but the database transaction subsequently fails, the orphaned MinIO object is not automatically cleaned up within this feature. Object lifecycle policies or a separate cleanup job are out of scope here.

## 9. Linked References

| Document                    | Section                                                                  | Relevance                                                                                                             |
| :-------------------------- | :----------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | §8 — AI-Powered Brochure Summarization; §8.1 `ai-brochure-summarization` | Feature scope, module ownership, constraints, status                                                                  |
| `docs/system-design.md`     | §4 — Async AI Summarization Flow (complete sequence diagram)             | End-to-end flow: upload → outbox → worker → Redis push                                                                |
| `docs/system-design.md`     | §6 — Real-Time Event Distribution Architecture                           | Redis Pub/Sub channel pattern, SSE delivery                                                                           |
| `docs/database-schema.md`   | `workshop_brochures` table                                               | Columns, constraints, partial unique index `(workshop_id) WHERE is_active = true`                                     |
| `docs/database-schema.md`   | `ai_summary_jobs` table                                                  | Columns, state machine fields (`status`, `attempts`, `next_retry_at`, `result_text`, `error_message`, `completed_at`) |
| `docs/api-standards.md`     | §Content Types                                                           | Multipart upload rules, PDF MIME/magic byte validation, max 10 MB                                                     |
| `docs/api-standards.md`     | §HTTP Status Codes                                                       | 202, 409, 422, 404, 401, 403, 429 semantics                                                                           |
| `docs/api-standards.md`     | §Standard JSON Payloads — Success and Error envelopes                    | Response shape, RFC 7807 error format                                                                                 |
| `docs/api-standards.md`     | §Unified Event Catalog                                                   | `ai.summary.completed` and `ai.summary.failed` event names and payload contracts                                      |
| `docs/adrs.md`              | ADR-006                                                                  | RBAC — role and tenant isolation enforcement                                                                          |
| `docs/adrs.md`              | ADR-011                                                                  | MinIO object storage — `unihub-brochures` bucket                                                                      |
| `docs/adrs.md`              | ADR-017                                                                  | RabbitMQ — queue `q.ai.summarization`, exchange `unihub.events`                                                       |
| `docs/adrs.md`              | ADR-018                                                                  | Transactional Outbox pattern — atomic DB + event publication                                                          |
| `docs/adrs.md`              | ADR-020                                                                  | Resilience4j circuit breaker (`aiProvider`)                                                                           |
| `docs/adrs.md`              | ADR-026                                                                  | Async worker pipeline + PDFBox + Google Gemini Free Tier + Text Preprocessing & Summarization Strategy                |
| `docs/adrs.md`              | ADR-031                                                                  | Real-time SSE push + Redis Pub/Sub                                                                                    |
| `docs/project-structure.md` | Backend Clean Architecture layout; Worker Hexagonal Architecture layout  | Package and layer placement rules                                                                                     |
