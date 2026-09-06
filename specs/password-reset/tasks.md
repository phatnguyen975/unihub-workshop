# Implementation Plan: Password Reset

> **Spec:** `specs/password-reset/SPEC.md`  
> **API Contract:** `specs/password-reset/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This implementation plan delivers the complete password modification and credential recovery capabilities for Feature 1.4 across four vertical slices:

1. **Password-Reset OTP generation and delivery** via the active-account branch of the shared `POST /api/v1/auth/otp/resend` endpoint.
2. **Atomic single-step forgot password reset** via `POST /api/v1/auth/password/reset` with OTP validation, Argon2id hashing, and global session revocation (PostgreSQL refresh tokens + Redis JTI blacklist).
3. **First-login temporary password exchange** via `POST /api/v1/auth/password/temporary` under the Zero-Trust model (ADR-028), issuing a full authentication session (`accessToken`, `refreshToken`, and `user` profile) for immediate dashboard access.
4. **Concurrent replay protection and state invalidation verification** ensuring single-use OTP consumption and temporary password transitions naturally guard against replay attacks.

## Architecture Decisions for This Plan

- **T1 before T2 (OTP Dispatch before Consumption):** The forgot password flow cannot be tested end-to-end until the system can generate and record valid `PASSWORD_RESET_OTP` tokens in `user_verification_tokens`. T1 activates this branch in `ResendOtpUseCaseImpl`.
- **Single-Request Atomic Reset (T2):** In alignment with `auth-flows.md` and `docs/feature-catalog.md §1.4`, verification and password reset are united in `POST /api/v1/auth/password/reset`. This eliminates intermediate `resetToken` state, reducing latency and DB roundtrips while ensuring atomic consumption.
- **Global Session Revocation (T2):** A forgotten password reset indicates a potentially compromised account. Successful reset in T2 revokes all active refresh tokens in PostgreSQL and blacklists active JWT access token JTIs in Redis.
- **No Session Interruption for First-Login (T3):** `POST /api/v1/auth/password/temporary` does not redirect the user back to the login screen. It immediately issues an active JWT access token and refresh token, and returns the nested `user` profile (`UserProfileResponse`), reusing the token-issuing infrastructure established in Feature 1.2.
- **Uniform Error Strategy for OTP Verification:** To prevent account enumeration and timing attacks, invalid OTP, expired OTP, consumed OTP, or unknown email all return `422 Unprocessable Content` with `validationErrors: ["otp: Invalid or expired verification code."]`, matching the `ValidationException` pattern unified in Features 1.1 and 1.2.
- **Dummy Hash Computation on Auth Failure:** When checking credentials in T3, if the email does not exist, a dummy Argon2 password verification is executed to prevent timing-based user enumeration.

## Task List

### Phase 1: Password-Reset OTP Dispatch (Shared Resend Extension)

- [x] **T1 — Implement password-reset OTP generation and asynchronous delivery via shared resend endpoint (`POST /api/v1/auth/otp/resend`)**
  - **Size:** S
  - **Depends on:** — (Shared resend endpoint infrastructure already initialized in Feature 1.1)
  - **Layers touched:** Application, Domain, Infrastructure (persistence/events), Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/UserVerificationToken.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ResendOtpUseCaseImpl.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ResendOtpUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerResendOtpTest.java`
  - **Acceptance Criteria:**
    - Calling `POST /api/v1/auth/otp/resend` with an email belonging to an active account (`is_active = true`) invalidates all prior unconsumed `PASSWORD_RESET_OTP` records for that user (`used_at = now()`).
    - Exactly one new 6-digit numeric OTP is generated, hashed with SHA-256, and saved to `user_verification_tokens` with `token_type = 'PASSWORD_RESET_OTP'` and a 15-minute TTL.
    - An `OtpRequestedEvent` with `purpose = OtpRequestedEvent.PURPOSE_PASSWORD_RESET` is published to the Transactional Outbox for asynchronous email delivery.
    - The response returns `200 OK` with message `"A verification code has been sent to your email."` and `data: null`.
    - Calling `POST /api/v1/auth/otp/resend` with a non-existent email returns `404 Not Found` (`urn:unihub:error:not-found`).
    - Calling `POST /api/v1/auth/otp/resend` with a blank or malformed email returns `422 Unprocessable Content` (`urn:unihub:error:validation`).
    - _Traces to:_ SPEC FR-01 through FR-05, BR-01, BR-08, Scenario 1, Scenario 2; API_CONTRACT §3.1

#### Checkpoint: Phase 1

- [x] Active accounts can request and receive 6-digit password reset OTPs via `POST /api/v1/auth/otp/resend`. Token scoping and event publishing verified by unit tests. Build is clean.

### Phase 2: Core Forgot Password Reset

