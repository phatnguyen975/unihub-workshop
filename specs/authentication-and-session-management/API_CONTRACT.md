# API Contract: Authentication and Session Management

> **Status:** APPROVED  
> **Spec:** `specs/authentication-and-session-management/SPEC.md`  
> **Last revised:** 2026-09-02

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

**Purpose:** Authenticate a user with email/password. Issues an RS256-signed JWT access token and an opaque refresh token. Enforces inactive-account and temporary-password gates before issuing tokens.  
**FR Traces:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06  
**Authentication:** Not required (this endpoint IS the authentication mechanism).  
**Idempotency:** Not applicable — each login creates a new session. No `Idempotency-Key` header required.

### Request

**Headers:**

| Header         | Required | Value                   |
| :------------- | :------: | :---------------------- |
| `Content-Type` |   Yes    | `application/json`      |
| `User-Agent`   |    No    | Stored as `device_info` |

**Request Body:**

```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd123"
}
```

| Field      | Type   | Required | Constraints                                  |
| :--------- | :----- | :------: | :------------------------------------------- |
| `email`    | string |   Yes    | Non-blank, valid email format, max 254 chars |
| `password` | string |   Yes    | Non-blank, max 255 chars                     |

### Success Response — 200 OK

**`Content-Type: application/json`**

**Response Body:**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxYjhmOGM3Yi0...",
    "refreshToken": "a8f3c2e1b9d74f5e9a2c8b7d6e1f4g3h",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
  }
}
```

| Field          | Type    | Description                                                                        |
| :------------- | :------ | :--------------------------------------------------------------------------------- |
| `accessToken`  | string  | Signed RS256 JWT. Claims: `sub`, `role`, `org`, `jti`, `iat`, `exp`.               |
| `refreshToken` | string  | Opaque cryptographically random string. Must be treated as a secret by the client. |
| `tokenType`    | string  | Always `"Bearer"`.                                                                 |
| `expiresIn`    | integer | Access token TTL in seconds (e.g., `3600`).                                        |

**Side effects:**

- A new `refresh_tokens` row is inserted.
- `users.last_login_at` is updated to `NOW()`.

### Error Responses

| Scenario                                      | HTTP Status | `type`                                 | `title`             | Notes                                                                                                                                             |
| :-------------------------------------------- | :---------: | :------------------------------------- | :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------ |
| Wrong password or unknown email               |    `401`    | `urn:unihub:error:authentication`      | Unauthorized        | Generic; does not distinguish email-not-found from wrong-password (anti-enumeration)                                                              |
| Account is inactive                           |    `403`    | `urn:unihub:error:account-disabled`    | Forbidden           | `is_active = FALSE`                                                                                                                               |
| Temporary password must be reset              |    `403`    | `urn:unihub:error:temporary-password`  | Forbidden           | `is_temporary_password = TRUE`; client must route to password-change screen                                                                       |
| Request body validation failure               |    `400`    | `urn:unihub:error:validation`          | Bad Request         | Missing or invalid `email`/`password` fields                                                                                                      |
| Redis unavailable (JTI write on future calls) |    `503`    | `urn:unihub:error:service-unavailable` | Service Unavailable | `Retry-After: 30` header included. Login itself does not write to Redis; this only applies if Redis is checked during subsequent protected calls. |

**RFC 7807 Error body shape (Authentication Failed):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:authentication",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Invalid email or password.",
    "instance": "/api/v1/auth/login"
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
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
    "processedBy": "unihub-backend-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4737"
  }
}
```

## Endpoint 2 — Token Refresh: `POST /api/v1/auth/refresh`

**Purpose:** Exchange a valid refresh token for a new access token. The refresh token is rotated atomically: the old token is revoked and a new one is issued in a single transaction.  
**FR Traces:** FR-08, FR-09  
**Authentication:** Not required (the refresh token itself is the credential).  
**Idempotency:** Not applicable — refresh is inherently stateful. A second request with the same (now revoked) refresh token returns `401`.

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

| Field          | Type   | Required | Constraints |
| :------------- | :----- | :------: | :---------- |
| `refreshToken` | string |   Yes    | Non-blank   |

### Success Response — 200 OK

**`Content-Type: application/json`**

```json
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9.newToken...",
    "refreshToken": "z9k2p4x1m8n3q7w5v6u0t",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "metadata": {
    "processedBy": "unihub-backend-1",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4737"
  }
}
```

| Field          | Type    | Description                                                         |
| :------------- | :------ | :------------------------------------------------------------------ |
| `accessToken`  | string  | New RS256-signed JWT with a fresh `jti`.                            |
| `refreshToken` | string  | New opaque refresh token. The old token is now permanently revoked. |
| `tokenType`    | string  | Always `"Bearer"`.                                                  |
| `expiresIn`    | integer | Access token TTL in seconds.                                        |

**Side effects:**

- The consumed `refresh_tokens` row has `revoked_at` set to `NOW()`.
- A new `refresh_tokens` row is inserted with the new token hash and a fresh `expires_at`.

### Error Responses

| Scenario                                 | HTTP Status | `type`                            | Notes                                           |
| :--------------------------------------- | :---------: | :-------------------------------- | :---------------------------------------------- |
| Token not found (unknown hash)           |    `401`    | `urn:unihub:error:authentication` | No matching `token_hash` in `refresh_tokens`    |
| Token revoked (`revoked_at IS NOT NULL`) |    `401`    | `urn:unihub:error:authentication` | Token was already rotated or explicitly revoked |
| Token expired (`expires_at < NOW()`)     |    `401`    | `urn:unihub:error:authentication` | Refresh token TTL has lapsed                    |
| Request body validation failure          |    `400`    | `urn:unihub:error:validation`     | Missing or blank `refreshToken` field           |

