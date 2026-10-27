# Implementation Plan: Payment History and Status

> **Spec:** `specs/payment-history-and-status/SPEC.md`  
> **API Contract:** `specs/payment-history-and-status/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan delivers the four approved read endpoints as small, independently testable vertical slices. It starts with student history because that first caller-visible capability establishes the canonical payment read model, cursor handling, and the minimal public registration context needed without breaking Modulith boundaries. Organizer-specific list, detail, and summary capabilities then reuse those proven read contracts.

## Architecture Decisions for This Plan

- **T1 owns the shared read foundation** — the first externally visible capability, student history, introduces only the minimal registration public query DTO/operation needed by payments; later tasks reuse it rather than adding direct access to registrations internals.
- **Cursor ordering is fixed at payment creation time** — all list tasks use the existing shared keyset pagination contract with `createdAt DESC, id DESC`; status and workshop filters are carried consistently between pages.
- **Scoped query capability precedes detail and aggregate views** — organization filtering and minimal student identity are proven through the organizer collection endpoint before they are reused by payment detail and summary, reducing tenant-isolation risk.
- **No schema task is planned** — the approved schema already contains the payment and registration keyset indexes needed by this feature. Query implementation must use those shapes; if implementation demonstrates an additional index is necessary, it must follow the approved mid-build improvement process rather than being added ad hoc.
- **Scoping and ordering share one statement** — student ownership and organization scope are not stored on `payments`, and post-filtering a fetched page would corrupt page size, `hasMore`, and cursor continuity. T1/T2 therefore use the bounded read-only cross-module projection rule in `docs/project-structure.md` §3: one read-only statement joins `payments` to the owning module's tables to produce the keyset window, while every response field owned by another module (registration id, workshop id, registration status, student identity) is still resolved through `registrations/api`.
- **Cursors are bound to their filter set** — issuing and validating cursors goes through the shared cursor contract, extended with an optional server-derived filter fingerprint so a cursor replayed against a different `status`/`workshopId` filter is rejected with `400` instead of silently producing an inconsistent page.

## Task List

### Phase 1: Student-facing payment history foundation

- [x] **T1 — A student can retrieve a stable, status-filterable page of only their own payment history.**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Registrations public API, Payments application ports/use case, Payments persistence adapter, Payments presentation, shared pagination integration, unit tests.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RegistrationPaymentContextDto.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/GetStudentPaymentHistoryUseCase.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/out/PaymentRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/GetStudentPaymentHistoryUseCaseImpl.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentHistoryController.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...` (new payment-history response DTO and MapStruct mapper)
    - `apps/backend/src/test/java/com/unihub/workshop/{registrations,payments}/...` (new facade, use-case, persistence-adapter, and controller unit tests)
    - `apps/backend/src/main/java/com/unihub/workshop/shared/pagination/KeysetCursor.java` (add nullable `filterFingerprint`)
    - `apps/backend/src/main/java/com/unihub/workshop/shared/pagination/KeysetCursorCodec.java` (single filter-aware `decode(cursor, expectedSortField, expectedFilterFingerprint)` signature; callers that bind no filters pass `null` for the expectation)
    - `apps/backend/src/test/java/com/unihub/workshop/shared/pagination/KeysetCursorCodecTest.java` (filter-binding tests)
    - `apps/backend/src/test/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentRepositoryAdapterTest.java` (update constructor; the adapter now injects `EntityManager`)
  - **Notes:** `JpaKeysetPagination` is Criteria-based and can only address a single entity root, so it cannot express the ownership join. The adapter composes the row-value keyset predicate in the native projection query while still using `KeysetCursorCodec` for the shared opaque cursor format. The projection selects only `payments` columns; `registration` context comes from `RegistrationApi`. Exact T1 shape:

    ```sql
    SELECT p.id, p.registration_id, p.provider, p.provider_payment_id, p.provider_refund_id,
           p.status, p.amount, p.currency, p.completed_at, p.failed_at, p.refunded_at,
           p.failure_code, p.failure_reason, p.created_at, p.updated_at, p.created_by, p.updated_by
    FROM payments p
    JOIN registrations r ON r.id = p.registration_id
    WHERE r.student_user_id = :studentUserId
        -- appended only when a status filter is present
        AND p.status = :status
        -- appended only when a cursor is present
        AND (p.created_at, p.id) < (:cursorCreatedAt, :cursorId)
    ORDER BY p.created_at DESC, p.id DESC
    ```

    The adapter bounds the window with `Query.setMaxResults(size + 1)` rather than a bound `LIMIT`; the extra row signals `hasMore` without a second query.

  - **Acceptance Criteria:**
    - An authenticated STUDENT calling `GET /api/v1/payments/me` receives only payment records linked to their registrations, newest first, with the standard page envelope. _(FR-01, FR-10, FR-14; Scenario 1)_
    - A valid `status` filter returns only that status; a valid returned cursor gives the next non-duplicated page; a cursor replayed against a different `status` filter receives the contract-defined `400`; invalid filters, size, or cursor receive the contract-defined `400`/`422` response. _(FR-02, FR-14; Scenario 2)_
    - Each item contains exactly the student-safe payment and registration context in the contract, including a final-four-only masked provider reference and progress timestamp, while omitting all prohibited secrets and failure details. A provider reference shorter than four characters is fully masked. _(FR-04, FR-12, BR-09; Scenario 1)_
    - Unit tests cover application authorization/filter orchestration, query mapping/keyset behavior with mocked persistence, and controller response/error mapping; `ModulithArchitectureTest` passes.

