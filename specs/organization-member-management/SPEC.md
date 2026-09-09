# Spec: Organization Member Management

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `iam` module (Spring Modulith Core Monolith)  
> **Last revised:** 2026-09-09 — first version

## 1. Overview

This feature enables **ORGANIZER** users to administer the member roster of their own organization from within the IAM domain (`iam` module). It provides four core administrative capabilities:

1. Listing organization members across all roles (`ORGANIZER`, `STAFF`, `STUDENT`) with cursor-based pagination and flexible filtering (by role, active status, and search keyword).
2. Viewing a single member's complete profile.
3. Provisioning new `STAFF` or `ORGANIZER` accounts (zero-touch: the system auto-generates a secure temporary password and emits a transactional outbox event for credential delivery).
4. Updating a member's active status (banning or unbanning). When banning an account, session tokens are revoked in Redis (blacklist and session tracker) before database records are modified to guarantee fail-closed security, and a domain event is published via the transactional outbox to notify the user via email.

All operations are strictly scoped to the calling organizer's organization (`jwt.org`). The calling organizer is automatically excluded from member list results. Retry safety is handled via natural idempotency (unique email constraints on creation, and idempotent state assignment on status updates).

## 2. Actors & Preconditions

| Actor         | Precondition                                                                                                                                                                                                                                                                         |
| :------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ORGANIZER** | Must hold a valid, non-expired, non-revoked JWT with claim `role = 'ORGANIZER'` and an `org` claim identifying their organization ID. The caller account must be active (`is_active = true`). Target members must belong to the caller's organization (`organization_id = jwt.org`). |

## 3. Functional Requirements

### 3.1 List Organization Members

- **FR-01:** The system SHALL return a cursor-paginated list of users whose `organization_id` matches the caller's `org` claim, strictly excluding the caller themselves from the result set.
- **FR-02:** The system SHALL support filtering the member list by `role` (`ORGANIZER`, `STAFF`, or `STUDENT`).
- **FR-03:** The system SHALL support filtering the member list by active status (`isActive = true` or `false`).
- **FR-04:** The system SHALL support a `search` keyword parameter that performs a case-insensitive partial match (`ILIKE`) across `full_name`, `email`, and `phone_number`.
- **FR-05:** The system SHALL allow combining multiple filters (`role`, `isActive`, `search`) conjunctively (logical `AND`).
- **FR-06:** The system SHALL order member list results by `created_at DESC`, then `id DESC` as a tie-breaker, to guarantee deterministic cursor pagination.
- **FR-07:** The system SHALL default pagination limit to 20 items per page, allow a maximum limit of 100, and reject requests exceeding 100 with `400 Bad Request`.
- **FR-08:** The list query SHALL be routed to the **PostgreSQL read replica** to preserve primary database write capacity.

### 3.2 View Member Detail

- **FR-09:** The system SHALL return the full profile of a single member identified by `userId`, provided the member belongs to the caller's organization (`organization_id = jwt.org`).
- **FR-10:** The system SHALL return `404 Not Found` (`urn:unihub:error:not-found`) when the target member does not exist or belongs to a different organization, preventing cross-organization user enumeration.
- **FR-11:** The detail query SHALL be routed to the **PostgreSQL read replica**.

### 3.3 Provision Member Account

- **FR-12:** The system SHALL allow an `ORGANIZER` to provision a new user account with role `STAFF` or `ORGANIZER` within their own organization (`organization_id = jwt.org`).
- **FR-13:** The system SHALL reject provisioning requests specifying role `STUDENT` with `422 Unprocessable Content` (`urn:unihub:error:student-provisioning-forbidden`).
- **FR-14:** The system SHALL reject provisioning requests with `409 Conflict` (`urn:unihub:error:conflict`) if the submitted email already exists in the system (globally unique across all organizations).
- **FR-15:** The system SHALL auto-generate a cryptographically secure random temporary password (minimum 16 characters, mixed character classes) and store its Argon2id hash.
- **FR-16:** The new member account SHALL be created with `is_active = true` and `is_temporary_password = true`.
- **FR-17:** Upon successful account creation, the system SHALL publish a `UserProvisionedEvent` to the transactional outbox (`event_publication` table) externalized to RabbitMQ (`unihub.events::notifications`). The event payload SHALL include `userId`, `organizationId`, `orgSlug`, `email`, `fullName`, `role`, and the plaintext `temporaryPassword` for delivery via email.
- **FR-18:** The provisioning write operation SHALL be routed to the **PostgreSQL primary** database.

