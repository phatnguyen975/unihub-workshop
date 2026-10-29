# Spec: Mobile Offline Check-in — Workshop Data Provisioning

> **Status:** APPROVED  
> **Domain:** 6 — On-Site Check-in & Attendance  
> **Owner/area:** `workshops` module — `apps/backend/`  
> **Last revised:** 2026-10-29 — first version

## 1. Overview

This feature implements the backend data provisioning endpoint that enables Check-in Staff to perform offline QR-code-based attendance recording during Skills and Career Week. Before going on-site, a staff member authenticates and calls a single authenticated endpoint to download the complete list of workshops they are assigned to, along with their enrollment data, caching it locally on the mobile device. This endpoint — scoped strictly to the `workshops` module — returns a keyset-paginated, filterable list of workshop records for the authenticated staff member, drawing exclusively from the primary database to guarantee that newly-assigned workshops are always visible regardless of replication lag. It is the first of two phases in the offline check-in workflow; the second phase (attendance sync reconciliation) is a separate, currently-blocked feature.

## 2. Actors & Preconditions

| Actor                             | Precondition                                                                                                                                                                    |
| :-------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Check-in Staff** (`STAFF` role) | Has a valid, non-revoked JWT with `role = STAFF` issued by the authentication system (`F-1.2`).                                                                                 |
| **Check-in Staff** (`STAFF` role) | Has been explicitly assigned to at least one workshop via the staff assignment feature (`F-1.6`); however, zero assignments is not an error — the system returns an empty list. |
| **Check-in Staff** (`STAFF` role) | The `workshop_assignments` and `workshops` tables are accessible on the primary database.                                                                                       |

## 3. Functional Requirements

### 3.1 Authentication & Authorization

- **FR-01:** The system SHALL reject the request with an unauthenticated error when no valid `Authorization: Bearer <token>` header is present.
- **FR-02:** The system SHALL reject the request with a forbidden error when the JWT is valid but the caller's role is not `STAFF`.
- **FR-03:** The system SHALL scope all data returned to the authenticated staff member identified by `jwt.sub` — no staff member may retrieve another staff member's assigned workshops.

### 3.2 Data Retrieval

- **FR-04:** The system SHALL return a paginated list of workshops that the authenticated staff member has been explicitly assigned to, joining the `workshop_assignments` and `workshops` tables on `staff_user_id = jwt.sub`.
- **FR-05:** The system SHALL exclude any workshop with `status = CANCELED` from the returned results.
- **FR-06:** The system SHALL read from the primary database instance to guarantee read-after-write consistency immediately after a new assignment is created.

### 3.3 Pagination

- **FR-07:** The system SHALL paginate results using a keyset cursor mechanism, using `starts_at ASC` as the primary sort field and `id ASC` as the tie-breaker.
- **FR-08:** The system SHALL default to a page size of 20 items when no size is specified by the caller.
- **FR-09:** The system SHALL reject the request with a validation error (400 Bad Request) when `size` is greater than 100 or less than 1.
- **FR-10:** The system SHALL encode the cursor position as an opaque, Base64-encoded token that contains sufficient information to resume pagination at the exact position, including an active filter fingerprint when filters are present.
- **FR-11:** The system SHALL return `hasMore: false` when no further pages exist beyond the current response.

### 3.4 Filtering

- **FR-12:** The system SHALL support optional filtering by workshop `status`, accepting `PUBLISHED` or `COMPLETED` as the only valid values; filtering by `CANCELED` is explicitly disallowed (canceled workshops are always excluded by FR-05).
- **FR-13:** The system SHALL support optional filtering by a `from` timestamp, returning only workshops whose `starts_at` is greater than or equal to the supplied value.
- **FR-14:** The system SHALL support optional filtering by a `to` timestamp, returning only workshops whose `starts_at` is less than or equal to the supplied value.
- **FR-15:** The system SHALL support optional keyword search (`q`) performing a case-insensitive substring match against the workshop's `title`, `slug`, and `location` fields; a workshop is included if any of the three fields contains the keyword.
- **FR-16:** The system SHALL reject the request with a validation error when `status` is supplied with a value other than `PUBLISHED` or `COMPLETED`.
- **FR-17:** The system SHALL reject the request with a validation error when `from` or `to` is supplied with a value that is not a valid ISO-8601 UTC timestamp.
- **FR-18:** The system SHALL reject the request with a validation error when the cursor token is malformed or was generated with a different filter combination (filter fingerprint mismatch).

### 3.5 Response Payload

