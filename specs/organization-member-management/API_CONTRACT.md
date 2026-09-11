# API Contract: Organization Member Management

> **Status:** APPROVED  
> **Spec:** `specs/organization-member-management/SPEC.md`  
> **Last revised:** 2026-09-09

## 1. Overview

This contract defines the four REST endpoints for Feature 1.5 (`organization-member-management`) within the `iam` module. All endpoints reside under the `/api/v1/organizations/users` resource path. The contract enables an authenticated `ORGANIZER` to list organization members across all roles (`ORGANIZER`, `STAFF`, `STUDENT`), retrieve member details, provision new staff or organizer accounts, and update member active status with immediate session invalidation upon deactivation.

## 2. Global Standards Applied

- **URI structure / versioning:** `/api/v1/organizations/users` conforming to `docs/api-standards.md §Versioning Strategy`.
- **Success response shape:** Standard JSON envelope (`success: true`, `message: string`, `data: object`, `metadata: { processedBy, traceId }`). Null properties are omitted via `@JsonInclude(NON_NULL)`.
- **Collection / Pagination envelope:** Cursor-based pagination inside `data` (`data: { items: [...], cursor: { nextCursor: string | null, hasMore: boolean } }`), conforming to `docs/api-standards.md §Success Response (List / Pagination)`.
- **Error response format:** RFC 7807 Problem Details wrapped in the standard envelope (`success: false`, `error: { type, title, status, detail, instance, validationErrors? }`, `metadata: { processedBy, traceId }`), conforming to `docs/api-standards.md §Error Response (4xx, 5xx)`.
- **Error type naming:** Standardized URN format `urn:unihub:error:<error-slug>`.
- **Auth header convention:** Mandatory `Authorization: Bearer <JWT>` header on all endpoints.
- **Content types:**
  - Requests: `Content-Type: application/json`
  - Success responses: `Content-Type: application/json`
  - Error responses: `Content-Type: application/problem+json`
- **Tracing headers:** Optional `X-Correlation-ID: <UUID>` accepted on requests and echoed as `metadata.traceId`.
- **Date/time formatting convention:** ISO 8601 UTC with trailing `Z` (e.g., `2026-09-09T14:30:00Z`).
- **Idempotency convention:** Not required for F-1.5. Mutating endpoints rely on natural idempotency (unique constraint on `email` for account creation, and idempotent state assignment for active status updates).

## 3. Endpoints

### 3.1. GET /api/v1/organizations/users

**Description:** Retrieves a cursor-paginated list of members belonging to the caller's organization, with optional filters for role, active status, and search keyword. Excludes the authenticated caller from the results.  
**Required Role / Auth:** `ORGANIZER`  
**Idempotency Requirement:** Not Required — read-only safe idempotent operation.  
**Database Routing:** PostgreSQL Read Replica (`@Transactional(readOnly = true)`).

#### Request

**Headers:**

| Header             | Required | Description                                                                  |
| :----------------- | :------: | :--------------------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT containing claims `role: 'ORGANIZER'` and `org: <organizationId>` |
| `X-Correlation-ID` |    No    | Client-generated UUID for distributed request tracing                        |
| `Accept-Language`  |    No    | Preferred locale for messages (default: `en`)                                |

**Query Parameters:**