### 3.4 Update Member Active Status (Ban / Unban)

- **FR-19:** The system SHALL allow an `ORGANIZER` to update the `is_active` status of a member in their organization to `true` or `false`.
- **FR-20:** The system SHALL reject attempts by an `ORGANIZER` to modify their own active status with `403 Forbidden` (`urn:unihub:error:self-status-modification-forbidden`).
- **FR-21:** When setting `is_active = false` (banning a user):
  - The system SHALL first add all active JWT JTIs for the target user to the Redis token blacklist.
  - The system SHALL remove all active session records from the Redis session tracker.
  - The system SHALL bulk-revoke all active `refresh_tokens` rows in PostgreSQL (`revoked_at = now()`).
  - The system SHALL set `users.is_active = false` in PostgreSQL.
  - The system SHALL publish a `UserStatusChangedEvent` (carrying `userId`, `organizationId`, `email`, `fullName`, `isActive = false`, and `occurredAt`) to the transactional outbox (`event_publication` table) externalized to RabbitMQ (`unihub.events::notifications`) so that the notifications worker can deliver an email notification to the banned member.
  - This sequence SHALL be strictly enforced: Redis revocation MUST succeed before database modifications are executed. If Redis is unavailable, the operation SHALL fail with `503 Service Unavailable` (`urn:unihub:error:service-unavailable`) without modifying database records.
- **FR-22:** When setting `is_active = true` (unbanning a user), the system SHALL set `users.is_active = true` in PostgreSQL and publish a `UserStatusChangedEvent` (`isActive = true`) to the transactional outbox for notification delivery. No Redis session manipulation is performed.
- **FR-23:** When the requested `is_active` value matches the member's current status, the operation SHALL succeed as an idempotent no-op, returning `200 OK` without redundant Redis calls or database updates.
- **FR-24:** The status update operation SHALL be routed to the **PostgreSQL primary** database.

### 3.5 Authorization & Security Enforcement

- **FR-25:** The system SHALL reject all four endpoints with `401 Unauthorized` (`urn:unihub:error:unauthorized`) if the JWT is missing, expired, malformed, or blacklisted.
- **FR-26:** The system SHALL reject all four endpoints with `403 Forbidden` (`urn:unihub:error:forbidden`) if the caller's role is not `ORGANIZER`.
- **FR-27:** The system SHALL reject all four endpoints with `403 Forbidden` (`urn:unihub:error:forbidden`) if the caller account is inactive (`is_active = false`).
- **FR-28:** For member-specific endpoints (FR-09, FR-19), the system SHALL verify that the target user's `organization_id` equals the caller's `org` claim before performing any operation.

## 4. Business Rules

- **BR-01 (Tenant Boundary):** An `ORGANIZER` may only inspect and manage members belonging to their own organization (`jwt.org`). Cross-tenant access is prohibited and must return `404 Not Found` (to avoid leaking user existence).
- **BR-02 (Role Provisioning Restriction):** The `STUDENT` role cannot be provisioned via organization management. Student accounts are exclusively self-registered via `POST /api/v1/auth/register`.
- **BR-03 (Global Email Uniqueness):** Email addresses are globally unique across all organizations (`users.email` unique constraint). An email already in use cannot be re-registered or provisioned in any organization.
- **BR-04 (Zero-Trust Temporary Password):** Newly provisioned members are created active (`is_active = true`) with a mandatory password reset flag (`is_temporary_password = true`). The plaintext password is never stored in the database and never returned in API responses; it is delivered solely via outbox notification event.
- **BR-05 (Self-Protection):** An `ORGANIZER` cannot ban or deactivate their own account. Self-modification of `is_active` is forbidden.
- **BR-06 (Fail-Closed Ban Order & Notification):** When deactivating an account, Redis session invalidation (JTI blacklist + session tracker removal) must complete successfully before database tokens are revoked and `users.is_active` is set to `false`. If Redis fails, the entire ban operation fails immediately. Upon successful status update (ban or unban), a `UserStatusChangedEvent` is published to the transactional outbox (`unihub.events::notifications`) to deliver an asynchronous email notification to the user.
- **BR-07 (Database Routing):** Read operations (list, detail) route to the read replica (`@Transactional(readOnly = true)`). Write operations (provision, status update) route to the primary database (`@Transactional`).
- **BR-08 (Natural Idempotency):** Mutating operations rely on natural idempotency:
  - Account provisioning is guarded by the unique `email` constraint. Concurrent or repeated calls with the same email return `409 Conflict`.
  - Status updates are idempotent state assignments. Submitting the current `is_active` value returns `200 OK` as a no-op.