- **FR-19:** The system SHALL include the following fields for each workshop item in the response: `id` (UUID), `title`, `slug`, `description` (nullable, omitted when null per `@JsonInclude(NON_NULL)`), `coverImageUrl` (nullable, omitted when null per `@JsonInclude(NON_NULL)`), `location`, `startsAt`, `endsAt`, `status`, `organizationName`, and `assignedAt`.
- **FR-20:** The system SHALL format all timestamp fields (`startsAt`, `endsAt`, `assignedAt`) as ISO-8601 UTC strings (e.g. `2026-11-15T09:00:00Z`).
- **FR-21:** The system SHALL wrap the paginated result in the standard `ApiResponse` envelope conforming to `docs/api-standards.md` (`{ success, message, data, metadata: { processedBy, traceId } }`). All error responses SHALL also be wrapped in the standard `ApiResponse` error envelope (`{ success: false, error: { type, title, status, detail, instance, validationErrors }, metadata: { processedBy, traceId } }`).

## 4. Business Rules

- **BR-01:** A staff member's result set is bounded exclusively to workshops recorded in `workshop_assignments` for their `staff_user_id`. Workshop visibility is never inferred from organizational membership or any other relationship.
- **BR-02:** Workshops with `status = CANCELED` MUST be permanently excluded from results. Canceled workshops have no check-in relevance and must never appear in a staff member's provisioning payload, even if explicitly assigned.
- **BR-03:** The endpoint MUST read from the primary PostgreSQL database (not the read replica), because a staff member may have just been assigned to a workshop and must see that assignment immediately. Replication lag on the replica could hide a real, valid assignment and result in a staff member being unable to check in attendees for a workshop they were just assigned to.
- **BR-04:** The page `size` parameter MUST be between 1 and 100 inclusive. Any request with `size < 1` or `size > 100` MUST be rejected with a 400 Bad Request validation error to protect primary database throughput from oversized or invalid queries.
- **BR-05:** The cursor token MUST embed a fingerprint of the active filters. If the caller changes filter parameters and reuses a cursor generated under a different filter set, the system must detect the mismatch and reject the request. This prevents silent data corruption where a pagination cursor is applied to a different result set than the one it was generated for.
- **BR-06:** The `status` filter value `CANCELED` is disallowed at the API layer. Accepting it and returning zero results would be misleading (canceled workshops are excluded by a different rule, not by the filter); rejecting it makes the contract unambiguous.
- **BR-07:** Filtering by both `from` and `to` simultaneously is permitted. If both are provided, `from` MUST be less than or equal to `to`; otherwise the request is rejected with a validation error.

## 5. State Transitions

N/A — this feature is a read-only query endpoint. It does not introduce or modify any stateful entity. No workshop or assignment status is mutated by calling this endpoint.

## 6. Non-Functional Requirements

- **Performance:** The endpoint MUST execute in under 50 ms at the p99 latency percentile for a staff member with up to 50 assigned workshops. The query is a single indexed JOIN on `workshop_assignments.staff_user_id` + keyset pagination on `(starts_at ASC, id ASC)`; no sequential scan is acceptable.
- **Concurrency:** The endpoint is stateless and read-only; it is safe under any level of concurrent calls. No locking or coordination is required.
- **Idempotency:** Not applicable — `GET` requests are naturally idempotent. Repeated calls with the same parameters MUST return the same logical result (subject to underlying data changes).
- **Rate-limiting:** The endpoint is subject to the standard per-user token-bucket rate limit (100 tokens per 60 seconds, keyed on `jwt.sub`), enforced by Bucket4j + Redis as defined in `docs/system-design.md §System Protection Mechanisms`.
- **Security:** All JWT validation (signature, expiry, revocation via Redis JTI blacklist) is handled by the shared security layer before this endpoint's logic is reached. The RBAC check (`role = STAFF`) is enforced within this endpoint's authorization guard.
- **Reliability / Failure modes:** If the primary database is unavailable, the endpoint MUST return a service-unavailable error rather than falling back to the replica, to preserve the read-after-write consistency guarantee of BR-03. If Redis (for rate limiting) is unavailable, the system MUST fail open and allow the request through rather than rejecting valid staff members who need to pre-cache data before going offline.
- **Observability:** All requests MUST be logged with `user_id` (`jwt.sub`), `role`, query parameters, response size (item count), and latency. Response time and error rate MUST be emitted as Micrometer metrics for Prometheus ingestion.

## 7. Acceptance Criteria

