# Spec: Workshop Staff Assignment

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `workshops` module (backend)  
> **Last revised:** 2026-09-29 — first version

## 1. Overview

Workshop Staff Assignment enables an Organizer to administer the roster of Check-in Staff members explicitly assigned to a specific workshop within their organization. The Organizer can inspect currently assigned staff, query available staff (staff members with no schedule conflict who belong to the same organization), assign one or more staff members in a single atomic batch operation, and revoke individual assignments. To ensure operational integrity and schedule determinism, staff availability queries, batch assignments, and revocations are strictly restricted to workshops in the `PUBLISHED` lifecycle state prior to the workshop's start time (`now() < starts_at`). Querying the assigned staff roster remains available across all workshop states for historical audit. The `workshops` module owns all assignment records and business logic, resolving staff user identity through the `iam` public facade (`IamApi`).

## 2. Actors & Preconditions

| Actor       | Precondition                                                                                                                                                                                                                                                                                                                                          |
| :---------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ORGANIZER` | Must hold a valid, non-expired JWT with role `ORGANIZER` and an `org` (`organization_id`) claim. The target workshop must exist and belong to the same `organization_id`. For staff availability queries, batch assignments, and revocations, the workshop must be in `PUBLISHED` status and prior to its scheduled start time (`now() < starts_at`). |

> No other actor role (`STUDENT`, `STAFF`) is authorized to invoke any endpoint in this feature.

## 3. Functional Requirements

### 3.1 Authorization & Tenant Boundary

- **FR-01:** The system SHALL reject any request to this feature with HTTP 401 when the caller does not present a valid, non-expired JWT.
- **FR-02:** The system SHALL reject any request to this feature with HTTP 403 when the JWT role is not `ORGANIZER`.
- **FR-03:** The system SHALL reject any request to this feature with HTTP 403 when the target workshop does not belong to the `organization_id` claim present in the caller's JWT.

### 3.2 List Assigned Staff

- **FR-04:** The system SHALL return a paginated list of all staff members currently assigned to a given workshop when requested by an authorized Organizer.
- **FR-05:** The system SHALL permit the assigned-staff query regardless of the workshop's status (`DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`) and regardless of whether the workshop has already started or ended.
- **FR-06:** The system SHALL return HTTP 404 when the requested workshop does not exist.
- **FR-07:** The system SHALL support keyset cursor pagination for the assigned-staff list, defaulting to 20 items per page (maximum 100), sorted deterministically by assignment timestamp descending (`assignedAt DESC, id DESC`).
- **FR-08:** Each item in the assigned-staff list SHALL include: `userId`, `fullName`, `email`, `avatarUrl` (nullable), `phoneNumber` (nullable), and `assignedAt`.

### 3.3 List Available Staff

- **FR-09:** The system SHALL return a paginated list of staff members eligible for assignment to the target workshop when requested by an authorized Organizer.
- **FR-10:** The system SHALL reject the available-staff query with HTTP 409 when the target workshop status is not `PUBLISHED` (i.e. `DRAFT`, `COMPLETED`, or `CANCELED`).
- **FR-11:** The system SHALL reject the available-staff query with HTTP 422 when the workshop has already started (`now() >= starts_at`).
- **FR-12:** The system SHALL support keyset cursor pagination for the available-staff list, defaulting to 20 items per page (maximum 100), sorted by `fullName ASC, id ASC`.
- **FR-13:** The system SHALL support optional keyword filtering on the available-staff list via query parameter `q` (maximum 100 characters), matched case-insensitively against `fullName` and `email`. The pagination cursor SHALL be bound to the `q` value that produced it; replaying it with a different `q` SHALL be rejected with HTTP 400.
- **FR-14:** A staff member SHALL be included in the available-staff list only when all of the following conditions hold simultaneously:
  - (a) their `organization_id` matches the workshop's `organization_id`;
  - (b) their role is `STAFF` and account status is active (`is_active = true`);
  - (c) they are not already assigned to the target workshop;
  - (d) they have no active assignment to any other non-`CANCELED` workshop whose time interval overlaps the target workshop (`other.starts_at < target.ends_at AND other.ends_at > target.starts_at`).
- **FR-15:** Each item in the available-staff list SHALL include: `userId`, `fullName`, `email`, `avatarUrl` (nullable), `phoneNumber` (nullable), and `activeAssignmentsCount`.

### 3.4 Assign Staff (Batch)

- **FR-16:** The system SHALL allow an authorized Organizer to assign one or more staff members to a workshop in a single atomic batch request.
- **FR-17:** The system SHALL reject the batch assignment request with HTTP 409 when the target workshop status is not `PUBLISHED`.
- **FR-18:** The system SHALL reject the batch assignment request with HTTP 422 when the target workshop has already started (`now() >= starts_at`).
- **FR-19:** The system SHALL reject the batch assignment request with HTTP 409 when any staff user in the batch is already assigned to the target workshop.
- **FR-20:** The system SHALL reject the batch assignment request with HTTP 409 when any staff user in the batch has an overlapping schedule with another active (non-`CANCELED`) workshop.
- **FR-21:** The system SHALL reject the batch assignment request with HTTP 422 when any user ID in the batch does not exist, does not hold the `STAFF` role, belongs to a different organization, or is inactive (`is_active = false`), detailing the invalid members in `validationErrors`.
- **FR-22:** The system SHALL enforce all-or-nothing atomicity for batch assignments: if validation or conflict checks fail for any staff member in the batch, no assignments are written to the database.
- **FR-23:** The system SHALL publish a separate `StaffAssignedToWorkshopEvent` integration event for each successfully assigned staff member via the Spring Modulith Transactional Outbox upon transaction commit.

### 3.5 Revoke Assignment

- **FR-24:** The system SHALL allow an authorized Organizer to revoke the assignment of a single staff member from a workshop.
- **FR-25:** The system SHALL reject the revocation request with HTTP 409 when the target workshop status is not `PUBLISHED`.
- **FR-26:** The system SHALL reject the revocation request with HTTP 422 when the target workshop has already started (`now() >= starts_at`).
- **FR-27:** The system SHALL return HTTP 404 when the target staff member is not currently assigned to the specified workshop.
- **FR-28:** The system SHALL publish a `StaffRevokedFromWorkshopEvent` integration event via the Spring Modulith Transactional Outbox upon transaction commit after the assignment record is removed.

## 4. Business Rules

- **BR-01:** An Organizer may only manage staff assignments for workshops owned by their own organization (`organization_id` tenant boundary). Cross-tenant access is strictly prohibited.
- **BR-02:** Only active user accounts with the `STAFF` role may be assigned to a workshop. Accounts with role `STUDENT`, `ORGANIZER`, or inactive `STAFF` accounts are strictly disallowed.
- **BR-03:** Staff assignment, revocation, and availability queries require the workshop to be in `PUBLISHED` status. A `DRAFT` workshop is mutable/deletable and not finalized; `COMPLETED` and `CANCELED` workshops are terminal.
- **BR-04:** Staff assignment, revocation, and availability queries are only permitted prior to the workshop's start instant (`now() < starts_at`). Once a workshop has started, the staff roster is frozen to prevent concurrency conflicts with on-site offline check-in devices.
- **BR-05:** Schedule conflicts are determined using a half-open time interval overlap: `other.starts_at < target.ends_at AND other.ends_at > target.starts_at`. Workshops in `CANCELED` status are completely excluded from conflict evaluations.
- **BR-06:** Batch assignment is atomic: any failure (duplicate, schedule overlap, invalid user) causes the entire batch to fail with no partial writes.
- **BR-07:** The database unique constraint `UNIQUE (workshop_id, staff_user_id)` is the final concurrency guarantee against duplicate assignments. Concurrent race conditions that pass application pre-checks trigger `DataIntegrityViolationException`, translated to HTTP 409 Conflict.
- **BR-08:** The assigned-staff query (`GET .../staff`) is permitted across all workshop states (`DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`) and all times to preserve auditability and post-event reporting. For `DRAFT` workshops with no assignments, an empty collection is returned.
- **BR-09:** Read endpoints (`GET .../staff` and `GET .../staff/available`) route to PostgreSQL Read Replicas via `@Transactional(readOnly = true)` to offload query load from the Primary DB. Mutation operations (`POST` batch assign, `DELETE` revoke) and their internal validation pre-checks (check-then-act) execute strictly on the Primary DB within the write transaction, guaranteeing atomic consistency and preventing duplicate or conflicting assignments even under replication lag.
- **BR-10:** `activeAssignmentsCount` for an available staff member represents the total count of assignments held by that staff member across all non-`CANCELED` workshops in the organization.
- **BR-11:** Integration events are emitted individually per staff member (`StaffAssignedToWorkshopEvent` per assigned staff member, `StaffRevokedFromWorkshopEvent` per revoked staff member) into `event_publication` with routing key `notifications` to guarantee independent downstream delivery and retry isolation.

## 5. State Transitions

The database schema defines exactly four workshop lifecycle states: `DRAFT`, `PUBLISHED`, `COMPLETED`, `CANCELED`. Staff assignment records (`workshop_assignments`) do not hold independent state; their lifecycle is governed by the parent workshop's state and timing.

### Workshop State & Timing Matrix for Staff Operations

| Workshop State | Timing Constraint                            |   GET Assigned Staff   | GET Available Staff | POST Batch Assign |  DELETE Revoke  |
| :------------- | :------------------------------------------- | :--------------------: | :-----------------: | :---------------: | :-------------: |
| `DRAFT`        | Any time                                     | Allowed (returns `[]`) |   ❌ 409 Conflict   |  ❌ 409 Conflict  | ❌ 409 Conflict |
| `PUBLISHED`    | `now() < starts_at` (Upcoming)               |        Allowed         |       Allowed       |      Allowed      |     Allowed     |
| `PUBLISHED`    | `now() >= starts_at` (Started / In-Progress) |        Allowed         |   ❌ 422 Unproc.    |  ❌ 422 Unproc.   | ❌ 422 Unproc.  |
| `COMPLETED`    | Past event                                   |    Allowed (Audit)     |   ❌ 409 Conflict   |  ❌ 409 Conflict  | ❌ 409 Conflict |
| `CANCELED`     | Canceled event                               |    Allowed (Audit)     |   ❌ 409 Conflict   |  ❌ 409 Conflict  | ❌ 409 Conflict |

### Exhaustive Transition & Rejection Behavior

| Current Workshop State | Action / Trigger | Condition                   | Valid? | Rejection Behavior                                                                                                                      |
| :--------------------- | :--------------- | :-------------------------- | :----: | :-------------------------------------------------------------------------------------------------------------------------------------- |
| `DRAFT`                | POST assign      | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Workshop must be PUBLISHED before managing staff."                            |
| `DRAFT`                | DELETE revoke    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Workshop must be PUBLISHED before managing staff."                            |
| `DRAFT`                | GET available    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Workshop must be PUBLISHED before managing staff."                            |
| `COMPLETED`            | POST assign      | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot modify staff for a workshop in COMPLETED or CANCELED status."          |
| `COMPLETED`            | DELETE revoke    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot modify staff for a workshop in COMPLETED or CANCELED status."          |
| `COMPLETED`            | GET available    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot query available staff for a workshop in COMPLETED or CANCELED status." |
| `CANCELED`             | POST assign      | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot modify staff for a workshop in COMPLETED or CANCELED status."          |
| `CANCELED`             | DELETE revoke    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot modify staff for a workshop in COMPLETED or CANCELED status."          |
| `CANCELED`             | GET available    | Any time                    |   ❌   | HTTP 409 Conflict (`urn:unihub:error:conflict`), detail: "Cannot query available staff for a workshop in COMPLETED or CANCELED status." |
| `PUBLISHED`            | POST assign      | `now() >= starts_at`        |   ❌   | HTTP 422 Unprocessable Content (`urn:unihub:error:validation`), detail: "Cannot assign staff after the workshop has started."           |
| `PUBLISHED`            | DELETE revoke    | `now() >= starts_at`        |   ❌   | HTTP 422 Unprocessable Content (`urn:unihub:error:validation`), detail: "Cannot revoke staff after the workshop has started."           |
| `PUBLISHED`            | GET available    | `now() >= starts_at`        |   ❌   | HTTP 422 Unprocessable Content (`urn:unihub:error:validation`), detail: "Cannot query available staff after the workshop has started."  |
| `PUBLISHED`            | POST assign      | `now() < starts_at` (Valid) |   ✅   | Creates assignments, emits outbox events, returns HTTP 201 Created                                                                      |
| `PUBLISHED`            | DELETE revoke    | `now() < starts_at` (Valid) |   ✅   | Deletes assignment, emits outbox event, returns HTTP 204 No Content                                                                     |
| `PUBLISHED`            | GET available    | `now() < starts_at` (Valid) |   ✅   | Returns cursor-paginated available staff list, HTTP 200 OK                                                                              |
| Any                    | GET assigned     | Any time                    |   ✅   | Returns cursor-paginated assigned staff list, HTTP 200 OK                                                                               |

## 6. Non-Functional Requirements

- **Performance:** Staff queries (`GET .../staff` and `GET .../staff/available`) route to PostgreSQL Read Replicas (BR-09) using indexes on `workshop_assignments(workshop_id)`, `workshop_assignments(staff_user_id)`, and `workshops(starts_at)`, offloading join operations from the Primary DB. Mutation operations (`POST` and `DELETE`) execute on the Primary DB, where batch conflict pre-checks execute in a single set-based query rather than iterative per-user queries.
- **Concurrency & Race Conditions:** Concurrent duplicate assignments for the same staff member are guarded at the application level via primary-read pre-checks and finalized by the database constraint `UNIQUE (workshop_id, staff_user_id)`. Race conditions are caught and translated to HTTP 409 Conflict.
- **Idempotency:** POST batch assignment is not idempotent; retrying an already completed assignment returns HTTP 409 Conflict. DELETE is not idempotent; repeated deletion of a removed assignment returns HTTP 404 Not Found. Neither endpoint requires an `Idempotency-Key` header.
- **Rate Limiting:** Managed under the **Default Tier** (100 tokens, refill 100 per 60s, keyed by `jwt.sub`) enforced at the Core Backend Monolith filter layer using Bucket4j + Redis. No custom tier is required.
- **Security:** Endpoints strictly enforce JWT verification with role `ORGANIZER`. Multi-tenant isolation is enforced by matching `workshop.organization_id == jwt.org` on every request.
- **Reliability:** Outbox integration events (`StaffAssignedToWorkshopEvent`, `StaffRevokedFromWorkshopEvent`) are written to `event_publication` in the same database transaction as assignment rows, guaranteeing zero message loss on database commit.
- **Observability:** Standard request/response logging handled globally at the filter layer per `docs/api-standards.md`. No custom console logging of staff assignment details or custom metrics is emitted.

## 7. Acceptance Criteria

### Scenario 1: Unauthorized request is rejected

```gherkin
Given a request is made without a Bearer JWT or with an invalid token
When any endpoint of this feature is called
Then the system returns HTTP 401 Unauthorized
```

_Satisfies:_ FR-01

### Scenario 2: Non-ORGANIZER role is rejected

```gherkin
Given a valid JWT with role STUDENT or STAFF
When any endpoint of this feature is called
Then the system returns HTTP 403 Forbidden
```

_Satisfies:_ FR-02

### Scenario 3: Cross-organization workshop access is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And a workshop belonging to organization B
When any endpoint of this feature is called with organization B's workshop ID
Then the system returns HTTP 403 Forbidden
```