- **BR-09 (Deterministic Cursor Pagination):** Cursors encode `(created_at, id)` as Base64 JSON. The tie-breaker `id DESC` guarantees stable pagination order even when multiple members share identical timestamps.

## 5. State Transitions

The state machine manages the `users.is_active` attribute for organization members:

| From State | Event/Trigger                                          | To State   | Valid? | Rejection Behavior (if invalid)                                                                                                                                                            |
| :--------- | :----------------------------------------------------- | :--------- | :----: | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| _None_     | Account Provisioned (FR-12)                            | `ACTIVE`   |  Yes   | N/A — created with `is_active = true`, `is_temporary_password = true`                                                                                                                      |
| `ACTIVE`   | PATCH status `{ "isActive": false }` on another member | `INACTIVE` |  Yes   | Redis JTI blacklist $\rightarrow$ session tracker removal $\rightarrow$ DB refresh tokens revoked $\rightarrow$ `users.is_active = false` $\rightarrow$ `UserStatusChangedEvent` published |
| `ACTIVE`   | PATCH status `{ "isActive": false }` on self           | `ACTIVE`   |   No   | `403 Forbidden` (`urn:unihub:error:self-status-modification-forbidden`)                                                                                                                    |
| `ACTIVE`   | PATCH status `{ "isActive": true }` (no-op)            | `ACTIVE`   |  Yes   | Idempotent `200 OK` without DB write or Redis action                                                                                                                                       |
| `INACTIVE` | PATCH status `{ "isActive": true }`                    | `ACTIVE`   |  Yes   | Database update `users.is_active = true`                                                                                                                                                   |
| `INACTIVE` | PATCH status `{ "isActive": false }` (no-op)           | `INACTIVE` |  Yes   | Idempotent `200 OK` without DB write or Redis action                                                                                                                                       |
| Any        | Any operation targeting user in different org          | Unchanged  |   No   | `404 Not Found` (`urn:unihub:error:not-found`)                                                                                                                                             |
| Any        | Any operation targeting non-existent user              | Unchanged  |   No   | `404 Not Found` (`urn:unihub:error:not-found`)                                                                                                                                             |
| Any        | Non-ORGANIZER caller executes operation                | Unchanged  |   No   | `403 Forbidden` (`urn:unihub:error:forbidden`)                                                                                                                                             |

## 6. Non-Functional Requirements

- **Performance:**
  - List members endpoint (FR-01) must respond in under 100 ms at p95 for organizations with up to 10,000 members when using cursor pagination and read replica routing.
- **Concurrency:**
  - The ban operation must not span Redis and PostgreSQL in an open distributed transaction. Redis operations run first; PostgreSQL update follows upon success.
- **Idempotency:**
  - `POST /api/v1/organizations/users` enforces uniqueness via `users.email` index.
  - `PATCH /api/v1/organizations/users/{userId}/status` is inherently idempotent.
- **Rate-limiting:**
  - Standard authenticated rate limit applies via Bucket4j filter (`100 requests / minute / user`).
- **Security:**
  - Role-Based Access Control: All endpoints strictly require `ROLE_ORGANIZER`.
  - Organization boundary: Scoped strictly to `jwt.org`.
  - Temporary passwords must be generated using `SecureRandom` and hashed using Argon2id (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).
- **Reliability / Failure modes:**
  - Event publication for account provisioning must use Spring Modulith Transactional Outbox. If the database transaction fails, the event is rolled back.
  - If Redis is down during a ban request, the system must fail closed (`503 Service Unavailable`) and leave the user active in PostgreSQL.
- **Observability:**
  - Structured audit logging for account creation and ban/unban events, including caller ID, target user ID, organization ID, and timestamp.
  - All responses must include `traceId` and `processedBy` in the standard metadata envelope.

## 7. Acceptance Criteria

### Scenario 1: List organization members across roles successfully