| Parameter  | Type    | Required | Default | Constraints                                  | Description                                                                 |
| :--------- | :------ | :------: | :-----: | :------------------------------------------- | :-------------------------------------------------------------------------- |
| `cursor`   | string  |    No    |  none   | Base64 JSON `{"createdAt":"...","id":"..."}` | Opaque cursor returned from previous page. Absent on initial request.       |
| `limit`    | integer |    No    |  `20`   | Min 1, Max 100                               | Number of items to return per page. Values > 100 rejected with 400.         |
| `role`     | string  |    No    |  none   | `ORGANIZER`, `STAFF`, or `STUDENT`           | Filter by member role. Other values rejected with 400.                      |
| `isActive` | boolean |    No    |  none   | `true` or `false`                            | Filter by active status. Non-boolean values rejected with 400.              |
| `search`   | string  |    No    |  none   | Max 200 chars                                | Case-insensitive partial match across `full_name`, `email`, `phone_number`. |

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Organization members retrieved",
  "data": {
    "items": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440001",
        "email": "alice.staff@techclub.org",
        "fullName": "Alice Nguyen",
        "role": "STAFF",
        "phoneNumber": "+84901234567",
        "avatarUrl": null,
        "isActive": true,
        "isTemporaryPassword": false,
        "lastLoginAt": "2026-09-08T10:15:00Z",
        "createdAt": "2026-08-01T07:00:00Z"
      },
      {
        "id": "550e8400-e29b-41d4-a716-446655440002",
        "email": "charlie.student@techclub.org",
        "fullName": "Charlie Student",
        "role": "STUDENT",
        "phoneNumber": "+84909876543",
        "avatarUrl": null,
        "isActive": true,
        "isTemporaryPassword": false,
        "lastLoginAt": "2026-09-09T03:00:00Z",
        "createdAt": "2026-08-02T08:30:00Z"
      }
    ],
    "cursor": {
      "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTA4LTAyVDA4OjMwOjAwWiIsImlkIjoiNTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAyIn0=",
      "hasMore": true
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                                    |
| :----: | :------------------------------ | :-------------------- | :--------------------------------------------------------------------------- |
| `400`  | `urn:unihub:error:bad-request`  | Bad Request           | `limit` > 100, invalid `cursor` string, or invalid `role`/`isActive` values. |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, malformed, or blacklisted Bearer token.                    |
| `403`  | `urn:unihub:error:forbidden`    | Forbidden             | Caller role is not `ORGANIZER`, or caller account is inactive.               |
| `429`  | `urn:unihub:error:rate-limited` | Too Many Requests     | Rate limit exceeded.                                                         |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                                                 |

**Error Response Example (400 Bad Request):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:bad-request",
    "title": "Bad Request",
    "status": 400,
    "detail": "Page limit must not exceed 100.",
    "instance": "/api/v1/organizations/users"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- None.

#### Spec Traceability

- **Satisfies:** FR-01 through FR-08, FR-25, FR-26, FR-27, Scenarios 1–5, 16, 17.

### 3.2. GET /api/v1/organizations/users/{userId}

**Description:** Retrieves the detailed profile of a specific organization member by user ID.  
**Required Role / Auth:** `ORGANIZER`  
**Idempotency Requirement:** Not Required — read-only safe idempotent operation.  
**Database Routing:** PostgreSQL Read Replica (`@Transactional(readOnly = true)`).

#### Request

**Headers:**

| Header             | Required | Description                                                     |
| :----------------- | :------: | :-------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT with `role: 'ORGANIZER'` and `org: <organizationId>` |
| `X-Correlation-ID` |    No    | Client-generated UUID for tracing                               |

**Path Parameters:**

| Parameter | Type          | Required | Description                          |
| :-------- | :------------ | :------: | :----------------------------------- |
| `userId`  | string (UUID) |   Yes    | Unique identifier of the target user |

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Member details retrieved",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "organizationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "alice.staff@techclub.org",
    "fullName": "Alice Nguyen",
    "role": "STAFF",
    "phoneNumber": "+84901234567",
    "avatarUrl": null,
    "isActive": true,
    "isTemporaryPassword": false,
    "studentRecordId": null,
    "lastLoginAt": "2026-09-08T10:15:00Z",
    "createdAt": "2026-08-01T07:00:00Z",
    "updatedAt": "2026-09-08T10:15:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)       | Title                 | Condition                                                 |
| :----: | :------------------------------ | :-------------------- | :-------------------------------------------------------- |
| `400`  | `urn:unihub:error:bad-request`  | Bad Request           | `userId` is not a valid UUID format.                      |
| `401`  | `urn:unihub:error:unauthorized` | Unauthorized          | Missing, expired, malformed, or blacklisted Bearer token. |
| `403`  | `urn:unihub:error:forbidden`    | Forbidden             | Caller role is not `ORGANIZER`, or caller is inactive.    |
| `404`  | `urn:unihub:error:not-found`    | Not Found             | User does not exist, or belongs to another organization.  |
| `500`  | `urn:unihub:error:internal`     | Internal Server Error | Unexpected database failure.                              |

**Error Response Example (404 Not Found):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Member not found in your organization.",
    "instance": "/api/v1/organizations/users/550e8400-e29b-41d4-a716-446655440099"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- None.

#### Spec Traceability

- **Satisfies:** FR-09 through FR-11, FR-25, FR-26, FR-27, FR-28, Scenarios 6, 7, 16, 17.

### 3.3. POST /api/v1/organizations/users

**Description:** Provisions a new member account with role `STAFF` or `ORGANIZER` in the caller's organization. Generates a secure temporary password, hashes it with Argon2id, and publishes a transactional outbox notification event with the plaintext password.  
**Required Role / Auth:** `ORGANIZER`  
**Idempotency Requirement:** Not Required (Protected by natural idempotency — unique constraint on `users.email`).  
**Database Routing:** PostgreSQL Primary (`@Transactional`).

#### Request

**Headers:**

| Header             | Required | Description                                                     |
| :----------------- | :------: | :-------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT with `role: 'ORGANIZER'` and `org: <organizationId>` |
| `Content-Type`     |   Yes    | Must be `application/json`                                      |
| `X-Correlation-ID` |    No    | Client-generated UUID for tracing                               |

**Request Body:**

```json
{
  "email": "carol.staff@techclub.org",
  "fullName": "Carol Danvers",
  "phoneNumber": "+84912345678",
  "role": "STAFF"
}
```

**Validation Rules:**

- `email`: Required string. Valid email format per RFC 5321. Max 254 characters. Stored case-insensitive (`CITEXT`).
- `fullName`: Required string. Non-blank. 1–200 characters.
- `phoneNumber`: Optional string. If provided, must match international format `^\+[1-9]\d{1,14}$`. Max 20 characters.
- `role`: Required string. Enum value: `STAFF` or `ORGANIZER`. Submitting `STUDENT` returns `422 Unprocessable Content`.

#### Response

**Success — 201 Created:**

```json
{
  "success": true,
  "message": "User provisioned successfully. Temporary credentials sent via email.",
  "data": {
    "userId": "660f9511-f3ac-52e5-b827-557766551002",
    "email": "carol.staff@techclub.org",
    "fullName": "Carol Danvers",
    "role": "STAFF",
    "organizationId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)                         | Title                 | Condition                                                              |
| :----: | :------------------------------------------------ | :-------------------- | :--------------------------------------------------------------------- |
| `400`  | `urn:unihub:error:bad-request`                    | Bad Request           | Malformed JSON syntax or unparseable HTTP payload.                     |
| `401`  | `urn:unihub:error:unauthorized`                   | Unauthorized          | Missing, expired, malformed, or blacklisted Bearer token.              |
| `403`  | `urn:unihub:error:forbidden`                      | Forbidden             | Caller role is not `ORGANIZER`, or caller is inactive.                 |
| `409`  | `urn:unihub:error:conflict`                       | Conflict              | A user with this email address already exists globally in the system.  |
| `422`  | `urn:unihub:error:student-provisioning-forbidden` | Unprocessable Content | Caller specified `role: 'STUDENT'` (students must self-register).      |
| `422`  | `urn:unihub:error:validation`                     | Unprocessable Content | Request fails `@Valid` constraints (blank name, invalid email format). |
| `500`  | `urn:unihub:error:internal`                       | Internal Server Error | Database failure or outbox publication failure.                        |

**Error Response Example (409 Conflict):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:conflict",
    "title": "Conflict",
    "status": 409,
    "detail": "An account with this email already exists.",
    "instance": "/api/v1/organizations/users"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (422 Student Provisioning Forbidden):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:student-provisioning-forbidden",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "The STUDENT role cannot be provisioned by an organization. Students must self-register.",
    "instance": "/api/v1/organizations/users",
    "validationErrors": ["role: STUDENT role cannot be provisioned"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- A new row is inserted into `users` with `is_active = true`, `is_temporary_password = true`, and Argon2id hashed password.
- A `UserProvisionedEvent` is published to the transactional outbox (`event_publication` table) destined for RabbitMQ exchange `unihub.events::notifications`. Contains plaintext temporary password for email notification.

#### Spec Traceability

- **Satisfies:** FR-12 through FR-18, FR-25, FR-26, FR-27, Scenarios 8–10, 16, 17.

### 3.4. PATCH /api/v1/organizations/users/{userId}/status

**Description:** Updates the active status of an organization member (`true` to unban, `false` to ban). When banning a member, invalidates active JWT sessions in Redis before updating database records. Self-ban is strictly forbidden.  
**Required Role / Auth:** `ORGANIZER`  
**Idempotency Requirement:** Not Required (Naturally idempotent state assignment — submitting current status is a no-op returning 200).  
**Database Routing:** PostgreSQL Primary (`@Transactional`). Redis L1 session store.

#### Request

**Headers:**

| Header             | Required | Description                                                     |
| :----------------- | :------: | :-------------------------------------------------------------- |
| `Authorization`    |   Yes    | Bearer JWT with `role: 'ORGANIZER'` and `org: <organizationId>` |
| `Content-Type`     |   Yes    | Must be `application/json`                                      |
| `X-Correlation-ID` |    No    | Client-generated UUID for tracing                               |

**Path Parameters:**

| Parameter | Type          | Required | Description                                  |
| :-------- | :------------ | :------: | :------------------------------------------- |
| `userId`  | string (UUID) |   Yes    | Target user ID whose status is to be updated |

**Request Body:**

```json
{
  "isActive": false
}
```

**Validation Rules:**

- `isActive`: Required boolean (`true` or `false`). Missing or non-boolean values rejected with 422/400.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Member account status updated successfully.",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "isActive": false,
    "updatedAt": "2026-09-09T14:30:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Responses (RFC 7807):**

| Status | Error Type (`error.type`)                             | Title                 | Condition                                                                                  |
| :----: | :---------------------------------------------------- | :-------------------- | :----------------------------------------------------------------------------------------- |
| `400`  | `urn:unihub:error:bad-request`                        | Bad Request           | `userId` is not a valid UUID format, or malformed JSON body.                               |
| `401`  | `urn:unihub:error:unauthorized`                       | Unauthorized          | Missing, expired, malformed, or blacklisted Bearer token.                                  |
| `403`  | `urn:unihub:error:forbidden`                          | Forbidden             | Caller role is not `ORGANIZER`, or caller is inactive.                                     |
| `403`  | `urn:unihub:error:self-status-modification-forbidden` | Forbidden             | Target `userId` equals the caller's own user ID (self-deactivation forbidden).             |
| `404`  | `urn:unihub:error:not-found`                          | Not Found             | User does not exist, or belongs to another organization.                                   |
| `422`  | `urn:unihub:error:validation`                         | Unprocessable Content | `isActive` is missing or null.                                                             |
| `503`  | `urn:unihub:error:service-unavailable`                | Service Unavailable   | Redis is unreachable during a ban (`isActive = false`). Includes `Retry-After: 30` header. |
| `500`  | `urn:unihub:error:internal`                           | Internal Server Error | Unexpected database failure.                                                               |

**Error Response Example (403 Self Status Modification Forbidden):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:self-status-modification-forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "Organizers cannot modify their own active status.",
    "instance": "/api/v1/organizations/users/org-admin-1/status"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Error Response Example (503 Service Unavailable):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:service-unavailable",
    "title": "Service Unavailable",
    "status": 503,
    "detail": "Session revocation service is temporarily unavailable. Member status was not changed.",
    "instance": "/api/v1/organizations/users/550e8400-e29b-41d4-a716-446655440001/status"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

- **When banning (`isActive: false`):**
  1. Active JWT JTIs for the user are blacklisted in Redis.
  2. Active sessions are removed from Redis session tracker.
  3. Active `refresh_tokens` rows for the user are revoked in PostgreSQL (`revoked_at = now()`).
  4. `users.is_active` is updated to `false` in PostgreSQL.
  5. A `UserStatusChangedEvent` (carrying `userId`, `organizationId`, `email`, `fullName`, `isActive: false`, `occurredAt`) is published to the transactional outbox (`event_publication` table) destined for RabbitMQ exchange `unihub.events::notifications` to trigger an email notification to the user.
     _(If Redis operations fail, steps 3, 4, & 5 are aborted and 503 is returned)._
- **When unbanning (`isActive: true`):**
  1. `users.is_active` is updated to `true` in PostgreSQL.
  2. A `UserStatusChangedEvent` (`isActive: true`) is published to the transactional outbox to notify the user.
- **When no-op (status matches current):**
  - No database or Redis writes occur, and no event is published.

#### Spec Traceability

- **Satisfies:** FR-19 through FR-24, FR-25, FR-26, FR-27, FR-28, Scenarios 11–17.

## 4. Rate Limiting Behavior

All endpoints in this contract are authenticated and protected by the platform's distributed rate limiting filter (`RateLimitFilter`) via Bucket4j and Redis:

- **Tier Classification:** Default Tier (all endpoints under `/api/v1/organizations/users`).
- **Capacity & Refill:** 100 requests per minute per user ID (`jwt.sub`).
- **Standard Headers Returned:**
  - `X-RateLimit-Limit: 100`
  - `X-RateLimit-Remaining: <count>`
  - `X-RateLimit-Retry-After: <seconds>`
- **Breach Behavior:** HTTP `429 Too Many Requests` (`urn:unihub:error:rate-limited`) with `Retry-After` header.

### Endpoint Routing Summary

| Path                                                | Auth | Role        | Rate Limit Tier | Notes                  |
| :-------------------------------------------------- | :--: | :---------- | :-------------- | :--------------------- |
| `POST /api/v1/organizations/users`                  | Yes  | `ORGANIZER` | Default (100)   | Scoped to caller's org |
| `PATCH /api/v1/organizations/users/{userId}/status` | Yes  | `ORGANIZER` | Default (100)   | Scoped to caller's org |
| `GET /api/v1/organizations/users/{userId}`          | Yes  | `ORGANIZER` | Default (100)   | Scoped to caller's org |
| `GET /api/v1/organizations/users`                   | Yes  | `ORGANIZER` | Default (100)   | Scoped to caller's org |

## 5. Idempotency Behavior

Endpoints in Feature 1.5 rely on natural idempotency:

1. **`POST /api/v1/organizations/users`:** Guaranteed idempotent by database uniqueness constraint on `users.email`. Immediate retries or duplicated submissions will encounter a conflict check and safely fail with `409 Conflict` (`urn:unihub:error:conflict`), preventing duplicate account creation.
2. **`PATCH /api/v1/organizations/users/{userId}/status`:** Inherently idempotent state assignment. Re-applying the same status value is recognized as a no-op, executing zero writes and returning `200 OK`.
3. **`GET` endpoints:** Inherently safe and idempotent per HTTP specifications.

## 6. Cross-Endpoint Consistency Notes

- **User Representation:** The fields `id`, `email`, `fullName`, `role`, `phoneNumber`, `avatarUrl`, `isActive`, `isTemporaryPassword`, `lastLoginAt`, `createdAt` maintain identical names, types, and JSON representations across both list and detail endpoints.
- **Envelope Standardization:** All 2xx responses return `success: true`, a human-readable `message`, a payload in `data`, and node/trace details in `metadata` conforming to `ApiResponse<T>`.
- **RFC 7807 Error Standardization:** All 4xx and 5xx responses conform to `GlobalExceptionHandler` and `GlobalResponseBodyAdvice`, ensuring errors are wrapped in `ApiResponse<Void>` with `success: false`, `error: { type, title, status, detail, instance }`, and `metadata: { processedBy, traceId }`.