_Satisfies:_ FR-03, BR-01

### Scenario 4: List assigned staff across different workshop statuses

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W1 is in DRAFT with no assignments
And workshop W2 is in PUBLISHED with 2 assigned staff members
And workshop W3 is in COMPLETED with 3 assigned staff members
When the Organizer requests assigned staff for W1, W2, and W3 respectively
Then all three requests succeed with HTTP 200 OK
And W1 returns an empty items list
And W2 and W3 return their respective paginated staff rosters
And each item includes userId, fullName, email, avatarUrl, phoneNumber, and assignedAt
```

_Satisfies:_ FR-04, FR-05, FR-07, FR-08, BR-08

### Scenario 5: List assigned staff for non-existent workshop

```gherkin
Given a valid ORGANIZER JWT
And no workshop exists with ID 00000000-0000-0000-0000-000000000000
When the Organizer requests the assigned staff list for that ID
Then the system returns HTTP 404 Not Found
```

_Satisfies:_ FR-06

### Scenario 6: List available staff on DRAFT workshop is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W belongs to organization A and is in DRAFT status
When the Organizer requests available staff for workshop W
Then the system returns HTTP 409 Conflict with detail indicating workshop must be PUBLISHED
```

_Satisfies:_ FR-10, BR-03

### Scenario 7: List available staff on COMPLETED or CANCELED workshop is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W belongs to organization A and is in COMPLETED or CANCELED status
When the Organizer requests available staff for workshop W
Then the system returns HTTP 409 Conflict with detail indicating workshop cannot be modified
```

_Satisfies:_ FR-10, BR-03

### Scenario 8: List available staff when workshop has already started is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status but its starts_at is in the past (now >= starts_at)
When the Organizer requests available staff for workshop W
Then the system returns HTTP 422 Unprocessable Content indicating workshop has already started
```

