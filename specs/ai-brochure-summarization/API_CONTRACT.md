# API Contract: AI Brochure Summarization

> **Status:** APPROVED  
> **Spec:** `specs/ai-brochure-summarization/SPEC.md`  
> **Last revised:** 2026-10-12

## 1. Overview

This contract defines the two REST endpoints introduced by the `ai-brochure-summarization` feature, both served by the Core Backend (`apps/backend/`) within the `workshops` module:

1. **`POST /api/v1/admin/workshops/{id}/brochure`** — Multipart PDF upload. Accepts the brochure file, validates it, stores it in MinIO, creates the summary job record, and publishes the processing event via the transactional outbox. Returns `202 Accepted` immediately.
2. **`GET /api/v1/admin/workshops/{id}/brochure/summary-job`** — Fallback polling endpoint for reconnecting SSE clients. Returns the latest summary job for the workshop's active brochure.

Both endpoints are scoped to authenticated `ORGANIZER` users and are subject to multi-tenant organization isolation. The asynchronous worker pipeline (RabbitMQ consumer, AI provider, Redis Pub/Sub publisher) is not a network-facing interface and is therefore not covered by this contract.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/...` URI path versioning, per `docs/api-standards.md §Versioning Strategy`. Endpoints reside under `/api/v1/admin/workshops/` (admin namespace for organizer-facing operations).
- **Pagination convention:** Not applicable — neither endpoint returns a collection.
- **Success response shape:** Standard `ApiResponse<T>` envelope: `{ success: true, message: string, data: object, metadata: { processedBy: string, traceId: string } }` with `@JsonInclude(NON_NULL)` — null fields are omitted. Per `docs/api-standards.md §Success Response Envelope`.
- **Error response format:** RFC 7807 Problem Details wrapped in the standard envelope: `{ success: false, error: { type: "urn:unihub:error:*", title, status, detail, instance, validationErrors? }, metadata: { traceId } }`. Content-Type `application/problem+json`. Per `docs/api-standards.md §Error Response`.
- **Idempotency-key convention:** Not required for either endpoint (see §5 for rationale).
- **Auth header convention:** `Authorization: Bearer <JWT>` (RS256, asymmetric). Mandatory on both endpoints.
- **Date/time formatting convention:** ISO 8601 in UTC with trailing `Z` (e.g., `2026-10-12T00:00:00Z`). Per `docs/api-standards.md §Date/Time & Time Zone`.
- **Mandatory request headers:** `Authorization`, `X-Correlation-ID`, `Accept-Language` on all endpoints.
- **Upload content type:** `multipart/form-data` for the upload endpoint. Per `docs/api-standards.md §Content Types`.
- **Rate limiting headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After` returned by Bucket4j-protected endpoints.

## 3. Endpoints

### POST /api/v1/admin/workshops/{id}/brochure

**Description:** Uploads a PDF brochure for a workshop and enqueues an AI summarization job. Responds `202 Accepted` immediately; processing is asynchronous.  
**Required Role / Auth:** `ORGANIZER` — authenticated via Bearer JWT with role `ORGANIZER` and `organization_id` claim matching the target workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — each call with a valid payload intentionally creates a new brochure record and a new summary job (subject to the `PROCESSING` guard). There is no idempotency key on this endpoint. Callers must not rely on retry-safe semantics; the `409 Conflict` guard prevents duplicate concurrent jobs.

#### Request

**Headers:**

| Header             | Required    | Description                                                               |
| :----------------- | :---------- | :------------------------------------------------------------------------ |
| `Authorization`    | ✅ Required | `Bearer <JWT>` — RS256-signed access token                                |
| `X-Correlation-ID` | ✅ Required | Client-generated UUID for cross-boundary tracing                          |
| `Accept-Language`  | ✅ Required | Locale for RFC 7807 error messages (e.g., `en`, `vi`)                     |
| `Content-Type`     | ✅ Required | Must be `multipart/form-data` (boundary set by HTTP client automatically) |

**Path Parameters:**

| Parameter | Type          | Description                                  |
| :-------- | :------------ | :------------------------------------------- |
| `id`      | UUID (string) | The unique identifier of the target workshop |

**Query Parameters:** None.

**Request Body:**

Multipart form with a single required file part named `file`.

```
Content-Type: multipart/form-data

file=<binary PDF content>
```

**Validation Rules:**

