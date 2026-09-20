# Spec: Active Session Management

> **Status:** APPROVED  
> **Domain:** 1 — Identity & Access Management  
> **Owner/area:** `docs/feature-catalog.md §1.7`, `iam` module  
> **Last revised:** 2026-09-07 — first version

## 1. Overview

This feature provides authenticated users across all roles (`STUDENT`, `STAFF`, `ORGANIZER`) with the capability to view and revoke their active login sessions. Each authenticated session corresponds to an active refresh token record holding client device information and IP address for user identification. Users can inspect all their non-expired, non-revoked sessions via keyset cursor pagination with an explicit indicator for the caller's current session, terminate a specific suspicious or stale session remotely, or revoke all other remote sessions in a single operation while retaining their active session. Token invalidation is enforced immediately across distributed stateless backend instances through a centralized token blacklist, while database revocation ensures terminated sessions cannot perform further token rotations.

## 2. Actors & Preconditions

### Actors

| Actor       | Description                                                                            |
| :---------- | :------------------------------------------------------------------------------------- |
| `STUDENT`   | Authenticated student user inspecting or revoking their active web/mobile sessions.    |
| `STAFF`     | Authenticated staff user inspecting or revoking their active check-in or web sessions. |
| `ORGANIZER` | Authenticated event organizer inspecting or revoking their active management sessions. |

### Preconditions

| Actor / Context      | Precondition                                                                                                                                                                           |
| :------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authenticated Caller | The user possesses a valid, unexpired, and non-revoked RS256-signed JWT access token containing a `sub` (User ID), `jti` (Token ID), `exp` (Expiration), and `sid` (Session ID) claim. |
| Shared Redis         | The centralized Redis cluster is operational for token revocation checks and temporary session-to-token identifier mappings.                                                           |
| Primary Database     | The primary PostgreSQL database is operational and accessible for strongly consistent session reads and updates.                                                                       |

## 3. Functional Requirements

### Session Listing

- **FR-01:** The system SHALL return a paginated collection of active sessions belonging exclusively to the authenticated user upon request.
- **FR-02:** The system SHALL filter session records to include only those where revocation timestamp is absent (`revoked_at IS NULL`) and expiration timestamp is in the future (`expires_at > NOW()`).
- **FR-03:** The system SHALL support keyset cursor-based pagination using the combination of session creation timestamp (`created_at`) and unique session identifier (`id`), ordered descending (`created_at DESC, id DESC`). Any leading or trailing whitespace in the `cursor` query parameter SHALL be automatically trimmed prior to Base64 and JSON decoding.
- **FR-04:** The system SHALL default pagination page size to 20 items when not specified, and SHALL enforce a strict upper limit cap of 100 items per request.
- **FR-05:** The system SHALL evaluate and include a boolean indicator `isCurrentSession = true` for the session matching the `sid` claim present in the caller's active access token, and `isCurrentSession = false` for all other sessions.
- **FR-06:** The system SHALL expose only non-sensitive device metadata in each session item: unique session identifier (`id`), device details (`deviceInfo`), originating IP address (`ipAddress`), session establishment timestamp (`createdAt`), and the current session flag (`isCurrentSession`).

### Single Session Revocation

- **FR-07:** The system SHALL allow an authenticated user to revoke a specific session by providing its unique session identifier (`id`).
- **FR-08:** The system SHALL verify that the specified session exists, belongs to the authenticated user (`user_id = currentUserId`), and is currently active (`revoked_at IS NULL`).
- **FR-09:** The system SHALL reject revocation with `404 Not Found` when the specified session does not exist, belongs to another user, or has already been revoked.
- **FR-10:** The system SHALL update the session record by setting the revocation timestamp to the current UTC timestamp (`revoked_at = NOW()`) in the primary database.
- **FR-11:** When the session being revoked matches the caller's current session (`id == sid`), the system SHALL extract the current access token's `jti` and write it to the blacklist with a Time-To-Live (TTL) equal to the token's remaining validity duration (`exp - NOW()`).
- **FR-12:** When the session being revoked is a remote session (`id != sid`), the system SHALL invalidate any active access token associated with that session in the blacklist using its remaining validity duration, and remove any temporary session-to-token mapping.

### Bulk Revocation (Revoke All Other Sessions)