_Satisfies:_ FR-11, BR-04

### Scenario 9: List available staff — happy path before workshop starts

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status with starts_at in the future
And staff member S belongs to organization A, has role STAFF, is active, and has no overlapping assignments
When the Organizer requests available staff for workshop W
Then the system returns HTTP 200 OK
And staff member S appears in the items list with activeAssignmentsCount, avatarUrl, and phoneNumber
```

_Satisfies:_ FR-09, FR-12, FR-14, FR-15, BR-03, BR-04, BR-10

### Scenario 10: List available staff excludes staff with schedule conflict

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W1 is scheduled from 09:00 to 12:00 in PUBLISHED status (future)
And workshop W2 is scheduled from 10:00 to 13:00 in PUBLISHED status
And staff member S is already assigned to W2
When the Organizer requests available staff for workshop W1
Then staff member S does NOT appear in the available staff list
```

_Satisfies:_ FR-14-d, BR-05

### Scenario 11: List available staff includes staff assigned to CANCELED workshop

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W1 is scheduled from 09:00 to 12:00 in PUBLISHED status (future)
And workshop W2 has overlapping schedule but is in CANCELED status
And staff member S is assigned to W2
When the Organizer requests available staff for workshop W1
Then staff member S DOES appear in the available staff list
```

_Satisfies:_ FR-14-d, BR-05

### Scenario 12: List available staff with keyword search

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future)
And available staff "Alice Nguyen" (alice@example.com) and "Bob Tran" (bob@example.com) exist
When the Organizer queries available staff with q=alice
Then only "Alice Nguyen" is returned in the items list
```

