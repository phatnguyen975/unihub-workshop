# UniHub Workshop - Auth API Contracts

## 1. Conventions

- **Base path:** `/api/v1`
- All responses use the standard envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {},
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

- Error payloads use RFC 7807 inside `error`:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "metadata": {
    "statusCode": 400,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": {
    "type": "https://api.unihub.local/problems/validation",
    "title": "Bad Request",
    "detail": "One or more fields are invalid.",
    "instance": "/api/v1/auth/register",
    "validationErrors": [
      "email: must be a well-formed email address",
      "password: must be at least 8 characters"
    ]
  }
}
```

- **Required headers:**
  - `Authorization: Bearer <token>` for protected endpoints.
  - Rate-limited endpoints may return `X-RateLimit-Remaining` and `X-RateLimit-Retry-After`.
- **Security notes:** `refreshToken` is returned in the JSON body for cross-platform parity (Web + Android). Web clients must store it securely (avoid plain `LocalStorage` when possible) to mitigate XSS risk.
- All JSON keys are **camelCase**.

## 2. Contracts

### POST /api/v1/auth/register

**Security:** Public (Guest)

**Request Body:**

```json
{
  "email": "student@unihub.edu",
  "password": "StrongPass!123",
  "fullName": "Ava Student",
  "phone": "+1-555-0100"
}
```

| Field    | Type   | Required | Constraints / Business Rules                      |
| :------- | :----- | :------- | :------------------------------------------------ |
| email    | string | Yes      | Must be a valid email format.                     |
| password | string | Yes      | Minimum 8 characters; strong complexity required. |
| fullName | string | Yes      | Must be non-empty.                                |
| phone    | string | No       | If provided, must be a valid phone format.        |

**Success Response (201):**

```json
{
  "success": true,
  "message": "Registration initiated",
  "data": {
    "email": "student@unihub.edu",
    "status": "UNVERIFIED",
    "verificationRequired": true
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Alternate Success (202):**

- Used when OTP delivery is queued for retry due to transient infra issues.

```json
{
  "success": true,
  "message": "Registration accepted; verification scheduled",
  "data": {
    "email": "student@unihub.edu",
    "status": "UNVERIFIED",
    "verificationRequired": true
  },
  "metadata": {
    "statusCode": 202,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 409 Conflict (email already active).
- 429 Too Many Requests (OTP cooldown).
- 503 Service Unavailable (Redis/event publication failure).

### POST /api/v1/auth/verify-otp

**Security:** Public (Guest)

**Request Body:**

```json
{
  "email": "student@unihub.edu",
  "otp": "483920",
  "intent": "REGISTER"
}
```

| Field  | Type   | Required | Constraints / Business Rules         |
| :----- | :----- | :------- | :----------------------------------- |
| email  | string | Yes      | Must be a valid email format.        |
| otp    | string | Yes      | Exactly 6 digits.                    |
| intent | string | Yes      | One of `REGISTER`, `RESET_PASSWORD`. |

**Success Response (REGISTER, 200):**

```json
{
  "success": true,
  "message": "Account verified",
  "data": {
    "email": "student@unihub.edu",
    "status": "ACTIVE"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Success Response (RESET_PASSWORD, 200):**

```json
{
  "success": true,
  "message": "Reset token issued",
  "data": {
    "resetToken": "reset-token-opaque",
    "resetTokenExpiresInSeconds": 900
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid/expired OTP, unsupported intent).
- 401 Unauthorized (OTP mismatch).
- 409 Conflict (user state incompatible with intent).
- 429 Too Many Requests (too many OTP attempts).

### POST /api/v1/auth/login

**Security:** Public (Guest)

**Request Body:**

```json
{
  "email": "student@unihub.edu",
  "password": "StrongPass!123"
}
```

| Field    | Type   | Required | Constraints / Business Rules  |
| :------- | :----- | :------- | :---------------------------- |
| email    | string | Yes      | Must be a valid email format. |
| password | string | Yes      | Must be non-empty.            |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "refreshExpiresInSeconds": 2592000,
    "passwordChangeRequired": false,
    "user": {
      "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
      "email": "student@unihub.edu",
      "fullName": "Ava Student",
      "role": "STUDENT",
      "status": "ACTIVE",
      "organizationId": null,
      "studentId": "STU-10992",
      "isTemporaryPassword": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

> **Developer Note:** `studentId` can be `null` for users who registered manually and have not yet been matched by the nightly CSV ingestion process.

**Alternate Success (Password Change Required, 200):**

```json
{
  "success": true,
  "message": "Password change required",
  "data": {
    "accessToken": "restricted-jwt",
    "refreshToken": null,
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "passwordChangeRequired": true,
    "user": {
      "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
      "email": "staff@unihub.edu",
      "fullName": "Riley Staff",
      "role": "STAFF",
      "status": "ACTIVE",
      "organizationId": "1abf4f71-8c42-4af8-9d0f-4f8d8d6f6d5d",
      "isTemporaryPassword": true
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized (invalid credentials).
- 403 Forbidden (user status UNVERIFIED or LOCKED).
- 429 Too Many Requests (temporary lockout).

### POST /api/v1/auth/refresh

**Security:** Public (Guest, refresh token required)

**Request Body:**

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

| Field        | Type   | Required | Constraints / Business Rules                |
| :----------- | :----- | :------- | :------------------------------------------ |
| refreshToken | string | Yes      | Must be a valid, non-expired refresh token. |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "new-jwt-access-token",
    "refreshToken": "new-jwt-refresh-token",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "refreshExpiresInSeconds": 2592000,
    "refreshTokenRotated": true
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 401 Unauthorized (missing/invalid/expired/revoked refresh token).
- 403 Forbidden (user not ACTIVE).

### POST /api/v1/auth/forgot-password

**Security:** Public (Guest)

**Request Body:**

```json
{
  "email": "student@unihub.edu"
}
```

| Field | Type   | Required | Constraints / Business Rules  |
| :---- | :----- | :------- | :---------------------------- |
| email | string | Yes      | Must be a valid email format. |

**Success Response (200):**

```json
{
  "success": true,
  "message": "If the account exists, a reset code was sent",
  "data": {
    "requestAccepted": true
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid email).
- 429 Too Many Requests (OTP cooldown).
- 503 Service Unavailable (Redis/event publication failure).

### POST /api/v1/auth/reset-password

**Security:** Public (Guest)

**Request Body:**

```json
{
  "resetToken": "reset-token-opaque",
  "newPassword": "NewStrongPass!123",
  "confirmPassword": "NewStrongPass!123"
}
```

| Field           | Type   | Required | Constraints / Business Rules                      |
| :-------------- | :----- | :------- | :------------------------------------------------ |
| resetToken      | string | Yes      | Must be a valid, non-expired reset token.         |
| newPassword     | string | Yes      | Minimum 8 characters; strong complexity required. |
| confirmPassword | string | Yes      | Must match `newPassword`.                         |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Password reset successful",
  "data": null,
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (password mismatch or weak password).
- 401 Unauthorized (invalid or expired reset token).