- [x] **T2 — Implement atomic single-step forgot password reset with OTP verification, credential update, and global session revocation (`POST /api/v1/auth/password/reset`)**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure (persistence/security), Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ResetPasswordRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ResetPasswordUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ResetPasswordUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/RefreshTokenRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/RefreshTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ResetPasswordUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerResetPasswordTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/password/reset` with valid email, correct unexpired unconsumed `PASSWORD_RESET_OTP`, and compliant new password returns `200 OK` with message `"Password reset successfully. Please sign in with your new password."` and `data: null`.
    - The OTP record in `user_verification_tokens` is marked consumed (`used_at = now()`).
    - The user's `password_hash` in `users` is updated with the Argon2id hash of the new password, and `updated_at = now()`.
    - If `is_temporary_password` was `true`, it is cleared to `false`.
    - All active refresh tokens belonging to the user in `refresh_tokens` are revoked (`revoked_at = now()`).
    - Active access token JTIs are sent to the Redis blacklist with their remaining TTL.
    - No authenticated session (`accessToken` or `refreshToken`) is issued in the response.
    - Submitting an invalid, expired, or already-consumed OTP, or non-existent email, returns `422 Unprocessable Content` (`urn:unihub:error:validation` with `validationErrors: ["otp: Invalid or expired verification code."]`), leaving credentials and session states untouched.
    - Submitting a password violating policy (min 8 chars, uppercase, lowercase, digit, special char) returns `422 Unprocessable Content` with field validation errors.
    - _Traces to:_ SPEC FR-06 through FR-14, BR-01 through BR-04, BR-10, Scenarios 3, 4, 5; API_CONTRACT §3.2

#### Checkpoint: Phase 2

- [x] End-to-end forgot password reset capability verified: OTP request -> atomic reset -> session invalidation. Unit tests pass 100%.

### Phase 3: First-Login Temporary Password Exchange

- [x] **T3 — Implement first-login temporary password exchange with credential update and immediate full session issuance (`POST /api/v1/auth/password/temporary`)**
  - **Size:** M
  - **Depends on:** T2 (reuses password policy validation & response mapping conventions)
  - **Layers touched:** Domain, Application, Infrastructure (persistence/security), Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ChangeTemporaryPasswordRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ChangeTemporaryPasswordUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ChangeTemporaryPasswordUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ChangeTemporaryPasswordUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerTemporaryPasswordTest.java`
  - **Acceptance Criteria:**
    - Calling `POST /api/v1/auth/password/temporary` with valid email, correct current temporary password, and compliant new password for a user with `is_temporary_password = true` returns `200 OK` with message `"Password updated successfully. Welcome to UniHub!"`.
    - Response body `data` contains `accessToken` (valid RS256 JWT with 3600s TTL), `refreshToken`, `tokenType: "Bearer"`, `expiresIn: 3600`, and full `user` profile (`id`, `email`, `fullName`, `role`, `phoneNumber`, `avatarUrl`, `lastLoginAt`, `organizationId`, `studentRecordId`).
    - In PostgreSQL: `users.password_hash` is updated with Argon2id hash of `newPassword`, `users.is_temporary_password` is set to `false`, `users.last_login_at = now()`, and a new active row is inserted into `refresh_tokens`.
    - Calling with an incorrect `currentPassword` or non-existent/inactive email returns `401 Unauthorized` (`urn:unihub:error:unauthorized`) with dummy Argon2 verification for timing safety.
    - Calling for an account where `is_temporary_password = false` returns `400 Bad Request` (`urn:unihub:error:bad-request`).
    - Calling with `newPassword` equal to `currentPassword` returns `422 Unprocessable Content` (`urn:unihub:error:validation` with `validationErrors: ["newPassword: New password must be different from the temporary password."]`).
    - _Traces to:_ SPEC FR-15 through FR-21, BR-02, BR-05, BR-06, BR-07, Scenarios 6, 7, 8, 9; API_CONTRACT §3.3

#### Checkpoint: Phase 3

- [x] First-login temporary password exchange verified end-to-end with immediate session generation and dashboard routing. Unit tests pass 100%.

### Phase 4: Mutation Reliability & Hardening

- [x] **T4 — Verify concurrent replay protection and single-use state invalidation for password mutations**
  - **Size:** S
  - **Depends on:** T2, T3
  - **Layers touched:** Application, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerResetPasswordTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerTemporaryPasswordTest.java`
  - **Acceptance Criteria:**
    - When a client retries `POST /api/v1/auth/password/reset` with an already-consumed OTP, the request is safely rejected with `422 Unprocessable Content` (`validationErrors: ["otp: Invalid or expired verification code."]`).
    - When a client retries `POST /api/v1/auth/password/temporary` with the old temporary password after it was already changed (`is_temporary_password = false`), the request is rejected with `401 Unauthorized` (or `400 Bad Request`).
    - Under concurrent requests with the same reset OTP or temporary password, at most one request succeeds and any race condition is safely handled by database transactional boundaries.
    - _Traces to:_ SPEC FR-22 through FR-23, NFR Concurrency & Reliability, Scenario 10; API_CONTRACT §3.2, §3.3

#### Checkpoint: Feature Complete

- [x] All tasks T1, T2, T3, T4 checked off.
- [x] Every acceptance criterion across all tasks verified end-to-end.
- [x] Full unit test suite passes: `com.unihub.workshop.iam.*`, `com.unihub.workshop.shared.*`, `ModulithArchitectureTest`.
- [x] Ready for `/review`.

## Risks and Mitigations

| Risk                                                         | Impact | Mitigation                                                                                                                                                                                                           |
| :----------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Account enumeration & timing attacks on OTP verification** | High   | Return identical uniform RFC 7807 problem detail (`422 Unprocessable Content`, `validationErrors: ["otp: Invalid or expired verification code."]`) whether the email doesn't exist, OTP is wrong, or OTP is expired. |
| **Timing attack on temporary password validation**           | Medium | Execute a dummy Argon2 password verification when user email is not found, maintaining indistinguishable response latency.                                                                                           |
| **Race conditions on concurrent OTP consumption**            | High   | Perform OTP verification, consumption (`used_at = now()`), and password update in a single database `@Transactional` boundary with optimistic/row-level locking.                                                     |
| **Stale replica reads during password change**               | High   | Route all verification and mutation queries strictly to the primary database per ADR-015.                                                                                                                            |

## Open Questions

None.