```gherkin
Given an authenticated ORGANIZER with user ID "org-admin-1" and org ID "org-100"
And organization "org-100" has members:
  | id  | fullName      | role      | isActive |
  | u-1 | Alice Staff   | STAFF     | true     |
  | u-2 | Bob Organizer | ORGANIZER | true     |
  | u-3 | Charlie Stud  | STUDENT   | true     |
  | u-4 | Diana Staff   | STAFF     | false    |
When the ORGANIZER calls GET /api/v1/organizations/users with no query parameters
Then the response status is 200 OK
And the response envelope has success = true, message = "Organization members retrieved"
And the data contains items for u-1, u-2, u-3, and u-4
And "org-admin-1" is not present in the items list
And the pagination metadata contains cursor.hasMore = false
```

### Scenario 2: Filter member list by role and active status

```gherkin
Given the same ORGANIZER from Scenario 1
When the ORGANIZER calls GET /api/v1/organizations/users?role=STUDENT&isActive=true
Then the response status is 200 OK
And the data items contain only u-3 ("Charlie Stud")
And u-1, u-2, and u-4 are excluded
```

### Scenario 3: Search members with partial keyword match

```gherkin
Given an active member with full_name = "Nguyen Van Binh" and email = "binh.nv@example.com"
When the ORGANIZER calls GET /api/v1/organizations/users?search=van+binh
Then the response status is 200 OK
And the data items include "Nguyen Van Binh"
```

### Scenario 4: Cursor-based pagination on member list

```gherkin
Given organization "org-100" has 25 members (excluding the caller)
When the ORGANIZER calls GET /api/v1/organizations/users?limit=20
Then the response status is 200 OK
And data.items contains exactly 20 items
And data.cursor.hasMore is true
And data.cursor.nextCursor is a valid non-empty Base64 string
When the ORGANIZER calls GET /api/v1/organizations/users?limit=20&cursor=<nextCursor>
Then the response status is 200 OK
And data.items contains the remaining 5 items
And data.cursor.hasMore is false
And data.cursor.nextCursor is null
```

### Scenario 5: Reject invalid pagination limit

```gherkin
When the ORGANIZER calls GET /api/v1/organizations/users?limit=150
Then the response status is 400 Bad Request
And error.type is "urn:unihub:error:bad-request"
```

### Scenario 6: View member detail successfully

```gherkin
Given a STAFF member "u-1" in organization "org-100"
When the ORGANIZER calls GET /api/v1/organizations/users/u-1
Then the response status is 200 OK
And the response envelope has success = true, message = "Member details retrieved"
And data.id is "u-1"
And data.email is the email of u-1
And data.organizationId is "org-100"
```

### Scenario 7: View member belonging to another organization

```gherkin
Given a user "u-99" belonging to organization "org-200"
When the ORGANIZER of "org-100" calls GET /api/v1/organizations/users/u-99
Then the response status is 404 Not Found
And error.type is "urn:unihub:error:not-found"
And the error response does not disclose that u-99 exists in another organization
```

### Scenario 8: Provision a new STAFF member successfully

```gherkin
Given an authenticated ORGANIZER of organization "org-100" with slug "tech-club"
When the ORGANIZER calls POST /api/v1/organizations/users with body:
  """
  {
    "email": "staff.member@techclub.org",
    "fullName": "Staff Member",
    "phoneNumber": "+84901234567",
    "role": "STAFF"
  }
  """
Then the response status is 201 Created
And the response envelope has success = true, message = "Member provisioned successfully"
And data.email is "staff.member@techclub.org"
And data.role is "STAFF"
And data.isActive is true
And data.isTemporaryPassword is true
And the response body does NOT contain the plaintext password
And a UserProvisionedEvent is published to the transactional outbox with the plaintext temporary password
```

### Scenario 9: Reject provisioning with STUDENT role

```gherkin
When the ORGANIZER calls POST /api/v1/organizations/users with role = "STUDENT"
Then the response status is 422 Unprocessable Content
And error.type is "urn:unihub:error:student-provisioning-forbidden"
And no user record is created
```

### Scenario 10: Reject provisioning with duplicate email

```gherkin
Given a user with email "existing@example.com" already exists in the system
When the ORGANIZER calls POST /api/v1/organizations/users with email = "existing@example.com"
Then the response status is 409 Conflict
And error.type is "urn:unihub:error:conflict"
And no new user record is created
```

### Scenario 11: Ban member successfully (fail-closed flow)

