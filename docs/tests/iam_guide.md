# UniHub Workshop - IAM Test Guide

Since you are running two app instances (`unihub-app-1` and `unihub-app-2`), every response will contain an envelope with a `metadata` object.

- **Load Balancing Check:** In every successful response, verify the `metadata.processedBy` field. If sending requests via a Load Balancer (e.g., Nginx), you should see this value toggle between `unihub-app-1` and `unihub-app-2` (Round Robin).
- **Logging Check:** Check the console logs of the active instance. You should see a log entry from `RequestLoggingFilter` containing your `clientIp` and `userAgent`.
- **Consistency Check:** Perform 50 requests to `unihub-app-1`, then 51 requests to `unihub-app-2`. The 101st request (on `unihub-app-2`) must be blocked, confirming the distributed rate limit is working across different nodes.

## 1. Registration Flow

### POST `/api/v1/auth/register`

- **Purpose:** Create a new user account and trigger OTP delivery.
- **Steps to Test:**
  1. Send a registration request with a unique email, password, full name, and phone number.
  2. Confirm the response returns a verification-required payload.
  3. Retrieve the OTP using the guide below and continue with OTP verification.
- **Sample Request (Payload):**

```json
{
  "email": "student1@unihub.local",
  "password": "Password123!",
  "fullName": "Student One",
  "phone": "+15551234567"
}
```

- **Success Criteria:** HTTP `201 Created`; response contains `success: true`, `data.email`, `data.status` set to `UNVERIFIED`, and `data.verificationRequired: true`.
- **Edge Cases:**
  - Reuse an already-active email and verify the API returns `409 Conflict`.
  - Send an invalid phone format and verify validation fails with `400 Bad Request`.

## 2. OTP Verification Flow

### POST `/api/v1/auth/verify-otp`

- **Purpose:** Verify the registration OTP or exchange a password reset OTP for a reset token.
- **Steps to Test:**
  1. Retrieve the OTP from email or Redis.
  2. Submit the OTP with the correct `intent` value.
  3. Confirm the expected account verification or reset-token response.
- **Sample Request (Payload):**

```json
{
  "email": "student1@unihub.local",
  "otp": "123456",
  "intent": "REGISTER"
}
```

- **Success Criteria:** HTTP `200 OK`; registration verification returns `data.email` and `data.status: ACTIVE`. Reset flow returns `data.resetToken` and `data.resetTokenExpiresInSeconds`.
- **Edge Cases:**
  - Use an invalid OTP and verify `401 Unauthorized`.
  - Use the wrong `intent` for the user state and verify `409 Conflict`.

### POST `/api/v1/auth/resend-otp`

- **Purpose:** Request a fresh OTP for registration or password reset.
- **Steps to Test:**
  1. Submit a valid email and intent.
  2. Retrieve the new OTP from the chosen source.
  3. Verify the new code works and the old one no longer does.
- **Sample Request (Payload):**

```json
{
  "email": "student1@unihub.local",
  "intent": "REGISTER"
}
```

- **Success Criteria:** HTTP `200 OK`; response body contains `success: true` and a message indicating a new OTP was sent.
- **Edge Cases:**
  - Re-submit before the cooldown expires and verify `429 Too Many Requests`.
  - Use an email that does not exist and verify `404 Not Found`.

## 3. Authentication Flow

### POST `/api/v1/auth/login`

- **Purpose:** Exchange email and password for access and refresh tokens.
- **Steps to Test:**
  1. Verify the user is active and has a valid password.
  2. Submit the login payload.
  3. Confirm token fields are present in the response.
- **Sample Request (Payload):**

```json
{
  "email": "student1@unihub.local",
  "password": "Password123!"
}
```

- **Success Criteria:** HTTP `200 OK`; response contains `data.accessToken`, `data.refreshToken`. `data.user.role` must be a plain string (e.g., `STUDENT`). Verify there is NO `ROLE_` prefix.
- **Edge Cases:**
  - Use an incorrect password and verify `401 Unauthorized`.
  - Use an inactive user and verify `403 Forbidden`.

### POST `/api/v1/auth/refresh`