### Scenario 1: Staff retrieves assigned workshops (happy path)

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to 3 workshops with status PUBLISHED
When the user calls GET /api/v1/workshops/assigned with no filter parameters
Then the response status is 200
And the response body contains exactly 3 workshop items
And each item includes id, title, slug, description, coverImageUrl, location, startsAt, endsAt, status, organizationName, assignedAt
And hasMore is false
And nextCursor is null
```

_Satisfies: FR-01, FR-03, FR-04, FR-07, FR-11, FR-19, FR-20, FR-21_

### Scenario 2: Staff with no assignments receives empty list

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user has no entries in workshop_assignments
When the user calls GET /api/v1/workshops/assigned
Then the response status is 200
And the response body contains an empty items list
And hasMore is false
```

_Satisfies: FR-04_

### Scenario 3: Canceled workshops are excluded

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to 2 workshops: one PUBLISHED, one CANCELED
When the user calls GET /api/v1/workshops/assigned
Then the response status is 200
And the response body contains exactly 1 workshop item
And the returned workshop has status PUBLISHED
And the CANCELED workshop does not appear in the response
```

_Satisfies: FR-05, BR-02_

### Scenario 4: Unauthenticated request is rejected

```gherkin
Given no Authorization header is provided in the request
When the user calls GET /api/v1/workshops/assigned
Then the response status is 401
And the response body follows the RFC 7807 error format wrapped in the standard ApiResponse envelope
```

_Satisfies: FR-01_

### Scenario 5: Non-STAFF role is forbidden

```gherkin
Given a user with role STUDENT and a valid non-revoked JWT
When the user calls GET /api/v1/workshops/assigned
Then the response status is 403
And the response body follows the RFC 7807 error format wrapped in the standard ApiResponse envelope
```

_Satisfies: FR-02_

### Scenario 6: Cursor-based pagination traverses all pages

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to 25 workshops
When the user calls GET /api/v1/workshops/assigned with size=20
Then the response contains 20 items and hasMore is true and nextCursor is non-null
When the user calls GET /api/v1/workshops/assigned with cursor=<nextCursor> and size=20
Then the response contains 5 items and hasMore is false and nextCursor is null
And the combined items across both pages equal all 25 workshops with no duplicates or gaps
```

_Satisfies: FR-07, FR-08, FR-10, FR-11_

### Scenario 7: Page size exceeding 100 or less than 1 is rejected

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
When the user calls GET /api/v1/workshops/assigned with size=200
Then the response status is 400
And the response body follows the RFC 7807 error format with a field-level validation error on "size"

When the user calls GET /api/v1/workshops/assigned with size=0
Then the response status is 400
And the response body follows the RFC 7807 error format with a field-level validation error on "size"
```

_Satisfies: FR-09, BR-04_

### Scenario 8: Status filter returns only matching workshops

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to 2 PUBLISHED workshops and 1 COMPLETED workshop
When the user calls GET /api/v1/workshops/assigned with status=COMPLETED
Then the response contains exactly 1 workshop item
And the returned workshop has status COMPLETED
```

_Satisfies: FR-12_

### Scenario 9: Invalid status filter is rejected

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
When the user calls GET /api/v1/workshops/assigned with status=CANCELED
Then the response status is 400
And the response body follows the RFC 7807 error format with a field-level validation error on "status"
```

_Satisfies: FR-12, FR-16, BR-06_

### Scenario 10: Date range filter returns only matching workshops

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to workshops starting on 2026-11-10, 2026-11-15, and 2026-11-20
When the user calls GET /api/v1/workshops/assigned with from=2026-11-12T00:00:00Z and to=2026-11-18T00:00:00Z
Then the response contains exactly 1 workshop item
And the returned workshop starts on 2026-11-15
```

_Satisfies: FR-13, FR-14_

### Scenario 11: from > to is rejected

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
When the user calls GET /api/v1/workshops/assigned with from=2026-11-20T00:00:00Z and to=2026-11-10T00:00:00Z
Then the response status is 400
And the response body follows the RFC 7807 error format with a validation error indicating from must not be after to
```

_Satisfies: FR-17, BR-07_

### Scenario 12: Keyword search returns matching workshops

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user is assigned to a workshop with title "Python Bootcamp" and another with title "Java Workshop"
When the user calls GET /api/v1/workshops/assigned with q=python
Then the response contains exactly 1 workshop item
And the returned workshop title contains "Python Bootcamp"
```

_Satisfies: FR-15_

### Scenario 13: Cursor reused with different filters is rejected