- **FR-13:** The system SHALL allow an authenticated user to revoke all active sessions belonging to that user with the strict exception of the caller's current session (`id != sid`).
- **FR-14:** The system SHALL identify the caller's current session using the `sid` claim in the presented access token and reject the bulk revocation request with `400 Bad Request` if the `sid` claim is missing or unresolvable.
- **FR-15:** The system SHALL atomically update all active sessions belonging to the user (`user_id = currentUserId AND id != sid AND revoked_at IS NULL`) by setting `revoked_at = NOW()` in the primary database.
- **FR-16:** The system SHALL blacklist the active token identifiers (`jti`) for each revoked remote session in the centralized blacklist for their remaining validity durations.
- **FR-17:** The system SHALL preserve the caller's current session unmodified in both the database and the active cache, ensuring the caller remains logged in without interruption.

### Security & Reliability Protection

- **FR-18:** The system SHALL reject any incoming request presenting a JWT whose `jti` claim is present in the blacklist with `401 Unauthorized`.
- **FR-19:** The system SHALL enforce a fail-closed policy (ADR-010): if the blacklist storage is unreachable during any revocation operation, the system SHALL abort the operation, refrain from modifying the database, and return `503 Service Unavailable` with a `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30).
- **FR-20:** The system SHALL route all session queries (listing) and mutation commands (revocations) strictly to the primary database to eliminate replication lag anomalies (ADR-015).

## 4. Business Rules

- **BR-01 (Strict User Boundary):** A user can only inspect and revoke sessions that belong to their own account (`user_id = currentUserId`). Under no circumstances may a user view or terminate another user's session through these endpoints.
- **BR-02 (Current Session Immunity during Bulk Revocation):** The bulk revocation action (`DELETE /api/v1/auth/sessions`) must guarantee that the calling session is never terminated. The calling session remains active until explicit single-session logout or natural expiration.
- **BR-03 (Session Immutability Post-Revocation):** A revoked session (`revoked_at IS NOT NULL`) is in a terminal state. It cannot be reactivated, updated, or used to issue fresh access tokens via token rotation.
- **BR-04 (Blacklist TTL Pruning):** Blacklisted token identifiers (`jti`) must be persisted with a TTL strictly matching the remaining lifetime of the access token (`exp - NOW()`). Once an access token reaches its natural expiration, the blacklist entry expires automatically, bounding memory usage.
- **BR-05 (Fail-Closed Token Invalidation):** In alignment with ADR-010, the blacklist check and write operations are security-critical controls. Service unavailability takes precedence over allowing potentially revoked credentials to linger.
- **BR-06 (Information Disclosure Prevention):** Attempting to access or delete a session that does not exist, belongs to another user, or was previously revoked must produce identical `404 Not Found` responses to prevent ID enumeration.

## 5. State Transitions

### Session Entity Lifecycle

```text
[ Non-Existent ]
       │
       ▼ (Login / Refresh / Temp Password Exchange)
   [ ACTIVE ] ───────────── (Natural TTL Expiry) ─────────────► [ EXPIRED ] (Terminal)
       │
       ├──────────────► (Explicit Single Revoke: FR-07) ──────► [ REVOKED ] (Terminal)
       │
       └──────────────► (Bulk Revoke Others: FR-13) ──────────► [ REVOKED ] (Terminal)
