# Implementation Plan: Registration Reconciliation & Cancellation

> **Spec:** `specs/registration-reconciliation-and-cancellation/SPEC.md`  
> **API Contract:** `specs/registration-reconciliation-and-cancellation/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan decomposes Feature 4.3 (`registration-reconciliation-and-cancellation`) into 4 vertical-slice tasks across 3 phases. The feature delivers two complementary operational capabilities: (1) an automated background Dual-Case Reconciliation Sweeper running every 30 minutes to resolve expired `PENDING` registrations by querying Stripe (recovering delayed webhooks into `CONFIRMED` tickets or cleaning up abandoned checkouts into `FAILED` with Redis seat restoration), and (2) a unified cancellation endpoint (`POST /api/v1/registrations/{id}/cancel`) allowing students and organizers to cancel confirmed registrations before workshop start, automatically issuing 100% Stripe refunds for paid tickets, releasing capacity, and triggering asynchronous multi-channel notifications in the worker.

## Architecture Decisions for This Plan

- **Unidirectional Spring Modulith Port Inversion (`payments` → `registrations/api`):** To preserve Modulith boundary rules without cyclic dependencies, `registrations/api` declares the outbound SPI `RegistrationPaymentPort` along with its command/result DTOs (`PaymentStatusResult`, `RefundPaymentCommand`, `RefundPaymentResult`). The `payments` module implements this SPI via `StripePaymentPortAdapter`. `registrations` never imports any class from `payments`.
- **Transaction-Free Gateway Invocations:** All Stripe API interactions (payment status retrieval, payment attempt cancellation, and refund issuance) occur strictly _outside_ any Spring `@Transactional` database boundary and are protected by Resilience4j circuit breakers (`stripeCircuitBreaker`). Database transactions open only to commit state transitions after external gateway calls complete, preventing connection pool exhaustion under external latency (ADR-020).
- **Deterministic Gateway Refund Deduplication:** Stripe refund requests use a deterministic idempotency key formatted as `refund-reg-{registrationId}`, guaranteeing that retry attempts after partial network disconnects converge on a single refund without double-refunding (ADR-014, BR-05).
- **Transient Cancellation Reason:** The optional `reason` provided during cancellation is validated (max 1000 characters) and included in `RegistrationCanceledEvent` for email/notification rendering, but is deliberately not persisted in any relational table, preserving schema invariants (BR-07).
- **Fine-Grained Outbox Event for Worker Consumption:** A dedicated `RegistrationCanceledEvent` is published to the transactional outbox (`event_publication`) and externalized to RabbitMQ (`unihub.events`, routing key `registration.canceled`). It bundles complete snapshots of student, workshop, schedule, refund, and actor info, allowing `apps/workers/` to deliver notifications without synchronous queries back to backend modules.

## Task List

### Phase 1: Dual-Case Reconciliation Sweeper

- [x] **T1 — Dual-Case Reconciliation Sweeper (Scheduled Fallback & Delayed Recovery)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure (`registrations` and `payments` modules)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/PaymentStatusResult.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationPaymentPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/out/RegistrationRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/infrastructure/persistence/RegistrationRepositoryAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/RegistrationReconciliationSweeper.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapterSweeperTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/RegistrationReconciliationSweeperTest.java`
  - **Acceptance Criteria:**
    - Sweeper executes on cron schedule `app.registrations.sweeper.cron` (default `0 */30 * * * *`) and acquires non-blocking Redis lock `lock:sweeper:registration-reconciliation` with 240s TTL, ensuring single-node execution across instances (FR-01, FR-02).
    - Selects expired `PENDING` registrations (`status = 'PENDING' AND expires_at < now()`) ordered by `expires_at ASC`, capped at batch size `app.registrations.sweeper.batch-size` (default 100), utilizing partial composite index `(expires_at, workshop_id)` (FR-03).
    - Queries Stripe PaymentIntent status outside database transactions via `RegistrationPaymentPort.getPaymentStatus(...)` (FR-05).
    - **Case A (Delayed Webhook Recovery):** When Stripe reports payment `succeeded`, atomically in one transaction transitions registration to `CONFIRMED`, generates signed QR ticket fields (`qr_payload`, `qr_signature`, `qr_issued_at`), transitions payment to `COMPLETED`, writes `WorkshopRegisteredEvent` to outbox, and marks idempotency key `COMPLETED`. Post-commit publishes SSE `registration.updated` and `payment.confirmed` (FR-06, FR-07, Scenario 1).
    - **Case B (Standard Cleanup):** When Stripe reports payment failed, uninitiated, canceled, or requires payment method, requests payment attempt cancellation via `RegistrationPaymentPort.cancelPaymentAttempt(...)`, atomically transitions registration and payment to `FAILED`, and marks idempotency key `FAILED`. Post-commit executes Redis `INCR workshop:{workshopId}:capacity` to return the reserved seat (FR-08, FR-09, Scenario 2).
    - If Stripe cancel reveals payment has meanwhile succeeded, dynamically processes as Case A (FR-12, Scenario 3).
    - If expired `PENDING` registration has no payment record, resolves as Case B directly without calling Stripe (FR-13, Scenario 6).
    - Leaves registration `PENDING` when Stripe reports `processing` or when Stripe is unreachable / circuit breaker is open, continuing the batch for other items (FR-04, FR-10, FR-11, Scenarios 4, 5).
    - Verifies registration is still `PENDING` before committing transitions to prevent racing with incoming webhooks (FR-14, Scenario 7).

