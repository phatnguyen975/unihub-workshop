# API Contract: Authentication and Session Management

> **Status:** APPROVED  
> **Spec:** `specs/authentication-and-session-management/SPEC.md`  
> **Last revised:** 2026-09-05

## Overview

This contract specifies the four endpoints exposed by the `authentication-and-session-management` feature:

| #   | Method | Path                     | Auth Required | Summary                            |
| --- | ------ | ------------------------ | :-----------: | ---------------------------------- |
| 1   | `POST` | `/api/v1/auth/login`     |      No       | Authenticate and issue tokens      |
| 2   | `POST` | `/api/v1/auth/refresh`   |      No       | Rotate refresh token, issue new AT |
| 3   | `POST` | `/api/v1/auth/logout`    | Yes (Bearer)  | Revoke AT (JTI blacklist) + RT     |
| 4   | `GET`  | `/.well-known/jwks.json` |      No       | Return RSA public key in JWK Set   |

All API endpoints comply with the following invariants from `docs/api-standards.md`:

- URI prefix: `/api/v1/...` (except JWKS which follows RFC 8414 well-known convention)
- Success envelope: `{ success, message, data, metadata: { processedBy, traceId } }`
- Error format: RFC 7807 `application/problem+json`
- Timestamps: ISO 8601 UTC with `Z`
- Null fields omitted from responses (`@JsonInclude(NON_NULL)`)

## Endpoint 1 — Login: `POST /api/v1/auth/login`

**Purpose:** Authenticate a user with email/password. Issues an RS256-signed JWT access token, an opaque refresh token, and user profile data for immediate UI rendering. Enforces inactive-account and temporary-password gates before issuing tokens.  
**FR Traces:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07  
**Authentication:** Not required (this endpoint IS the authentication mechanism).

### Request

**Headers:**

| Header         | Required | Value                   |
| :------------- | :------: | :---------------------- |
| `Content-Type` |   Yes    | `application/json`      |
| `User-Agent`   |    No    | Stored as `device_info` |

**Request Body:**

```json
{
  "email": "student.alice@university.edu",
  "password": "StrongPassword123!"
}
```

| Field      | Type   | Required | Constraints                                                            |
| :--------- | :----- | :------: | :--------------------------------------------------------------------- |
| `email`    | string |   Yes    | Non-blank, valid email format, max 254 chars (whitespace auto-trimmed) |
| `password` | string |   Yes    | Non-blank, max 255 chars                                               |

### Success Response — 200 OK

**`Content-Type: application/json`**

**Response Body:**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InVuaWh1Yi0yMDI2In0...",
    "refreshToken": "7f9a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "d290f1ee-6c54-4b01-90e6-d701748f0851",
      "email": "student.alice@university.edu",
      "fullName": "Alice Nguyen",
      "role": "STUDENT",
      "phoneNumber": "+84912345678",
      "avatarUrl": "https://storage.unihub.edu/avatars/alice.jpg",
      "lastLoginAt": "2026-09-05T01:40:00Z",
      "organizationId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "studentRecordId": "f01e2d3c-4b5a-6789-0123-456789abcdef"
    }
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

| Field                  | Type    | Description                                                                             |
| :--------------------- | :------ | :-------------------------------------------------------------------------------------- |
| `accessToken`          | string  | Signed RS256 JWT. Claims: `sub`, `role`, `org`, `jti`, `sid`, `iat`, `exp`.             |
| `refreshToken`         | string  | Opaque cryptographically random string. Must be treated as a secret by the client.      |
| `tokenType`            | string  | Always `"Bearer"`.                                                                      |
| `expiresIn`            | integer | Access token TTL in seconds (e.g., `3600`).                                             |
| `user`                 | object  | User profile object for immediate UI rendering and frontend state initialization.       |
| `user.id`              | string  | UUID of the authenticated user.                                                         |
| `user.email`           | string  | Email address of the user.                                                              |
| `user.fullName`        | string  | Full display name of the user.                                                          |
| `user.role`            | string  | Role of the user: `"STUDENT"`, `"ORGANIZER"`, or `"STAFF"`.                             |
| `user.phoneNumber`     | string  | Phone number (optional, omitted if null).                                               |
| `user.avatarUrl`       | string  | URL of user's avatar image (optional, omitted if null).                                 |
| `user.lastLoginAt`     | string  | ISO 8601 UTC timestamp of previous login (optional, omitted if null).                   |
| `user.organizationId`  | string  | UUID of the associated organization (optional, omitted if null).                        |
| `user.studentRecordId` | string  | UUID of the student directory record for verified students (optional, omitted if null). |

**Side effects:**