#### Checkpoint: Student history foundation

- [x] T1 acceptance criteria pass, affected unit tests and `ModulithArchitectureTest` pass, and no payments class accesses a registrations internal package.

### Phase 2: Organization-scoped operational history

- [x] **T2 — An organizer can retrieve a stable, tenant-scoped page of payment operations with minimal student identity.**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Registrations public API, Payments application ports/use case, Payments persistence adapter, Payments presentation, shared pagination integration, unit tests.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RegistrationPaymentContextDto.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/GetOrganizerPaymentHistoryUseCase.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/out/PaymentRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/GetOrganizerPaymentHistoryUseCaseImpl.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/{PaymentRepositoryAdapter,PaymentJpaRepository}.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentHistoryController.java` (common student and student-or-organizer controller mapped at `"/api/v1/payments"`)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/AdminPaymentHistoryController.java` (new; organizer-only controller mapped at `"/api/v1/admin/payments"`)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...` (organizer item response and MapStruct mapping)
    - `apps/backend/src/test/java/com/unihub/workshop/{registrations,payments}/...` (new/updated facade, use-case, persistence-adapter unit tests)
    - `apps/backend/src/test/java/com/unihub/workshop/payments/presentation/controllers/{PaymentHistoryControllerTest,AdminPaymentHistoryControllerTest}.java` (split controller unit tests: common vs organizer-only)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/api/dto/StudentProfileDto.java` (add `studentNumber`)
    - `apps/backend/src/main/java/com/unihub/workshop/iam/application/facade/IamFacade.java` (populate `studentNumber` from `student_directory` through `users.student_record_id`)
    - `apps/backend/src/test/java/com/unihub/workshop/iam/application/facade/IamFacadeTest.java` (student-number mapping)
  - **Notes:** `studentNumber` is resolved through `IamApi`, never from the join. `StudentProfileDto` is an additive record component; the six `registrations` test fixtures that construct it must be updated with the new component. The T1 projection gains `JOIN workshops w ON w.id = r.workshop_id`, with `w.organization_id = :organizationId` plus the optional `r.workshop_id = :workshopId` filter; both remain predicate-only and no `workshops` column is selected. Organizer-only endpoints live in `AdminPaymentHistoryController` mapped at `"/api/v1/admin/payments"`; student-only and student-or-organizer endpoints live in `PaymentHistoryController` mapped at `"/api/v1/payments"`. Both use full literal paths, consistent with every other backend controller; no shared version-prefix constant is introduced. Payment ordering uses the `payments` keyset indexes; the registration and workshop composite indexes provide only the join/filter access path. An unfiltered organization page sorts the organization's matching payment set, not the global `payments` table; that bound is accepted and documented in `docs/database-schema.md`.
  - **Acceptance Criteria:**
    - An authenticated ORGANIZER calling `GET /api/v1/admin/payments` receives only payment records for workshops in the JWT organization scope, in the contracted cursor order. _(FR-05, FR-10, FR-14; Scenario 4)_
    - A valid accessible `workshopId` and/or payment `status` narrows results without changing cursor semantics; an inaccessible or nonexistent workshop is reported as the contracted non-enumerating 404. _(FR-06; Scenario 5)_
    - Each organizer item contains only `fullName` and `studentNumber` as student identity, both resolved through the IAM facade, and contains none of the excluded gateway, QR, refund, or failure-detail fields. _(FR-08, FR-12; Scenario 4)_
    - STUDENT and STAFF callers receive the contract-defined forbidden response; unit tests verify tenant filtering and response minimization; `ModulithArchitectureTest` passes. _(FR-11)_

#### Checkpoint: Organization history

- [x] T2 acceptance criteria pass, organization isolation is demonstrated in use-case and controller tests, affected unit tests and `ModulithArchitectureTest` pass.

### Phase 3: Reusable payment lookup and organization summary