#### Checkpoint: Phase 1 Complete

- [x] Sweeper resolves orphaned `PENDING` registrations reliably in both Case A and Case B paths under distributed lock.
- [x] No database transactions are held during Stripe gateway calls.
- [x] Redis capacity counter is accurately restored on abandoned checkouts.

### Phase 2: Unified Registration Cancellation & Automated Refund

- [x] **T2 — Unified Free Registration Cancellation Endpoint & State Machine**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Presentation, Application, Domain, Infrastructure (`registrations` module)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/events/RegistrationCanceledEvent.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/CancelRegistrationCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/ports/in/CancelRegistrationUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/CancelRegistrationUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/CancelRegistrationRequest.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/CancelRegistrationUseCaseFreeTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationControllerCancelTest.java`
  - **Acceptance Criteria:**
    - Exposes unified endpoint `POST /api/v1/registrations/{id}/cancel` protected by Registration Tier token bucket rate limiting (5 / 10s per IP, returning `429 Too Many Requests` on exhaustion) (FR-16, FR-29, API Contract §4).
    - Requires valid `Idempotency-Key` header (UUIDv4, max 64 chars, returning `422` on missing/invalid). Replay of completed key returns cached `200 OK`; concurrent in-flight key returns `409 Conflict` (`Retry-After: 2`) (FR-27, FR-28, Scenario 22).
    - Evaluates RBAC before validation: `STUDENT` must own the registration (`jwt.sub == student_user_id`); `ORGANIZER` must own the workshop organization (`jwt.org == workshop.organization_id`); unauthenticated returns `401`; other roles (`STAFF`) or unauthorized users receive `403 Forbidden` without leaking registration state; non-existent registration returns `404 Not Found` (FR-17, FR-18, FR-19, FR-20, FR-21, FR-22, Scenarios 12, 13, 14, 15).
    - Validates optional `reason` (trimmed, max 1000 characters, returning `422` if exceeded; whitespace-only treated as absent) (FR-23, Scenario 19).
    - Validates status & timing: registration must be in `CONFIRMED` status (returns `422` with distinct messages for `CANCELED`, `FAILED`, `PENDING`), workshop must be `PUBLISHED` (`422` otherwise), and workshop must not have started (`starts_at > now()`, returning `422` if `starts_at <= now()`) (FR-24, FR-25, FR-26, Scenarios 16, 17, 18).
    - For free registrations (`price_at_registration == 0`), cancels without calling payment gateway: atomically transitions registration to `CANCELED` (setting `updated_by` to caller), writes `RegistrationCanceledEvent` to outbox, and marks idempotency key `COMPLETED` (FR-30, FR-35, Scenario 10).
    - Post-commit restores one seat via Redis atomic `INCR workshop:{workshopId}:capacity` (FR-37, Scenario 24) and broadcasts `registration.updated` SSE to channel `unihub:stream:workshop:{workshopId}` (FR-38).
    - Responds with `200 OK` showing `status = "CANCELED"`, timestamps, price 0, and null QR fields omitted (FR-39).
    - Handles concurrent cancellations gracefully so exactly one succeeds and the other receives `422` without duplicate side effects (FR-40, Scenario 23).

- [ ] **T3 — Paid Registration Cancellation & Automated Stripe Refund Integration**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Presentation, Application, Infrastructure (`registrations` and `payments` modules)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RefundPaymentCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RefundPaymentResult.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationPaymentPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/usecases/CancelRegistrationUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/presentation/dto/RegistrationResponse.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapterRefundTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/usecases/CancelRegistrationUseCasePaidTest.java`
  - **Acceptance Criteria:**
    - When canceling a paid registration (`price_at_registration > 0`), verifies that an associated payment record exists in `COMPLETED` status; returns `500 Internal Server Error` with zero state changes if missing or inconsistent (FR-34, Scenario 29).
    - Requests 100% refund from Stripe outside database transactions via `RegistrationPaymentPort.refundPayment(...)` using deterministic idempotency key `refund-reg-{registrationId}` to guarantee exactly-once refunding across client retries (FR-31, FR-32, BR-04, BR-05, Scenarios 11, 21).
    - On Stripe timeout, gateway error, or open circuit breaker, returns `503 Service Unavailable` with `Retry-After`, leaving registration, payment, Redis counter, and outbox untouched, and releasing idempotency key for retry (FR-33, Scenario 20).
    - Upon successful refund, atomically in one database transaction transitions registration to `CANCELED`, payment to `REFUNDED`, records `RegistrationCanceledEvent` (carrying refunded amount and currency), and marks idempotency key `COMPLETED` (FR-35, FR-36, Scenario 11).
    - Post-commit restores one seat via Redis `INCR workshop:{workshopId}:capacity` and broadcasts `registration.updated` SSE (FR-37, FR-38).
    - Responds with `200 OK` containing `status: "CANCELED"` and `refund: { "amount": ..., "currency": ... }` while omitting QR fields and never exposing internal Stripe identifiers (FR-39, API Contract §3).