_Satisfies:_ FR-13

### Scenario 13: Batch assign staff — happy path

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status with starts_at in the future
And staff members S1 and S2 are eligible and have no conflicts
When the Organizer submits a batch assignment request with [S1.id, S2.id]
Then the system returns HTTP 201 Created
And two rows are persisted in workshop_assignments
And each assigned entry in assignedStaffs returns userId, fullName, email, avatarUrl, phoneNumber, and assignedAt
And one StaffAssignedToWorkshopEvent is published for S1 and one for S2
```

_Satisfies:_ FR-16, FR-22, FR-23, BR-03, BR-04, BR-11

### Scenario 14: Batch assign staff on non-PUBLISHED workshop is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in DRAFT, COMPLETED, or CANCELED status
When the Organizer submits a batch assignment request
Then the system returns HTTP 409 Conflict
And no assignment records are created
```

_Satisfies:_ FR-17, BR-03

### Scenario 15: Batch assign staff after workshop started is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status but now >= starts_at
When the Organizer submits a batch assignment request
Then the system returns HTTP 422 Unprocessable Content indicating workshop has already started
And no assignment records are created
```

_Satisfies:_ FR-18, BR-04

### Scenario 16: Batch assign staff with already assigned member is rejected atomically

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future)
And staff member S1 is already assigned to W, while S2 is available
When the Organizer submits a batch assignment request with [S1.id, S2.id]
Then the system returns HTTP 409 Conflict indicating staff member is already assigned
And S2 is NOT assigned to W (all-or-nothing rollback)
```

