# UniHub Workshop - Authentication & Authorization Specs

## 1. Login

Defines user sign-in behavior and token issuance.

| Field               | Details                                                                                                                                                                                                                                                                                                                                                                                                   |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Login                                                                                                                                                                                                                                                                                                                                                                                                     |
| Description         | Handles user sign-in and returns tokens for authenticated sessions.                                                                                                                                                                                                                                                                                                                                       |
| Main Flow           | 1. Validate email/password format.<br>2. Verify user exists and status is `ACTIVE`.<br>3. Check that the account/IP is not under a temporary lockout.<br>4. Compare password hash using BCrypt.<br>5. Issue short-lived JWT access token and long-lived refresh token with a unique `jti` claim.<br>6. Update `last_login_at` and reset the failed attempts counter.<br>7. Return tokens and role claims. |
| Error Scenarios     | - Invalid credentials increment the failed attempts counter and return 401.<br>- User status `UNVERIFIED`/`LOCKED` returns 403.<br>- Missing required fields return 400.<br>- Too many failed attempts trigger temporary lockout or return 429.                                                                                                                                                           |
| Constraints         | - User must be `ACTIVE` to login.<br>- Passwords must be stored and compared with BCrypt.<br>- Access token TTL is short; refresh token TTL is longer and revocable.<br>- Refresh token must include a unique `jti` claim.<br>- Failed attempts are tracked and rate-limited per email.                                                                                                                   |
| Acceptance Criteria | - Valid credentials return access and refresh tokens with role claim and `jti`.<br>- `last_login_at` is updated on successful login.<br>- Invalid credentials never reveal which field failed.<br>- Brute-force attempts trigger lockout/429 behavior.                                                                                                                                                    |

## 2. Register

Defines user onboarding and verification initiation.

| Field               | Details                                                                                                                                                                                                                                                  |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Register                                                                                                                                                                                                                                                 |
| Description         | Registers a new account and initiates verification.                                                                                                                                                                                                      |
| Main Flow           | 1. Validate input fields (email, password, name).<br>2. Create user with status `UNVERIFIED`.<br>3. Generate 6-digit OTP and store in Redis with 5-minute TTL.<br>4. Publish outbox event for verification email.<br>5. Return generic success response. |
| Error Scenarios     | - Duplicate email returns 409.<br>- Invalid fields return 400.<br>- Redis/outbox failure returns 202 with retry scheduled or 503 when critical.                                                                                                          |
| Constraints         | - Email must be unique and normalized.<br>- OTP TTL is 5 minutes and single-use.<br>- Outbox event must be written in the same transaction as user creation.                                                                                             |
| Acceptance Criteria | - User is stored as `UNVERIFIED`.<br>- OTP exists in Redis with correct TTL.<br>- Verification email event is emitted.                                                                                                                                   |

## 3. Verify OTP

Defines OTP verification behavior for multiple intents.

| Field               | Details                                                                                                                                                                                                                                      |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Verify OTP                                                                                                                                                                                                                                   |
| Description         | Verifies one-time codes and applies the appropriate account action.                                                                                                                                                                          |
| Main Flow           | 1. Validate email, OTP, and intent.<br>2. Load OTP from Redis and compare.<br>3. If intent is `REGISTER`, set user status to `ACTIVE`.<br>4. If intent is `RESET_PASSWORD`, issue temporary reset token.<br>5. Invalidate OTP after success. |
| Error Scenarios     | - OTP missing/expired returns 400.<br>- OTP mismatch returns 401.<br>- Unsupported intent returns 400.                                                                                                                                       |
| Constraints         | - OTP is single-use and deleted after validation.<br>- Reset token is short-lived and bound to user/email.                                                                                                                                   |
| Acceptance Criteria | - `REGISTER` intent activates the user and removes OTP.<br>- `RESET_PASSWORD` intent returns a reset token only.<br>- OTP cannot be reused.                                                                                                  |

## 4. Forgot Password

Defines password recovery initiation without user enumeration.

| Field               | Details                                                                                                                                                                          |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Forgot Password                                                                                                                                                                  |
| Description         | Starts the password recovery flow without revealing account existence.                                                                                                           |
| Main Flow           | 1. Accept email input.<br>2. If email exists, generate OTP and store in Redis with 5-minute TTL.<br>3. Emit outbox event for reset email.<br>4. Return generic success response. |
| Error Scenarios     | - Invalid email format returns 400.<br>- Redis/outbox failure returns 202 with retry scheduled or 503 when critical.                                                             |
| Constraints         | - Response must not reveal user existence.<br>- OTP TTL is 5 minutes and single-use.                                                                                             |
| Acceptance Criteria | - Response is identical for existing and non-existing emails.<br>- Existing users receive OTP and notification event.                                                            |

## 5. Reset Password

Defines credential reset behavior after verification.

