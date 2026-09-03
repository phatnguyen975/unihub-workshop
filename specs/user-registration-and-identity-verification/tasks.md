# Implementation Plan: User Registration & Identity Verification

> **Spec:** `specs/user-registration-and-identity-verification/SPEC.md`  
> **API Contract:** `specs/user-registration-and-identity-verification/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the three REST endpoints for Feature 1.1 as three sequential vertical-slice tasks. T1 builds the complete student self-registration capability end-to-end — and as its shared foundation, introduces all new domain models (`PasswordResetToken`, `StudentDirectory`), extends the existing `User` domain record and JPA entity with missing fields, adds every new repository port and persistence adapter, defines the `OtpService`, and publishes the outbox event. T2 adds the account-activation endpoint on top of the infrastructure T1 established. T3 adds the OTP resend endpoint as a final, independently testable capability.

## Architecture Decisions for This Plan

- **T1 carries all shared foundations** — `User` domain/JPA extension, `PasswordResetToken` domain/JPA/port/adapter, `StudentDirectory` domain/JPA/port/adapter, and `OtpService` — because every one of these pieces is first needed by the registration capability itself. T2 and T3 depend on T1 completing this groundwork; they do not rebuild it.
- **OTP outbox event published from the application use case** — The use case calls `ApplicationEventPublisher` directly; Spring Modulith's transactional outbox writes the event to `event_publication` within the same transaction. No worker-side consumption is in scope for this feature.
- **HMAC-SHA256 with server-side secret for OTP hashing** — `OtpService` owns hash generation and verification; the secret is injected from application configuration, never hardcoded.
- **`UserJpaEntity.organizationId` is now nullable** — DB schema was already updated. The JPA `nullable = false` annotation in the existing entity must be changed to `nullable = true` in T1.
- **`AuthController` is extended (not split)** — The three new endpoints are added to `AuthController` alongside the existing login/logout/refresh endpoints, consistent with Feature 1.2's established controller structure.
- **Test scope per the agreed build-time policy** — Only tests covering new or modified classes are run per task; the full regression suite is run by the human after all tasks are complete.

## Task List

### Phase 1: Student Self-Registration (Core Flow + Shared Foundation)

- [x] **T1 — A new student can self-register, receive an OTP via the outbox event, and the account is created inactive**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new):**
    - `iam/domain/model/PasswordResetToken.java`
    - `iam/domain/model/StudentDirectory.java`
    - `iam/domain/events/OtpRequestedEvent.java`
    - `iam/application/ports/in/RegisterUserUseCase.java`
    - `iam/application/ports/out/PasswordResetTokenRepository.java`
    - `iam/application/ports/out/StudentDirectoryRepository.java`
    - `iam/application/usecases/RegisterUserUseCaseImpl.java`
    - `iam/application/utils/OtpService.java`
    - `iam/infrastructure/persistence/PasswordResetTokenJpaEntity.java`
    - `iam/infrastructure/persistence/PasswordResetTokenJpaRepository.java`
    - `iam/infrastructure/persistence/PasswordResetTokenRepositoryAdapter.java`
    - `iam/infrastructure/persistence/StudentDirectoryJpaEntity.java`
    - `iam/infrastructure/persistence/StudentDirectoryJpaRepository.java`
    - `iam/infrastructure/persistence/StudentDirectoryRepositoryAdapter.java`
    - `iam/presentation/dto/RegisterRequest.java`
    - `iam/presentation/dto/RegisterResponse.java`
  - **Files (modified):**
    - `iam/domain/model/User.java` — add `fullName`, `phoneNumber`, `avatarUrl`, `studentRecordId`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`
    - `iam/infrastructure/persistence/UserJpaEntity.java` — add matching columns, change `organizationId` to `nullable = true`
    - `iam/infrastructure/persistence/UserRepositoryAdapter.java` — update mapping to include new fields
    - `iam/application/ports/out/UserRepository.java` — add `existsByEmail(String email)` method
    - `iam/presentation/controllers/AuthController.java` — add `POST /api/v1/auth/register` endpoint
  - **Test files (new):**
    - `iam/application/utils/OtpServiceTest.java`
    - `iam/application/usecases/RegisterUserUseCaseImplTest.java`
    - `iam/presentation/controllers/AuthControllerRegisterTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/register` with a valid payload (email not in users, matching student directory entry) → `201 Created`, response body contains `userId`, `users` row has `role=STUDENT`, `is_active=false`, `student_record_id` populated, `organization_id` set from directory record, `password_reset_tokens` row created with `used_at=NULL` and `expires_at` 15 minutes in the future, `event_publication` row created for `OtpRequestedEvent`. _(FR-01, FR-02, FR-03, FR-04, FR-05, FR-10; SPEC Scenario 1)_
    - `POST /api/v1/auth/register` with valid payload and no student directory match → `201 Created`, `student_record_id=NULL`, `organization_id=NULL`. _(FR-03, FR-10; SPEC Scenario 2)_
    - `POST /api/v1/auth/register` with email that already exists in `users` → `409 Conflict`. _(FR-06; SPEC Scenario 3)_
    - `POST /api/v1/auth/register` with malformed email → `400 Bad Request` with a `validationErrors` field naming the `email` field. _(FR-07; SPEC Scenario 4)_
    - `POST /api/v1/auth/register` with a password that fails complexity rules → `400 Bad Request` with a `validationErrors` field naming the `password` field. _(FR-08; SPEC Scenario 5)_
    - Plain-text password is never stored; `users.password_hash` is a BCrypt or Argon2 hash. _(FR-09)_
    - `OtpService` unit test: generated OTP is always 6 numeric digits; HMAC-SHA256 hash of OTP verifies correctly against stored hash; different OTP does not verify. _(BR-03)_