_Satisfies:_ FR-19, FR-22, BR-06

### Scenario 17: Batch assign staff with schedule conflict is rejected atomically

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future)
And staff member S1 is available, while S2 has an overlapping schedule with active workshop W2
When the Organizer submits a batch assignment request with [S1.id, S2.id]
Then the system returns HTTP 409 Conflict indicating schedule conflict
And neither S1 nor S2 is assigned to W (all-or-nothing rollback)
```

_Satisfies:_ FR-20, FR-22, BR-06

### Scenario 18: Batch assign staff with invalid user is rejected atomically

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future)
And user U belongs to organization B (or has role STUDENT, or is inactive)
When the Organizer submits a batch assignment request with [U.id]
Then the system returns HTTP 422 Unprocessable Content with validationErrors detailing invalid user U.id
```

_Satisfies:_ FR-21, FR-22, BR-02

### Scenario 19: Revoke staff assignment — happy path

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future) with staff member S assigned
When the Organizer sends a revocation request for S on workshop W
Then the system returns HTTP 204 No Content
And the assignment row for S on W is deleted
And one StaffRevokedFromWorkshopEvent is published for S
```

_Satisfies:_ FR-24, FR-28, BR-03, BR-04, BR-11

### Scenario 20: Revoke staff assignment on non-PUBLISHED workshop is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in DRAFT, COMPLETED, or CANCELED status
When the Organizer sends a revocation request for staff member S
Then the system returns HTTP 409 Conflict
And the assignment is not modified
```

