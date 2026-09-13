# Implementation Plan: Public Key Distribution

> **Spec:** `specs/public-key-distribution/SPEC.md`  
> **API Contract:** `specs/public-key-distribution/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan builds the complete `GET /api/v1/auth/public-key` endpoint end-to-end: the in-memory PEM serialization service that derives the response payload from the already-loaded `RsaKeyProperties` bean, the use case port that abstracts it, the `AuthController` handler that exposes it, and the STAFF-only role guard. The feature introduces no schema migration, no new domain entity, and no side effects — the entire implementation flows through `shared/security` (for the key material logic) and `iam` (for the endpoint presentation). Because there is exactly one user-observable capability (a STAFF user calls an endpoint and receives a public key), the plan contains a single task.

## Architecture Decisions for This Plan

- **One task, not two** — Splitting "happy path" from "role guard" would be horizontal slicing by concern rather than vertical slicing by capability: the role guard produces no caller-observable capability on its own (there is no endpoint to call without the handler). Both are required for the endpoint to exist at all, so they belong in the same task.
- **Key material logic lives in `shared/security`, not `iam`** — `RsaKeyProperties` is owned by `shared/security`. PEM serialization and SHA-256 fingerprint computation are operations on that key material, so they belong in a new `PublicKeyService` within `shared/security`, consistent with where `JwksController` also lives. The `iam` use case port imports `PublicKeyService` through a standard Spring injection (not through an `api/` facade boundary), because `shared/security` is a shared kernel, not a peer Modulith module — direct injection is the correct pattern (ADR-005 context).
- **No new inbound port interface needed in `iam/application/ports/in/`** — The operation has no business logic of its own: it delegates entirely to `PublicKeyService`. Introducing a thin `GetPublicKeyUseCase` interface with one implementation that does nothing but call `PublicKeyService` would be pure ceremony with no benefit. The controller injects `PublicKeyService` directly, consistent with how the codebase already handles shared-kernel services.
- **Role enforcement via `@PreAuthorize`** — The existing `SecurityConfig` already wires `@EnableMethodSecurity`. Role guard on this endpoint is expressed as `@PreAuthorize("hasRole('STAFF')")` on the controller method, keeping authorization intent visible at the endpoint definition rather than buried in security configuration.

## Task List

### Phase 1: Core Endpoint — Staff Public Key Retrieval

- [x] **T1 — A STAFF-authenticated caller can call `GET /api/v1/auth/public-key` and receive the RSA public key in PEM format, with a deterministic `keyId` and an `issuedAt` timestamp; non-STAFF callers receive `403 Forbidden`**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Shared kernel (key material service), Presentation (controller handler, DTO, role guard)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/PublicKeyService.java` _(new)_ — serializes `RSAPublicKey` to PEM string; computes deterministic `keyId` via SHA-256 of DER-encoded SubjectPublicKeyInfo bytes (hex, lowercase); produces `issuedAt` as current UTC instant.
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/dto/PublicKeyResponse.java` _(new)_ — record `{ String keyId, String publicKeyPem, Instant issuedAt }`.
    - `apps/backend/src/main/java/com/unihub/workshop/iam/presentation/controllers/AuthController.java` _(modified)_ — add `@GetMapping("/public-key")` handler annotated `@PreAuthorize("hasRole('STAFF')")` that calls `PublicKeyService` and returns `ApiResponse<PublicKeyResponse>`.
    - `apps/backend/src/test/java/com/unihub/workshop/shared/security/PublicKeyServiceTest.java` _(new)_ — unit tests: PEM begins/ends with correct headers; `keyId` is deterministic (same key → same id); `keyId` changes when key changes; `issuedAt` is non-null and in the past or present.
    - `apps/backend/src/test/java/com/unihub/workshop/iam/presentation/controllers/AuthControllerPublicKeyTest.java` _(new)_ — controller-layer unit tests (MockMvc slice): 200 OK with correct JSON shape for a STAFF principal; 403 for ORGANIZER principal; 403 for STUDENT principal; 401 for missing Authorization header; 401 for expired JWT; 503 with Retry-After: 30 when Redis is down.
  - **Acceptance Criteria:**
    - Calling `GET /api/v1/auth/public-key` with a valid STAFF JWT returns HTTP `200 OK` with a JSON body whose `data.publicKeyPem` begins with `-----BEGIN PUBLIC KEY-----` and ends with `-----END PUBLIC KEY-----`. _(Traces to FR-01, Scenario 1)_
    - The `data.keyId` in the response is a non-empty lowercase hex string. Two successive calls with the same key loaded return an identical `keyId`. _(Traces to FR-02, FR-08, Scenario 7)_
    - The `data.issuedAt` in the response is a non-null ISO 8601 UTC timestamp string (e.g. `"2026-09-03T05:42:00Z"`). _(Traces to FR-03, Scenario 1)_
    - Calling the endpoint with a valid JWT whose `role` is `ORGANIZER` returns HTTP `403 Forbidden` with RFC 7807 error `type = "urn:unihub:error:forbidden"`. _(Traces to FR-06, BR-01, Scenario 5)_
    - Calling the endpoint with a valid JWT whose `role` is `STUDENT` returns HTTP `403 Forbidden` with RFC 7807 error `type = "urn:unihub:error:forbidden"`. _(Traces to FR-06, BR-01, Scenario 6)_
    - Calling the endpoint with no `Authorization` header returns HTTP `401 Unauthorized` with RFC 7807 error `type = "urn:unihub:error:unauthorized"`. _(Traces to FR-04, Scenario 2)_
    - Calling the endpoint with an expired JWT returns HTTP `401 Unauthorized` with RFC 7807 error `type = "urn:unihub:error:unauthorized"`. _(Traces to FR-05, Scenario 3)_
    - Calling the endpoint when Redis is unavailable returns HTTP `503 Service Unavailable` with `Retry-After: 30` header and RFC 7807 error `type = "urn:unihub:error:service-unavailable"`. _(Traces to BR-04, ADR-010, Scenario 9)_
    - The full test suite (including `ModulithArchitectureTest`) passes with no failures.

#### Checkpoint: Feature Complete

- [x] T1 is checked off and all its acceptance criteria have been verified end-to-end against the running application (or MockMvc slice).
- [x] `PublicKeyService` unit tests pass: deterministic `keyId`, correct PEM format, non-null `issuedAt`.
- [x] `AuthControllerPublicKeyTest` passes all scenarios including the rejection scenarios (403 ×2, 401 ×2, 503 ×1).
- [x] `ModulithArchitectureTest` passes — no new cross-module boundary violation introduced.
- [x] Full test suite is green.
- [x] Ready for `/review public-key-distribution`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                                             | Impact                                                                                                                          | Mitigation                                                                                                                                                                                                         |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@EnableMethodSecurity` may not be active on `SecurityConfig`, causing `@PreAuthorize` to silently no-op and the 403 test to pass for the wrong reason.                                                                                                          | High — would mean non-STAFF roles are not actually blocked in production.                                                       | Verify `@EnableMethodSecurity` (or `@EnableGlobalMethodSecurity(prePostEnabled = true)`) is present on `SecurityConfig` or a related `@Configuration` class before implementing the role guard; add it if missing. |
| `RSAPublicKey.getEncoded()` in Java returns X.509 SubjectPublicKeyInfo (DER) bytes, which is the correct input for `-----BEGIN PUBLIC KEY-----` PEM wrapping — but `getRSAPublicKey()` variants in some older library versions return raw modulus bytes instead. | Medium — would produce a PEM the mobile app cannot parse.                                                                       | Confirm `RSAPublicKey.getEncoded()` returns the expected X.509 DER bytes in a unit test by round-tripping: encode → Base64 wrap → parse back with standard library → verify key equality.                          |
| Line-break convention in PEM output (64 chars per line vs. no line breaks) may differ from what the mobile client expects.                                                                                                                                       | Low — PEM spec requires line breaks at 64 chars; mobile standard libraries tolerate both, but the spec states 64-char wrapping. | Implement using `Base64.getMimeEncoder(64, new byte[]{'\n'})` to match the documented format. Verify in `PublicKeyServiceTest` that the PEM string contains newline characters within the body.                    |

## Open Questions

None.
