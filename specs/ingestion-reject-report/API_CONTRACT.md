# API Contract: Ingestion Reject Report

> **Status:** APPROVED  
> **Spec:** `specs/ingestion-reject-report/SPEC.md`  
> **Last revised:** 2026-08-31

## 1. Overview

This contract defines the two REST endpoints exposed by the `workshops` module in the Core Backend for the `ingestion-reject-report` feature (Feature 9.2). The first endpoint returns a paginated list of nightly CSV ingestion job reports scoped to the authenticated organizer's organization. The second endpoint generates and returns a short-lived (configurable TTL, default 15 minutes) pre-signed MinIO/S3 download URL for the rejection artifact attached to a specific job report. Both endpoints are restricted to `ORGANIZER` role.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/` prefix per `docs/api-standards.md §Versioning Strategy`.
- **Pagination convention:** Cursor-based (keyset) pagination with `?cursor=` and `?size=` parameters. Default size: 20. Hard cap: 100. Response shape uses `data.cursor.{ nextCursor, hasMore }` per `docs/api-standards.md §Pagination`.
- **Success response shape:** Standard success envelope `{ success, message, data, metadata }` per `docs/api-standards.md §Standard JSON Payloads`. Null fields omitted (`@JsonInclude(NON_NULL)`).
- **Error response format:** RFC 7807 Problem Details wrapped in the standard error envelope (`{ "success": false, "error": { ... }, "metadata": { ... } }`) with `Content-Type: application/problem+json` per `docs/api-standards.md §Error Response`.
- **Idempotency-key convention:** Not applicable — both endpoints are `GET` (naturally idempotent). No `Idempotency-Key` header required.
- **Auth header convention:** `Authorization: Bearer <JWT>` (RS256-signed). `ORGANIZER` role required on both endpoints.
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z` (e.g., `2026-05-03T12:00:00Z`) per `docs/api-standards.md §Date/Time`.
- **Mandatory request headers:** `Authorization`, `X-Correlation-ID` (client-generated for tracing). `Accept-Language` is optional on GET endpoints.

## 3. Endpoints

### GET /api/v1/admin/ingestion-jobs

**Description:** Returns a paginated, cursor-based list of nightly CSV ingestion job reports scoped to the authenticated organizer's organization, ordered by most recent first.  
**Required Role / Auth:** `ORGANIZER` — JWT Bearer token required; `jwt.role` must be `ORGANIZER`.  
**Idempotency Requirement:** Not Required — `GET` requests are naturally idempotent.

#### Request

**Headers:**

| Header             | Required | Description                                               |
| :----------------- | :------- | :-------------------------------------------------------- |
| `Authorization`    | Yes      | `Bearer <JWT>` — RS256-signed access token.               |
| `X-Correlation-ID` | Yes      | Client-generated UUID for cross-boundary request tracing. |

**Path Parameters:** None.

**Query Parameters:**

| Parameter | Type    | Required | Description                                                                                                                      |
| :-------- | :------ | :------- | :------------------------------------------------------------------------------------------------------------------------------- |
| `cursor`  | String  | No       | Opaque keyset cursor returned by a previous response. When omitted, returns the first page (most recent reports).                |
| `size`    | Integer | No       | Page size. Default: `20`. Maximum: `100`. Values exceeding `100` are clamped to `100`. Values below `1` are rejected with `400`. |

**Request Body:** None.

**Validation Rules:**

- `size` — optional integer; if supplied, must be in range `[1, 100]`. Values outside this range return `400 Bad Request`.
- `cursor` — optional opaque string; if supplied and malformed (cannot be decoded to a valid pagination position), returns `400 Bad Request`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Ingestion job reports retrieved",
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "sourceFileKey": "inbox/faculty-of-science/students_2026-08-31.csv",
        "outcome": "COMPLETED",
        "processedRowCount": 850,
        "rejectedRowCount": 12,
        "completedAt": "2026-08-31T02:15:30Z",
        "artifactAvailable": true
      },
      {
        "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
        "sourceFileKey": "inbox/faculty-of-science/students_2026-08-30.csv",
        "outcome": "FILE_REJECTED",
        "processedRowCount": 0,
        "rejectedRowCount": 0,
        "completedAt": "2026-08-30T02:05:10Z",
        "artifactAvailable": true
      },
      {
        "id": "c3d4e5f6-a7b8-9012-cdef-012345678902",
        "sourceFileKey": "inbox/faculty-of-science/students_2026-08-29.csv",
        "outcome": "COMPLETED",
        "processedRowCount": 820,
        "rejectedRowCount": 0,
        "completedAt": "2026-08-29T02:10:00Z",
        "artifactAvailable": false
      }
    ],
    "cursor": {
      "nextCursor": "eyJjb21wbGV0ZWRBdCI6IjIwMjYtMDgtMjlUMDI6MTA6MDBaIiwiaWQiOiJjM2Q0ZTVmNi1hN2I4LTkwMTItY2RlZi0wMTIzNDU2Nzg5MDIifQ==",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Definitions:**

| Field                            | Type    | Description                                                                                                                   |
| :------------------------------- | :------ | :---------------------------------------------------------------------------------------------------------------------------- |
| `data.items[].id`                | UUID    | Primary key of `ingestion_job_reports` record. Used as `{jobId}` in the reject-artifact endpoint.                             |
| `data.items[].sourceFileKey`     | String  | Original inbox object key for the processed source file (`inbox/<org-slug>/...`).                                             |
| `data.items[].outcome`           | String  | Final source-file outcome: `COMPLETED` or `FILE_REJECTED`.                                                                    |
| `data.items[].processedRowCount` | Integer | Number of valid rows committed to `student_directory`. `0` for file-level rejections.                                         |
| `data.items[].rejectedRowCount`  | Integer | Number of quarantined row-level rejects. `0` for file-level rejections and clean runs.                                        |
| `data.items[].completedAt`       | String  | ISO 8601 UTC finalization timestamp.                                                                                          |
| `data.items[].artifactAvailable` | Boolean | `true` if a rejection artifact (source-file or row-report) is available for download. `false` for clean runs with no rejects. |
| `data.cursor.nextCursor`         | String  | Opaque cursor for the next page. Omitted (null-excluded) when `hasMore = false`.                                              |
| `data.cursor.hasMore`            | Boolean | `true` if more records exist beyond this page.                                                                                |

**Error Responses:**

| Status | Error Identifier                       | Title                 | Condition                                                                                     |
| :----- | :------------------------------------- | :-------------------- | :-------------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:bad-request`         | Bad Request           | `size` is out of `[1, 100]` range, or `cursor` is malformed.                                  |
| 401    | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing or invalid JWT Bearer token.                                                          |
| 403    | `urn:unihub:error:forbidden`           | Forbidden             | Authenticated, but `jwt.role` is not `ORGANIZER`.                                             |
| 429    | `urn:unihub:error:rate-limited`        | Too Many Requests     | Per-user Token Bucket exhausted (existing `RateLimitFilter`). Includes `Retry-After` header.  |
| 500    | `urn:unihub:error:internal`            | Internal Server Error | Unhandled backend error (e.g., database unreachable).                                         |
| 503    | `urn:unihub:error:service-unavailable` | Service Unavailable   | Redis JTI blacklist unavailable (fail-closed per ADR-010). Includes `Retry-After: 30` header. |