- `file` — **Required**. Must be present; a missing or empty `file` part returns `422`.
- `file` content type — **Must be `application/pdf`**. Any other declared MIME type returns `422`.
- `file` magic bytes — **Must begin with `%PDF-`**. A file that declares `application/pdf` but does not start with `%PDF-` returns `422` (MIME spoofing prevention).
- `file` size — **Maximum 10 MB** (10,485,760 bytes). Files exceeding this size return `422`.
- `{id}` — **Must be a valid UUID**. An unparseable non-UUID path segment returns `400`. A valid UUID that does not correspond to an existing workshop returns `404`.

#### Response

**Success — 202 Accepted:**

The upload was accepted, the brochure record and summary job have been persisted, and the processing event has been enqueued. The AI summarization job is now running asynchronously.

```json
{
  "success": true,
  "message": "Brochure upload accepted. AI summarization is processing.",
  "data": {
    "jobId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "brochureId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "status": "PROCESSING",
    "createdAt": "2026-10-12T00:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

| Field             | Type                  | Description                                                                                                          |
| :---------------- | :-------------------- | :------------------------------------------------------------------------------------------------------------------- |
| `data.jobId`      | UUID string           | The newly created `ai_summary_jobs` record ID. Use this to correlate SSE push events and fallback polling responses. |
| `data.brochureId` | UUID string           | The newly created `workshop_brochures` record ID.                                                                    |
| `data.status`     | string enum           | Always `"PROCESSING"` at this stage.                                                                                 |
| `data.createdAt`  | string (ISO-8601 UTC) | Timestamp when the job record was created.                                                                           |

**Error Responses:**

| Status                       | Error Type (`type` field)                 | Condition                                                                                                                                                                                                                                                                                                                                                    |
| :--------------------------- | :---------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`            | `urn:unihub:error:bad-request`            | The `{id}` path parameter is not a valid UUID format, or the request is structurally malformed (e.g., malformed multipart boundary).                                                                                                                                                                                                                         |
| `401 Unauthorized`           | `urn:unihub:error:unauthorized`           | Missing, invalid, expired, or revoked JWT in the `Authorization` header.                                                                                                                                                                                                                                                                                     |
| `403 Forbidden`              | `urn:unihub:error:forbidden`              | The authenticated user does not hold the `ORGANIZER` role, or `workshop.organization_id` does not equal `jwt.org`.                                                                                                                                                                                                                                           |
| `404 Not Found`              | `urn:unihub:error:not-found`              | No workshop exists with the given `{id}`.                                                                                                                                                                                                                                                                                                                    |
| `409 Conflict`               | `urn:unihub:error:conflict`               | An `ai_summary_jobs` record with `status = PROCESSING` already exists for this workshop. Wait for the current job to complete before uploading a new brochure.                                                                                                                                                                                               |
| `413 Content Too Large`      | `urn:unihub:error:bad-request`            | The uploaded file exceeds 10 MB. May be raised by the multipart resolver before business logic runs.                                                                                                                                                                                                                                                         |
| `415 Unsupported Media Type` | `urn:unihub:error:unsupported-media-type` | The request `Content-Type` is not `multipart/form-data`.                                                                                                                                                                                                                                                                                                     |
| `422 Unprocessable Entity`   | `urn:unihub:error:validation`             | One or more validation rules failed: (a) declared MIME type is not `application/pdf`; (b) file does not begin with `%PDF-` magic bytes; (c) file size exceeds 10 MB (caught at business logic layer); (d) target workshop is in `COMPLETED` or `CANCELED` status; (e) `file` part is missing or empty. The `validationErrors` array lists specific failures. |
| `429 Too Many Requests`      | `urn:unihub:error:rate-limited`           | Bucket4j token-bucket rate limit exceeded. `X-RateLimit-Retry-After` header indicates retry wait in seconds.                                                                                                                                                                                                                                                 |
| `500 Internal Server Error`  | `urn:unihub:error:internal`               | Unexpected backend error (internal stack trace suppressed).                                                                                                                                                                                                                                                                                                  |
| `503 Service Unavailable`    | `urn:unihub:error:service-unavailable`    | MinIO is unreachable, the database is unavailable, or the circuit breaker for a downstream dependency is OPEN.                                                                                                                                                                                                                                               |

> **Note on 413 vs 422:** The Spring multipart resolver may raise a `413 Content Too Large` (mapped to `400`/`413` depending on configuration) before business logic runs when the raw multipart payload exceeds the configured limit. The business-logic layer performs an independent 10 MB size check and raises `422` if the resolver permits the request through. Clients should treat both as "file too large."

