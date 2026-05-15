# UniHub Workshop - Authentication Postman Guide

This guide covers the public authentication APIs under `/api/v1/auth`. All requests should be sent to `http://localhost/api/v1/...` through Nginx so that load balancing can be verified from the JSON envelope `metadata.processedBy` field.

## 1. Test Setup

### Postman Environment

Create a Postman environment with these variables:

- `baseUrl` = `http://localhost/api/v1`
- `studentEmail` = `student1@unihub.local`
- `studentPassword` = `Password123!`
- `studentNewPassword` = `Password456!`
- `resetToken` = empty string
- `accessToken` = empty string
- `refreshToken` = empty string

### Global Checks

- Use the Nginx entrypoint at `http://localhost/api/v1/...` only.
- Verify `metadata.processedBy` alternates between `unihub-app-1` and `unihub-app-2` when traffic is sent through the load balancer.
- Confirm all responses use the standard JSON envelope.
- For public endpoints, do not send an `Authorization` header.

## 2. Async Event / OTP Testing Paths

The authentication module has OTP-based flows that currently depend on Redis and temporary console logging for verification.

### Registration OTP, Forgot Password OTP, and Resend OTP

#### Path 1. Future Production

- Retrieve the OTP from the user inbox after the notification module is available.
- Confirm the email contains the exact 6-digit code that matches the request.
- Use that OTP in `verify-otp`.

#### Path 2. Current Dev Workaround

There are two options:

- **Option A:** Check the Spring Boot console for the temporary QA log line.
- **Option B:** Read the OTP directly from Redis.

Temporary console log examples:

- `TEMP TEST ONLY - Generated registration OTP for <email>: <otp>`
- `TEMP TEST ONLY - Generated registration OTP resend for <email>: <otp>`
- `TEMP TEST ONLY - Generated forgot-password OTP for <email>: <otp>`

Redis commands for OTP retrieval:

```bash
# Bash
docker exec -it <redis-container> redis-cli
GET otp:<email_lowercased>
TTL otp:<email_lowercased>

# PowerShell
docker exec -it <redis-container> redis-cli GET otp:<email_lowercased>
docker exec -it <redis-container> redis-cli TTL otp:<email_lowercased>
```

> [!NOTE]
>
> - The key is normalized to lowercase.
> - The OTP TTL is 5 minutes.
> - If Redis returns `(nil)`, the OTP expired or was consumed.

## 3. POST `/auth/register`

### Purpose

