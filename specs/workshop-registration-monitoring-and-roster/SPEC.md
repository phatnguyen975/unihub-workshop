# Spec: Workshop Registration Monitoring and Roster

> **Status:** APPROVED  
> **Domain:** 4 — Workshop Registration  
> **Owner/area:** `registrations` module — Core Backend (`apps/backend/`)  
> **Last revised:** 2026-10-28 — first version

## 1. Overview

This feature gives **ORGANIZER** users a real-time, read-only dashboard capability over any workshop that belongs to their organization: (1) a **registration summary** showing current seat counts and occupancy, and (2) a **paginated attendee roster** for reviewing and filtering who has registered. It exists because organizers currently have no in-platform way to track registration progress, which forces manual headcount reconciliation before and during the event. The feature is purely read-only — no writes, no cancellations, no refunds — and it is owned by the `registrations` module of the Core Backend.

In addition, this feature formally specifies the **complete real-time event payload** (`registration.updated`) that is emitted to the organizer's live dashboard whenever a registration transitions to `CONFIRMED` or `CANCELED`. The payload fix itself is an implementation detail deferred to `/build`, but the required payload shape is captured here as a binding Functional Requirement so that the spec is the authoritative source of truth.

## 2. Actors & Preconditions

| Actor                             | Precondition                                                                                                                                                                                                                                                   |
| :-------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ORGANIZER**                     | Has an active account with role `ORGANIZER`. Holds a valid, non-expired JWT (RS256) that includes an `org` claim identifying their organization. The workshop being queried must exist and must belong to the same organization (`organizationId == jwt.org`). |
| **Registered Student** (indirect) | A student must have at least attempted registration for the workshop in order for their entry to appear in the roster. This actor does not directly invoke this feature.                                                                                       |

## 3. Functional Requirements

### 3.1 Access Control (applies to both endpoints)

- **FR-01:** The system SHALL reject any request to the registration summary or roster endpoints when the caller does not hold the `ORGANIZER` role, returning an authorization error.
- **FR-02:** The system SHALL reject any request when the target workshop does not exist, returning a not-found error.
- **FR-03:** The system SHALL reject any request when the calling organizer's organization identifier (from the JWT) does not match the workshop's `organizationId`, returning an authorization error. This check SHALL be performed after the workshop-existence check.

### 3.2 Registration Summary

- **FR-04:** The system SHALL return, for a given workshop, the current total number of registrations in `CONFIRMED` status (`confirmedCount`) and in `PENDING` status (`pendingCount`).
- **FR-05:** The system SHALL return the workshop's configured maximum seat capacity (`capacity`).
- **FR-06:** The system SHALL compute and return the number of available seats as: `availableSeats = max(0, capacity − confirmedCount − pendingCount)`. The result SHALL never be negative.
- **FR-07:** The system SHALL compute and return the occupancy percentage as: `occupancyPercentage = (confirmedCount × 100.0) / capacity`, rounded to two decimal places. When `capacity` is zero, the system SHALL return `0.00` to avoid division by zero.
- **FR-08:** The system SHALL return the timestamp of the most recent registration state change for the workshop as `lastUpdatedAt`. When no registrations exist for the workshop, the system SHALL return the workshop's own last-updated timestamp as a fallback.
- **FR-09:** The system SHALL route both summary and roster queries exclusively to the database read replica.

### 3.3 Attendee Roster

- **FR-10:** The system SHALL return a cursor-paginated list of registration entries for a given workshop, ordered by registration creation time descending (most recent first), with ties broken by registration ID descending.
- **FR-11:** Each roster entry SHALL include: the registration identifier, a nested student profile (student user identifier, full name, student number, email), the registration status, the price charged at registration time, the currency, and the registration timestamp.
- **FR-12:** The system SHALL support optional filtering of the roster by a single `status` value. Only registrations whose status exactly matches the supplied value SHALL be returned. When no filter is supplied, registrations in all statuses SHALL be returned.
- **FR-13:** The roster endpoint SHALL use keyset (cursor-based) pagination. The default page size SHALL be 20 entries. The maximum page size SHALL be 100 entries. Requests specifying a size greater than 100 SHALL be rejected. A cursor representing a position and active filter state SHALL be included in every response to allow the caller to retrieve the next page.
- **FR-14:** The system SHALL batch-resolve student profile information (full name, student number, email) for all entries on the current page from the identity and access management module, using only the user identifiers stored in the `registrations` table. No direct query against the student directory table is permitted.

