# UniHub Workshop - Users Postman Guide

This guide covers the authenticated IAM APIs under `/api/v1/users` and `/api/v1/organizations`. Send requests to `http://localhost/api/v1/...` through Nginx so you can verify load balancing through `metadata.processedBy` in the JSON envelope.

## 1. Test Setup

### Postman Environment

Create a Postman environment with these variables:

- `baseUrl` = `http://localhost/api/v1`
- `accessToken` = empty string
- `refreshToken` = empty string
- `staffId` = empty string
- `staffEmail` = `staff.new@unihub.edu`
- `organizationId` = empty string
- `organizationName` = `Faculty of Information Technology`
- `studentPassword` = `Password123!`
- `studentNewPassword` = `Password456!`

### Global Checks

- All requests must include `Authorization: Bearer {{accessToken}}` unless the request is a public login or OTP flow used to prepare test data.
- Confirm `metadata.processedBy` alternates between `unihub-app-1` and `unihub-app-2` when the load balancer distributes requests.
- For organizer-only endpoints, verify the caller is an organizer before starting.
- For staff provisioning, keep the backend console visible because the temporary password is logged only for QA.

## 2. Async Event / Temporary Credential Testing Paths

### Staff Provisioning Password

The staff account provisioning flow publishes an event that carries the generated temporary password. The current dev environment uses a console log workaround so testers can capture the password immediately.

#### Path 1. Future Production

- Retrieve the temporary password from the staff member's email inbox once the notification module is implemented.
- Confirm the email content matches the exact password used during provisioning.

#### Path 2. Current Dev Workaround

You can follow these instructions:

- Check the Spring Boot console for the temporary QA log line.
- Use the plaintext password from the log to sign in as the new staff user.

Temporary console log example:

- `TEMP TEST ONLY - Generated temporary staff password for <email>: <password>`

> [!NOTE]
>
> - The password is hashed in the database, so it cannot be recovered from the `users` table.
> - Remove the temporary log statement after QA is complete.

## 3. POST `/users/me/logout`

### Purpose