- A new `refresh_tokens` row is inserted.
- `users.last_login_at` is updated to `NOW()`.

### Error Responses

| Scenario                                      | HTTP Status | `type`                                    | `title`                | Notes                                                                                                                                             |
| :-------------------------------------------- | :---------: | :---------------------------------------- | :--------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------ |
| Wrong password or unknown email               |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | Generic; does not distinguish email-not-found from wrong-password (anti-enumeration)                                                              |
| Account is inactive                           |    `403`    | `urn:unihub:error:account-disabled`       | Forbidden              | `is_active = FALSE`                                                                                                                               |
| Temporary password must be reset              |    `403`    | `urn:unihub:error:temporary-password`     | Forbidden              | `is_temporary_password = TRUE`; client must route to password-change screen (`POST /api/v1/auth/password/temporary`)                              |
| Request body validation failure               |    `422`    | `urn:unihub:error:validation`             | Unprocessable Content  | Missing or invalid `email`/`password` fields; returns `validationErrors` list                                                                     |
| Malformed JSON body                           |    `400`    | `urn:unihub:error:bad-request`            | Bad Request            | Unparseable JSON syntax                                                                                                                           |
| Unsupported Content-Type                      |    `415`    | `urn:unihub:error:unsupported-media-type` | Unsupported Media Type | Request `Content-Type` header is not `application/json`                                                                                           |
| Redis unavailable (JTI write on future calls) |    `503`    | `urn:unihub:error:service-unavailable`    | Service Unavailable    | `Retry-After: 30` header included. Login itself does not write to Redis; this only applies if Redis is checked during subsequent protected calls. |
| Unexpected server error                       |    `500`    | `urn:unihub:error:internal`               | Internal Server Error  | Unhandled server error (technical stack trace suppressed)                                                                                         |

**RFC 7807 Error body shape (Authentication Failed):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:unauthorized",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Invalid email or password.",
    "instance": "/api/v1/auth/login"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**RFC 7807 Error body shape (Temporary Password Enforcement):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:temporary-password",
    "title": "Forbidden",
    "status": 403,
    "detail": "Mandatory password change required before accessing the system.",
    "instance": "/api/v1/auth/login"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**RFC 7807 Error body shape (Validation Failure):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:validation",
    "title": "Unprocessable Content",
    "status": 422,
    "detail": "Validation failed for 'loginRequest'. 1 error(s) found.",
    "instance": "/api/v1/auth/login",
    "validationErrors": ["email: must be a well-formed email address"]
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

## Endpoint 2 — Token Refresh: `POST /api/v1/auth/refresh`

**Purpose:** Exchange a valid refresh token for a new access token. The refresh token is rotated atomically: the old token is revoked and a new one is issued in a single transaction.  
**FR Traces:** FR-08, FR-09  
**Authentication:** Not required (the refresh token itself is the credential).

### Request

**Headers:**

| Header         | Required | Value              |
| :------------- | :------: | :----------------- |
| `Content-Type` |   Yes    | `application/json` |

**Request Body:**

```json
{
  "refreshToken": "a8f3c2e1b9d74f5e9a2c8b7d6e1f4g3h"
}
```

| Field          | Type   | Required | Constraints                                                                           |
| :------------- | :----- | :------: | :------------------------------------------------------------------------------------ |
| `refreshToken` | string |   Yes    | Non-blank, max 255 chars (whitespace is automatically trimmed by compact constructor) |

### Success Response — 200 OK

**`Content-Type: application/json`**