### 3.4 Real-Time Seat Count Events

- **FR-15:** Whenever a registration transitions to `CONFIRMED` status, the system SHALL publish a `registration.updated` event to the workshop's real-time channel containing the complete seat count snapshot: workshop identifier, confirmed count, pending count, capacity, available seats, and the timestamp of the change.
- **FR-16:** Whenever a registration transitions to `CANCELED` status, the system SHALL publish a `registration.updated` event to the workshop's real-time channel with the same complete seat count snapshot as FR-15.
- **FR-17:** The seat count values included in the `registration.updated` event payload SHALL be computed by the same formulas defined in FR-04 through FR-08, applied at the moment of the transition.

> **Build note:** FR-15 through FR-17 describe the **required** payload shape. The current implementation publishes a truncated payload (`workshopId`, `status` only). Correcting the implementation to match this spec is a `/build` task.

## 4. Business Rules

- **BR-01 — Multi-tenancy isolation:** An organizer may only access data for workshops that belong to their own organization. Cross-organization access is prohibited regardless of any other condition. _(Source: docs/system-design.md — RBAC; docs/adrs.md — ADR-006)_
- **BR-02 — Read replica routing:** All read operations under this feature SHALL be directed to the read replica. Writes, if any trigger side effects, MAY still use the primary but the query results for this feature are always replica-sourced. _(Source: docs/database-schema.md — Replication Lag & Read-After-Write Consistency; docs/adrs.md — ADR-015)_
- **BR-03 — No soft-delete bypass:** Registrations in any status (including `CANCELED` and `FAILED`) remain visible in the roster unless filtered out by the caller. The roster does not hide any record. _(Source: docs/database-schema.md — registrations table; no soft-delete column exists)_
- **BR-04 — Pagination caps:** The maximum page size for any cursor-paginated list endpoint is 100 entries. Requests exceeding this limit SHALL be rejected with a validation error. _(Source: docs/api-standards.md — Pagination)_
- **BR-05 — Cursor filter fingerprint integrity:** A cursor encodes the filter state active at the time it was issued. If a caller supplies a cursor that was issued with a different filter than the current request's filter, the system SHALL reject the request with a validation error to prevent result-set inconsistency. _(Source: existing keyset pagination implementation pattern in the codebase)_
- **BR-06 — No cross-module direct data access:** The `registrations` module may not query the `iam` module's tables directly. Student profile resolution SHALL go through the `iam` module's published API surface only. _(Source: docs/project-structure.md — Modular Boundary Enforcement; docs/adrs.md — Spring Modulith)_
- **BR-07 — Rate limiting:** Both endpoints are subject to the `default-tier` token bucket: 100 requests per 60 seconds per authenticated user (by JWT subject). The rate limiter is fail-open. _(Source: docs/system-design.md — System Protection Mechanisms; docs/adrs.md — ADR-010)_
- **BR-08 — Availability seats floor:** Available seats are always `max(0, capacity − confirmedCount − pendingCount)` and SHALL never be returned as a negative number. _(Source: docs/system-design.md — Overbooking Prevention)_
- **BR-09 — Occupancy percentage denominator guard:** When a workshop has `capacity = 0`, `occupancyPercentage` SHALL return `0.00`. This prevents division-by-zero errors. _(Source: derived from BR-08 arithmetic)_

## 5. State Transitions

This feature is **read-only** — it does not introduce or modify the lifecycle of any entity. However, it observes the registration state machine to trigger real-time events (FR-15 and FR-16). The transitions below document the existing registration FSM transitions that are relevant to this feature's publishing requirement:

| From State      | Event/Trigger                                       | To State                | Valid? | Rejection Behavior                                                        |
| :-------------- | :-------------------------------------------------- | :---------------------- | :----: | :------------------------------------------------------------------------ |
| `PENDING`       | Free workshop registration confirmed by system      | `CONFIRMED`             |   ✅   | N/A — triggers FR-15 event                                                |
| `PENDING`       | Payment webhook `payment_intent.succeeded` received | `CONFIRMED`             |   ✅   | N/A — triggers FR-15 event                                                |
| `CONFIRMED`     | Student cancels or organizer cancels                | `CANCELED`              |   ✅   | N/A — triggers FR-16 event                                                |
| `PENDING`       | Student cancels or registration sweeper times out   | `CANCELED`              |   ✅   | N/A — triggers FR-16 event                                                |
| `CONFIRMED`     | Summary / roster read                               | `CONFIRMED` (unchanged) |   ✅   | N/A — read-only, no state change                                          |
| Any → Any other | Any transition not listed above                     | —                       |   ❌   | Rejected by existing registration FSM; no event published by this feature |

> **Note:** This feature does not implement the FSM transitions above. It publishes events when those transitions occur, and it reads the resulting state for summary/roster queries. The FSM itself is owned by the free-workshop-registration and payment-webhook-handling features.

## 6. Non-Functional Requirements

- **Performance:** Both endpoints are read-only. The summary query uses a single `GROUP BY status` aggregate over an indexed `workshop_id` column. The roster uses keyset pagination over the existing composite index `(workshop_id, created_at DESC, id DESC)`. Student profile batch-resolution is a single in-process cross-module call per page, not a per-row call. No additional indexes are required. The system MUST NOT perform a per-registration profile lookup (N+1 pattern is prohibited).
- **Concurrency:** Both endpoints are stateless reads. Multiple concurrent requests for the same workshop from the same or different organizers are safe with no locking requirements. The summary reflects a point-in-time snapshot and is not transactionally guaranteed to be up-to-the-millisecond — eventual consistency via read replica lag (typically < 100 ms) is acceptable.
- **Idempotency:** Both endpoints are `GET` requests and are naturally idempotent. No idempotency key is required.
- **Rate-limiting:** `default-tier` token bucket: 100 requests per 60 seconds per authenticated user (`jwt.sub`). Fail-open: if the rate-limit check fails (Redis unavailable), the request proceeds rather than being rejected. _(ADR-010)_
- **Security:** JWT RS256 validation is required on every request. The `org` claim MUST be present and matched against `workshop.organizationId` before any data is returned. No student PII is returned beyond: full name, student number, institutional email — consistent with data already exposed in other organizer views.
- **Reliability / Failure modes:** If the `iam` module's profile resolution is unavailable, the system SHALL propagate the error as a 500 Internal Server Error rather than returning a partial roster with missing student data. Partial data is worse than a clear error. The read replica path follows the existing circuit-breaker configuration (Resilience4j).
- **Observability:** All requests and errors are traced via Micrometer → Prometheus → Grafana per the existing observability stack. No additional custom metrics are required beyond what the existing `@Timed` instrumentation on controllers provides.

## 7. Acceptance Criteria

### Scenario 1: Organizer retrieves registration summary — happy path

```gherkin
Given an ORGANIZER is authenticated with a valid JWT containing org claim "org-A"
And a workshop with ID "ws-1" exists with organizationId "org-A", capacity 100
And 72 registrations are in CONFIRMED status and 5 are in PENDING status for "ws-1"
When the organizer requests the registration summary for workshop "ws-1"
Then the response is 200 OK
And confirmedCount is 72
And pendingCount is 5
And capacity is 100
And availableSeats is 23
And occupancyPercentage is 72.00
And lastUpdatedAt is the timestamp of the most recently updated registration
```

_Traces to: FR-01 (passes), FR-02 (passes), FR-03 (passes), FR-04, FR-05, FR-06, FR-07, FR-08_

### Scenario 2: Organizer retrieves summary — capacity is zero

```gherkin
Given a workshop with capacity 0 and 0 registrations
When the organizer requests the registration summary
Then the response is 200 OK
And occupancyPercentage is 0.00
And availableSeats is 0
```

_Traces to: FR-07 (division-by-zero guard), FR-06 (floor at 0)_

### Scenario 3: Summary — no registrations yet, lastUpdatedAt falls back to workshop timestamp

```gherkin
Given a workshop with capacity 50 and zero registrations
When the organizer requests the registration summary
Then confirmedCount is 0 and pendingCount is 0
And lastUpdatedAt is the workshop's own updatedAt timestamp
```

