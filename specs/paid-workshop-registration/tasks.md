# Implementation Plan: Paid Workshop Registration

> **Spec:** `specs/paid-workshop-registration/SPEC.md`  
> **API Contract:** `specs/paid-workshop-registration/API_CONTRACT.md`  
> **Status:** APPROVED

## Overview

This plan decomposes the merged Feature 4.2 (`paid-workshop-registration`) and Feature 5.1 (`payment-webhook-handling`) into 5 vertical-slice tasks across 2 phases. The system enables authenticated students to register for paid workshops (receiving a `PENDING` registration and Stripe `paymentClientSecret`), and processes asynchronous Stripe webhooks (`payment_intent.succeeded` and `payment_intent.payment_failed`) to atomically update payment records, finalize registration status, issue RSA-signed QR tickets, broadcast SSE events, and publish outbox notification events.

## Architecture Decisions for This Plan

- **Unidirectional Spring Modulith Dependency (`payments → registrations::api`):** To avoid circular dependencies between `registrations` and `payments`, `registrations` declares an outbound SPI port `RegistrationPaymentPort` in `registrations.api`. The `payments` module implements this port (`StripePaymentPortAdapter`). For webhook confirmation, `payments` calls `RegistrationApi` declared in `registrations.api`. `registrations` never references `payments`.
- **Transaction-Free Payment Gateway Call:** Creating the Stripe Payment Intent occurs strictly outside any database transaction. The database transaction opens only to persist the `PENDING` registration record after Stripe returns successfully, preventing database connection pool exhaustion under external API latency (ADR-020).
- **Universal Keyset Currency Conversion:** A dedicated `CurrencyUnitConverter` encapsulates conversion from `BigDecimal` price to smallest currency units (e.g., cents for USD, unit factor 1 for VND) to prevent floating-point rounding errors and ensure multi-currency extensibility.
- **Webhook Always-200 Pattern with HMAC-SHA256 Raw Verification:** The webhook controller receives the raw payload string and verifies the `Stripe-Signature` header before JSON parsing. Any post-verification failures return `200 OK` to prevent retry floods, while duplicate deliveries are handled idempotently via the `payments.provider_payment_id` database unique constraint (ADR-021, ADR-022).

## Task List

### Phase 1: Paid Registration Creation Flow

- [x] **T1 — Paid Workshop Registration Initiation (Happy Path)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Domain, Application, Infrastructure, Presentation (spanning `registrations` and `payments`)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationPaymentPort.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/CreatePaymentIntentCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/PaymentIntentResult.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/package-info.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/package-info.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/CurrencyUnitConverter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapter.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/strategy/PaidRegistrationStrategy.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/infrastructure/gateway/CurrencyUnitConverterTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/strategy/PaidRegistrationStrategyTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationControllerPaidTest.java`
  - **Acceptance Criteria:**
    - When an authenticated student submits `POST /api/v1/registrations` for a `PUBLISHED` workshop with `price > 0`, the endpoint dispatches to `PaidRegistrationStrategy` and returns `201 Created` with `status: "PENDING"`, `priceAtRegistration`, `currency`, and `paymentClientSecret` (FR-01, FR-05, FR-16, FR-17).
    - `expires_at` is populated on the created `PENDING` registration with `now() + TTL` (default 30 minutes, loaded from `app.registration.pending-ttl-minutes`) (FR-01, BR-09).
    - `qrPayload`, `qrSignature`, and `qrIssuedAt` are null and omitted from the response JSON (FR-05).
    - The workshop's Redis capacity is decremented atomically (DECR) and `registered_count` is incremented in DB (FR-02).
    - Stripe Payment Intent is created with amount converted via `CurrencyUnitConverter`, currency in lowercase, and `registrationId` in metadata (FR-03, BR-12).
    - Free registrations (`price == 0`) continue to execute via `FreeRegistrationStrategy` returning `201 Created` with `status: "CONFIRMED"` and QR fields intact (no regression).

- [x] **T2 — Paid Registration Capacity, Resilience & Idempotency Edge Cases**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/strategy/PaidRegistrationStrategy.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/StripePaymentPortAdapter.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/strategy/PaidRegistrationStrategyResilienceTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/presentation/controllers/RegistrationControllerPaidEdgeCasesTest.java`
  - **Acceptance Criteria:**
    - When workshop remaining seats in Redis reaches zero, `POST /api/v1/registrations` returns `409 Conflict` ("No seats remaining for this workshop"), compensates Redis counter, and creates no registration record (FR-06, BR-01).
    - When a student already has an active (`PENDING` or `CONFIRMED`) registration for the workshop, returns `409 Conflict` (FR-07, BR-10).
    - When a student has a `CONFIRMED` registration overlapping the target workshop's time slot, returns `422 Unprocessable Entity` (FR-08, BR-11).
    - When Stripe call fails or the Resilience4j circuit breaker is `OPEN`, returns `503 Service Unavailable`, compensates Redis capacity DECR (INCR), and creates no DB records (FR-14, FR-15, BR-13).
    - The student's `Idempotency-Key` header is forwarded to Stripe Payment Intent creation; subsequent requests with the same key replay the cached `201 Created` without calling Stripe or creating new records (FR-04, FR-12).

