# Implementation Plan: User Profile Management

> **Spec:** `specs/user-profile-management/SPEC.md`  
> **API Contract:** `specs/user-profile-management/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds Feature 1.8 (User Profile Management) in three vertical slices, each delivering one independently testable, end-to-end working capability: (1) retrieving your own profile, (2) updating mutable personal fields, and (3) changing your own password with automatic remote session revocation. All three capabilities live in the `iam` module's `UserController` (new), operating exclusively on the JWT `sub`-identified caller. No schema migrations are required — all necessary columns exist. No new infrastructure adapters are required — `UserRepository`, `RefreshTokenRepository`, `JtiBlacklistPort`, and `SessionTrackerPort` already exist and will be reused as-is.

## Architecture Decisions for This Plan

- **Shared foundation built in T1:** `UserController`, `GetUserProfileUseCase` port+impl, and `User.updateProfile()` domain method are all created in T1 (profile retrieval), because both T2 and T3 depend on the controller scaffold and the domain method is needed by T2. These pieces have no standalone caller-observable behavior of their own if split off — assigning them to the first capability that needs them is the correct handling per the skill's shared-foundation guidance.
- **`User.updatePassword()` already exists** in `User.java`. T3 reuses it; no new domain method is needed. T3 adds only the `ChangeUserPasswordUseCase` orchestration layer (fail-closed check order, Redis-before-DB) and the presentation handler.
- **Session revocation in T3 reuses existing infrastructure** (`RefreshTokenRepository.findActiveRemoteSessions`, `revokeAllOtherActiveSessions`, `SessionTrackerPort`, `JtiBlacklistPort`) exactly as F-1.7's `RevokeAllOtherSessionsUseCaseImpl` does. No new outbound port or adapter is needed.
- **`UserRepository.save(User)` is used for both profile update and password change** — load → derive new record via domain method → save. This keeps the repository surface minimal and relies on `@DynamicUpdate` on `UserJpaEntity` to avoid issuing unnecessary column writes.
- **New `UserControllerTestBuilder`** follows the exact pattern of the existing `AuthControllerTestBuilder`, created in T1 and extended in T2 and T3.
- **No migration task:** All required columns (`full_name`, `phone_number`, `avatar_url`, `password_hash`, `updated_at`, `updated_by`) already exist in the `users` table. `database-schema-evolution` skill is not invoked.

## Task List

### Phase 1: Profile Retrieval — Establish the `UserController` scaffold and the first working endpoint

- [x] **T1 — A caller can retrieve their own full profile via `GET /api/v1/users/me`**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application (in port, use-case impl), Infrastructure (no new adapter — existing `UserRepository.findById` used), Presentation (new controller + stub DTO)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/User.java` — add `updateProfile(String fullName, String phoneNumber, String avatarUrl)` domain method (shared foundation; needed by T2)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/GetUserProfileUseCase.java` — NEW inbound port: `User getProfile(UUID userId)`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/GetUserProfileUseCaseImpl.java` — NEW: loads user by UUID via `UserRepository.findById`; throws `UnauthorizedException` if not found
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/UserController.java` — NEW: `@RequestMapping("/api/v1/users")`; `GET /me` handler; returns `ApiResponse<UserProfileResponse>`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/UpdateProfileRequest.java` — NEW: stub request DTO with `fullName`, `phoneNumber`, `avatarUrl` fields and validation annotations; `@JsonIgnoreProperties(ignoreUnknown = false)` to enable immutable-field detection in T2
    - `apps/backend/src/test/java/com/unihub/workshop/iam/domain/model/UserTest.java` — extend with tests for new `updateProfile()` method
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/GetUserProfileUseCaseImplTest.java` — NEW: happy path (all roles), not-found guard
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerTestBuilder.java` — NEW: test builder for `UserController`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerGetProfileTest.java` — NEW: MockMvc tests for 200 OK (STUDENT, ORGANIZER, STAFF), 401 no token
  - **Acceptance Criteria:**
    - `GET /api/v1/users/me` with a valid `STUDENT` JWT returns `200 OK` with `data` containing all 9 profile fields; `organizationId` is absent when null; `studentRecordId` is present when set. (FR-01–FR-05, Scenario 1, Scenario 15)
    - `GET /api/v1/users/me` with a valid `ORGANIZER` JWT returns `200 OK` with `organizationId` present and `studentRecordId` absent. (FR-03, FR-04, Scenario 15)
    - `GET /api/v1/users/me` without an `Authorization` header returns `401` with `error.type = urn:unihub:error:unauthorized`. (FR-28, Scenario 2)
    - `User.updateProfile(fullName, phoneNumber, avatarUrl)` returns a new `User` record with the supplied fields updated and `updatedAt` set to now, with all other fields identical. (FR-07, FR-14)
    - Full test suite passes, including `ModulithArchitectureTest`.

#### Checkpoint: Phase 1

- [x] `GET /api/v1/users/me` verified end-to-end by MockMvc tests (200 OK for all roles, 401 for missing token). `User.updateProfile()` domain method covered by unit tests. `UpdateProfileRequest` DTO compiles with validation annotations. Full test suite passes.

### Phase 2: Profile Update — Caller can partially update their own mutable personal fields

- [x] **T2 — A caller can update `fullName` and/or `phoneNumber` via `PATCH /api/v1/users/me`**
  - **Size:** M
  - **Depends on:** T1 (requires `UserController`, `UpdateProfileRequest` DTO, `User.updateProfile()`)
  - **Layers touched:** Application (new in port, new use-case impl), Infrastructure (no change), Presentation (new handler added to existing `UserController`)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/UpdateUserProfileUseCase.java` — NEW inbound port: `User updateProfile(UUID userId, UpdateUserProfileCommand command)`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/UpdateUserProfileUseCaseImpl.java` — NEW: load → `user.updateProfile(...)` (PATCH semantics: absent = keep existing value) → `userRepository.save(updated)` → return updated User
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/UserController.java` — ADD `PATCH /me` handler; `UpdateProfileRequest.from(body).toCommand()`; returns `ApiResponse<UserProfileResponse>`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/UpdateUserProfileUseCaseImplTest.java` — NEW: partial update (fullName only), clear phoneNumber (null), absent = untouched
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerUpdateProfileTest.java` — NEW: MockMvc for 200 OK (partial update preserves organizationId), 422 (blank fullName, invalid phoneNumber, immutable field `email`), 401
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerTestBuilder.java` — UPDATE: add `UpdateUserProfileUseCase` mock + builder method
  - **Acceptance Criteria:**
    - `PATCH /api/v1/users/me` with `{ "fullName": "Alice Thu Nguyen" }` returns `200 OK` with `data.fullName = "Alice Thu Nguyen"`, `data.phoneNumber` unchanged, and `data.organizationId` still present if the stored user has one. (FR-06, FR-07, FR-09, Scenario 3)
    - `PATCH /api/v1/users/me` with `{ "phoneNumber": null }` returns `200 OK` and `data.phoneNumber` is absent from the JSON payload. (FR-06, FR-07, FR-04, Scenario 4)
    - `PATCH /api/v1/users/me` with `{ "phoneNumber": "not-a-phone" }` returns `422` with `error.type = urn:unihub:error:validation` and `validationErrors` listing `phoneNumber`. (FR-10, FR-11, Scenario 5)
    - `PATCH /api/v1/users/me` with `{ "fullName": "   " }` returns `422` with `validationErrors` listing `fullName`. (FR-10, FR-11, Scenario 6)
    - `PATCH /api/v1/users/me` with `{ "email": "hack@evil.com" }` returns `422` with `validationErrors` listing `email` as an unrecognized/immutable field. (FR-08, FR-11, Scenario 7)
    - `PATCH /api/v1/users/me` without a request body returns `400` with `error.type = urn:unihub:error:bad-request`. (FR-12, Scenario 8)
    - After a successful update, the stored user record's `updated_at` reflects the current timestamp and `updated_by` reflects the caller's UUID. (FR-14)
    - `PATCH /api/v1/users/me` without an `Authorization` header returns `401`. (FR-35)
    - Full test suite passes, including `ModulithArchitectureTest`.

#### Checkpoint: Phase 2

- [x] `PATCH /api/v1/users/me` verified end-to-end by MockMvc tests: partial update, null-clear, immutable-field rejection, and validation rejections all produce correct HTTP status and error body. Full test suite passes.

### Phase 3: Password Change — Caller can change their own password with automatic remote session revocation

- [x] **T3 — A caller can change their own password via `POST /api/v1/users/me/password`, with fail-closed Redis session revocation of all remote sessions**
  - **Size:** M
  - **Depends on:** T1 (requires `UserController`, `UserRepository.findById`, `UserRepository.save`)
  - **Layers touched:** Application (new in port, new use-case impl with fail-closed orchestration), Infrastructure (no new adapter), Presentation (new handler + new request DTO)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/ChangePasswordRequest.java` — NEW: `{ currentPassword (NotBlank), newPassword (NotBlank) }`; strength validation done at use-case level
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/ChangeUserPasswordUseCase.java` — NEW inbound port: `void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword)`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/ChangeUserPasswordUseCaseImpl.java` — NEW: (1) load user; (2) Argon2 verify currentPassword → 422 on failure; (3) enforce newPassword ≠ currentPassword and strength policy → 422 on failure; (4) fetch remote sessions via `findActiveRemoteSessions`; (5) for each: `getActiveJti` → `blacklistJti` → `removeSession` (Redis fail-closed — `ServiceUnavailableException` on `RedisConnectionFailureException`); (6) `@Transactional`: `user.updatePassword(encode(newPassword))` + `save` + `revokeAllOtherActiveSessions`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/UserController.java` — ADD `POST /me/password` handler; extracts `sid` from `JwtAuthenticatedPrincipal`; calls `ChangeUserPasswordUseCase`; returns `ApiResponse<Void>` with message; maps `ServiceUnavailableException` → 503 + `Retry-After` header
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/ChangeUserPasswordUseCaseImplTest.java` — NEW: happy path, wrong currentPassword → 422, same password → 422, weak newPassword → 422, no remote sessions (no Redis writes, no revocations), Redis unavailable → 503 (DB not mutated), Mockito `InOrder` assertion: Redis before DB
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerChangePasswordTest.java` — NEW: MockMvc for 200 OK (data absent, message present, no Retry-After), 422 (wrong password, same, weak, blank), 503 (Redis down, Retry-After header present), 401
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerTestBuilder.java` — UPDATE: add `ChangeUserPasswordUseCase` mock + builder method
  - **Acceptance Criteria:**
    - `POST /api/v1/users/me/password` with valid `currentPassword` and a strength-conforming `newPassword` returns `200 OK` with `success: true`, `message: "Password changed successfully."`, and no `data` key in the JSON. (FR-22, FR-26, FR-30, Scenario 11)
    - After success, all remote sessions have `revoked_at` set and their `jti` values are in the Redis blacklist; the caller's current session (`sid`) is untouched. (FR-28, FR-29, FR-30, Scenario 11)
    - `POST /api/v1/users/me/password` with incorrect `currentPassword` returns `422` with `validationErrors` mentioning `currentPassword`. (FR-23, FR-24, Scenario 12)
    - `POST /api/v1/users/me/password` with `newPassword == currentPassword` returns `422`. (FR-25, Scenario 13)
    - `POST /api/v1/users/me/password` with `newPassword = "short"` (fails strength) returns `422` with `validationErrors` mentioning `newPassword`. (FR-26, Scenario 14)
    - `POST /api/v1/users/me/password` when Redis is unreachable returns `503` with a `Retry-After` header; `password_hash` in the database is **not** changed (verified by asserting `userRepository.save` was never called). (FR-31, BR-06, Scenario 15)
    - `POST /api/v1/users/me/password` without a request body returns `400` with `error.type = urn:unihub:error:bad-request`. (FR-34, Scenario 16)
    - `POST /api/v1/users/me/password` without an `Authorization` header returns `401`. (FR-35)
    - Full test suite passes, including `ModulithArchitectureTest` and all existing `AuthController` tests.