_Traces to: FR-08_

### Scenario 4: Summary request — workshop does not exist

```gherkin
Given no workshop with ID "ws-999" exists
When the organizer requests the registration summary for "ws-999"
Then the response is 404 Not Found
```

_Traces to: FR-02_

### Scenario 5: Summary request — workshop belongs to a different organization

```gherkin
Given a workshop "ws-1" belongs to organization "org-B"
And the organizer's JWT contains org claim "org-A"
When the organizer requests the registration summary for "ws-1"
Then the response is 403 Forbidden
```

_Traces to: FR-01, FR-03_

### Scenario 6: Unauthenticated request rejected

```gherkin
Given a request with no Authorization header (or an expired/invalid JWT)
When the request is sent to either endpoint
Then the response is 401 Unauthorized
```

_Traces to: FR-01_

### Scenario 7: Request by a user with STUDENT role rejected

```gherkin
Given a user is authenticated with role STUDENT
When the user sends a request to the summary or roster endpoint
Then the response is 403 Forbidden
```

_Traces to: FR-01_

### Scenario 8: Organizer retrieves roster — happy path, first page

```gherkin
Given a workshop "ws-1" belongs to organization "org-A"
And 25 CONFIRMED registrations exist for "ws-1", ordered by created_at DESC
And an ORGANIZER with org "org-A" is authenticated
When the organizer requests the roster for "ws-1" with default page size (no cursor, no status filter)
Then the response is 200 OK
And the items list contains 20 entries (the 20 most recent by created_at DESC, id DESC)
And each entry contains: id, student.id, student.fullName, student.studentNumber, student.email, status, price, currency, registeredAt
And cursor.hasMore is true
And cursor.nextCursor is a non-null opaque token
```

_Traces to: FR-10, FR-11, FR-13, FR-14_

### Scenario 9: Organizer retrieves roster — second page using cursor

```gherkin
Given the organizer has the nextCursor from Scenario 8
When the organizer requests the roster for "ws-1" with that cursor
Then the response is 200 OK
And the items list contains 5 entries (the remaining 5 registrations)
And cursor.hasMore is false
And cursor.nextCursor is null
```

_Traces to: FR-10, FR-13_

### Scenario 10: Roster — filter by status CONFIRMED

```gherkin
Given a workshop with 10 CONFIRMED, 5 PENDING, and 3 CANCELED registrations
When the organizer requests the roster with status filter "CONFIRMED"
Then all returned items have status CONFIRMED
And no PENDING or CANCELED items appear in the result
```

_Traces to: FR-12_

### Scenario 11: Roster — page size exceeds maximum (100)

```gherkin
Given a valid organizer request for workshop "ws-1"
When the organizer requests size=101
Then the response is 422 Unprocessable Entity (validation error)
```

_Traces to: FR-13, BR-04_

### Scenario 12: Roster — cursor issued with different filter is rejected

```gherkin
Given the organizer obtained a cursor while filtering by status=CONFIRMED
When the organizer re-uses that cursor with status=PENDING
Then the response is 400 Bad Request (cursor/filter mismatch)
```

_Traces to: BR-05_

### Scenario 13: Real-time event published on registration CONFIRMED

```gherkin
Given a workshop "ws-1" with capacity 100, 71 CONFIRMED, 5 PENDING registrations
And a student's registration transitions from PENDING to CONFIRMED
When the registration status is updated to CONFIRMED
Then a registration.updated event is published to the workshop's real-time channel
And the event payload contains: workshopId="ws-1", confirmedCount=72, pendingCount=4, capacity=100, availableSeats=24, lastUpdatedAt=<now>
```

_Traces to: FR-15, FR-17_

### Scenario 14: Real-time event published on registration CANCELED

```gherkin
Given a workshop "ws-1" with capacity 100, 72 CONFIRMED, 5 PENDING registrations
And a student cancels their CONFIRMED registration
When the registration status is updated to CANCELED
Then a registration.updated event is published to the workshop's real-time channel
And the event payload contains: workshopId="ws-1", confirmedCount=71, pendingCount=5, capacity=100, availableSeats=24, lastUpdatedAt=<now>
```