- [x] **T3 — An eligible student or organizer can retrieve one payment without learning about an inaccessible record.**
  - **Size:** S
  - **Depends on:** T1, T2
  - **Layers touched:** Payments application ports/use case, Payments persistence adapter, Payments presentation, unit tests.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/GetPaymentDetailUseCase.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/out/PaymentRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/GetPaymentDetailUseCaseImpl.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentHistoryController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/{application,infrastructure,presentation}/...` (new/updated unit tests)
  - **Acceptance Criteria:**
    - A STUDENT calling `GET /api/v1/payments/:paymentId` receives the contracted student-safe payment only for their own registration. _(FR-03, FR-04; Scenario 3)_
    - An ORGANIZER receives the same payment fields plus only the contracted student name and student number when the linked workshop is in their organization. _(FR-07, FR-08; Scenario 4)_
    - An absent, cross-student, or cross-organization payment returns the same 404 contract response with no data leakage; STAFF receives 403. _(FR-07, FR-11, FR-12; Scenarios 3, 5, 6)_
    - Unit tests cover both role-specific response shapes and indistinguishable-not-found behavior; `ModulithArchitectureTest` passes.

- [x] **T4 — An organizer can retrieve a currency-separated operational payment summary.**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Payments application ports/use case, Payments persistence adapter, Payments presentation, unit tests.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/GetOrganizerPaymentSummaryUseCase.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/out/PaymentRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/GetOrganizerPaymentSummaryUseCaseImpl.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/{PaymentRepositoryAdapter,PaymentJpaRepository}.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/AdminPaymentHistoryController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...` (summary response and MapStruct mapping)
    - `apps/backend/src/test/java/com/unihub/workshop/payments/{application,infrastructure,presentation}/...` (new unit tests)
  - **Notes:** The summary is produced by one read-only aggregate statement. Exact T4 shape:

    ```sql
    SELECT p.currency, p.status, COUNT(*), SUM(p.amount)
    FROM payments p
    JOIN registrations r ON r.id = p.registration_id
    JOIN workshops w ON w.id = r.workshop_id
    WHERE w.organization_id = :organizationId
    GROUP BY p.currency, p.status
    ORDER BY p.currency ASC, p.status ASC
    ```

    The `SELECT` list contains only `payments` columns; `registrations` and `workshops` appear only in `JOIN`/`WHERE` as the organization visibility predicate. The query is reachable only from a `@Transactional(readOnly = true)` path and uses existing documented identifiers/indexes; no schema or index change is introduced. Feature-specific query detail stays in `specs/`, not `docs/`.

  - **Acceptance Criteria:**
    - An authenticated ORGANIZER calling `GET /api/v1/admin/payments/summary` receives one bucket per visible ISO 4217 currency, with count and amount separated by `PENDING`, `COMPLETED`, `FAILED`, and `REFUNDED`. _(FR-09; Scenario 4)_
    - An organization with no payment records receives a successful empty `byCurrency` collection; no response provides a cross-currency grand total. _(FR-09)_
    - A STUDENT or STAFF caller is rejected according to the contract, and summary data from another organization is never included. _(FR-10, FR-11, FR-12; Scenarios 4, 6)_
    - Unit tests cover grouping, empty results, organization scoping, and controller error mapping; `ModulithArchitectureTest` passes.

#### Checkpoint: Feature complete

- [x] T3 and T4 acceptance criteria pass.
- [x] Every acceptance criterion across T1–T4 has been verified through the required unit-test layers and API-controller tests with mocked dependencies.
- [x] The backend unit-test suite and `ModulithArchitectureTest` pass under the project testing policy.
- [x] Ready for `/review` after all tasks have been explicitly built and reviewed.

## Risks and Mitigations

| Risk                                                                                                               | Impact | Mitigation                                                                                                                                                                                                              |
| :----------------------------------------------------------------------------------------------------------------- | :----- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A payments query bypasses registrations ownership or organization boundaries through a direct internal dependency. | High   | Establish/extend only a minimal `registrations/api` read DTO in T1; enforce Modulith architecture tests in every task; scope the read-only projection to the six conditions recorded in `docs/project-structure.md` §3. |
| A read-only projection query is reused for a non-read purpose or starts selecting foreign-module columns.          | High   | The projection rule forbids both; the persistence-adapter test asserts read-only usage and a `payments`-only select list, and `/review` re-checks it under `performance-and-efficiency-engineering`.                    |
| Enriching each payment with registration/student data produces an N+1 query pattern.                               | High   | Design the public registration lookup as a bounded batch/read-page capability and test the persistence/query interaction with mocks; do not perform per-item remote or repository calls.                                |
| Cursor pages drift, repeat, or skip records when status/workshop filters change.                                   | Medium | Reuse `shared/pagination` opaque keyset cursor, extend it with the server-derived filter fingerprint, and reject a cursor whose fingerprint does not match the current filter set.                                      |
| Replica staleness is interpreted as an immediate payment outcome.                                                  | Medium | Keep the eventual-consistency notice explicit in responses/UI-facing contract and never use these reads for mutations or idempotency decisions.                                                                         |
| Gateway identifiers or sensitive failure/refund fields leak through a new DTO or mapper.                           | High   | Make response DTOs allow-list only contract fields; add controller mapping tests that assert excluded fields are absent.                                                                                                |
| Summary accidentally aggregates unlike currencies or omits a status bucket.                                        | Medium | Group before aggregation by currency and status; cover mixed-currency and empty-result cases in application and persistence-adapter unit tests.                                                                         |

## Open Questions

None outstanding.

## Execution Notes

Human-directed working rules for this feature:

- **Scoped test runs only.** Run only the tests related to the files touched by the current task, plus `ModulithArchitectureTest` (static boundary check, no I/O). The human runs the full backend suite and reports any fallout.
- **Complete imports, zero ignored warnings.** Every type must be imported explicitly before it is used; any compiler or IDE warning in a touched file must be fixed at the root — never suppressed, `@SuppressWarnings`-ed away, or left behind.