#### Side Effects

None. This endpoint is read-only.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-06, FR-07, FR-08, FR-09, FR-10
- **Acceptance Criteria:** Scenario 1, Scenario 2, Scenario 3, Scenario 8, Scenario 9, Scenario 10

### GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact

**Description:** Returns a short-lived (configurable TTL, default 15 minutes) MinIO/S3 pre-signed download URL for the rejection artifact attached to a specific ingestion job report. The report must belong to the caller's organization and must have an applicable artifact.  
**Required Role / Auth:** `ORGANIZER` — JWT Bearer token required; `jwt.role` must be `ORGANIZER`.  
**Idempotency Requirement:** Not Required — `GET` requests are naturally idempotent (each call produces a fresh time-window URL; the underlying artifact is immutable).

#### Request

**Headers:**

| Header             | Required | Description                                               |
| :----------------- | :------- | :-------------------------------------------------------- |
| `Authorization`    | Yes      | `Bearer <JWT>` — RS256-signed access token.               |
| `X-Correlation-ID` | Yes      | Client-generated UUID for cross-boundary request tracing. |

**Path Parameters:**

| Parameter | Type | Description                                                                                            |
| :-------- | :--- | :----------------------------------------------------------------------------------------------------- |
| `jobId`   | UUID | The UUID primary key `id` of `ingestion_job_reports`. This is NOT the Spring Batch `job_execution_id`. |

**Query Parameters:** None.

**Request Body:** None.

**Validation Rules:**

