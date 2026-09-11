# API Contract: Public Key Distribution

> **Status:** APPROVED  
> **Spec:** `specs/public-key-distribution/SPEC.md`  
> **Last revised:** 2026-09-05

## 1. Overview

This contract defines the single REST endpoint introduced by the `public-key-distribution` feature (F-1.3). The endpoint is hosted in the `iam` module's presentation layer and sourced from the `shared/security` key material. It is designed exclusively to provide the mobile React Native check-in app with the current RSA public key in PEM format, enabling offline QR code signature verification without network access. The contract must be read alongside `specs/public-key-distribution/SPEC.md`.

## 2. Global Standards Applied

- **URI structure / versioning:** URI path versioning: `/api/v1/...` (consistent with all UniHub endpoints).
- **Pagination convention:** Not applicable — this endpoint returns a single resource object, not a collection.
- **Success response shape:** Standard `ApiResponse<T>` envelope: `{ "success": true, "message": "...", "data": { ... }, "metadata": { "processedBy": "...", "traceId": "..." } }`. Null fields are omitted (`@JsonInclude(NON_NULL)`).
- **Error response format:** RFC 7807 Problem Details wrapped in the `ApiResponse` error envelope: `{ "success": false, "error": { "type": "urn:unihub:error:...", "title": "...", "status": <code>, "detail": "...", "instance": "..." }, "metadata": { "processedBy": "...", "traceId": "..." } }`.
- **Auth header convention:** `Authorization: Bearer <access_token>` (RS256-signed JWT). Required on this endpoint.
- **Date/time formatting convention:** ISO 8601 in UTC with trailing `Z` (e.g., `2026-09-03T05:42:00Z`).

## 3. Endpoints

### GET /api/v1/auth/public-key

**Description:** Returns the currently active RSA public key in PEM format, along with a deterministic key identifier and the timestamp at which the key was fetched.  
**Required Role / Auth:** `AUTHENTICATED | STAFF`. Users must be authenticated with a valid, non-revoked RS256 JWT, and must possess the `STAFF` role. Unauthenticated requests are rejected with `401`. Requests from other roles are rejected with `403`.

#### Request

**Headers:**

| Header             | Required | Description                                                                                                       |
| :----------------- | :------- | :---------------------------------------------------------------------------------------------------------------- |
| `Authorization`    | Yes      | `Bearer <access_token>` — RS256-signed JWT. Must not be expired or revoked. Must contain the `STAFF` role.        |
| `X-Correlation-ID` | No       | Client-generated correlation token for end-to-end tracing. Forwarded into the `traceId` in the response metadata. |
| `Accept-Language`  | No       | Preferred language for RFC 7807 error message `detail` field.                                                     |

**Path Parameters:** None.

**Query Parameters:** None.

**Request Body:** None (GET request).

**Validation Rules:**

- `Authorization` header — must be present; must be in the format `Bearer <token>`; the token must be a valid, non-expired RS256 JWT signed by the server's private key containing standard claims (`sub`, `org`, `jti`, `role`, `sid`, `iat`, `exp`); the JWT's `jti` claim must not be present in the Redis JTI blacklist; the JWT's `role` claim must be `STAFF`.

#### Response

**Success — 200 OK:**

```json
{
  "success": true,
  "message": "Public key retrieved successfully",
  "data": {
    "keyId": "a3f4c2e1b8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----",
    "issuedAt": "2026-09-03T05:42:00Z"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**Field Descriptions:**

| Field               | Type                  | Description                                                                                                                                                                                                      |
| :------------------ | :-------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data.keyId`        | string                | A deterministic fingerprint of the current RSA public key (e.g., SHA-256 of DER-encoded SubjectPublicKeyInfo, hex-encoded, lowercase). Changes when the key is rotated; stable across requests for the same key. |
| `data.publicKeyPem` | string                | The RSA public key in PEM format (X.509 SubjectPublicKeyInfo, Base64-encoded, wrapped in `-----BEGIN PUBLIC KEY-----` / `-----END PUBLIC KEY-----` delimiters, with line breaks at 64 characters).               |
| `data.issuedAt`     | string (ISO 8601 UTC) | The UTC timestamp at which the response was generated (i.e., when the mobile device fetched the key). Format: `2026-09-03T05:42:00Z`.                                                                            |