Create a new account and trigger OTP generation for account verification.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/register`
3. **Headers:** none
4. **Body:** `raw` JSON

```json
{
  "email": "{{studentEmail}}",
  "password": "{{studentPassword}}",
  "fullName": "Student One",
  "phone": "+15551234567"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Registration initiated",
  "data": {
    "email": "student1@unihub.local",
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

### Edge Cases

- Reuse an active email and confirm `409 Conflict`.
- Send an invalid email format and confirm `400 Bad Request`.
- Send the same email again within the cooldown window and confirm `429 Too Many Requests`.

### Testing Async Event

- **Future production:** Retrieve the OTP from email.
- **Current dev:** Use the temporary log line or Redis key `otp:<email_lowercased>`.

## 4. POST `/auth/verify-otp`

### Purpose

Verify the OTP for registration or password reset.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/verify-otp`
3. **Headers:** none
4. **Body:** `raw` JSON

Registration example:

```json
{
  "email": "{{studentEmail}}",
  "otp": "123456",
  "intent": "REGISTER"
}
```

Password reset example:

```json
{
  "email": "{{studentEmail}}",
  "otp": "123456",
  "intent": "RESET_PASSWORD"
}
```

### Sample Responses

Registration:

```json
{
  "success": true,
  "message": "Account verified",
  "data": {
    "email": "student1@unihub.local",
    "status": "ACTIVE"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

Reset token issuance:

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

### Edge Cases

- Use an expired or missing OTP and confirm `400 Bad Request`.
- Use a wrong OTP and confirm `401 Unauthorized`.
- Use the wrong `intent` for the user state and confirm `409 Conflict`.
- Reuse the same OTP and confirm it is rejected.

## 5. POST `/auth/resend-otp`

### Purpose

Generate a fresh OTP for registration or password recovery.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/resend-otp`
3. **Headers:** none
4. **Body:** `raw` JSON

```json
{
  "email": "{{studentEmail}}",
  "intent": "REGISTER"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "A new OTP has been sent",
  "data": null,
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Request too quickly and confirm `429 Too Many Requests`.
- Use a non-existent email and confirm `404 Not Found`.
- Request a resend after verification and confirm the state rules are enforced.

### Testing Async Event

- **Future production:** Fetch the OTP from the inbox.
- **Current dev:** Check the console log or Redis key.

## 6. POST `/auth/login`

### Purpose

Exchange valid credentials for JWT access and refresh tokens.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/login`
3. **Headers:** none
4. **Body:** `raw` JSON

```json
{
  "email": "{{studentEmail}}",
  "password": "{{studentPassword}}"
}
```

### Sample Response

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
    "user": {
      "id": "6c3f3a7c-1f15-4f9b-a0df-8d7d1d9c8f2a",
      "email": "student1@unihub.local",
      "fullName": "Student One",
      "phone": "+15551234567",
      "role": "STUDENT",
      "status": "ACTIVE",
      "organizationId": null,
      "studentId": null,
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

### Edge Cases

- Use an invalid password and confirm `401 Unauthorized`.
- Use an unverified or locked user and confirm `403 Forbidden`.
- Use a temporary-password staff account and confirm the restricted login response is returned.

### Rate Limiting Test with Postman Runner

Use this flow to validate the distributed token bucket at the load balancer.

1. Put the login request into a collection.
2. Open Postman Runner.
3. Set `Iterations` to `101`.
4. Set `Delay` to `0`.
5. Run the collection against `{{baseUrl}}/auth/login`.
6. Use the same valid test account for every iteration.
7. Inspect successful responses and confirm `metadata.processedBy` alternates between `unihub-app-1` and `unihub-app-2`.
8. Confirm the 101st request returns `429 Too Many Requests`.
9. If the 101st request does not return `429`, the distributed rate-limit configuration is not behaving as expected.

> [!NOTE]
>
> - Do not use an invalid password for the runner test, because authentication failures can trigger account lockout before the rate limit test is complete.
> - The request must go through `http://localhost/api/v1/auth/login`, not directly to a backend port.

## 7. POST `/auth/refresh`

### Purpose

Rotate a refresh token and obtain a new access token.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/refresh`
3. **Headers:** none
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
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Use a blacklisted token and confirm `401 Unauthorized`.
- Use an expired token and confirm `401 Unauthorized`.
- Use a token for a non-active user and confirm `403 Forbidden`.

## 8. POST `/auth/forgot-password`

### Purpose

Start the recovery flow without revealing whether the email exists.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/forgot-password`
3. **Headers:** none
4. **Body:** `raw` JSON

```json
{
  "email": "{{studentEmail}}"
}
```

### Sample Response

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

### Edge Cases

- Send an invalid email format and confirm `400 Bad Request`.
- Repeat immediately and confirm `429 Too Many Requests`.
- Use an email that does not exist and confirm the response still looks identical.

### Testing Async Event

- **Future production:** Retrieve the OTP from email.
- **Current dev:** Check the console log or Redis key.

## 9. POST `/auth/reset-password`

### Purpose

Complete password recovery using a reset token.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/auth/reset-password`
3. **Headers:** none
4. **Body:** `raw` JSON

```json
{
  "resetToken": "{{resetToken}}",
  "newPassword": "{{studentNewPassword}}",
  "confirmPassword": "{{studentNewPassword}}"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Password reset successful",
  "data": null,
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-11T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Make the passwords mismatch and confirm `400 Bad Request`.
- Use an invalid or expired reset token and confirm `401 Unauthorized`.
- Reuse the same reset token and confirm it is rejected.

## 10. Quick Validation Checklist

- The login response contains plain role names such as `STUDENT`, `ORGANIZER`, and `STAFF`.
- The `metadata.processedBy` value alternates between the two backend nodes when load balanced through Nginx.
- OTP-based endpoints can be tested from logs or Redis until the notification module is available.
- The rate-limit runner test must end with the 101st request returning `429 Too Many Requests`.
