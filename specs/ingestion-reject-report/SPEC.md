# Spec: Ingestion Reject Report

> **Status:** APPROVED  
> **Owner/area:** `workshops` module (Core Backend) — organizer-facing ingestion history and reject-artifact access  
> **Last revised:** 2026-08-31 — first version

## 1. Overview

The **Ingestion Reject Report** feature gives authenticated `ORGANIZER` users read-only visibility into their own organization's nightly CSV ingestion history and access to rejection artifacts stored in MinIO/S3. Every nightly run of the `batch_ingestion` worker produces one `ingestion_job_reports` record per processed source file. A failed file is moved to `rejected/<org-slug>/source-files/`; a completed file with quarantined rows has its row-level report written to `rejected/<org-slug>/row-reports/`; a clean file has no artifact.

This feature exposes two REST endpoints inside the `workshops` module (defined in detail in the accompanying `API_CONTRACT.md`):

1. A **paginated job-history list** that shows all final source-file outcomes for the caller's organization, with outcome metrics and artifact availability.
2. A **pre-signed URL endpoint** that returns a short-lived (configurable via environment variables, default 15 minutes) MinIO/S3 download URL for the one authorized artifact attached to a given job report.

The feature does **not** deliver notifications — it is the destination that a future notification broadcast directs organizers to after a nightly run completes.

> **Additional Implementation Notes:**
>
> - No security/auth/JWT infrastructure exists yet in `shared/security`. This feature must establish a proper Spring Security + JWT RS256 filter chain as the foundational base, designed so subsequent IAM features can extend it without wholesale rewrites.
> - The `workshops` module controller for this feature must be structured so that future endpoints added to the same module are automatically covered by the shared security configuration — no per-controller security re-registration is needed.

## 2. Actors & Preconditions