```json
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InVuaWh1Yi0yMDI2In0...",
    "refreshToken": "a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4e5f67890",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

| Field          | Type    | Description                                                         |
| :------------- | :------ | :------------------------------------------------------------------ |
| `accessToken`  | string  | New RS256-signed JWT with a fresh `jti` and `sid`.                  |
| `refreshToken` | string  | New opaque refresh token. The old token is now permanently revoked. |
| `tokenType`    | string  | Always `"Bearer"`.                                                  |
| `expiresIn`    | integer | Access token TTL in seconds.                                        |

**Side effects:**

- The consumed `refresh_tokens` row has `revoked_at` set to `NOW()`.
- A new `refresh_tokens` row is inserted with the new token hash and a fresh `expires_at`.

### Error Responses

| Scenario                                 | HTTP Status | `type`                                    | `title`                | Notes                                                                             |
| :--------------------------------------- | :---------: | :---------------------------------------- | :--------------------- | :-------------------------------------------------------------------------------- |
| Token not found (unknown hash)           |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | No matching `token_hash` in `refresh_tokens`                                      |
| Token revoked (`revoked_at IS NOT NULL`) |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | Token was already rotated or explicitly revoked                                   |
| Token expired (`expires_at < NOW()`)     |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | Refresh token TTL has lapsed                                                      |
| Inactive or temporary password user      |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | Account disabled (`is_active=false`) or temporary password reset required (BR-08) |
| Request body validation failure          |    `422`    | `urn:unihub:error:validation`             | Unprocessable Content  | Missing, blank, or > 255 chars `refreshToken`; returns `validationErrors` list    |
| Malformed JSON body                      |    `400`    | `urn:unihub:error:bad-request`            | Bad Request            | Unparseable JSON syntax                                                           |
| Unsupported media type                   |    `415`    | `urn:unihub:error:unsupported-media-type` | Unsupported Media Type | Sent payload without `Content-Type: application/json`                             |
| Redis unavailable                        |    `503`    | `urn:unihub:error:service-unavailable`    | Service Unavailable    | `Retry-After: 30` header included                                                 |
| Unexpected server error                  |    `500`    | `urn:unihub:error:internal`               | Internal Server Error  | Unhandled server error (technical stack trace suppressed)                         |

## Endpoint 3 — Logout: `POST /api/v1/auth/logout`

**Purpose:** Revoke the caller's current access token by writing its `jti` to the Redis JTI blacklist with a TTL matching the token's remaining validity, and invalidate the corresponding refresh token row in the database.  
**FR Traces:** FR-10, FR-11  
**Authentication:** Required — caller must present a valid Bearer JWT access token.

### Request

**Headers:**

| Header          | Required | Value                                |
| :-------------- | :------: | :----------------------------------- |
| `Authorization` |   Yes    | `Bearer <access_token>`              |
| `Content-Type`  |    No    | `application/json` (if body present) |

**Request Body (optional):**

```json
{
  "refreshToken": "a8f3c2e1b9d74f5e9a2c8b7d6e1f4g3h"
}
```

| Field          | Type   | Required | Description                                                                                                                                                                                          |
| :------------- | :----- | :------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `refreshToken` | string |    No    | If provided, max 255 chars (whitespace automatically trimmed by compact constructor). If absent or blank, all non-expired, non-revoked refresh tokens for the user are revoked (full session clear). |

### Success Response — 204 No Content

No response body.

**Side effects:**

- The caller's access token `jti` is written to the Redis JTI blacklist with TTL = `exp - NOW()` seconds.
- The matched `refresh_tokens` row(s) have `revoked_at` set to `NOW()`.

### Error Responses

| Scenario                                      | HTTP Status | `type`                                    | `title`                | Notes                                                       |
| :-------------------------------------------- | :---------: | :---------------------------------------- | :--------------------- | :---------------------------------------------------------- |
| Request body validation failure               |    `422`    | `urn:unihub:error:validation`             | Unprocessable Content  | > 255 chars `refreshToken`; returns `validationErrors` list |
| Malformed JSON body                           |    `400`    | `urn:unihub:error:bad-request`            | Bad Request            | Unparseable JSON syntax                                     |
| Unsupported media type                        |    `415`    | `urn:unihub:error:unsupported-media-type` | Unsupported Media Type | Sent payload without `Content-Type: application/json`       |
| Missing or invalid Bearer token               |    `401`    | `urn:unihub:error:unauthorized`           | Unauthorized           | Standard filter rejection before the endpoint is reached    |
| Redis unavailable (JTI blacklist write fails) |    `503`    | `urn:unihub:error:service-unavailable`    | Service Unavailable    | `Retry-After: 30` header included. No DB changes are made.  |
| Unexpected server error                       |    `500`    | `urn:unihub:error:internal`               | Internal Server Error  | Unhandled server error (technical stack trace suppressed)   |

## Endpoint 4 — JWKS (Public Key): `GET /.well-known/jwks.json`

**Purpose:** Return the RSA public key in JWK Set format (RFC 7517) for token signature verification by downstream services and API gateways.  
**FR Traces:** FR-12  
**Authentication:** Not required. This is a public endpoint.

### Request

No request body or query parameters.

### Success Response — 200 OK

**`Content-Type: application/json`**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "n": "sn38-rsa-public-modulus-base64url-encoded...",
      "alg": "RS256"
    }
  ]
}
```

> [!NOTE]
> The JWKS endpoint response does NOT use the standard UniHub success envelope (`{ success, message, data, metadata }`). It returns the raw JWK Set object directly, conforming to RFC 7517 §5. This is the industry-standard shape expected by JWT libraries and API gateways.