## Endpoint 3 — Logout: `POST /api/v1/auth/logout`

**Purpose:** Revoke the caller's current access token by writing its `jti` to the Redis JTI blacklist with a TTL matching the token's remaining validity, and invalidate the corresponding refresh token row in the database.  
**FR Traces:** FR-10, FR-11  
**Authentication:** Required — caller must present a valid Bearer JWT access token.  
**Idempotency:** Not applicable. Calling logout twice with the same (now blacklisted) access token results in `401` on the second call because the token has been revoked.

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

| Field          | Type   | Required | Description                                                                                                                                                                 |
| :------------- | :----- | :------: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `refreshToken` | string |    No    | If provided, the system revokes only this specific refresh token row. If absent, all non-expired, non-revoked refresh tokens for the user are revoked (full session clear). |

### Success Response — 204 No Content

No response body.

**Side effects:**

- The caller's access token `jti` is written to the Redis JTI blacklist with TTL = `exp - NOW()` seconds.
- The matched `refresh_tokens` row(s) have `revoked_at` set to `NOW()`.

### Error Responses

| Scenario                                      | HTTP Status | `type`                                 | Notes                                                      |
| :-------------------------------------------- | :---------: | :------------------------------------- | :--------------------------------------------------------- |
| Missing or invalid Bearer token               |    `401`    | `urn:unihub:error:authentication`      | Standard filter rejection before the endpoint is reached   |
| Redis unavailable (JTI blacklist write fails) |    `503`    | `urn:unihub:error:service-unavailable` | `Retry-After: 30` header included. No DB changes are made. |

## Endpoint 4 — JWKS (Public Key): `GET /.well-known/jwks.json`

**Purpose:** Return the RSA public key in JWK Set format (RFC 7517) for token signature verification by downstream services and API gateways.  
**FR Traces:** FR-12  
**Authentication:** Not required. This is a public endpoint.  
**Idempotency:** Inherently idempotent (read-only).

### Request

No request body or query parameters.

### Success Response — 200 OK

**`Content-Type: application/json`**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "n": "sn38-rsa-public-modulus-base64url-encoded...",
      "e": "AQAB"
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

| Scenario                                      | HTTP Status | Notes                                                                     |
| :-------------------------------------------- | :---------: | :------------------------------------------------------------------------ |
| RSA key not loaded (startup misconfiguration) |    `500`    | Should not occur in normal operation; monitored via Actuator health check |

## Global Security Notes

### JWT Access Token Claims

All access tokens issued by `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` carry the following claims:

```json
{
  "sub": "1b8f8c7b-2e4b-4f6c-9b3b-6b8d2f0c9a12",
  "role": "STUDENT",
  "org": "b4d3f9b7-1c2a-4b8d-9e61-2e5f6a7b8c90",
  "jti": "9c1f6c23-0f5d-4f8b-9f51-2d5f3a2f1d9e",
  "iat": 1767225600,
  "exp": 1767229200
}
```

- `sub` — UUID of the authenticated user (`users.id`)
- `role` — One of `STUDENT`, `ORGANIZER`, `STAFF` (no `ROLE_` prefix in the token)
- `org` — UUID of `users.organization_id`
- `jti` — UUIDv4, unique per issued token; used for revocation
- `iat` — Issued-at time (Unix epoch seconds, UTC)
- `exp` — Expiry time (Unix epoch seconds, UTC)

### Authorization for Protected Endpoints

All endpoints under `/api/v1/**` (excluding those in the `app.security.public-paths` list in `application.yml`) require a valid Bearer access token. The `JwtAuthenticationFilter` in `shared/security` enforces:

1. JWT signature verification against the RSA public key.
2. Token expiry check (`exp > NOW()`).
3. JTI blacklist check in Redis (fail-closed: Redis unavailability → `503`).

### Redis Fail-Closed Semantics

Per ADR-010, JTI blacklist checks on protected requests are fail-closed. If Redis is unavailable:

- **Protected endpoint requests** that require JTI validation: → `503 Service Unavailable`, `Retry-After: 30`
- **Logout** (which writes to Redis): → `503 Service Unavailable`, `Retry-After: 30`; no DB changes
- **Login** (which does NOT write to Redis): → succeeds normally; Redis is not involved in credential validation

### Rate Limiting

Per NFR-09, the `/api/v1/auth/login` and `/api/v1/auth/refresh` endpoints are subject to the **browsing-tier** Bucket4j rate limit (capacity: 100 tokens, refill: 100 per minute). They are NOT subject to the registration-tier (capacity: 5 per 10 seconds) rate limit.

## Endpoint Routing Summary

The table below summarises which paths are public and which are protected, as configured in `app.security.public-paths` and enforced by `SecurityConfig`:

| Path                         | Auth | Rate Limit Tier | Notes                                      |
| :--------------------------- | :--: | :-------------- | :----------------------------------------- |
| `POST /api/v1/auth/login`    |  No  | Browsing        | Must be added to `public-paths`            |
| `POST /api/v1/auth/refresh`  |  No  | Browsing        | Must be added to `public-paths`            |
| `POST /api/v1/auth/logout`   | Yes  | Browsing        | Bearer token required                      |
| `GET /.well-known/jwks.json` |  No  | None            | Already in `public-paths` from feature 9.2 |