#### Checkpoint: Phase 1

- [x] T1 acceptance criteria pass against the running application (endpoint callable, DB rows verifiable, event_publication row present).
- [x] All T1 test files pass; no compilation errors.
- [x] `User` domain record and `UserJpaEntity` include all fields defined in `docs/database-schema.md §users`.

### Phase 2: Account Activation via OTP

- [ ] **T2 — An unverified student can activate their account by submitting the correct OTP**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Domain, Application, Infrastructure, Presentation
  - **Files (new):**
    - `iam/application/ports/in/ActivateAccountUseCase.java`
    - `iam/application/usecases/ActivateAccountUseCaseImpl.java`
    - `iam/presentation/dto/ActivateAccountRequest.java`
  - **Files (modified):**
    - `iam/application/ports/out/PasswordResetTokenRepository.java` — add `findActiveTokenByUserEmail(String email)` and `markConsumed(UUID tokenId)` methods
    - `iam/application/ports/out/UserRepository.java` — add `findByEmail(String email)` and `activate(UUID userId)` methods
    - `iam/infrastructure/persistence/PasswordResetTokenRepositoryAdapter.java` — implement new query methods
    - `iam/infrastructure/persistence/UserRepositoryAdapter.java` — implement `findByEmail` and `activate`
    - `iam/presentation/controllers/AuthController.java` — add `POST /api/v1/auth/activate` endpoint
  - **Test files (new):**
    - `iam/application/usecases/ActivateAccountUseCaseImplTest.java`
    - `iam/presentation/controllers/AuthControllerActivateTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/activate` with correct OTP for an inactive account → `200 OK`, `users.is_active = true`, `password_reset_tokens.used_at` set to a non-null timestamp. _(FR-11; SPEC Scenario 6)_
    - `POST /api/v1/auth/activate` with an expired OTP (`expires_at < now()`) → `422 Unprocessable Entity` with `error.type = urn:unihub:error:otp-invalid`; `users.is_active` unchanged. _(FR-13; SPEC Scenario 7)_
    - `POST /api/v1/auth/activate` with an already-consumed OTP (`used_at IS NOT NULL`) → `422 Unprocessable Entity`; `users.is_active` unchanged. _(FR-14; SPEC Scenario 8)_
    - `POST /api/v1/auth/activate` with an incorrect OTP value → `422 Unprocessable Entity`; `users.is_active` unchanged. _(FR-12; SPEC Scenario 9)_
    - `POST /api/v1/auth/activate` for an email with `is_active = true` → `409 Conflict`. _(FR-16; SPEC Scenario 10)_
    - `POST /api/v1/auth/activate` for an email with no `users` record → `422 Unprocessable Entity` (same error type as OTP failures — no enumeration). _(FR-15)_
    - Error messages for all 422 cases use an identical `error.detail` string (uniform, non-distinguishable). _(OQ-02 resolution; NFR Security)_

