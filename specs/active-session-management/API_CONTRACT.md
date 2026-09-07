# API Contract: Active Session Management

> **Status:** APPROVED  
> **Spec:** `specs/active-session-management/SPEC.md`  
> **Last revised:** 2026-09-07

## 1. Overview

This contract specifies the HTTP REST interface for the **Active Session Management** feature (Domain 1: Identity & Access Management, Feature 1.7). It defines the endpoints under `/api/v1/auth/sessions` that allow authenticated callers to query their active login sessions with keyset cursor pagination and device details, revoke a specific session remotely, or revoke all other remote sessions while maintaining their active session uninterrupted.

## 2. Global Standards Applied

- **URI structure / versioning:** URI path versioning with `/api/v1/...` prefix conforming to `docs/api-standards.md`.
- **Pagination convention:** Keyset cursor-based pagination using `cursor` (Base64-encoded JSON) and `size` (default 20, max cap 100) query parameters.
- **Success response shape:** Unified `ApiResponse<T>` envelope with `success`, `message`, `data`, and `metadata` (`processedBy`, `traceId`). All null fields dropped via non-null serialization.
- **Error response format:** RFC 7807 Problem Details wrapped inside the standard response envelope with `success = false`, `error` object (`type`, `title`, `status`, `detail`, `instance`), and `metadata`.
- **Idempotency-key convention:** Not required on read-only `GET` or standard REST `DELETE` operations.
- **Auth header convention:** Mandatory `Authorization: Bearer <accessToken>` on all endpoints.
- **Date/time formatting convention:** ISO 8601 UTC string format with trailing `Z` (e.g., `2026-09-04T10:00:00Z`).

## 3. Endpoints

### GET /api/v1/auth/sessions

**Description:** Retrieves a cursor-paginated list of active, non-expired, and non-revoked sessions belonging to the calling user, with an indicator for the current session.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — Read-only query.

#### Request

**Headers:**

| Header          | Required | Description                                                |
| :-------------- | :------: | :--------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub` and `sid` claims. |

**Path Parameters:** None.

**Query Parameters:**

| Parameter | Type    | Required | Default | Description                                                                             |
| :-------- | :------ | :------: | :-----: | :-------------------------------------------------------------------------------------- |
| `cursor`  | String  |    No    | `null`  | Base64-encoded keyset cursor string representing `{ "createdAt": "...", "id": "..." }`. |
| `size`    | Integer |    No    |  `20`   | Requested page size. Must be an integer between 1 and 100 inclusive.                    |

**Request Body:** None.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Active sessions retrieved",
  "data": {
    "items": [
      {
        "id": "7b123456-1234-1234-1234-1234567890ab",
        "deviceInfo": "Chrome 120 on Windows 11",
        "ipAddress": "14.232.208.12",
        "createdAt": "2026-09-04T10:00:00Z",
        "isCurrentSession": true
      },
      {
        "id": "8c234567-2345-2345-2345-2345678901bc",
        "deviceInfo": "UniHub Mobile App on iPhone 15 Pro",
        "ipAddress": "118.69.182.40",
        "createdAt": "2026-09-03T18:30:00Z",
        "isCurrentSession": false
      }
    ],
    "cursor": {
      "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTA5LTAzVDE4OjMwOjAwWiIsImlkIjoiOGMyMzQ1NjctMjM0NS0yMzQ1LTIzNDUtMjM0NTY3ODkwMWJjIn0=",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Descriptions (`data.items`):**

- `id` (UUID): Unique session identifier (maps to `refresh_tokens.id`).
- `deviceInfo` (String, nullable): User-Agent or client device description.
- `ipAddress` (String, nullable): Originating IP address recorded when session was established.
- `createdAt` (String): ISO 8601 UTC timestamp when session was initialized.
- `isCurrentSession` (Boolean): `true` if this session corresponds to the `sid` claim in the caller's active JWT, `false` otherwise.

**Error Responses:**

| Status                      | Error Type (`error.type`)       | Title                 | Condition                                                                                              |
| :-------------------------- | :------------------------------ | :-------------------- | :----------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`  | Bad Request           | The `cursor` query parameter is malformed / invalid Base64, or `size` is outside the range [1, 100].   |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized` | Unauthorized          | Missing Bearer token, invalid token signature, expired token, or token revoked in Redis JTI blacklist. |
| `500 Internal Server Error` | `urn:unihub:error:internal`     | Internal Server Error | Unexpected server failure.                                                                             |

#### Side Effects

None.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-18, FR-20, Scenario 1, Scenario 2.

### DELETE /api/v1/auth/sessions/{id}

**Description:** Terminates a specific active session belonging to the authenticated caller and immediately blacklists its active access token.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — Standard REST resource deletion semantics.

#### Request

**Headers:**

| Header          | Required | Description                                                |
| :-------------- | :------: | :--------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub` and `sid` claims. |

**Path Parameters:**

| Parameter | Type | Required | Description                                    |
| :-------- | :--- | :------: | :--------------------------------------------- |
| `id`      | UUID |   Yes    | Unique identifier of the session to terminate. |

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 204 No Content:** _Empty body (Status 204 No Content)._

**Error Responses:**