_Traces to: FR-16, FR-17_

### Scenario 15: Roster — invalid status filter value rejected

```gherkin
Given a valid organizer request for workshop "ws-1"
When the organizer requests the roster with status="INVALID_VALUE"
Then the response is 422 Unprocessable Entity (validation error)
```

_Traces to: FR-12_

### Scenario 16: Rate limit exceeded

```gherkin
Given an organizer has made 100 requests to either endpoint within the last 60 seconds
When the organizer makes the 101st request
Then the response is 429 Too Many Requests
```

_Traces to: BR-07_

## 8. Out of Scope

- **Student-facing registration views:** Students viewing their own registration history is a separate feature (`payment-history-and-status`). This feature is organizer-only.
- **Write operations:** No registration creation, cancellation, status override, or refund operations are part of this feature.
- **Export / CSV download:** Exporting the roster to a file is not part of this feature.
- **Workshop lifecycle management:** Changing workshop status (publish, cancel) is owned by a separate feature. This feature reads workshop data only.
- **Payment detail in roster:** The price shown is `price_at_registration` — the charge amount at the time of registration. Detailed payment transaction records, payment method, or Stripe charge IDs are not included.
- **WebSocket or long-polling:** The real-time event specified in FR-15 through FR-17 uses the existing SSE/Redis Pub-Sub infrastructure (ADR-031). No new real-time transport is introduced.
- **Organizer manual seat adjustment:** Manually overriding seat counts is not part of this feature.
- **STAFF role access:** Check-in staff do not have access to registration monitoring; their QR scan workflow is a separate feature (`qr-check-in-staff`).
- **Search or full-text filtering on the roster:** Only status-based filtering is supported. Name or email search over the roster is out of scope.
- **Attendance / check-in status on roster:** Whether a registrant has been checked in is not included in the roster entries. That is a concern of the check-in feature.

## 9. Linked References

| Document                                              | Section                                              | Relevance                                                            |
| :---------------------------------------------------- | :--------------------------------------------------- | :------------------------------------------------------------------- |
| `docs/feature-catalog.md`                             | §4.4 — `workshop-registration-monitoring-and-roster` | Feature entry, module ownership, DB tables, status                   |
| `docs/system-design.md`                               | §4 — Live Registration Broadcasting                  | Real-time event payload specification                                |
| `docs/system-design.md`                               | §4 — Organizer Registration Monitoring & Roster      | Both endpoint definitions and data shapes                            |
| `docs/system-design.md`                               | §6 — Real-Time Event Distribution Architecture       | SSE + Redis Pub-Sub pattern (ADR-031)                                |
| `docs/system-design.md`                               | §4 — RBAC Sequence Diagrams                          | ORGANIZER role enforcement pattern                                   |
| `docs/database-schema.md`                             | `registrations` table                                | Column definitions, index `idx_registrations_workshop_created_at_id` |
| `docs/database-schema.md`                             | Replication Lag & Read-After-Write Consistency       | Read replica routing rule                                            |
| `docs/api-standards.md`                               | Pagination                                           | Keyset cursor convention, default/max page size                      |
| `docs/api-standards.md`                               | JSON envelope, HTTP status codes, RFC 7807 errors    | Response and error shapes                                            |
| `docs/adrs.md`                                        | ADR-006 — RBAC                                       | Role enforcement and tenant isolation                                |
| `docs/adrs.md`                                        | ADR-010 — Rate Limiting                              | `default-tier` token bucket, fail-open                               |
| `docs/adrs.md`                                        | ADR-015 — Read Replica Routing                       | `@Transactional(readOnly = true)` → replica                          |
| `docs/adrs.md`                                        | ADR-031 — SSE + Redis Pub-Sub                        | Real-time event broadcasting                                         |
| `specs/free-workshop-registration/API_CONTRACT.md`    | Keyset cursor format, `registration.updated` event   | Cursor structure, event payload reference                            |
| `specs/real-time-event-streaming-hub/API_CONTRACT.md` | `registration.updated` wire example                  | SSE event wire format                                                |
| `specs/payment-history-and-status/API_CONTRACT.md`    | Nested `student: {}` pattern in organizer views      | Roster entry shape precedent                                         |