- **Purpose:** Rotate a valid refresh token and return a new access token.
- **Steps to Test:**
  1. Login successfully and copy the refresh token.
  2. Submit the refresh request with that token.
  3. Confirm a new access token and refresh token are returned.
- **Sample Request (Payload):**

```json
{
  "refreshToken": "<refresh-token>"
}
```

- **Success Criteria:** HTTP `200 OK`; response contains `data.accessToken`, `data.refreshToken`, and `data.refreshTokenRotated: true`.
- **Edge Cases:**
  - Reuse a blacklisted refresh token and verify `401 Unauthorized`.
  - Submit an expired token and verify `401 Unauthorized`.

### POST `/api/v1/auth/forgot-password`

- **Purpose:** Start the password reset flow without revealing whether the email exists.
- **Steps to Test:**
  1. Submit a valid email address.
  2. Retrieve the OTP from email or Redis.
  3. Use the OTP with the reset-password intent to obtain a reset token.
- **Sample Request (Payload):**

```json
{
  "email": "student1@unihub.local"
}
```

- **Success Criteria:** HTTP `200 OK`; response contains `data.requestAccepted: true`.
- **Edge Cases:**
  - Repeat the request immediately and verify `429 Too Many Requests` due to cooldown.
  - Submit an invalid email format and verify `400 Bad Request`.

### POST `/api/v1/auth/reset-password`

- **Purpose:** Complete password reset using a valid reset token.
- **Steps to Test:**
  1. Obtain a reset token from the OTP verification flow.
  2. Submit the reset payload with matching passwords.
  3. Log in with the new password.
- **Sample Request (Payload):**

```json
{
  "resetToken": "<reset-token>",
  "newPassword": "NewPassword123!",
  "confirmPassword": "NewPassword123!"
}
```

- **Success Criteria:** HTTP `200 OK`; response indicates success and the password no longer works with the old value.
- **Edge Cases:**
  - Make the passwords mismatch and verify `400 Bad Request`.
  - Use an invalid or expired reset token and verify `401 Unauthorized`.

## 4. OTP Retrieval Guide

### Scenario A: Production

- Check the email inbox for the account used in the registration or password reset request.
- The OTP is delivered to the user-facing mailbox, so no Redis access is needed.

### Scenario B: Current Dev State - No Notification Module

The OTP is stored in Redis under the lowercase email key.

```bash
docker exec -it <redis_container_name> redis-cli
AUTH <redis_password>
GET otp:<email_address_lowercased>
TTL otp:<email_address_lowercased>
```

Example:

```bash
docker exec -it unihub-redis redis-cli
AUTH redis
GET otp:student1@unihub.local
```

Notes:

- Use the exact Redis container name from your local environment.
- The email key is normalized to lowercase before storage.
- If Redis returns `(nil)`, the OTP expired or was already consumed.

## 5. User Profile Flow

### GET `/api/v1/users/me`

- **Purpose:** Fetch the authenticated user profile.
- **Steps to Test:**
  1. Log in and copy the access token.
  2. Call the endpoint with the `Authorization` header.
  3. Confirm the returned profile matches the logged-in account.
- **Sample Request (Payload):**

```json
{}
```

- **Authorization Header:** `Authorization: Bearer <access-token>`
- **Success Criteria:** HTTP `200 OK`; response contains `data.id`, `data.email`, `data.fullName`, and `data.status`.
- **Edge Cases:**
  - Omit the `Authorization` header and verify `401 Unauthorized`.
  - Use an invalid or expired token and verify `401 Unauthorized`.

### PATCH `/api/v1/users/me`

- **Purpose:** Update the authenticated user profile with partial-field semantics.
- **Steps to Test:**
  1. Log in and copy the access token.
  2. Send only the fields you want to change.
  3. Verify that omitted fields remain unchanged and explicit `null` clears a nullable field.
- **Sample Request (Payload):**

```json
{
  "fullName": "Student One Updated",
  "phone": null
}
```

- **Authorization Header:** `Authorization: Bearer <access-token>`
- **Success Criteria:** HTTP `200 OK`; `data.fullName` reflects the new value and `data.phone` becomes `null` when `"phone": null` is sent.
- **Edge Cases:**
  - Send no fields and verify `400 Bad Request`.
  - Send an invalid phone format and verify `400 Bad Request`.