| Field               | Details                                                                                                                                                                                    |
| :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Reset Password                                                                                                                                                                             |
| Description         | Updates credentials after verification and hardens session security.                                                                                                                       |
| Main Flow           | 1. Validate reset token and password confirmation.<br>2. Verify token ownership and validity.<br>3. Hash new password with BCrypt and update DB.<br>4. Revoke all existing refresh tokens. |
| Error Scenarios     | - Invalid/expired reset token returns 401.<br>- Password mismatch returns 400.<br>- Weak password returns 400.                                                                             |
| Constraints         | - Reset token is short-lived and single-use.<br>- Passwords must be hashed with BCrypt.<br>- All refresh tokens are invalidated on success.                                                |
| Acceptance Criteria | - Password update succeeds and old sessions are revoked.<br>- Reset token cannot be reused.<br>- Old password no longer authenticates.                                                     |

## 6. Refresh Token

Defines session renewal behavior using refresh tokens.

| Field               | Details                                                                                                                                                                                |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Refresh Token                                                                                                                                                                          |
| Description         | Renews access for an authenticated session using refresh credentials.                                                                                                                  |
| Main Flow           | 1. Validate refresh token structure and signature.<br>2. Check revocation list.<br>3. Issue new access token and optionally rotate refresh token.<br>4. Return tokens and role claims. |
| Error Scenarios     | - Missing/invalid refresh token returns 401.<br>- Revoked refresh token returns 401.<br>- Expired refresh token returns 401.                                                           |
| Constraints         | - Refresh tokens must be trackable for revocation.<br>- Rotation invalidates the previous refresh token if enabled.                                                                    |
| Acceptance Criteria | - Valid refresh returns a new access token.<br>- Revoked refresh token is rejected.<br>- Rotation invalidates the prior token.                                                         |

## 7. Logout

Defines logout behavior and token revocation.

| Field               | Details                                                                                                              |
| :------------------ | :------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Logout                                                                                                               |
| Description         | Revokes refresh capability to end an authenticated session.                                                          |
| Main Flow           | 1. Accept refresh token input.<br>2. Add token to Redis blocklist with remaining TTL.<br>3. Return success response. |
| Error Scenarios     | - Missing token returns 400.<br>- Invalid token format returns 400.                                                  |
| Constraints         | - Blocklist TTL must match refresh token expiry.<br>- Logout must be idempotent for the same token.                  |
| Acceptance Criteria | - Blocklisted token cannot be used for refresh.<br>- Repeat logout does not error.                                   |

## 8. JWT & RBAC Middleware

Defines token validation and role enforcement at the boundary.

| Field               | Details                                                                                                                                                                                                                           |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | JWT & RBAC Middleware (Internal)                                                                                                                                                                                                  |
| Description         | Validates tokens and enforces role-based access for protected operations.                                                                                                                                                         |
| Main Flow           | 1. Extract `Authorization: Bearer` header.<br>2. Validate signature and expiration.<br>3. Extract `sub` and `role` claims.<br>4. Enforce endpoint role rules (e.g., `STAFF`).<br>5. Forward authenticated request to controllers. |
| Error Scenarios     | - Missing token returns 401.<br>- Invalid/expired token returns 401.<br>- Insufficient role returns 403.                                                                                                                          |
| Constraints         | - All protected endpoints must pass middleware validation.<br>- Role claim is the sole RBAC input.                                                                                                                                |
| Acceptance Criteria | - Valid token grants access per role rules.<br>- Invalid token never reaches controllers.<br>- Role mismatch returns 403.                                                                                                         |

## 9. Data Ownership Validation

Defines ownership checks for protected resource mutations.

| Field               | Details                                                                                                                           |
| :------------------ | :-------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Data Ownership Validation (Internal)                                                                                              |
| Description         | Ensures resource mutations are limited to the owning user.                                                                        |
| Main Flow           | 1. Load target resource after RBAC check.<br>2. Compare `jwt.sub` with resource `owner_id`.<br>3. Allow mutation only if matched. |
| Error Scenarios     | - Ownership mismatch returns 403.<br>- Resource not found returns 404.                                                            |
| Constraints         | - Ownership checks are required for organizer-scoped mutations.<br>- No cross-tenant data access is allowed.                      |
| Acceptance Criteria | - Owner can update resource successfully.<br>- Non-owner receives 403 even with valid role.                                       |

## 10. Rate Limiting (Token Bucket)

Defines gateway throttling behavior for traffic protection.

| Field               | Details                                                                                                                                                          |
| :------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Rate Limiting (Token Bucket)                                                                                                                                     |
| Description         | Protects APIs by enforcing per-user/IP token bucket limits.                                                                                                      |
| Main Flow           | 1. API Gateway checks token bucket in Redis.<br>2. If tokens available, consume and forward request.<br>3. If exhausted, reject with 429 and rate-limit headers. |
| Error Scenarios     | - Redis unavailable returns 503 or degraded mode per policy.<br>- Token bucket empty returns 429 with retry hints.                                               |
| Constraints         | - Bucket4j uses Redis for distributed state.<br>- `X-RateLimit-Remaining` and `X-RateLimit-Retry-After` must be returned.                                        |
| Acceptance Criteria | - Requests above limit return 429.<br>- Allowed requests are forwarded without delay.<br>- Rate-limit headers are present.                                       |