- `jobId` — required UUID (format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`). If not a valid UUID, return `400 Bad Request` before any database query.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Pre-signed download URL generated",
  "data": {
    "jobId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "artifactType": "ROW_REPORT",
    "downloadUrl": "https://minio.local/unihub-bucket/rejected/faculty-of-science/row-reports/exec-123.csv?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&...",
    "expiresAt": "2026-08-31T02:30:30Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Definitions:**

| Field               | Type   | Description                                                                                                                                     |
| :------------------ | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------- |
| `data.jobId`        | UUID   | The `ingestion_job_reports.id` whose artifact this URL references.                                                                              |
| `data.artifactType` | String | `SOURCE_FILE` when artifact is `file_rejection_source_key` (file-level rejection); `ROW_REPORT` when `reject_artifact_key` (row-level rejects). |
| `data.downloadUrl`  | String | MinIO/S3 pre-signed GET URL. Valid for the configured TTL (default 15 minutes) from the time of generation.                                     |
| `data.expiresAt`    | String | ISO 8601 UTC timestamp when the pre-signed URL expires (`now + configured TTL`).                                                                |

**Error Responses:**

| Status | Error Identifier                       | Title                 | Condition                                                                                                                              |
| :----- | :------------------------------------- | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------- |
| 400    | `urn:unihub:error:bad-request`         | Bad Request           | `jobId` is not a valid UUID format.                                                                                                    |
| 401    | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing or invalid JWT Bearer token.                                                                                                   |
| 403    | `urn:unihub:error:forbidden`           | Forbidden             | Authenticated, but `jwt.role` is not `ORGANIZER`.                                                                                      |
| 404    | `urn:unihub:error:not-found`           | Not Found             | The `jobId` does not exist, belongs to a different organization, or the job has no applicable artifact (clean run or no artifact key). |
| 429    | `urn:unihub:error:rate-limited`        | Too Many Requests     | Per-user Token Bucket exhausted (existing `RateLimitFilter`). Includes `Retry-After` header.                                           |
| 500    | `urn:unihub:error:internal`            | Internal Server Error | Unhandled backend error, including MinIO SDK failure during pre-signed URL generation.                                                 |
| 503    | `urn:unihub:error:service-unavailable` | Service Unavailable   | Redis JTI blacklist unavailable (fail-closed per ADR-010). Includes `Retry-After: 30` header.                                          |

> **Important:** `404` is returned for both "report not found" and "report belongs to a different organization" cases. This prevents org-membership enumeration attacks — an organizer from org-A MUST NOT be able to distinguish between a non-existent `jobId` and a `jobId` belonging to org-B.

#### Side Effects

None. A pre-signed URL is generated but no object is read, written, or modified. The URL authorizes the holder to `GET` the object directly from MinIO/S3 without further backend involvement.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-11, FR-12, FR-13, FR-14, FR-15
- **Acceptance Criteria:** Scenario 4, Scenario 5, Scenario 6, Scenario 7, Scenario 8, Scenario 9, Scenario 10, Scenario 11

## 4. Rate Limiting Behavior

Both endpoints are subject to the standard per-user Token Bucket rate limiting already implemented in `shared/web/RateLimitFilter`. No feature-specific rate limit configuration is needed. On limit exhaustion, the filter returns `429 Too Many Requests` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` headers per `docs/api-standards.md §Standard Response Headers`.

## 5. Idempotency Behavior

Not applicable. Both endpoints are `GET` requests and are naturally idempotent. The `GET .../reject-artifact` endpoint will produce a different pre-signed URL token on each call (fresh configured time window), but the underlying artifact key and content are immutable — this is not a semantic idempotency concern.

## 6. Cross-Endpoint Consistency Notes

- **`id` field naming:** The `id` field in the list response (`data.items[].id`) is the same UUID used as `{jobId}` in the reject-artifact endpoint path. These are consistent references to `ingestion_job_reports.id`.
- **`artifactAvailable` ↔ 404 correlation:** A list item where `artifactAvailable = false` WILL produce a `404` if its `id` is used in the reject-artifact endpoint. This is intentional and consistent — the list signals upfront that no artifact download is available for that job.
- **`artifactType` values:** `SOURCE_FILE` and `ROW_REPORT` are the only two possible values, derived from which column (`file_rejection_source_key` vs `reject_artifact_key`) is non-null on the authorized record. These values are mutually exclusive per the `ingestion_job_reports` schema constraints.
- **Timestamp format:** `completedAt` (list) and `expiresAt` (artifact URL) both use ISO 8601 UTC with trailing `Z`, consistent with the project-wide convention in `docs/api-standards.md`.
- **Error identifier namespace:** All error `type` URIs follow the project convention `urn:unihub:error:<category>`, consistent with the `422` example in `docs/api-standards.md`.