| Status                      | Error Type (`error.type`)              | Title                 | Condition                                                                                                                                                                    |
| :-------------------------- | :------------------------------------- | :-------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | The `id` path variable is not a well-formed UUID string.                                                                                                                     |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing Bearer token, invalid token signature, expired token, or token revoked in Redis JTI blacklist.                                                                       |
| `404 Not Found`             | `urn:unihub:error:not-found`           | Not Found             | Session does not exist, belongs to another user, or has already been revoked (`revoked_at IS NOT NULL`).                                                                     |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Service Unavailable   | Centralized Redis blacklist is unreachable (fail-closed per ADR-010). Response includes `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30s). |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Internal Server Error | Unexpected server failure.                                                                                                                                                   |

#### Side Effects

1. Sets `revoked_at = NOW()` on the target `refresh_tokens` record in PostgreSQL primary database.
2. If `{id}` equals the caller's `sid`: Writes the current access token's `jti` to Redis blacklist (`jti:blacklisted:<jti>`) with remaining TTL (`exp - NOW()`).
3. If `{id}` is a remote session: Resolves the remote session's active token `jti` from Redis mapping, writes it to the Redis blacklist, and deletes the temporary session mapping.

#### Spec Traceability

- **Satisfies:** FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-18, FR-19, FR-20, Scenario 3, Scenario 4, Scenario 5, Scenario 7, Scenario 8.

### DELETE /api/v1/auth/sessions

**Description:** Terminates all active sessions belonging to the authenticated caller with the strict exception of the caller's current session.  
**Required Role / Auth:** Authenticated (`STUDENT`, `STAFF`, or `ORGANIZER`)  
**Idempotency Requirement:** Not Required — Standard bulk deletion semantics.

#### Request

**Headers:**

| Header          | Required | Description                                                |
| :-------------- | :------: | :--------------------------------------------------------- |
| `Authorization` |   Yes    | Bearer JWT access token containing `sub` and `sid` claims. |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None.

#### Response

**Success — 204 No Content:** _Empty body (Status 204 No Content)._

**Error Responses:**

| Status                      | Error Type (`error.type`)              | Title                 | Condition                                                                                                                                                                    |
| :-------------------------- | :------------------------------------- | :-------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `400 Bad Request`           | `urn:unihub:error:bad-request`         | Bad Request           | The calling JWT does not contain a valid, resolvable session identifier (`sid`) claim required to preserve the current session.                                              |
| `401 Unauthorized`          | `urn:unihub:error:unauthorized`        | Unauthorized          | Missing Bearer token, invalid token signature, expired token, or token revoked in Redis JTI blacklist.                                                                       |
| `503 Service Unavailable`   | `urn:unihub:error:service-unavailable` | Service Unavailable   | Centralized Redis blacklist is unreachable (fail-closed per ADR-010). Response includes `Retry-After` header (configurable via `app.http.retry-after-seconds`, default 30s). |
| `500 Internal Server Error` | `urn:unihub:error:internal`            | Internal Server Error | Unexpected server failure.                                                                                                                                                   |

#### Side Effects

1. Updates all active `refresh_tokens` records in PostgreSQL primary database where `user_id = currentUserId AND id != sid AND revoked_at IS NULL` by setting `revoked_at = NOW()`.
2. Resolves and blacklists all active token `jti`s for the affected remote sessions in Redis for their remaining lifetimes.
3. The caller's current session record and active access token remain active and unrevoked.

#### Spec Traceability

- **Satisfies:** FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, Scenario 6, Scenario 8.

## 4. Rate Limiting Behavior

All three endpoints are non-registration endpoints and are governed by the platform's standard **Browsing Tier** Bucket4j Token Bucket filter backed by centralized Redis counters (per ADR-019 and the system's 2-tier rate limit configuration):

- **Tier Classification:** Browsing Tier (all endpoints under `/api/v1/auth/sessions`).
- **Bucket Keying:** Keyed by authenticated user identifier (`jwt.sub`).
- **Bucket Configuration:** Configuration-driven via `rate-limit.browsing-tier` in `application.yml` (default: capacity 100 tokens, refill 100 tokens per 60 seconds).
- **On Limit Exceeded:** Returns HTTP `429 Too Many Requests` with RFC 7807 problem type `urn:unihub:error:rate-limited`, standard response headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Retry-After`, and `Retry-After`.
- **Fail-Open Policy:** If Redis is unavailable for rate limiting checks, the filter operates in fail-open mode, allowing the request through and emitting telemetry (ADR-010 / ADR-019). Note that this is strictly distinct from token blacklist validation and revocation, which operate in **fail-closed** mode.

## 5. Idempotency Behavior

- `GET /api/v1/auth/sessions`: Naturally idempotent and safe (read-only).
- `DELETE /api/v1/auth/sessions/{id}`: Idempotent in outcome; once the target session has been revoked, repeated calls with the same identifier return `404 Not Found` without altering system state.
- `DELETE /api/v1/auth/sessions`: Calling bulk revocation repeatedly produces the same end state (zero other active sessions remaining), returning `204 No Content`.

## 6. Cross-Endpoint Consistency Notes

- **Cursor Structure:** The pagination container `data.cursor` uses the exact same keyset schema (`nextCursor`, `hasMore`) established in `docs/api-standards.md` and feature `ingestion-reject-report`.
- **Envelope Standardization:** Both success and error payloads adhere to the standard `ApiResponse` envelope pattern without custom top-level keys.
- **Error Types:** All error representations use unified `urn:unihub:error:*` URIs (`bad-request`, `unauthorized`, `not-found`, `rate-limited`, `service-unavailable`, `internal`).
- **Session ID Schema:** Session IDs are exposed strictly as canonical lowercase UUID strings across listing and revocation paths.