#### Checkpoint: Phase 1 Complete

- [x] Authenticated students can register for paid workshops and receive a valid `PENDING` registration with Stripe client secret.
- [x] All capacity, concurrency, time-slot overlap, idempotency, and circuit-breaker failure guards pass tests.
- [x] `ModulithArchitectureTest` passes with zero boundary violations.

### Phase 2: Payment Webhook Ingestion & Lifecycle Confirmation

- [x] **T3 — Payment Webhook Gateway & HMAC Verification**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Presentation, Infrastructure, Application (`payments` module)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/gateway/StripeWebhookVerifier.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/ports/in/ProcessPaymentWebhookUseCase.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookUseCaseImpl.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/presentation/controllers/PaymentWebhookController.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/infrastructure/gateway/StripeWebhookVerifierTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/presentation/controllers/PaymentWebhookControllerTest.java`
  - **Acceptance Criteria:**
    - `POST /api/v1/payments/webhook` accepts unauthenticated requests and verifies the `Stripe-Signature` HMAC-SHA256 header against the raw request body using `stripe.webhook-secret` (FR-18, FR-25, BR-05).
    - Requests with missing, malformed, or invalid signatures are rejected with `400 Bad Request` (FR-18).
    - Requests with a valid signature for unhandled or unknown event types (e.g., `customer.subscription.created`) return `200 OK` with `{ "success": true, "message": "Webhook received." }` without executing business logic (FR-19, FR-26, BR-06).

- [x] **T4 — Payment Intent Succeeded Processing, Ticket Issuance & Outbox Notification**
  - **Size:** M
  - **Depends on:** T3
  - **Layers touched:** Domain, Application, Infrastructure (`registrations` and `payments` modules)
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/ConfirmPaidRegistrationCommand.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/dto/RegistrationConfirmedDto.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/domain/model/Payment.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/domain/model/PaymentStatus.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentJpaEntity.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/infrastructure/persistence/PaymentJpaRepository.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookUseCaseImpl.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/facade/RegistrationFacadeTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookSuccessTest.java`
  - **Acceptance Criteria:**
    - When `payment_intent.succeeded` webhook arrives with a valid signature, a new `payments` record is saved with `status = COMPLETED`, `provider_payment_id`, `amount`, and `currency` (FR-20).
    - In the same DB transaction, `RegistrationApi.confirmPaidRegistration(...)` transitions the registration from `PENDING` to `CONFIRMED`, atomically sets RSA-signed QR fields (`qr_payload`, `qr_signature`, `qr_issued_at`), and writes `WorkshopRegisteredEvent` to the Spring Modulith outbox (FR-20, FR-23, BR-04).
    - Post-commit side effects publish SSE `payment.confirmed` to `unihub:stream:user:{studentUserId}` and `registration.updated` to `unihub:stream:workshop:{workshopId}` (FR-24).
    - The notification worker receives `WorkshopRegisteredEvent` from RabbitMQ and delivers confirmation email with QR PNG attachment to the student (FR-23).
    - Calling `GET /api/v1/registrations/{id}` for the confirmed registration returns the full ticket with QR fields (API Contract §POST /api/v1/registrations/me consistency).