#### Checkpoint: Phase 3

- [x] All tasks T1, T2, T3 are verified end-to-end.
- [x] `ModulithArchitectureTest` passes.

### Phase 4: Avatar Upload & Storage Integration — Caller can upload and replace their avatar via dedicated endpoint

- [ ] **T4 — A caller can upload and replace their avatar via `POST /api/v1/users/me/avatar` with old avatar cleanup, and remove `avatarUrl` from `PATCH /api/v1/users/me`**
  - **Size:** M
  - **Depends on:** T1, `com.unihub.workshop.shared.storage.MediaStoragePort` (established in Feature 2.1 T4)
  - **Layers touched:** Domain, Application (in port, use-case impl), Infrastructure (shared storage port), Presentation (controller handler)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/iam/domain/model/User.java` — add `updateAvatar(String newAvatarUrl)` domain method
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/ports/in/UploadUserAvatarUseCase.java` — NEW inbound port: `User uploadAvatar(UUID userId, MultipartFile file)`
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/UploadUserAvatarUseCaseImpl.java` — NEW: validate file size (<= 5MB), MIME type, and magic bytes via `MediaValidationUtils` -> upload to MinIO under `users/avatars/{uuid}.{ext}` using `MediaStoragePort` with `MediaType.USER_AVATAR` -> call `user.updateAvatar(newUrl)` -> save to DB via `userRepository.save()` -> if previous avatar was in MinIO, delete old file via `mediaStoragePort.deleteByUrl(oldAvatarUrl)` -> return updated User
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/UpdateProfileRequest.java` — UPDATE: remove `avatarUrl` field; only `fullName` and `phoneNumber` remain
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/usecases/UpdateUserProfileUseCaseImpl.java` — UPDATE: remove `avatarUrl` from command and update logic
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/UserController.java` — ADD `POST /me/avatar` handler (`@RequestParam("file") MultipartFile file`); returns `ApiResponse<UserProfileResponse>`
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/usecases/UploadUserAvatarUseCaseImplTest.java` — NEW: happy path (upload + DB update + old avatar purge), file validation errors (too large, invalid type, empty file)
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerAvatarTest.java` — NEW: MockMvc for 200 OK (multipart upload), 422 (invalid file type / size), 401
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerTestBuilder.java` — UPDATE: add `UploadUserAvatarUseCase` mock + builder method
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/UserControllerUpdateProfileTest.java` — UPDATE: assert `avatarUrl` in PATCH request is rejected as unrecognized/immutable field (422)
  - **Acceptance Criteria:**
    - `POST /api/v1/users/me/avatar` with valid JPEG/JPG/PNG/WebP image (<= 5MB) uploads file to MinIO under `users/avatars/`, updates `avatar_url` in DB, and returns `200 OK` with updated `data.avatarUrl`. (FR-15, FR-18, FR-19, FR-21, Scenario 7, 8)
    - If user previously had an avatar stored in MinIO, `POST /api/v1/users/me/avatar` purges the previous avatar object from MinIO. (FR-20, BR-11, Scenario 10)
    - `POST /api/v1/users/me/avatar` with file > 5MB or invalid MIME format (supported: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`) returns `422 Unprocessable Content`. (FR-16, FR-17, Scenario 9)
    - `PATCH /api/v1/users/me` with `{ "avatarUrl": "https://example.com/avatar.png" }` returns `422 Unprocessable Content` with `avatarUrl` flagged as immutable. (FR-08, BR-01)
    - `ModulithArchitectureTest` passes.

#### Checkpoint: Feature Complete

- [ ] All tasks T1, T2, T3, T4 are checked off.
- [ ] Every acceptance criterion verified end-to-end by controller MockMvc tests and use-case unit tests.
- [ ] `ModulithArchitectureTest` passes — `UserController` in the `iam` module does not breach module boundaries.
- [ ] No regression in existing `AuthController` test suite.
- [ ] Ready for `/review user-profile-management`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                                                                                           | Impact | Mitigation                                                                                                                                                                                                                                                                                                                                                       |
| :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Fail-closed ordering in T3 is subtle:** Redis blacklist writes must precede the DB `revokeAllOtherActiveSessions` call. Swapping the order creates a window where sessions are DB-revoked but access tokens remain valid.                                                                                                    | High   | `ChangeUserPasswordUseCaseImpl` follows the identical ordering already established in `RevokeAllOtherSessionsUseCaseImpl` (F-1.7). A Mockito `InOrder` assertion in `ChangeUserPasswordUseCaseImplTest` explicitly verifies that `jtiBlacklistPort.blacklistJti` is called **before** `refreshTokenRepository.revokeAllOtherActiveSessions`.                     |
| **Transactional boundary in T3:** If the `@Transactional` block opens before Redis writes, a `RedisConnectionFailureException` thrown mid-transaction could leave the DB in a partially mutated state.                                                                                                                         | High   | Redis writes execute **outside and before** the `@Transactional` block opens, mirroring the `Propagation.NEVER` pattern in `RevokeAllOtherSessionsUseCaseImpl`. A `RedisConnectionFailureException` caught before the transaction starts means `userRepository.save` is never called. Test verifies `save` is not called when Redis throws.                      |
| **Immutable field rejection mechanism for `UpdateProfileRequest`:** `@JsonIgnoreProperties(ignoreUnknown = false)` causes Jackson to throw `UnrecognizedPropertyException` on unknown fields — this may surface as a `400 Bad Request` via the global exception handler instead of the expected `422` with `validationErrors`. | Medium | During T2 controller test, verify the HTTP status is `422` and the `validationErrors` array is present for unknown fields. If the global exception mapper does not handle `UnrecognizedPropertyException` as `422`, add a specific handler in the global `@ControllerAdvice` or switch to a sentinel-field approach that maps into a Bean Validation constraint. |
| **Password strength check at use-case level (not Bean Validation):** This is deliberate (to produce a single, field-specific `validationErrors` entry), but means the strength check is not automatically applied if the use case is called programmatically without going through the controller.                             | Low    | `ChangeUserPasswordUseCaseImplTest` explicitly tests weak-password input and asserts that `ValidationException` is thrown at the use-case level, confirming the check is always enforced regardless of the call path.                                                                                                                                            |

## Open Questions

None.
