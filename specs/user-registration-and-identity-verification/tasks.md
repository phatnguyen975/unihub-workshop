# Implementation Plan: User Registration & Identity Verification

> **Spec:** `specs/user-registration-and-identity-verification/SPEC.md`  
> **API Contract:** `specs/user-registration-and-identity-verification/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the three REST endpoints for Feature 1.1 as three sequential vertical-slice tasks.

- **T1** builds the student self-registration capability end-to-end: refactors the OTP domain/persistence model to `UserVerificationToken` mapped to PostgreSQL table `user_verification_tokens` with `token_type = 'ACTIVATION_OTP'`, implements Argon2 password hashing, configurable OTP TTL (default: 15 minutes), identity linking to active `student_directory` records, and publishes `OtpRequestedEvent` to the transactional outbox (`event_publication`) externalized to RabbitMQ (worker-side email delivery is decoupled and out of scope).
- **T2** delivers the account activation endpoint (`POST /api/v1/auth/activate`), verifying the 6-digit OTP scoped to `ACTIVATION_OTP`, marking `used_at = now()`, activating the user (`is_active = true`), and returning uniform error responses.
- **T3** delivers the shared OTP resend endpoint (`POST /api/v1/auth/otp/resend`), dynamically checking `is_active` to issue `ACTIVATION_OTP` (for inactive accounts) or leaving active flow blank with comments (for active accounts, deferred to Feature 1.4), scoping token invalidation, and returning `404 Not Found` when the account does not exist.

## Architecture Decisions for This Plan

- **`user_verification_tokens` replaces `password_reset_tokens`:** The database table is `user_verification_tokens` with column `token_type VARCHAR(32) NOT NULL CHECK (token_type IN ('ACTIVATION_OTP', 'PASSWORD_RESET_OTP'))`. The domain model and JPA entity are named `UserVerificationToken` and `UserVerificationTokenJpaEntity`.
- **Argon2 Password Hashing:** All password hashing uses `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`. BCrypt is strictly prohibited.
- **Configurable OTP TTL:** Configured via `app.otp.ttl-minutes` (default: 15), injectable via `APP_OTP_TTL_MINUTES` in `.env` / `application.yml`.
- **Decoupled Notification Delivery:** Backend publishes `OtpRequestedEvent` via Spring Modulith's transactional outbox externalized to RabbitMQ exchange `unihub.events::notifications`. Worker-side notification consumption/rendering/SMTP is owned by the workers notification feature and is explicitly out of scope for Feature 1.1.
- **Strictly Scoped Token Invalidation:** Invalidation queries must filter by `(user_id, token_type, used_at IS NULL)` so that activation OTPs and password reset OTPs never invalidate each other.
- **Test Execution Scope:** Per the agreed policy, tests run strictly on modified files (`./gradlew test --tests ...`); full suite runs are handled independently.

## Task List

### Phase 1: Student Self-Registration (Core Flow + Shared Foundation)

- [x] **T1 — Student self-registration with Argon2, active directory linking, user_verification_tokens, configurable TTL, and outbox event publishing**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new / refactored):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/UserVerificationToken.java` (replaces `PasswordResetToken.java`, includes `tokenType`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/StudentDirectory.java`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/OtpRequestedEvent.java` (includes `purpose`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/RegisterUserUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserVerificationTokenRepository.java` (replaces `PasswordResetTokenRepository.java`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/StudentDirectoryRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserRepository.java` (method `existsByEmail`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/RegisterUserUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/utils/OtpService.java` (HMAC-SHA256, configurable TTL)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserVerificationTokenJpaEntity.java` (mapped to `user_verification_tokens`, with `tokenType`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserVerificationTokenJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserVerificationTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/StudentDirectoryJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/StudentDirectoryJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/StudentDirectoryRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/RegisterRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/RegisterResponse.java`
  - **Files (modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/User.java` — ensure fields: `role`, `is_active = false`, `is_temporary_password = false`, `studentRecordId`, `organizationId`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserJpaEntity.java` — ensure fields match `docs/database-schema.md §users`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` — endpoint `POST /api/v1/auth/register`
    - `apps/backend/src/main/resources/application.yml` / `apps/backend/src/test/resources/application-test.yml` — configure `app.otp.ttl-minutes: 15`
  - **Test files:**
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/utils/OtpServiceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/RegisterUserUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerRegisterTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/register` with valid payload and active student directory match → `201 Created`, returns `data.userId`, user has `role=STUDENT`, `is_active=false`, `is_temporary_password=false`, `password_hash` encoded with Argon2, `student_record_id` and `organization_id` set from directory, `user_verification_tokens` record created with `token_type='ACTIVATION_OTP'`, `used_at=NULL`, and `expires_at=now() + TTL`, `event_publication` record created for `OtpRequestedEvent`. _(FR-01, FR-02, FR-03, FR-04, FR-05, FR-09, FR-10; SPEC Scenario 1)_
    - `POST /api/v1/auth/register` with valid payload and no student directory match (or directory status `INACTIVE`) → `201 Created`, `student_record_id=NULL`, `organization_id=NULL`. _(FR-03; SPEC Scenario 2)_
    - `POST /api/v1/auth/register` with duplicate email → `409 Conflict` (`urn:unihub:error:conflict`). _(FR-06; SPEC Scenario 3)_
    - `POST /api/v1/auth/register` with weak password (missing digit, uppercase, lowercase, or special char) → `422 Unprocessable Content` (`urn:unihub:error:validation`). _(FR-08; SPEC Scenario 4)_
    - `POST /api/v1/auth/register` with malformed email or missing fields → `422 Unprocessable Content` (`urn:unihub:error:validation`). _(FR-07)_
    - `POST /api/v1/auth/register` with invalid JSON body → `400 Bad Request`. _(FR-10)_

#### Checkpoint: Phase 1

- [x] Code and tests compile cleanly.
- [x] Unit tests for T1 pass (`OtpServiceTest`, `RegisterUserUseCaseImplTest`, `AuthControllerRegisterTest`).
- [x] Database mapping points to `user_verification_tokens` table with `token_type`.

### Phase 2: Account Activation via OTP

- [x] **T2 — Account activation via 6-digit OTP verification scoped to ACTIVATION_OTP**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new / modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ActivateAccountUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ActivateAccountUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserVerificationTokenRepository.java` (query methods scoped to `ACTIVATION_OTP`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserVerificationTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ActivateAccountRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` — endpoint `POST /api/v1/auth/activate`
  - **Test files:**
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ActivateAccountUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerActivateTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/activate` with valid OTP for inactive account → `200 OK` (`success: true`, message: `"Account activated successfully. You may now log in."`), `users.is_active = true`, token's `used_at = now()`. _(FR-11; SPEC Scenario 5)_
    - `POST /api/v1/auth/activate` with expired OTP (`expires_at < now()`) → `422 Unprocessable Content` (`urn:unihub:error:validation`, uniform message `"Invalid or expired activation code."`, with `validationErrors`). _(FR-12; SPEC Scenario 6)_
    - `POST /api/v1/auth/activate` with already-consumed OTP (`used_at IS NOT NULL`) → `422 Unprocessable Content` (uniform message, with `validationErrors`). _(FR-12; SPEC Scenario 6)_
    - `POST /api/v1/auth/activate` with incorrect OTP value → `422 Unprocessable Content` (uniform message, with `validationErrors`). _(FR-12; SPEC Scenario 6)_
    - `POST /api/v1/auth/activate` for non-existent user email → `422 Unprocessable Content` (uniform message, anti-enumeration, with `validationErrors`). _(FR-12)_
    - `POST /api/v1/auth/activate` for already active account (`is_active = true`) → `409 Conflict` (`urn:unihub:error:conflict`, `"Account is already active."`). _(FR-13; SPEC Scenario 7)_
    - `POST /api/v1/auth/activate` with malformed OTP (not 6 digits) → `422 Unprocessable Content` (`urn:unihub:error:validation`, with `validationErrors`). _(FR-14)_

#### Checkpoint: Phase 2

- [x] Unit tests for T2 pass (`ActivateAccountUseCaseImplTest`, `AuthControllerActivateTest`).
- [x] Token consumption sets `used_at = now()` and does not affect tokens of other types.

### Phase 3: Shared OTP Resend

- [x] **T3 — Shared OTP resend supporting activation and password-reset contexts with scoped invalidation**
  - **Size:** S
  - **Depends on:** T1, T2
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files (new / modified):**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ResendOtpUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ResendOtpUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/out/UserVerificationTokenRepository.java` (method `invalidateActiveTokens(UUID userId, String tokenType)`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/infrastructure/persistence/UserVerificationTokenRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ResendOtpRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` — endpoint `POST /api/v1/auth/otp/resend`
  - **Test files:**
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ResendOtpUseCaseImplTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerResendOtpTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/otp/resend` for inactive account (`is_active = false`) → `200 OK` (`"A verification code has been sent to your email."`), prior `ACTIVATION_OTP` tokens marked `used_at = now()`, new `ACTIVATION_OTP` token created with `used_at = NULL` and fresh `expires_at`, new `OtpRequestedEvent` (purpose: `ACTIVATION`) published. _(FR-15, FR-16; SPEC Scenario 8)_
    - `POST /api/v1/auth/otp/resend` for active account (`is_active = true`) → Flow left blank with comment for Feature 1.4. _(FR-15, FR-17; SPEC Scenario 9)_
    - `POST /api/v1/auth/otp/resend` for non-existent email → `404 Not Found` (`urn:unihub:error:not-found`, detail: `"No account found for this email."`). _(FR-18; SPEC Scenario 10)_
    - `POST /api/v1/auth/otp/resend` with invalid email format → `422 Unprocessable Content` (`urn:unihub:error:validation`). _(FR-19)_

#### Checkpoint: Feature Complete

- [x] All three tasks (T1, T2, T3) are completed.
- [x] Targeted tests for all modified classes in `iam` pass in isolation (`./gradlew test --tests ...`).
- [x] Ready for `/review user-registration-and-identity-verification`.

## Risks and Mitigations

| Risk                                                                                                           | Impact | Mitigation                                                                                                                                   |
| :------------------------------------------------------------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------- |
| Renaming `PasswordResetToken` to `UserVerificationToken` affects references across the `iam` module.           | Medium | Perform clean refactoring in T1; ensure legacy `PasswordResetToken` classes are fully replaced by `UserVerificationToken`.                   |
| Cross-flow token invalidation: resending an activation OTP might accidentally invalidate a password reset OTP. | High   | Scope all token queries and update statements with `token_type` (`WHERE user_id = :userId AND token_type = :tokenType AND used_at IS NULL`). |
| Database schema mismatch: `user_verification_tokens` table expects `token_type` column.                        | High   | Entity `UserVerificationTokenJpaEntity` explicitly defines `@Column(name = "token_type", nullable = false)`.                                 |
| Argon2 password encoding configuration missing or default mismatch.                                            | Medium | Inject `PasswordEncoder` bean configured with Argon2 (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`).                             |