_Satisfies:_ FR-25, BR-03

### Scenario 21: Revoke staff assignment after workshop started is rejected

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status but now >= starts_at
When the Organizer sends a revocation request for staff member S
Then the system returns HTTP 422 Unprocessable Content indicating workshop has already started
And the assignment is not modified
```

_Satisfies:_ FR-26, BR-04

### Scenario 22: Revoke staff assignment when not assigned returns 404

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future) but staff member S is not assigned
When the Organizer sends a revocation request for S on workshop W
Then the system returns HTTP 404 Not Found
```

_Satisfies:_ FR-27

### Scenario 23: Concurrent batch assignment race condition caught by DB unique constraint

```gherkin
Given a valid ORGANIZER JWT for organization A
And workshop W is in PUBLISHED status (future)
And staff member S is currently available
When two concurrent requests attempt to assign S to W simultaneously
Then exactly one request succeeds with HTTP 201 Created
And the other request is caught by the UNIQUE (workshop_id, staff_user_id) constraint and returns HTTP 409 Conflict
And exactly one assignment row exists for (W, S)
```

_Satisfies:_ FR-19, BR-07, NFR Concurrency

## 8. Out of Scope

- **Worker notification consumption (Feature 7.x):** This feature only publishes `StaffAssignedToWorkshopEvent` and `StaffRevokedFromWorkshopEvent` to RabbitMQ via the Transactional Outbox. Worker consumers sending actual emails/push notifications are out of scope.
- **Mobile check-in execution (Feature 6.1):** Offline QR ticket scanning and local SQLite syncing by staff on mobile devices are covered by Feature 6.1.
- **Self-assignment by staff:** `STAFF` users cannot assign themselves or inspect administrative assignment management tools.
- **Bulk revocation:** Revocation is performed on a single staff member per request (`DELETE .../staff/{userId}`).
- **Staff capacity limit:** No maximum limit is placed on the number of staff members assigned to a single workshop.
- **Assignment audit trail table:** Assignment records maintain `assigned_at`, `assigned_by`, `created_at`, and `updated_at`. A separate audit history log table is out of scope.

## 9. Linked References

| Document                    | Section Consulted                                                                                            |
| :-------------------------- | :----------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | Feature 1.6 catalog entry, module ownership, dependency map                                                  |
| `docs/system-design.md`     | §5 Account Provisioning & Security (Explicit Staff Assignment & Conflict Prevention); §6 Rate Limiting Tiers |
| `docs/database-schema.md`   | `workshops` lifecycle status enum; `workshop_assignments` table definition; Primary Routing Requirements     |
| `docs/api-standards.md`     | Response envelope, RFC 7807 problem details, Keyset cursor pagination standards                              |
| `docs/project-structure.md` | Clean Architecture modular boundaries, `iam` public API facade pattern                                       |