```gherkin
Given a user with role STAFF and a valid non-revoked JWT
And the user has called GET /api/v1/workshops/assigned with status=PUBLISHED and received a nextCursor
When the user calls GET /api/v1/workshops/assigned with cursor=<nextCursor> and status=COMPLETED
Then the response status is 400
And the response body follows the RFC 7807 error format indicating a filter fingerprint mismatch
```

_Satisfies: FR-10, FR-18, BR-05_

### Scenario 14: Data isolation between staff members

```gherkin
Given staff member A (jwt.sub = "user-A") is assigned to Workshop X
And staff member B (jwt.sub = "user-B") is assigned to Workshop Y but NOT Workshop X
When staff member B calls GET /api/v1/workshops/assigned
Then Workshop X does NOT appear in staff member B's response
And only Workshop Y appears
```

_Satisfies: FR-03, BR-01_

## 8. Out of Scope

- **Mobile application (apps/mobile/):** This spec covers only the backend REST endpoint. The React Native client, SQLite local cache, and offline UI are out of scope.
- **Attendance recording and sync (F-6.2):** The `POST /api/v1/workshops/{workshopId}/attendances/sync` endpoint that reconciles offline attendance records is a separate, currently-blocked feature (`attendance-sync-reconciliation`). No attendance data is read or written by this endpoint.
- **QR code generation or scanning:** QR code logic is out of scope. This endpoint provides workshop metadata, not QR codes.
- **Public-key distribution (F-1.3):** The `GET /api/v1/auth/public-key` endpoint is a separate, already-completed feature.
- **Enrollment/registration data:** This endpoint does not return the list of registered students per workshop. That data is required for QR-scan validation and belongs to a later phase.
- **Staff assignment CRUD (F-1.6):** Creating, updating, or deleting workshop assignments is already implemented and out of scope for this feature.
- **ORGANIZER and STUDENT roles:** This endpoint is exclusively for `STAFF`. No other role receives any behavior from it.
- **Real-time push or server-sent events:** No push notifications or SSE streams. The client polls by calling this endpoint on demand.
- **Read replica routing:** Deliberately excluded — primary DB routing is required (BR-03).
- **Elasticsearch search:** Full-text search is provided by the `q` filter on primary DB; Elasticsearch is not used here.

## 9. Linked References

| Document                                          | Section                                        | Relevance                                                                      |
| :------------------------------------------------ | :--------------------------------------------- | :----------------------------------------------------------------------------- |
| `docs/feature-catalog.md`                         | §6.1 `mobile-offline-checkin`                  | Feature entry, module ownership, dependency list                               |
| `docs/feature-catalog.md`                         | Appendix A                                     | Dependency map: `auth`, `public-key-distribution`, `workshop-staff-assignment` |
| `docs/system-design.md`                           | §4 — Offline Check-in & Synchronization Flow   | End-to-end flow; Phase 1 (data provisioning) describes this endpoint           |
| `docs/system-design.md`                           | §System Protection Mechanisms                  | Rate limiting (Bucket4j), circuit breaker, JWT revocation                      |
| `docs/database-schema.md`                         | `workshops` table                              | Column definitions, status enum, indexes                                       |
| `docs/database-schema.md`                         | `workshop_assignments` table                   | Column definitions, `staff_user_id`, `assigned_at`, indexes                    |
| `docs/database-schema.md`                         | Replication Lag & Read-After-Write Consistency | Justification for primary DB routing (BR-03)                                   |
| `docs/api-standards.md`                           | §Pagination                                    | Keyset cursor convention used for FR-07 through FR-11                          |
| `docs/api-standards.md`                           | §Response Envelope                             | `ApiResponse<T>` requirement for FR-21                                         |
| `docs/api-standards.md`                           | §Error Format                                  | RFC 7807 requirement for all error responses                                   |
| `docs/adrs.md`                                    | ADR-006 — RBAC                                 | Basis for FR-01, FR-02, FR-03                                                  |
| `docs/adrs.md`                                    | ADR-015 — Primary DB for assignments read      | Basis for FR-06, BR-03                                                         |
| `docs/adrs.md`                                    | ADR-028 — Explicit assignment model            | Basis for BR-01                                                                |
| `specs/public-key-distribution/SPEC.md`           | Full spec                                      | Prerequisite feature context                                                   |
| `specs/workshop-staff-assignment/API_CONTRACT.md` | Full contract                                  | Prerequisite feature context; `workshop_assignments` table ownership           |