| Actor       | Precondition                                                                                                                                                                                                                                              |
| :---------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ORGANIZER` | Authenticated (valid, non-expired JWT with `role = ORGANIZER`). `jwt.org` resolves to an existing organization in `organizations`. At least one `ingestion_job_reports` record exists for that organization (otherwise the list is empty — not an error). |

> **No other actor can call these endpoints.** `STUDENT` and `STAFF` roles receive `403 Forbidden`.

## 3. Functional Requirements

### 3.1 Security & Authorization

- **FR-01:** The system SHALL reject any request to either endpoint that does not carry a valid, non-expired JWT RS256 `Bearer` token in the `Authorization` header with `401 Unauthorized`.
- **FR-02:** The system SHALL reject any authenticated request where `jwt.role` is not `ORGANIZER` with `403 Forbidden`.
- **FR-03:** The system SHALL enforce that all data queries and artifact access are scoped exclusively to `jwt.org` — no query parameter, path parameter, or request body field may override the organization boundary derived from the JWT.
- **FR-04:** The system SHALL provide a foundational JWT RS256 Spring Security filter chain in `shared/security` that: (a) validates token signature against the RSA public key loaded at startup; (b) enforces token expiry; (c) populates the Spring Security `SecurityContext` with the authenticated principal; and (d) is designed to accept additional security rules from future IAM features without requiring structural changes.
- **FR-05:** The system SHALL configure the security filter chain such that all current and future endpoints within the `workshops` module under `/api/v1/**` are automatically subject to authentication enforcement, except for explicitly permitted public endpoints (e.g., Stripe webhooks, JWKS, Swagger/OpenAPI, and Actuator). Adding a new authenticated endpoint to the `workshops` module MUST NOT require modifying the security configuration.

### 3.2 Job History List

- **FR-06:** The system SHALL return a paginated list of `ingestion_job_reports` records, strictly filtered by `organization_id = jwt.org`, ordered by `(completed_at DESC, id DESC)` for stable cursor-based pagination.
- **FR-07:** The system SHALL support cursor-based (keyset) pagination with a configurable `size` parameter (default: 20, maximum: 100). A `cursor` parameter, when supplied, resumes from the position after the last returned item.
- **FR-08:** Each item in the list response SHALL include: the report's `id` (UUID), `source_file_key`, `outcome` (`COMPLETED` or `FILE_REJECTED`), `processed_row_count`, `rejected_row_count`, `completed_at` timestamp, and an `artifact_available` boolean indicating whether a rejection artifact exists for that report.
- **FR-09:** The system SHALL derive `artifact_available` from whether the report has a non-null `file_rejection_source_key` (for `FILE_REJECTED` outcome) or a non-null `reject_artifact_key` (for `COMPLETED` outcome with rows rejected). A clean completed run (`rejected_row_count = 0`, no artifact key) SHALL yield `artifact_available = false`.
- **FR-10:** The system SHALL read the job list from the PostgreSQL replica database, as this is a non-critical reporting path that does not require read-after-write consistency.

### 3.3 Reject Artifact Pre-signed URL

- **FR-11:** The system SHALL, given a `jobId` (the UUID primary key of `ingestion_job_reports`), first verify that the identified report belongs to `jwt.org`. If the report does not exist or belongs to a different organization, the system SHALL return `404 Not Found`.
- **FR-12:** The system SHALL return `404 Not Found` when the identified report has no applicable rejection artifact key — this includes clean completed runs (`rejected_row_count = 0`, `reject_artifact_key` is null) and any other record with no artifact key regardless of outcome.
- **FR-13:** The system SHALL select exactly one worker-owned artifact key for the authorized report: `file_rejection_source_key` when `outcome = FILE_REJECTED`, and `reject_artifact_key` when `outcome = COMPLETED` and `reject_artifact_key` is non-null.
- **FR-14:** The system SHALL generate a MinIO/S3 pre-signed GET URL for the selected artifact key with a configurable TTL (defaulting to 15 minutes via environment variables) and return it in the response body. The system SHALL NOT accept a client-supplied object key for URL generation under any circumstances.
- **FR-15:** The system SHALL read the `ingestion_job_reports` record for the pre-signed URL endpoint from the PostgreSQL primary database to avoid replication-lag edge cases on freshly-written reports.

## 4. Business Rules

- **BR-01:** The `ORGANIZER` role is required; there is no system-administrator role for this feature. The `/admin/` URL prefix explicitly denotes endpoints reserved for `ORGANIZER` operations (e.g., `/api/v1/admin/workshops/*`), rather than an elevated system admin role.
- **BR-02:** The organization boundary for all data access is derived exclusively from `jwt.org`. Client-supplied parameters (query strings, path variables, request bodies) MUST NOT influence the organization filter for the job list or the organization check for the pre-signed URL.
- **BR-03:** The `{jobId}` path parameter is the UUID primary key `id` of `ingestion_job_reports`, NOT the Spring Batch `job_execution_id`.
- **BR-04:** The pre-signed URL endpoint must prove the report belongs to `jwt.org` before generating any URL. An organizer from organization A MUST NOT be able to obtain a pre-signed URL for any artifact belonging to organization B.
- **BR-05:** The API MUST NOT accept an arbitrary MinIO/S3 object key from the client. The key used to generate the pre-signed URL MUST be retrieved exclusively from the immutable `file_rejection_source_key` or `reject_artifact_key` columns of the authorized `ingestion_job_reports` record.
- **BR-06:** Pre-signed URL TTL is configurable via environment variables, with a default of 15 minutes from the time of generation.
- **BR-07:** Artifact keys are immutable once written by the `batch_ingestion` worker. The backend MUST treat them as read-only and MUST NOT write or modify any MinIO/S3 object.
- **BR-08:** The JWT RS256 security filter chain MUST use fail-closed semantics for JWT revocation checks: if the Redis JTI blacklist is unavailable, the request MUST be rejected with `503 Service Unavailable` (consistent with ADR-010 and the JTI blacklist fail-closed policy).
- **BR-09:** Cursor-based pagination on the job list MUST use the composite index `(organization_id, completed_at DESC, id DESC)` to guarantee O(1) keyset performance regardless of data volume. An N+1 pagination query pattern is prohibited.
- **BR-10:** The JWT filter chain setup MUST explicitly permit access without authentication to public system endpoints. This includes the Stripe webhook (`POST /api/v1/payments/webhook`), the public JWKS endpoint (`/.well-known/jwks.json`), OpenAPI/Swagger endpoints (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`), and Micrometer Actuator endpoints (`/actuator/**`).

## 5. State Transitions

N/A — this feature does not introduce or modify any stateful entity. It is entirely read-only. The `ingestion_job_reports` records are written exclusively by the `batch_ingestion` worker and are immutable from this feature's perspective.

## 6. Non-Functional Requirements

- **Performance:** The job history list query MUST execute via the composite index `(organization_id, completed_at DESC, id DESC)` and return results in O(1) keyset time independent of total record count. The pre-signed URL generation path MUST add at most one indexed primary-key lookup to the MinIO SDK call — no full table scans.
- **Concurrency:** Both endpoints are read-only and stateless; concurrent requests from the same or different organizers do not require any locking or coordination.
- **Idempotency:** GET requests are naturally idempotent. No `Idempotency-Key` is needed.
- **Rate-limiting:** Subject to the standard per-user Token Bucket rate limiting already enforced by `shared/web/RateLimitFilter`. No feature-specific rate limit.
- **Security:** JWT validation uses the RSA public key loaded at startup (from `resources/security/public.pem`). The JTI blacklist check against Redis MUST be fail-closed (see BR-08). The org-boundary check MUST happen before any MinIO operation. The pre-signed URL generation MUST use only server-retrieved artifact keys.
- **Reliability / Failure modes:** MinIO SDK errors during pre-signed URL generation MUST return `500 Internal Server Error`. A non-existent or non-owned job returns `404`, not `403`, to avoid org-membership enumeration attacks. Redis unavailability on the JTI check returns `503 Service Unavailable`.
- **Observability:** Requests MUST carry `X-Correlation-ID` (echoed in logs). Response includes `metadata.processedBy` and `metadata.traceId` per the standard success envelope. MinIO pre-sign latency SHOULD be instrumented via Micrometer.

## 7. Acceptance Criteria

### Scenario 1: List returns only the caller's organization's reports

```gherkin
Given an ORGANIZER authenticated with jwt.org = "org-A"
And ingestion_job_reports exist for org-A (3 records) and org-B (2 records)
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs
Then the response status is 200 OK
And the data.items list contains exactly 3 records
And all items have organization_id = "org-A"
And no items belonging to org-B appear in the response
```

_(Traces to: FR-06)_

### Scenario 2: Pagination returns correct page and next-cursor

```gherkin
Given an ORGANIZER authenticated with jwt.org = "org-A"
And 5 ingestion_job_reports exist for org-A ordered by completed_at DESC
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs?size=3
Then the response status is 200 OK
And data.items contains exactly 3 records (the 3 most recent)
And data.cursor.hasMore is true
And data.cursor.nextCursor is non-null
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs?size=3&cursor={nextCursor}
Then the response status is 200 OK
And data.items contains the remaining 2 records
And data.cursor.hasMore is false
```

_(Traces to: FR-07)_

### Scenario 3: `artifact_available` flag reflects artifact presence

```gherkin
Given an ORGANIZER with 3 reports:
  - Report A: outcome=FILE_REJECTED, file_rejection_source_key is set
  - Report B: outcome=COMPLETED, reject_artifact_key is set, rejected_row_count > 0
  - Report C: outcome=COMPLETED, reject_artifact_key is null, rejected_row_count = 0
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs
Then Report A has artifact_available = true
And Report B has artifact_available = true
And Report C has artifact_available = false
```

_(Traces to: FR-08, FR-09)_

### Scenario 4: Pre-signed URL returned for `FILE_REJECTED` artifact

```gherkin
Given an ORGANIZER with a report where outcome = FILE_REJECTED and file_rejection_source_key is set
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact
Then the response status is 200 OK
And the response body contains a download_url field
And the download_url is a valid MinIO/S3 pre-signed URL referencing the file_rejection_source_key
And the URL expires in the configured TTL (default 15 minutes)
```

_(Traces to: FR-11, FR-13, FR-14)_

### Scenario 5: Pre-signed URL returned for row-report artifact

```gherkin
Given an ORGANIZER with a report where outcome = COMPLETED, reject_artifact_key is set, rejected_row_count > 0
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact
Then the response status is 200 OK
And the response body contains a download_url field
And the download_url references the reject_artifact_key
```

_(Traces to: FR-11, FR-13, FR-14)_

### Scenario 6: 404 when report has no artifact (clean run)

```gherkin
Given an ORGANIZER with a report where outcome = COMPLETED and rejected_row_count = 0 and reject_artifact_key is null
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact
Then the response status is 404 Not Found
```

_(Traces to: FR-12)_

### Scenario 7: 404 when jobId belongs to another organization

```gherkin
Given an ORGANIZER authenticated with jwt.org = "org-A"
And a report exists with jobId belonging to org-B
When the ORGANIZER calls GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact
Then the response status is 404 Not Found
And no MinIO pre-signed URL is generated
```

_(Traces to: FR-11, BR-04)_

### Scenario 8: 401 when JWT is missing

```gherkin
Given a request with no Authorization header
When the caller calls GET /api/v1/admin/ingestion-jobs
Then the response status is 401 Unauthorized
```

_(Traces to: FR-01)_

### Scenario 9: 403 when caller is `STUDENT` role

```gherkin
Given a request with a valid JWT where role = STUDENT
When the caller calls GET /api/v1/admin/ingestion-jobs
Then the response status is 403 Forbidden
```

_(Traces to: FR-02)_

### Scenario 10: 403 when caller is `STAFF` role

```gherkin
Given a request with a valid JWT where role = STAFF
When the caller calls GET /api/v1/admin/ingestion-jobs/{jobId}/reject-artifact
Then the response status is 403 Forbidden
```

_(Traces to: FR-02)_

### Scenario 11: `ORGANIZER` cannot obtain URL with arbitrary MinIO key

```gherkin
Given an ORGANIZER and a jobId whose report has artifact_available = true
When the ORGANIZER sends any request body or query parameter containing an arbitrary MinIO key
Then the system ignores the supplied key
And the pre-signed URL is generated only from the server-retrieved artifact key on the authorized report
```

_(Traces to: FR-14, BR-05)_

## 8. Out of Scope

- **Web client integration** — UI rendering and web API calls are excluded from this implementation session. The API contract is designed for eventual web client integration but no React components are produced here.
- **Notification delivery** — this feature does NOT send notifications to organizers. The notifications worker (future feature) will direct organizers here; this feature only serves the content once they arrive.
- **CSV upload UI** — no user-facing upload capability; the legacy system deposits CSVs into MinIO externally.
- **Artifact content parsing or transformation** — the API returns a pre-signed URL only; it does not parse, preview, or transform the CSV or source file contents.
- **Paginating or listing MinIO object keys directly** — the API ONLY queries `ingestion_job_reports`; it never lists MinIO bucket contents.
- **Deletion or modification of ingestion_job_reports records** — all records are written by the `batch_ingestion` worker and are read-only from this feature.
- **`ORGANIZER` write operations** (creating, updating, canceling ingestion jobs) — this feature is purely read-only.
- **Webhook endpoint security** (`POST /api/v1/payments/webhook`) — the existing Stripe webhook must remain unauthenticated. This spec acknowledges the security configuration must permit it, but the actual webhook implementation is a separate feature (`payment-webhook-handling`).
- **JWKS endpoint** (`/.well-known/jwks.json`) — public key distribution for downstream JWT verification is a separate feature (`public-key-distribution`); this spec only notes that the security config must not block it.
- **Full IAM feature set** (login, logout, token refresh, RBAC provisioning) — the JWT filter chain established here is infrastructure-only; business logic for auth flows belongs to `authentication-and-session-management` (feature 1.2).

## 9. Linked References

- `docs/feature-catalog.md §9.1 nightly-student-csv-ingestion` — batch job that writes `ingestion_job_reports` records and uploads artifacts to MinIO.
- `docs/feature-catalog.md §9.2 ingestion-reject-report` — authoritative feature definition, module ownership, DB tables, key constraints.
- `docs/database-schema.md §ingestion_job_reports` — column spec, constraint definitions, composite index `(organization_id, completed_at DESC, id DESC)` for keyset pagination.
- `docs/database-schema.md §organizations` — org name/slug/status constraints.
- `docs/system-design.md §4 — Nightly Legacy CSV Ingestion Flow (Post-job: reject artifact upload step)` — end-to-end flow that produces the artifacts this feature exposes.
- `docs/api-standards.md` — URI versioning (`/api/v1/`), cursor-based pagination standard, success envelope, RFC 7807 error format, mandatory headers.
- `docs/project-structure.md §Core Backend` — `workshops` module directory layout (`domain/ application/ infrastructure/ presentation/`), `shared/security/` location for JWT infrastructure.
- **ADR-005:** JWT RS256 + Redis JTI revocation (fail-closed).
- **ADR-006:** RBAC + logical multi-tenancy (`jwt.org` boundary).
- **ADR-010:** Redis fail-closed for JTI checks; fail-open for rate limiting.
- **ADR-011:** MinIO/S3-compatible object storage; API returns pre-signed URLs, never raw object bytes.
- **ADR-015:** Replica routing acceptable for non-critical read paths (job list). Primary DB required for pre-signed URL lookup (freshness guarantee on recently-written reports).
- **ADR-019:** Token Bucket rate limiting via Bucket4j (existing `RateLimitFilter` already enforces this).
- **ADR-025:** Spring Batch artifacts (local reject staging uploaded to S3 on completion) — the keys stored in `ingestion_job_reports` reference these artifacts.