#### Checkpoint: Phase 2 Complete

- [ ] Both free and paid registrations can be canceled by student or organizer before workshop start.
- [ ] 100% Stripe refund executes outside database transaction with strict idempotency and circuit-breaker protection.
- [ ] State transitions, outbox events, Redis capacity restoration, and SSE updates execute cleanly.

### Phase 3: Worker Notification Delivery

- [ ] **T4 — Worker Multi-Channel Cancellation Notification Delivery (Email, In-App, SSE)**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Inbound, Core, Outbound (`apps/workers/notifications`)
  - **Files:**
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/RegistrationCanceledEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/RegistrationCanceledNotificationHandler.java`
    - `apps/workers/src/main/resources/templates/email/registration-canceled.html`
    - `apps/workers/src/test/java/com/unihub/workshop/workers/notifications/inbound/handler/RegistrationCanceledNotificationHandlerTest.java`
    - `apps/workers/src/test/java/com/unihub/workshop/workers/notifications/outbound/email/RegistrationCanceledEmailTemplateTest.java`
  - **Acceptance Criteria:**
    - Worker consumes `RegistrationCanceledEvent` from RabbitMQ queue `q.notifications` (routing key `registration.canceled`) (FR-41).
    - Creates in-app notification record with type `REGISTRATION_CANCELED` for the student (FR-41, Scenario 27).
    - Renders and delivers cancellation email using Thymeleaf template `registration-canceled.html` containing workshop details, canceler role, optional reason (properly HTML-escaped), and refund details for paid registrations (FR-41, Scenario 27).
    - Emits real-time SSE event `notification.new` to the student's personal channel `unihub:stream:user:{studentUserId}` (FR-41).
    - Skips malformed events missing student or registration IDs with logged warning without failing or blocking the RabbitMQ queue (FR-42, Scenario 28).
    - Tolerates event redelivery idempotently without duplicating in-app notifications or emails (FR-42, Scenario 28).

#### Checkpoint: Feature Complete

- [ ] All 4 tasks above are completed and verified against unit and integration tests.
- [ ] Every acceptance criterion across all tasks has been verified.
- [ ] Architecture tests pass: `ModulithArchitectureTest` and `HexagonalArchitectureTest`.
- [ ] Ready for `/review registration-reconciliation-and-cancellation`.

## Risks and Mitigations

| Risk                                                                            | Impact | Mitigation                                                                                                                                                                                                                                    |
| :------------------------------------------------------------------------------ | :----: | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Spring Modulith cyclic dependency (`registrations` ↔ `payments`)**            |  High  | `registrations/api` defines `RegistrationPaymentPort` SPI and DTOs; `payments` implements this port via `StripePaymentPortAdapter`. `registrations` never references or imports classes from `payments`, keeping dependencies unidirectional. |
| **Stripe API latency / DB connection pool starvation during refund or sweeper** |  High  | All Stripe API calls (`getPaymentStatus`, `cancelPaymentAttempt`, `refundPayment`) are executed strictly outside database transactions, wrapped by Resilience4j circuit breaker with explicit timeout and fallbacks (BR-04, FR-31).           |
| **Double refund on retry after partial commit failure**                         |  High  | The Stripe refund invocation uses a deterministic idempotency key `refund-reg-{registrationId}` derived from the registration ID. Retries converge on the existing refund without issuing duplicate charges (BR-05, FR-32).                   |
| **Webhook vs Sweeper race condition on expiring PENDING registration**          | Medium | Sweeper checks `WHERE id = :id AND status = 'PENDING'` right before applying the state transition. If an incoming webhook confirmed the registration concurrently, the sweeper detects the state change and safely skips it (FR-14).          |
| **Late payment charge after sweeper cleans up abandoned checkout**              | Medium | Sweeper Case B explicitly requests Stripe to cancel the `PaymentIntent` before marking the registration `FAILED`, invalidating the student's client secret before releasing the seat (FR-08, BR-13).                                          |
| **Post-commit Redis failure during seat restoration**                           |  Low   | Redis `INCR` failure is caught and logged at WARN without failing the committed HTTP response; any counter drift is automatically repaired by the existing 5-minute Capacity Reconciliation Job (BR-06).                                      |

## Open Questions

None. All architectural and business questions were clarified and confirmed during spec drafting.
