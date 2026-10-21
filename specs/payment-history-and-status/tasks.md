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

## Task List

### Phase 1: Student-facing payment history foundation

- [ ] **T1 — A student can retrieve a stable, status-filterable page of only their own payment history.**
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
  - **Acceptance Criteria:**
    - An authenticated STUDENT calling `GET /api/v1/payments/me` receives only payment records linked to their registrations, newest first, with the standard page envelope. _(FR-01, FR-10, FR-14; Scenario 1)_
    - A valid `status` filter returns only that status; a valid returned cursor gives the next non-duplicated page; invalid filters, size, or cursor receive the contract-defined 400/422 response. _(FR-02, FR-14; Scenario 2)_
    - Each item contains exactly the student-safe payment and registration context in the contract, including a final-four-only masked provider reference and progress timestamp, while omitting all prohibited secrets and failure details. _(FR-04, FR-12; Scenario 1)_
    - Unit tests cover application authorization/filter orchestration, query mapping/keyset behavior with mocked persistence, and controller response/error mapping; `ModulithArchitectureTest` passes.

#### Checkpoint: Student history foundation

- [ ] T1 acceptance criteria pass, affected unit tests and `ModulithArchitectureTest` pass, and no payments class accesses a registrations internal package.

### Phase 2: Organization-scoped operational history

- [ ] **T2 — An organizer can retrieve a stable, tenant-scoped page of payment operations with minimal student identity.**
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
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentHistoryController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...` (organizer item response and MapStruct mapping)
    - `apps/backend/src/test/java/com/unihub/workshop/{registrations,payments}/...` (new/updated facade, use-case, persistence-adapter, and controller unit tests)
  - **Acceptance Criteria:**
    - An authenticated ORGANIZER calling `GET /api/v1/admin/payments` receives only payment records for workshops in the JWT organization scope, in the contracted cursor order. _(FR-05, FR-10, FR-14; Scenario 4)_
    - A valid accessible `workshopId` and/or payment `status` narrows results without changing cursor semantics; an inaccessible or nonexistent workshop is reported as the contracted non-enumerating 404. _(FR-06; Scenario 5)_
    - Each organizer item contains only `fullName` and `studentNumber` as student identity and contains none of the excluded gateway, QR, refund, or failure-detail fields. _(FR-08, FR-12; Scenario 4)_
    - STUDENT and STAFF callers receive the contract-defined forbidden response; unit tests verify tenant filtering and response minimization; `ModulithArchitectureTest` passes. _(FR-11)_

#### Checkpoint: Organization history

- [ ] T2 acceptance criteria pass, organization isolation is demonstrated in use-case and controller tests, affected unit tests and `ModulithArchitectureTest` pass.

### Phase 3: Reusable payment lookup and organization summary

- [ ] **T3 — An eligible student or organizer can retrieve one payment without learning about an inaccessible record.**
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

- [ ] **T4 — An organizer can retrieve a currency-separated operational payment summary.**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Payments application ports/use case, Payments persistence adapter, Payments presentation, unit tests.
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/GetOrganizerPaymentSummaryUseCase.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/out/PaymentRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/GetOrganizerPaymentSummaryUseCaseImpl.java` (new)
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/{PaymentRepositoryAdapter,PaymentJpaRepository}.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentHistoryController.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/{dto,mapper}/...` (summary response and MapStruct mapping)
    - `apps/backend/src/test/java/com/unihub/workshop/payments/{application,infrastructure,presentation}/...` (new unit tests)
  - **Acceptance Criteria:**
    - An authenticated ORGANIZER calling `GET /api/v1/admin/payments/summary` receives one bucket per visible ISO 4217 currency, with count and amount separated by `PENDING`, `COMPLETED`, `FAILED`, and `REFUNDED`. _(FR-09; Scenario 4)_
    - An organization with no payment records receives a successful empty `byCurrency` collection; no response provides a cross-currency grand total. _(FR-09)_
    - A STUDENT or STAFF caller is rejected according to the contract, and summary data from another organization is never included. _(FR-10, FR-11, FR-12; Scenarios 4, 6)_
    - Unit tests cover grouping, empty results, organization scoping, and controller error mapping; `ModulithArchitectureTest` passes.

#### Checkpoint: Feature complete

- [ ] T3 and T4 acceptance criteria pass.
- [ ] Every acceptance criterion across T1–T4 has been verified through the required unit-test layers and API-controller tests with mocked dependencies.
- [ ] The backend unit-test suite and `ModulithArchitectureTest` pass under the project testing policy.
- [ ] Ready for `/review` after all tasks have been explicitly built and reviewed.

## Risks and Mitigations

| Risk                                                                                                               | Impact | Mitigation                                                                                                                                                                               |
| :----------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A payments query bypasses registrations ownership or organization boundaries through a direct internal dependency. | High   | Establish/extend only a minimal `registrations/api` read DTO in T1; enforce Modulith architecture tests in every task.                                                                   |
| Enriching each payment with registration/student data produces an N+1 query pattern.                               | High   | Design the public registration lookup as a bounded batch/read-page capability and test the persistence/query interaction with mocks; do not perform per-item remote or repository calls. |
| Cursor pages drift, repeat, or skip records when status/workshop filters change.                                   | Medium | Reuse `shared/pagination` opaque keyset cursor and reject cursors that do not match the requested sort/filter contract.                                                                  |
| Replica staleness is interpreted as an immediate payment outcome.                                                  | Medium | Keep the eventual-consistency notice explicit in responses/UI-facing contract and never use these reads for mutations or idempotency decisions.                                          |
| Gateway identifiers or sensitive failure/refund fields leak through a new DTO or mapper.                           | High   | Make response DTOs allow-list only contract fields; add controller mapping tests that assert excluded fields are absent.                                                                 |
| Summary accidentally aggregates unlike currencies or omits a status bucket.                                        | Medium | Group before aggregation by currency and status; cover mixed-currency and empty-result cases in application and persistence-adapter unit tests.                                          |

## Open Questions

None. The approved spec and contract resolve endpoint scope, visibility semantics, status set, masking rule, summary shape, pagination, and replica consistency behavior.