#### Side Effects

On a successful `202 Accepted` response, the following side effects occur atomically within a single database transaction:

1. A new `workshop_brochures` record is created with `is_active = true`.
2. Any previously active `workshop_brochures` record for the same workshop has `is_active` set to `false`.
3. A new `ai_summary_jobs` record is created with `status = PROCESSING`.
4. A `BrochureSummarizationRequestedEvent` is written to the transactional outbox (`event_publication` table), routed to `unihub.events::ai.summarization`.
5. The PDF binary is stored in MinIO bucket `unihub-brochures` at key `workshops/{workshopId}/brochures/{brochureId}.pdf`.

> **Note:** Step 5 (MinIO write) occurs before the database transaction commits. If the database transaction fails after MinIO write, the MinIO object becomes an orphan (not cleaned up by this endpoint). The outbox relay guarantees that once committed, the event is eventually delivered to the worker.

**Asynchronous downstream side effects** (not part of the HTTP response SLA):

- The worker processes the event and calls the Google Gemini API.
- On completion/failure, `ai_summary_jobs` is updated and a `RealtimeEvent` is published to Redis Pub/Sub channel `unihub:stream:user:{organizerId}`, which is forwarded to the Organizer's active SSE stream.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17
- **Scenarios:** Scenario 1, 2, 3, 4, 5, 6, 7, 8, 9, 10

### GET /api/v1/admin/workshops/{id}/brochure/summary-job

**Description:** Returns the latest AI summarization job for the workshop's currently active brochure. Intended as a fallback for clients that reconnect to SSE after a disconnection and need to retrieve the current job state synchronously.  
**Required Role / Auth:** `ORGANIZER` — authenticated via Bearer JWT with role `ORGANIZER` and `organization_id` claim matching the target workshop's `organization_id`.  
**Idempotency Requirement:** Not Required — this is a read-only `GET` endpoint; naturally safe to repeat.

#### Request

**Headers:**

| Header             | Required    | Description                                      |
| :----------------- | :---------- | :----------------------------------------------- |
| `Authorization`    | ✅ Required | `Bearer <JWT>` — RS256-signed access token       |
| `X-Correlation-ID` | ✅ Required | Client-generated UUID for cross-boundary tracing |
| `Accept-Language`  | ✅ Required | Locale for RFC 7807 error messages               |

**Path Parameters:**