```gherkin
Given an active member "u-1" with 2 active JWT sessions
When the ORGANIZER calls PATCH /api/v1/organizations/users/u-1/status with body:
  """
  { "isActive": false }
  """
Then both JWT JTIs are added to the Redis blacklist
And both session records are removed from the Redis session tracker
And all active refresh tokens for u-1 are revoked in the database
And users.is_active is updated to false in PostgreSQL
And a UserStatusChangedEvent is published to the transactional outbox with isActive = false
And the response status is 200 OK
And the response envelope has success = true, message = "Member status updated"
And data.isActive is false
```

### Scenario 12: Ban member fails when Redis is unavailable

```gherkin
Given Redis is unreachable
When the ORGANIZER calls PATCH /api/v1/organizations/users/u-1/status with isActive = false
Then the response status is 503 Service Unavailable
And error.type is "urn:unihub:error:service-unavailable"
And Retry-After header is present
And users.is_active for u-1 remains true in PostgreSQL
And refresh tokens for u-1 remain active
```

### Scenario 13: Reject self-ban attempt

```gherkin
Given the caller's user ID is "org-admin-1"
When the ORGANIZER calls PATCH /api/v1/organizations/users/org-admin-1/status with isActive = false
Then the response status is 403 Forbidden
And error.type is "urn:unihub:error:self-status-modification-forbidden"
```

### Scenario 14: Unban an inactive member

```gherkin
Given an inactive member "u-4" with is_active = false
When the ORGANIZER calls PATCH /api/v1/organizations/users/u-4/status with isActive = true
Then users.is_active is updated to true in PostgreSQL
And no Redis operations are performed
And the response status is 200 OK
And data.isActive is true
```

### Scenario 15: Idempotent status update (no-op)

```gherkin
Given an inactive member "u-4" with is_active = false
When the ORGANIZER calls PATCH /api/v1/organizations/users/u-4/status with isActive = false
Then the response status is 200 OK
And data.isActive is false
And no database update or Redis operation is executed
```

### Scenario 16: Reject unauthenticated caller

```gherkin
When any endpoint under /api/v1/organizations/users is requested without a valid Bearer token
Then the response status is 401 Unauthorized
And error.type is "urn:unihub:error:unauthorized"
```

### Scenario 17: Reject caller with non-ORGANIZER role

```gherkin
Given an authenticated user with role "STAFF" or "STUDENT"
When the user requests any endpoint under /api/v1/organizations/users
Then the response status is 403 Forbidden
And error.type is "urn:unihub:error:forbidden"
```

## 8. Out of Scope

- **STUDENT Account Provisioning:** Students self-register via `POST /api/v1/auth/register` (Feature 1.1). Provisioning students via this feature is strictly blocked.
- **Bulk Member Import via CSV:** Batch user creation is handled separately in the administrative CSV ingestion pipeline (Domain 9 & 10).
- **Workshop Assignment Management:** Viewing or assigning staff to specific workshops is managed in Feature 1.6 (`workshop-staff-assignment`).
- **Organization Profile Management:** Updating organization metadata (name, slug, logo) is owned by organization administration features.
- **Member Password Reset / Recovery:** Forgotten passwords and password reset flows are handled in Feature 1.3 & 1.4. This feature only produces the initial temporary password.
- **Full-Text Search via Elasticsearch:** Search queries on member lists use PostgreSQL `ILIKE` on the read replica. Elasticsearch is dedicated to Domain 3 (Workshop Discovery & Search).

## 9. Linked References

| Document                    | Section(s) Consulted                                                                                                                                                               |
| :-------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md`   | Feature 1.5 entry (Domain 1, lines 90–104)                                                                                                                                         |
| `docs/system-design.md`     | §5 Identity & Access Management (Account Provisioning, Delegated Creation, Status Gating)                                                                                          |
| `docs/adrs.md`              | ADR-005 (JWT RS256 + Redis JTI Blacklist), ADR-006 (Multi-Tenant RBAC), ADR-015 (Primary-Replica Routing), ADR-028 (Zero-Trust Provisioning), ADR-030 (Spring Modulith Boundaries) |
| `docs/database-schema.md`   | Tables `users`, `refresh_tokens`; Replication Lag & Read-After-Write Consistency rules                                                                                             |
| `docs/api-standards.md`     | Versioning, Mandatory Headers, Success Envelope, Cursor Pagination, RFC 7807 Error Envelope, Standard Error Types                                                                  |
| `docs/project-structure.md` | Monorepo layout, IAM module Clean Architecture                                                                                                                                     |
