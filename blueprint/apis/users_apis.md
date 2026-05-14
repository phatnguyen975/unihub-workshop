# UniHub Workshop - Users API Contracts

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
    "instance": "/api/v1/users/me/password",
    "validationErrors": [
      "currentPassword: must match the current password",
      "newPassword: must be at least 8 characters"
    ]
  }
}
```

- **Required headers:** `Authorization: Bearer <token>`.
- All JSON keys are **camelCase**.

## 2. Contracts

### POST /api/v1/users/me/logout

**Security:** Bearer Token (Student, Organizer, Staff)

**Request Body:**

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

| Field        | Type   | Required | Constraints / Business Rules                             |
| :----------- | :----- | :------- | :------------------------------------------------------- |
| refreshToken | string | No       | If provided, it will be explicitly invalidated in Redis. |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Logged out",
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

- 400 Bad Request (malformed refresh token).
- 401 Unauthorized (missing/invalid token).

### PUT /api/v1/users/me/password

**Security:** Bearer Token (Student, Organizer, Staff)

**Request Body:**

```json
{
  "currentPassword": "OldPass!123",
  "newPassword": "NewStrongPass!123",
  "confirmPassword": "NewStrongPass!123"
}
```

| Field           | Type   | Required | Constraints / Business Rules                      |
| :-------------- | :----- | :------- | :------------------------------------------------ |
| currentPassword | string | Yes      | Must match the current password.                  |
| newPassword     | string | Yes      | Minimum 8 characters; strong complexity required. |
| confirmPassword | string | Yes      | Must match `newPassword`.                         |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Password changed",
  "data": {
    "isTemporaryPassword": false
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

**Post-Condition:**

- The system sets `isTemporaryPassword` to `false` in the `USERS` table after a successful password update.
- The response confirms that `isTemporaryPassword` is now `false`.

**Common Errors:**

- 400 Bad Request (validation or weak password).
- 401 Unauthorized (missing token or current password mismatch).

### GET /api/v1/users/me

**Security:** Bearer Token (Student, Organizer, Staff)

**Success Response (200):**

```json
{
  "success": true,
  "message": "Profile retrieved",
  "data": {
    "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
    "email": "student@unihub.edu",
    "fullName": "Ava Student",
    "phone": "+1-555-0100",
    "role": "STUDENT",
    "status": "ACTIVE",
    "organizationId": null,
    "studentId": "STU-10992",
    "isTemporaryPassword": false
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

**Common Errors:**

- 401 Unauthorized (missing/invalid token).
- 404 Not Found (user not found).

### PATCH /api/v1/users/me

**Security:** Bearer Token (Student, Organizer, Staff)

**Request Body:**

```json
{
  "fullName": "Ava B. Student",
  "phone": "+1-555-0101"
}
```

| Field    | Type   | Required | Constraints / Business Rules                                                                               |
| :------- | :----- | :------- | :--------------------------------------------------------------------------------------------------------- |
| fullName | string | No       | If provided, must be non-empty string. Cannot be set to null.                                              |
| phone    | string | No       | If provided, must be a valid phone format. If explicitly sent as `null`, the phone number will be cleared. |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Profile updated",
  "data": {
    "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
    "email": "student@unihub.edu",
    "fullName": "Ava B. Student",
    "phone": "+1-555-0101",
    "role": "STUDENT",
    "status": "ACTIVE",
    "organizationId": null,
    "studentId": "STU-10992",
    "isTemporaryPassword": false
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
- 401 Unauthorized (missing/invalid token).
- 404 Not Found (user not found).

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

### POST /api/v1/organizations

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "name": "Northside Learning",
  "description": "Community learning center",
  "address": "123 Learning Way"
}
```

| Field       | Type   | Required | Constraints / Business Rules                            |
| :---------- | :----- | :------- | :------------------------------------------------------ |
| name        | string | Yes      | Non-empty; unique within tenant scope where applicable. |
| description | string | No       | Optional descriptive text.                              |
| address     | string | No       | Optional physical address.                              |

**Success Response (201):**

```json
{
  "success": true,
  "message": "Organization created",
  "data": {
    "id": "org-12345",
    "name": "Northside Learning",
    "description": "Community learning center",
    "address": "123 Learning Way",
    "ownerId": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a"
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation failure).
- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (caller not an Organizer).
- 409 Conflict (organization name duplicate).

### PATCH /api/v1/organizations/{id}

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "name": "Northside Learning Updated",
  "description": "Updated description"
}
```

| Field       | Type   | Required | Constraints / Business Rules                     |
| :---------- | :----- | :------- | :----------------------------------------------- |
| name        | string | No       | If provided, must be non-empty and owner-unique. |
| description | string | No       | Optional.                                        |

**Success Response (200):**

```json
{
  "success": true,
  "message": "Organization updated",
  "data": {
    "id": "org-12345",
    "name": "Northside Learning Updated",
    "description": "Updated description"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (not owner of organization).
- 404 Not Found (organization not found).

### POST /api/v1/organizations/{organizationId}/staff

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "email": "staff.new@unihub.edu",
  "fullName": "New Staff",
  "role": "STAFF"
}
```

| Field    | Type   | Required | Constraints / Business Rules                                     |
| :------- | :----- | :------- | :--------------------------------------------------------------- |
| email    | string | Yes      | Must be a valid email format; duplicate emails return 409.       |
| fullName | string | Yes      | Non-empty.                                                       |
| role     | string | Yes      | One of `STAFF`; client cannot provision higher privileged roles. |

**Note:** The organizer does not provide a password. The system generates a cryptographically strong temporary password and sends it to the staff member's email through an internal event.

**Success Response (201):**

```json
{
  "success": true,
  "message": "Staff account provisioned",
  "data": {
    "id": "user-54321",
    "email": "staff.new@unihub.edu",
    "fullName": "New Staff",
    "role": "STAFF",
    "organizationId": "org-12345",
    "isTemporaryPassword": true
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (caller not Organizer or organization mismatch).
- 409 Conflict (email already exists).

### PATCH /api/v1/users/{userId}/status

**Security:** Bearer Token (Organizer)

**Request Body:**

```json
{
  "status": "LOCKED"
}
```

| Field  | Type   | Required | Constraints / Business Rules                                         |
| :----- | :----- | :------- | :------------------------------------------------------------------- |
| status | string | Yes      | One of `ACTIVE`, `LOCKED`. Organizers cannot lock their own account. |

**Success Response (200):**

```json
{
  "success": true,
  "message": "User status updated",
  "data": {
    "id": "user-54321",
    "status": "LOCKED"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

**Common Errors:**

- 400 Bad Request (invalid status).
- 401 Unauthorized (missing/invalid token).
- 403 Forbidden (non-owner attempting action or organizer locking self).
- 404 Not Found (user not found).