| Field        | Type   | Description                                                                                    |
| :----------- | :----- | :--------------------------------------------------------------------------------------------- |
| `keys`       | array  | Array of JWK objects. Contains exactly one key (the current RSA public key loaded at startup). |
| `keys[].kty` | string | Key type. Always `"RSA"`.                                                                      |
| `keys[].use` | string | Key use. Always `"sig"` (signature verification only).                                         |
| `keys[].alg` | string | Algorithm. Always `"RS256"`.                                                                   |
| `keys[].n`   | string | Base64url-encoded RSA modulus.                                                                 |
| `keys[].e`   | string | Base64url-encoded RSA public exponent. Typically `"AQAB"` (65537).                             |

**No private key material (`d`, `p`, `q`, `dp`, `dq`, `qi`) is ever included.**

**Side effects:** None. Read-only.

### Error Responses

| Scenario                                      | HTTP Status | `type`                      | `title`               | Notes                                                                     |
| :-------------------------------------------- | :---------: | :-------------------------- | :-------------------- | :------------------------------------------------------------------------ |
| RSA key not loaded (startup misconfiguration) |    `500`    | `urn:unihub:error:internal` | Internal Server Error | Should not occur in normal operation; monitored via Actuator health check |

## Global Security Notes

### JWT Access Token Claims

All access tokens issued by `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` carry the following claims:

```json
{
  "sub": "1b8f8c7b-2e4b-4f6c-9b3b-6b8d2f0c9a12",
  "role": "STUDENT",
  "org": "b4d3f9b7-1c2a-4b8d-9e61-2e5f6a7b8c90",
  "jti": "9c1f6c23-0f5d-4f8b-9f51-2d5f3a2f1d9e",
  "sid": "f3b97b0a-3c2d-4e5f-8a1b-0c2d3e4f5a6b",
  "iat": 1767225600,
  "exp": 1767229200
}
```

- `sub` — UUID of the authenticated user (`users.id`)
- `role` — One of `STUDENT`, `ORGANIZER`, `STAFF` (no `ROLE_` prefix in the token)
- `org` — UUID of `users.organization_id` (optional, omitted if user has no associated organization)
- `jti` — UUIDv4, unique per issued token; used for revocation
- `sid` — UUID of the active session (`refresh_tokens.id`) bound to this access token
- `iat` — Issued-at time (Unix epoch seconds, UTC)
- `exp` — Expiry time (Unix epoch seconds, UTC)

### Authorization for Protected Endpoints

All endpoints under `/api/v1/**` (excluding those in the `app.security.public-paths` list in `application.yml`) require a valid Bearer access token. The `JwtAuthenticationFilter` in `shared/security` enforces:

1. JWT signature verification against the RSA public key.
2. Token expiry check (`exp > NOW()`).
3. Mandatory claims check (`sub`, `org`, `jti`, `role`, `sid`, `iat`, `exp`).
4. JTI blacklist check in Redis (fail-closed: Redis unavailability → `503`).

### Redis Fail-Closed Semantics

Per ADR-010, JTI blacklist checks on protected requests are fail-closed. If Redis is unavailable:

- **Protected endpoint requests** that require JTI validation: → `503 Service Unavailable`, `Retry-After: 30`
- **Logout** (which writes to Redis blacklist): → `503 Service Unavailable`, `Retry-After: 30`; no DB changes
- **Login / Refresh** (which track active session JTI in Redis `session:jti:{sessionId}`): fail-closed if Redis is down → `503 Service Unavailable`, `Retry-After: 30`

### Rate Limiting

Per NFR-09, the `/api/v1/auth/login` and `/api/v1/auth/refresh` endpoints are subject to the **auth-tier** Bucket4j rate limit (capacity: 20 tokens, refill: 20 per minute per client IP). They are NOT subject to the registration-tier (capacity: 5 per 10 seconds) or default-tier rate limits. `/api/v1/auth/logout` is an authenticated endpoint governed by the **default-tier** (keyed by `jwt.sub`).

## Endpoint Routing Summary

The table below summarises which paths are public and which are protected, as configured in `app.security.public-paths` and enforced by `SecurityConfig`:

| Path                         | Auth | Rate Limit Tier | Notes                                             |
| :--------------------------- | :--: | :-------------- | :------------------------------------------------ |
| `POST /api/v1/auth/login`    |  No  | Auth            | Configured in `public-paths` (20 req/60s per IP)  |
| `POST /api/v1/auth/refresh`  |  No  | Auth            | Configured in `public-paths` (20 req/60s per IP)  |
| `POST /api/v1/auth/logout`   | Yes  | Default         | Bearer token required (100 req/60s per user)      |
| `GET /.well-known/jwks.json` |  No  | Default         | Configured in `public-paths` (100 req/60s per IP) |
