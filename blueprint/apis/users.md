# UniHub Workshop - User API Contracts

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

- Error payloads use RFC 7807 inside `error`.
- **Required headers:** `Authorization: Bearer <token>`.
- All JSON keys are **camelCase**.

## 2. Contracts

### POST /api/v1/users/logout

**Security:** Bearer Token (Student, Organizer, Staff)

**Request Body:**

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

| Field        | Type   | Required | Constraints / Business Rules   |
| :----------- | :----- | :------- | :----------------------------- |
| refreshToken | string | Yes      | Must be a valid refresh token. |

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

- 400 Bad Request (missing or malformed refresh token).
- 401 Unauthorized (missing/invalid token).

### PUT /api/v1/users/change-password

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

| Field    | Type   | Required | Constraints / Business Rules                                                                  |
| :------- | :----- | :------- | :-------------------------------------------------------------------------------------------- |
| fullName | string | No       | If provided, must be non-empty. At least one of `fullName` or `phone` is required.            |
| phone    | string | No       | If provided, must be a valid phone format. At least one of `fullName` or `phone` is required. |

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

> **Developer Note:** `studentId` can be `null` for users who registered manually and have not yet been matched by the nightly CSV ingestion process.

**Common Errors:**

- 400 Bad Request (validation).
- 401 Unauthorized (missing/invalid token).
- 404 Not Found (user not found).