## 6. Session Management Flow

### PUT `/api/v1/users/me/password`

- **Purpose:** Change the authenticated user's password.
- **Steps to Test:**
  1. Log in and copy the access token.
  2. Submit the current password, a new password, and the confirmation.
  3. Log out and verify the new password works.
- **Sample Request (Payload):**

```json
{
  "currentPassword": "Password123!",
  "newPassword": "Password456!",
  "confirmPassword": "Password456!"
}
```

- **Authorization Header:** `Authorization: Bearer <access-token>`
- **Success Criteria:** HTTP `200 OK`; password changes successfully and the old password no longer authenticates.
- **Edge Cases:**
  - Make the new password and confirmation mismatch and verify `400 Bad Request`.
  - Provide the wrong current password and verify `401 Unauthorized`.

### POST `/api/v1/users/me/logout`

- **Purpose:** Revoke the current session refresh token.
- **Steps to Test:**
  1. Log in and copy the refresh token.
  2. Call logout with the refresh token.
  3. Attempt to reuse the same refresh token and confirm it fails.
- **Sample Request (Payload):**

```json
{
  "refreshToken": "<refresh-token>"
}
```

- **Authorization Header:** `Authorization: Bearer <access-token>`
- **Success Criteria:** HTTP `200 OK`; the token is blacklisted and cannot be used for refresh again.
- **Edge Cases:**
  - Omit `refreshToken` and verify the request still completes successfully.
  - Send a token that belongs to another user and verify `401 Unauthorized`.

## 7. Infrastructure Rate Limit Test

**Objective:** Verify that the 100 req/min limit is enforced globally across both `unihub-app-1` and `unihub-app-2` using the shared Redis state.

### Preparation

- **Load Balancer URL:** `http://localhost` (Nginx).
- **Expectation:** Requests 1-100 should return `200 OK`. Request 101+ must return `429 Too Many Requests`.
- **Verification:** Check `metadata.processedBy` in responses to confirm Nginx is distributing traffic to both ports.

### Method 1: Using Apache Benchmark (`ab`)

```bash
# -n: total requests, -c: concurrency, -p: file containing login JSON, -T: content-type
ab -n 110 -c 5 \
  -p login_payload.json \
  -T "application/json" \
  http://localhost/api/v1/auth/login
```

### Method 2: Using `hey`

```bash
# -n: total requests, -c: concurrency, -m: method, -D: data file
hey -n 110 -c 5 -m POST \
  -D login_payload.json \
  -T "application/json" \
  http://localhost/api/v1/auth/login
```

### Method 3: Using Postman Runner

This is the most visual way to monitor the processedBy metadata across instances.

1. **Prepare the Request:**
   - Open Postman and create a `POST` request to `http://localhost/api/v1/auth/login`.
   - In the **Body** tab, select `raw` then `JSON` and enter valid login credentials.
   - Save this request into a Collection (e.g., "IAM Module").
2. **Open the Runner:**
   - In the left sidebar, click on the **Collections** tab.
   - Click the **three dots (...)** next to your "IAM Module" collection.
   - Select **Run**.
3. **Configure the Run:**
   - On the right side (**Functional** tab), ensure your `Login` request is checked.
   - **Iterations:** Change this value to `110`.
   - **Delay:** Set to `0ms` (to simulate a spike).
   - **Data:** Leave as is.
4. **Execute and Observe:**
   - Click the big blue button: **Start run**.
   - **In the Results Table:**
     - Scroll through the list. You should see `200 OK` for the first 100 entries.
     - At **Iteration 101**, the status must change to **429 Too Many Requests**.
   - **Verify Load Balancing:** Click on any `200 OK` result, go to the **Response Body**, and check `metadata.processedBy`. You should see it alternating between `unihub-app-1` and `unihub-app-2`.
5. **Verify Shared Redis State:** Even if Nginx alternates requests between `unihub-app-1` and `unihub-app-2`, the total count must still hit the limit at exactly the 101st request. This proves both instances are using the same "bucket" in Redis.

### Edge Cases

- Change the source IP or proxy path and verify the bucket is isolated per IP.
- Wait a full minute and verify the bucket refills and requests are accepted again.