End the session by blacklisting the refresh token.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/users/me/logout`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

### Sample Response

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

### Edge Cases

- Omit the refresh token body and confirm logout still succeeds for the access token session.
- Send an invalid refresh token and confirm `400 Bad Request`.
- Send the request with an invalid or missing access token and confirm `401 Unauthorized`.

## 4. GET `/users/me`

### Purpose

Fetch the authenticated user profile.

### Postman Steps

1. **Method:** `GET`
2. **URL:** `{{baseUrl}}/users/me`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "Profile retrieved",
  "data": {
    "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
    "email": "organizer@unihub.edu",
    "fullName": "Riley Organizer",
    "phone": "+15550000001",
    "role": "ORGANIZER",
    "status": "ACTIVE",
    "organizationId": "1abf4f71-8c42-4af8-9d0f-4f8d8d6f6d5d",
    "studentId": null,
    "lastLoginAt": "2026-05-11T10:00:00Z",
    "isTemporaryPassword": false
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Omit the `Authorization` header and confirm `401 Unauthorized`.
- Use an expired token and confirm `401 Unauthorized`.
- Verify `organizationId` is populated for organizers and staff, and `studentId` remains optional.

## 5. PATCH `/users/me`

### Purpose

Update the authenticated user profile using partial-field semantics.

### Postman Steps

1. **Method:** `PATCH`
2. **URL:** `{{baseUrl}}/users/me`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "fullName": "Riley Organizer Updated",
  "phone": "+15550000002"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Profile updated",
  "data": {
    "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
    "email": "organizer@unihub.edu",
    "fullName": "Riley Organizer Updated",
    "phone": "+15550000002",
    "role": "ORGANIZER",
    "status": "ACTIVE",
    "organizationId": "1abf4f71-8c42-4af8-9d0f-4f8d8d6f6d5d",
    "studentId": null,
    "lastLoginAt": "2026-05-11T10:00:00Z",
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

### Edge Cases

- Send no fields and confirm `400 Bad Request`.
- Send an invalid phone format and confirm `400 Bad Request`.
- Send `phone: null` and confirm the number is cleared.

## 6. PUT `/users/me/password`

### Purpose

Change the authenticated user's password and clear the temporary-password restriction.

### Postman Steps

1. **Method:** `PUT`
2. **URL:** `{{baseUrl}}/users/me/password`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "currentPassword": "{{studentPassword}}",
  "newPassword": "{{studentNewPassword}}",
  "confirmPassword": "{{studentNewPassword}}"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Password changed",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "refreshExpiresInSeconds": 2592000,
    "user": {
      "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
      "email": "organizer@unihub.edu",
      "fullName": "Riley Organizer",
      "phone": "+15550000001",
      "role": "ORGANIZER",
      "status": "ACTIVE",
      "organizationId": "1abf4f71-8c42-4af8-9d0f-4f8d8d6f6d5d",
      "studentId": null,
      "lastLoginAt": "2026-05-11T10:00:00Z",
      "isTemporaryPassword": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Send mismatched passwords and confirm `400 Bad Request`.
- Use a wrong current password and confirm `401 Unauthorized`.
- After success, log in again with the new password and confirm the old password no longer works.

## 7. POST `/organizations`

### Purpose

Create an organization owned by the authenticated organizer.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/organizations`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "name": "{{organizationName}}",
  "description": "Organizer QA org",
  "address": "123 Learning Way"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Organization created",
  "data": {
    "id": "org-12345",
    "name": "Faculty of Information Technology",
    "description": "Organizer QA org",
    "address": "123 Learning Way",
    "status": "ACTIVE",
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

### Edge Cases

- Try to create a second organization for the same organizer and confirm `409 Conflict`.
- Use a non-organizer token and confirm `403 Forbidden`.
- Use a missing token and confirm `401 Unauthorized`.

## 8. PATCH `/organizations/{id}`

### Purpose

Update only the organization owned by the authenticated organizer.

### Postman Steps

1. **Method:** `PATCH`
2. **URL:** `{{baseUrl}}/organizations/{{organizationId}}`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "name": "Northside Learning QA Updated",
  "description": "Updated description"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Organization updated",
  "data": {
    "id": "org-12345",
    "name": "Northside Learning QA Updated",
    "description": "Updated description",
    "address": "123 Learning Way",
    "status": "ACTIVE",
    "ownerId": "03f7237f-8049-4649-bbd7-5f3b8ca9ec07"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Use an organization ID that belongs to another organizer and confirm `403 Forbidden`.
- Use a missing organization ID and confirm `404 Not Found`.
- Leave the body empty and confirm validation fails with `400 Bad Request`.

## 9. GET `/organizations/{organizationId}/staff`

### Purpose

Fetch the organizer's staff list with pagination.

### Postman Steps

1. **Method:** `GET`
2. **URL:** `{{baseUrl}}/organizations/{{organizationId}}/staff?page=0&size=20`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "Staff list retrieved",
  "data": {
    "items": [
      {
        "id": "d4f5a1a1-1f7d-4d59-8aa1-5bce7d7e1c10",
        "email": "staff1@unihub.edu",
        "fullName": "Jordan Lee",
        "role": "STAFF",
        "status": "ACTIVE",
        "lastLoginAt": "2026-05-11T10:00:00Z",
        "isTemporaryPassword": false
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Use another organizer's organization ID and confirm `403 Forbidden`.
- Use a missing organization ID and confirm `404 Not Found`.
- Use `page=1&size=1` and confirm the `pagination` object updates correctly.
- Verify the response uses `data.items` and not a root-level list.

## 10. POST `/organizations/{organizationId}/staff`

### Purpose

Provision a new staff account tied to the organizer's organization.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/organizations/{{organizationId}}/staff`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "email": "{{staffEmail}}",
  "fullName": "Staff One"
}
```

### Sample Response

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
    "lastLoginAt": null,
    "isTemporaryPassword": true
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-14T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Reuse an existing email and confirm `409 Conflict`.
- Use a non-organizer token and confirm `403 Forbidden`.
- Use an organization not owned by the caller and confirm `403 Forbidden`.

### Two-Path Testing for Staff Provisioning

#### Path 1. Future Production

- Retrieve the temporary password from the staff member's inbox.
- Log in with the password to confirm the invitation email is correct.

#### Path 2. Current Dev Workaround

- Check the Spring Boot console for the temporary QA log line.
- Copy the plaintext password from the log.
- Use it to sign in as the new staff account.

Temporary console log example:

- `TEMP TEST ONLY - Generated temporary staff password for <email>: <password>`

## 11. PATCH `/users/{userId}/status`

### Purpose

Activate or lock a staff account in the organizer's tenant.

### Postman Steps

1. **Method:** `PATCH`
2. **URL:** `{{baseUrl}}/users/{{staffId}}/status`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "status": "LOCKED"
}
```

### Sample Response

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

### Edge Cases

- Try to lock the organizer's own account and confirm `403 Forbidden`.
- Use a target user outside the organizer's organization and confirm `403 Forbidden`.
- Use an invalid status and confirm `400 Bad Request`.

## 12. Force Password Change Flow

Use this scenario immediately after provisioning a staff account.

1. Log in with the newly created staff account using the generated temporary password.
2. Confirm the login response indicates `isTemporaryPassword = true` and returns the restricted login variant.
3. Call a protected endpoint such as `GET /api/v1/users/me` with the staff access token.
4. Verify the call returns `403 Forbidden` because the account still has `isTemporaryPassword = true`.
5. Call `PUT /api/v1/users/me/password` with the current temporary password and the new password.
6. Confirm the response returns a payload containing `accessToken` and `refreshToken`, and the `user` object shows `isTemporaryPassword = false`.
7. Use the returned `accessToken` to call the protected endpoint.
8. Confirm access is now granted and the endpoint returns the expected data.

Recommended endpoint order for the check:

- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `PUT /api/v1/users/me/password`
- `GET /api/v1/users/me`

## 13. Validation Checklist

- Staff provisioning is tenant bound to the organizer's organization.
- Staff list responses use `data.items` and `data.pagination`.
- Temporary passwords are only recoverable through the QA log during current dev testing.
- `metadata.processedBy` alternates between the two backend nodes when routed through Nginx.