```

### Transition Matrix

| Current State | Trigger / Event                                          | Target State | Valid? | Rejection Behavior                                                                                                                          |
| :------------ | :------------------------------------------------------- | :----------- | :----: | :------------------------------------------------------------------------------------------------------------------------------------------ |
| `ACTIVE`      | Query active sessions (`GET`)                            | `ACTIVE`     |  Yes   | Returned in paginated response with `isCurrentSession` flag.                                                                                |
| `ACTIVE`      | Revoke remote session (`DELETE /{id}`, `id != sid`)      | `REVOKED`    |  Yes   | `revoked_at` set to `NOW()`, remote `jti` blacklisted via Redis mapping, returns `204 No Content`.                                          |
| `ACTIVE`      | Revoke own current session (`DELETE /{id}`, `id == sid`) | `REVOKED`    |  Yes   | `revoked_at` set to `NOW()`, current `jti` blacklisted; caller is force-logged-out (subsequent requests → `401`). Returns `204 No Content`. |
| `ACTIVE`      | Revoke other sessions (`DELETE`)                         | `REVOKED`    |  Yes   | Remote sessions become `REVOKED`, current session remains `ACTIVE`, returns `204 No Content`.                                               |
| `ACTIVE`      | Natural time passage (`expires_at < NOW()`)              | `EXPIRED`    |  Yes   | Omitted from active sessions query; purged by TTL cleanup job.                                                                              |
| `REVOKED`     | Query active sessions (`GET`)                            | `REVOKED`    |   No   | Excluded from results by query filter (`revoked_at IS NULL`).                                                                               |
| `REVOKED`     | Revoke single session (`DELETE /{id}`)                   | `REVOKED`    |   No   | Rejected with `404 Not Found` (`urn:unihub:error:not-found`).                                                                               |
| `EXPIRED`     | Revoke single session (`DELETE /{id}`)                   | `EXPIRED`    |   No   | Rejected with `404 Not Found` (`urn:unihub:error:not-found`).                                                                               |

## 6. Non-Functional Requirements

- **Performance:** Keyset cursor pagination query execution time on the primary database must remain under 30ms at p95 under standard concurrent load.
- **Concurrency & Distributed Consistency:** The solution must be completely stateless at the application layer. Multiple backend server nodes operating behind the load balancer must observe identical session and revocation state without requiring sticky sessions.
- **Idempotency:** `DELETE` operations are idempotent from a system-state standpoint. Once revoked, subsequent revocation requests on the same session identifier predictably return `404 Not Found`.
- **Rate Limiting:** Session listing and revocation endpoints are non-registration endpoints and are governed by the **default tier** Token Bucket filter (`rate-limit.default-tier`, per ADR-019), keyed by authenticated user ID (`jwt.sub`) and operating in fail-open mode if Redis is unavailable (ADR-010).
- **Security:**
  - Strict Bearer token authentication required across all endpoints.
  - Access tokens bound to session records via cryptographic claims.
  - Fail-closed token blacklist write and validation.
  - Zero leakage of raw refresh token hashes or cross-user identifiers.
- **Reliability & Resilience:**
  - When the centralized Redis blacklist service is down, revocation returns `503 Service Unavailable` with a `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30) to prevent partial revocation states.
  - Driver-level fail-fast is enforced via Lettuce client configuration (`REJECT_COMMANDS`, `TimeoutOptions.enabled()`, `spring.data.redis.timeout: 2s`, `connect-timeout: 2s`), guaranteeing instant rejection (<1ms) when Redis is disconnected without blocking Tomcat HTTP request threads.
  - API Gateway (Nginx) reverse proxy explicitly excludes `http_503` from `proxy_next_upstream`, ensuring backend 503 responses with `Retry-After` headers pass directly to clients without triggering upstream retry loops.
  - Database queries are protected with connection lease timeouts via HikariCP.
- **Observability:**
  - Active session queries rely on structured application logging and standard HTTP status codes; internal Micrometer counters are omitted to minimize overhead and maintain Clean Architecture isolation in application use cases.
  - All operations log masked client IP and User-Agent metadata under structured logging format.

## 7. Acceptance Criteria

### Scenario 1: Retrieve active sessions successfully with current session indicator

```gherkin
Given an authenticated user "student.alice@university.edu" with user ID "u-101"
And the user has 2 active sessions in the database:
  | Session ID | Device Info                | IP Address    | Created At           |
  | s-001      | Chrome 120 on Windows 11   | 14.232.208.12 | 2026-09-04T10:00:00Z |
  | s-002      | UniHub Mobile on iPhone 15 | 118.69.182.40 | 2026-09-03T18:30:00Z |
And the user's access token contains session claim "sid" equal to "s-001"
When the user sends a GET request to "/api/v1/auth/sessions"
Then the response status is 200 OK
And the response envelope contains 2 items
And the item with ID "s-001" has "isCurrentSession" equal to true
And the item with ID "s-002" has "isCurrentSession" equal to false
And the cursor container indicates "hasMore" is false
```

### Scenario 2: Keyset cursor pagination over multiple active sessions

```gherkin
Given an authenticated user with 3 active sessions ordered by creation time descending:
  | Session ID | Created At           |
  | s-003      | 2026-09-05T12:00:00Z |
  | s-002      | 2026-09-04T10:00:00Z |
  | s-001      | 2026-09-03T08:00:00Z |
When the user sends a GET request to "/api/v1/auth/sessions?size=2"
Then the response status is 200 OK
And the response contains 2 items ("s-003", "s-002")
And "cursor.hasMore" is true
And "cursor.nextCursor" is a non-empty base64 string encoding the cursor for "s-002"
When the user sends a GET request to "/api/v1/auth/sessions?cursor=<nextCursor>&size=2"
Then the response status is 200 OK
And the response contains 1 item ("s-001")
And "cursor.hasMore" is false
And "cursor.nextCursor" is null
```