**Error Responses:**

| Status | Error `type` (URN)                     | Title                 | Condition                                                                                                                                                                                                                                                  |
| :----- | :------------------------------------- | :-------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `401`  | `urn:unihub:error:unauthorized`        | Unauthorized          | `Authorization` header is missing, the token format is invalid (not `Bearer <token>`), the JWT signature verification fails, the JWT is expired (`exp` claim in the past), the JWT is malformed, or the JWT's `jti` is present in the Redis JTI blacklist. |
| `403`  | `urn:unihub:error:forbidden`           | Forbidden             | The authenticated user does not have the `STAFF` role.                                                                                                                                                                                                     |
| `500`  | `urn:unihub:error:internal`            | Internal Server Error | An unexpected server-side error occurs (e.g., PEM serialization fails for an unknown reason). This should not occur in normal operation as key material is validated at startup.                                                                           |
| `503`  | `urn:unihub:error:service-unavailable` | Service Unavailable   | Redis is unavailable; the JTI blacklist check cannot be performed (fail-closed per ADR-010, BR-04). This is produced by the authentication filter before the endpoint handler is reached. Includes `Retry-After: 30` header.                               |

**RFC 7807 Error body shape (401 Unauthorized):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:unauthorized",
    "title": "Unauthorized",
    "status": 401,
    "detail": "Full authentication is required to access this resource",
    "instance": "/api/v1/auth/public-key"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**RFC 7807 Error body shape (403 Forbidden):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:forbidden",
    "title": "Forbidden",
    "status": 403,
    "detail": "Access Denied",
    "instance": "/api/v1/auth/public-key"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

**RFC 7807 Error body shape (503 Service Unavailable):**

```json
{
  "success": false,
  "error": {
    "type": "urn:unihub:error:service-unavailable",
    "title": "Service Unavailable",
    "status": 503,
    "detail": "Redis service is unavailable",
    "instance": "/api/v1/auth/public-key"
  },
  "metadata": {
    "processedBy": "unihub-app-1",
    "traceId": "7f6f7c9b2d0b4d8c"
  }
}
```

#### Side Effects

None. This is a read-only endpoint. No database records are created or modified. No events are published. No external systems are called. The public key bean is read from memory only.

#### Spec Traceability

- **Satisfies:** FR-01, FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08
- **Satisfies Scenarios:** Scenario 1, Scenario 2, Scenario 3, Scenario 4, Scenario 5, Scenario 6, Scenario 7, Scenario 8, Scenario 9

## 4. Rate Limiting Behavior

This endpoint is an authenticated endpoint and is subject to the platform's standard **default-tier** Bucket4j Token Bucket rate limiter (capacity: 100 tokens, refill: 100 tokens per 60 seconds, keyed by `jwt.sub`). Standard response headers `X-RateLimit-Limit: 100`, `X-RateLimit-Remaining`, and `X-RateLimit-Retry-After` are returned on every request.

### Endpoint Routing Summary

| Path                          | Auth | Role    | Rate Limit Tier | Notes                          |
| :---------------------------- | :--: | :------ | :-------------- | :----------------------------- |
| `GET /api/v1/auth/public-key` | Yes  | `STAFF` | Default (100)   | Keyed by `jwt.sub`, PEM format |

## 5. Cross-Endpoint Consistency Notes

- **Response envelope:** Follows the same `ApiResponse<T>` envelope as all other `GET` endpoints in the `iam` module.
- **Error shape:** Uses the same RFC 7807 error URN namespace (`urn:unihub:error:...`) as all other endpoints in the system. The `401` error shape is identical to what the `/api/v1/auth/login` endpoint produces when a JWT check fails.
- **`issuedAt` field naming:** The field name `issuedAt` is consistent with the `issuedAt` column defined in the mobile SQLite `mobile_public_keys` table (`docs/database-schema.md`).
- **`keyId` field naming:** The field name `keyId` is consistent with the `keyId` column in the mobile SQLite `mobile_public_keys` table.
- **Authorization header convention:** Same `Authorization: Bearer <token>` convention used across all authenticated endpoints.