- [x] **T5 — Payment Intent Failed Processing, Seat Restoration & Webhook Idempotency**
  - **Size:** S
  - **Depends on:** T4
  - **Layers touched:** Application, Infrastructure, Presentation
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/api/RegistrationApi.java`
    - `apps/backend/src/main/java/com/unihub/workshop/registrations/application/facade/RegistrationFacade.java`
    - `apps/backend/src/main/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookUseCaseImpl.java`
    - `apps/backend/src/test/java/com/unihub/workshop/payments/application/usecases/ProcessPaymentWebhookFailedAndIdempotencyTest.java`
    - `apps/backend/src/test/java/com/unihub/workshop/registrations/application/facade/RegistrationFacadeFailureTest.java`
  - **Acceptance Criteria:**
    - When `payment_intent.payment_failed` webhook arrives, `payments` row is saved with `status = FAILED`, `RegistrationApi.failPaidRegistration(...)` sets registration status to `FAILED`, Redis capacity is incremented (INCR), and `workshops.registered_count` is decremented in DB (FR-21, BR-08).
    - No `WorkshopRegisteredEvent` is published on payment failure (FR-21).
    - When a duplicate webhook for an already-processed `provider_payment_id` arrives, the DB unique constraint violation is caught gracefully, returning `200 OK` without re-executing state transitions, outbox events, or notifications (FR-22, BR-07).
    - If a webhook references a nonexistent or non-PENDING registration, the handler logs a warning and returns `200 OK` to satisfy Stripe's delivery contract (FR-27).

#### Checkpoint: Feature Complete

- [x] All 5 tasks are checked off.
- [x] Every acceptance criterion across all tasks has been verified end-to-end.
- [x] Core Backend unit test suite passes: `./gradlew test --tests "com.unihub.workshop.*"`.
- [x] Modulith architecture test passes: `./gradlew test --tests "com.unihub.workshop.ModulithArchitectureTest"`.
- [x] Ready for `/review paid-workshop-registration`.

## Risks and Mitigations

| Risk                                                                 | Impact | Mitigation                                                                                                                                                                                                                            |
| :------------------------------------------------------------------- | :----: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Spring Modulith cyclic dependency (`registrations` ↔ `payments`)** |  High  | `registrations` exposes pure SPI interface `RegistrationPaymentPort` and facade `RegistrationApi` in its `api/` package. `payments` depends strictly on `registrations::api`. `registrations` never imports anything from `payments`. |
| **Stripe raw body stream consumption during HMAC verification**      |  High  | In `PaymentWebhookController`, accept raw `String payload` (or preserve `byte[]`) directly via Spring MVC and pass it unmodified to `StripeWebhookVerifier` before any ObjectMapper deserialization.                                  |
| **Stripe API latency / thread pool starvation**                      |  High  | Stripe Payment Intent call is isolated outside any Spring `@Transactional` boundary (FR-14, BR-02) and wrapped by Resilience4j circuit breaker with explicit fallback (FR-15, BR-13).                                                 |
| **Floating-point rounding error in Stripe amount calculation**       | Medium | `CurrencyUnitConverter` uses `BigDecimal.multiply(BigDecimal.valueOf(100)).longValueExact()` for 2-decimal currencies and factor 1 for 0-decimal currencies (like VND).                                                               |
| **Stripe webhook retry storms on unhandled errors**                  | Medium | Webhook controller catches all post-verification errors, logs structured alerts, and always responds `200 OK` to Stripe (BR-06).                                                                                                      |

## Open Questions

None. All architectural and business questions were clarified and confirmed during spec drafting.