#### Checkpoint: Phase 2

- [ ] T2 acceptance criteria pass against the running application (calling the endpoint, verifying DB state).
- [ ] All T2 test files pass; no compilation errors.

### Phase 3: OTP Resend

- [ ] **T3 — An unverified student can request a new OTP, invalidating any prior unconsumed OTPs**
  - **Size:** S
  - **Depends on:** T1, T2
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files (new):**
    - `iam/application/ports/in/ResendOtpUseCase.java`
    - `iam/application/usecases/ResendOtpUseCaseImpl.java`
    - `iam/presentation/dto/ResendOtpRequest.java`
  - **Files (modified):**
    - `iam/application/ports/out/PasswordResetTokenRepository.java` — add `invalidateAllActiveTokensForUser(UUID userId)` method
    - `iam/infrastructure/persistence/PasswordResetTokenRepositoryAdapter.java` — implement `invalidateAllActiveTokensForUser`
    - `iam/presentation/controllers/AuthController.java` — add `POST /api/v1/auth/otp/resend` endpoint
  - **Test files (new):**
    - `iam/application/usecases/ResendOtpUseCaseImplTest.java`
    - `iam/presentation/controllers/AuthControllerResendOtpTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/auth/otp/resend` for an email with `is_active = false` and an existing unconsumed OTP → `200 OK`, the prior `password_reset_tokens` row has `used_at` set to a non-null timestamp, a new `password_reset_tokens` row is created with `used_at = NULL` and a fresh `expires_at`, a new `event_publication` row is created for the resend `OtpRequestedEvent`. _(FR-17, FR-18; SPEC Scenario 11)_
    - `POST /api/v1/auth/otp/resend` for an email that has no `users` record or has `is_active = true` → `422 Unprocessable Entity` with `error.type = urn:unihub:error:state`; no OTP created, no event published. _(FR-19; SPEC Scenario 12, Scenario 13)_
    - `POST /api/v1/auth/otp/resend` with malformed email → `400 Bad Request`. _(API_CONTRACT §/api/v1/auth/otp/resend validation)_

#### Checkpoint: Feature Complete

- [ ] All tasks (T1, T2, T3) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end.
- [ ] Tests for all new and modified files pass in isolation (per the agreed build-time policy).
- [ ] No new compilation errors or warnings in the `iam` module.
- [ ] Ready for `/review user-registration-and-identity-verification`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                                                         | Impact | Mitigation                                                                                                                                                                                                                                              |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `User.java` and `UserJpaEntity.java` are referenced by existing use cases (`LoginUseCaseImpl`, `LogoutUseCaseImpl`, `RefreshTokenUseCaseImpl`) and their tests. Adding new fields to the `User` record will break all call sites that use positional constructor syntax or pattern matching on the record.                   | High   | T1 must update all call sites — primarily `UserRepositoryAdapter.toDomain()` — immediately after extending the record. The existing use case tests must compile and pass with no behavioral change after T1.                                            |
| `UserJpaEntity.organizationId` is currently `nullable = false`. Changing it to `nullable = true` is a schema-level change that is already applied in `V1__init_schema.sql`, but the JPA annotation must also be updated in T1. Any entity-wide JPA validation could flag this inconsistency at startup if not done together. | Medium | T1 updates the JPA annotation and `User` record in the same commit as the `organizationId` nullability change.                                                                                                                                          |
| HMAC-SHA256 OTP verification requires a server-side secret injected via `application.yml`. If the config key is missing, the `OtpService` will fail at startup with an unhelpful error.                                                                                                                                      | Medium | `OtpService` validates the presence and minimum length of the secret at bean initialization (`@PostConstruct`) and throws a descriptive `IllegalStateException` if absent. The `application-test.yml` must include a stub value.                        |
| Two concurrent registration calls with the same email (both bypassing the application-layer `existsByEmail` check) will race on the `UNIQUE` constraint in PostgreSQL. One will succeed; the other will receive a `DataIntegrityViolationException` that the presentation layer must map to `409 Conflict`.                  | Medium | The exception translator (GlobalExceptionHandler or equivalent) must handle `DataIntegrityViolationException` with a `409 Conflict` response. Verify this is covered by the existing exception-mapping infrastructure before T1 is considered complete. |

## Open Questions

None.