| Parameter | Type          | Description                                  |
| :-------- | :------------ | :------------------------------------------- |
| `id`      | UUID (string) | The unique identifier of the target workshop |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Summary job retrieved.",
  "data": {
    "jobId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "brochureId": "8e4b9b3b-9c1a-4a8d-9f6b-3c8b7f9f2c10",
    "workshopId": "b2a3f0b8-22f9-4b6a-9a54-1a8d8d1f20f4",
    "status": "COMPLETED",
    "resultText": "## Workshop Summary\n\n**Objectives:** ...\n\n**Target Audience:** ...",
    "attempts": 1,
    "completedAt": "2026-10-12T00:05:00Z",
    "createdAt": "2026-10-12T00:00:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-2",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

| Field               | Type                  | Nullable | Description                                                                                                                                          |
| :------------------ | :-------------------- | :------: | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data.jobId`        | UUID string           |    No    | `ai_summary_jobs` record ID                                                                                                                          |
| `data.brochureId`   | UUID string           |    No    | `workshop_brochures` record ID for the active brochure                                                                                               |
| `data.workshopId`   | UUID string           |    No    | The target workshop ID                                                                                                                               |
| `data.status`       | string enum           |    No    | Current job status: `PROCESSING`, `COMPLETED`, or `FAILED`                                                                                           |
| `data.resultText`   | string                |   Yes    | The AI-generated Markdown summary. Present only when `status = COMPLETED`. Omitted (null → field absent per NON_NULL) when `PROCESSING` or `FAILED`. |
| `data.errorMessage` | string                |   Yes    | The error description. Present only when `status = FAILED`. Omitted when `PROCESSING` or `COMPLETED`.                                                |
| `data.attempts`     | integer               |    No    | Number of processing attempts made (1–3).                                                                                                            |
| `data.completedAt`  | string (ISO-8601 UTC) |   Yes    | Timestamp when the job reached a terminal state (`COMPLETED` or `FAILED`). Omitted when `status = PROCESSING`.                                       |
| `data.createdAt`    | string (ISO-8601 UTC) |    No    | Timestamp when the job record was created.                                                                                                           |

**Error Responses:**

| Status                      | Error Type (`type` field)              | Condition                                                                                                                                                                |
| :-------------------------- | :------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | The `{id}` path parameter is not a valid UUID format.                                                                                                                    |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Missing, invalid, expired, or revoked JWT in the `Authorization` header.                                                                                                 |
| `403 Forbidden`             | `urn:unihub:error:forbidden`           | The authenticated user does not hold the `ORGANIZER` role, or `workshop.organization_id` does not equal `jwt.org`.                                                       |
| `404 Not Found`             | `urn:unihub:error:not-found`           | No workshop exists with the given `{id}`, OR no brochure has ever been uploaded for this workshop. The response does not distinguish between these two `404` conditions. |
| `429 Too Many Requests`     | `urn:unihub:error:rate-limited`        | Bucket4j rate limit exceeded.                                                                                                                                            |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Unexpected backend error.                                                                                                                                                |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Database or downstream dependency unavailable.                                                                                                                           |

#### Side Effects

None. This is a read-only endpoint.

> **Read Routing Note:** Per `docs/database-schema.md §Replication Lag & Read-After-Write Consistency`, read-only queries may be routed to the PostgreSQL read replica. However, because this endpoint is frequently called immediately after an upload (to poll the newly-created job), the implementation SHOULD route this query to the primary database to avoid replication-lag-induced `404` on a job just created. If replica routing is used, a brief lag window is acceptable given the SSE push is the primary delivery mechanism and this endpoint is explicitly a fallback.

#### Spec Traceability

- **Satisfies:** FR-05, FR-31, FR-32
- **Scenarios:** Scenario 14, 15

## 4. Rate Limiting Behavior

Both endpoints are protected by the standard Bucket4j token-bucket rate limiter configured at the application layer (not at the Nginx gateway). When the limit is exceeded, both endpoints return `429 Too Many Requests` with the following response headers:

| Header                    | Description                                     |
| :------------------------ | :---------------------------------------------- |
| `X-RateLimit-Limit`       | Total token capacity of the bucket              |
| `X-RateLimit-Remaining`   | Remaining tokens in the current window          |
| `X-RateLimit-Retry-After` | Seconds to wait before the next allowed request |

Specific bucket capacities and refill rates are infrastructure configuration (not part of this contract). The `429` response body follows the standard RFC 7807 envelope with `type = urn:unihub:error:rate-limited`.

## 5. Idempotency Behavior

Neither endpoint requires an `Idempotency-Key` header:

- **`POST /api/v1/admin/workshops/{id}/brochure`:** Each call with a valid payload intentionally creates a distinct brochure record and a distinct summary job. Re-submitting the same PDF is a deliberate Organizer action (e.g., replacing a previous brochure). The `409 Conflict` guard (FR-07) prevents accidental concurrent duplicate jobs by checking for an existing `PROCESSING` job — but this is a business-rule guard, not an idempotency key mechanism. The endpoint is therefore intentionally non-idempotent by design.
- **`GET /api/v1/admin/workshops/{id}/brochure/summary-job`:** Read-only; naturally idempotent without any key mechanism.

## 6. Cross-Endpoint Consistency Notes

- Both endpoints share the same path prefix `/api/v1/admin/workshops/{id}/`, using the same `{id}` UUID path parameter for the workshop identifier. The same `404` semantics apply to a non-existent workshop in both endpoints.
- Both endpoints enforce the same tenant isolation rule (`jwt.org == workshop.organization_id`) and return `403 Forbidden` on mismatch — consistent with the same pattern used throughout the `workshops` module.
- The `jobId` returned by the upload endpoint (`POST`) is the same UUID that appears in the fallback polling endpoint (`GET`) response and in the SSE push payloads (`ai.summary.completed` / `ai.summary.failed`). Clients use `jobId` to correlate across all three delivery mechanisms.
- The `status` enum values (`PROCESSING`, `COMPLETED`, `FAILED`) are consistent between the `POST` response (always `PROCESSING`) and the `GET` response (any of the three). Clients should treat this as a single shared status vocabulary.
- `null` fields are omitted from all success responses per `@JsonInclude(NON_NULL)` — `resultText`, `errorMessage`, and `completedAt` are absent from the payload when null, not present with a `null` literal value.