### Scenario 3: Revoke a specific remote session successfully

```gherkin
Given an authenticated user with current session "s-001" and remote session "s-002"
And remote session "s-002" is active in the database
And an active token identifier for "s-002" is mapped in Redis
When the user sends a DELETE request to "/api/v1/auth/sessions/s-002"
Then the response status is 204 No Content
And session "s-002" in the database has "revoked_at" set to approximately now
And the token identifier for "s-002" is stored in the Redis blacklist
And current session "s-001" remains active with "revoked_at" equal to null
```

### Scenario 4: Revoke caller's own current session explicitly

```gherkin
Given an authenticated user with current session "s-001"
And the caller presents a JWT with "sid" "s-001", "jti" "jti-001", and remaining TTL of 1800 seconds
When the user sends a DELETE request to "/api/v1/auth/sessions/s-001"
Then the response status is 204 No Content
And session "s-001" in the database has "revoked_at" set to approximately now
And "jti-001" is stored in the Redis blacklist with a TTL of 1800 seconds
And subsequent requests presenting "jti-001" are rejected with 401 Unauthorized
```

### Scenario 5: Revoke non-existent or foreign session is rejected

```gherkin
Given an authenticated user "u-101"
And session "s-999" belongs to user "u-202"
When the user sends a DELETE request to "/api/v1/auth/sessions/s-999"
Then the response status is 404 Not Found
And the error response type is "urn:unihub:error:not-found"
And session "s-999" remains unmodified
```

### Scenario 6: Revoke all other sessions preserves current session

```gherkin
Given an authenticated user with current session "s-001"
And the user has active remote sessions "s-002" and "s-003"
When the user sends a DELETE request to "/api/v1/auth/sessions"
Then the response status is 204 No Content
And sessions "s-002" and "s-003" have "revoked_at" set to approximately now
And active token identifiers for "s-002" and "s-003" are stored in the Redis blacklist
And current session "s-001" remains active with "revoked_at" equal to null
And the caller can continue making authenticated requests with their current token
```

### Scenario 7: Remote session is immediately rejected after revocation

```gherkin
Given remote session "s-002" was revoked by the user via "/api/v1/auth/sessions/s-002"
When the remote device attempts to call any protected endpoint with its access token
Then the system detects the token's "jti" in the Redis blacklist
And the response status is 401 Unauthorized
When the remote device attempts to call "/api/v1/auth/refresh" with its refresh token
Then the system detects the refresh token record has "revoked_at" set
And the response status is 401 Unauthorized
```

### Scenario 8: Revocation fails closed when Redis is unavailable

```gherkin
Given an authenticated user attempts to revoke session "s-002"
And the centralized Redis service is unreachable
When the user sends a DELETE request to "/api/v1/auth/sessions/s-002"
Then the response status is 503 Service Unavailable
And the response header "Retry-After" is present and reflects the configured duration (default "30")
And the error response type is "urn:unihub:error:service-unavailable"
And session "s-002" in the database remains active (not revoked)
```

## 8. Out of Scope

- Client-side WebSockets / Server-Sent Events for real-time notification to the terminated device (devices are rejected on their next network request via JTI blacklist and refresh token rejection).
- Geolocation IP reverse resolution or browser fingerprinting beyond raw `User-Agent` and client IP logging.
- Administrative revocation of another user's sessions by an Organizer (handled separately in `organization-member-management` F-1.5).
- Custom session naming / user-defined device aliases.

## 9. Linked References

- `docs/feature-catalog.md §1.7 — active-session-management`
- `docs/system-design.md §5 — Account Provisioning & Security (Active Session Tracking & Revocation)`
- `docs/system-design.md §5 — RBAC Model & Multi-Layer Enforcement (Decoded JWT Payload with sid claim)`
- `docs/database-schema.md §3 — Table: refresh_tokens`
- `docs/database-schema.md §4 — Replication Lag & Read-After-Write Consistency (Primary DB Routing)`
- `docs/api-standards.md — Keyset Cursor Pagination, RFC 7807 Problem Details, Status Codes`
- `docs/adrs.md — ADR 005 (JWT RS256 + sid claim + Redis JTI Revocation)`
- `docs/adrs.md — ADR 006 (Authorization Model & User Ownership Isolation)`
- `docs/adrs.md — ADR 010 (Redis Fail-Closed Mode for JTI Revocation)`
- `docs/adrs.md — ADR 015 (Primary-Replica Routing Strategy)`
