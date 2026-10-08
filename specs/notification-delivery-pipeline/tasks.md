# Implementation Plan: Notification Delivery Pipeline

> **Spec:** `specs/notification-delivery-pipeline/SPEC.md`  
> **Status:** APPROVED

## Overview

This implementation plan decomposes the `notification-delivery-pipeline` (Feature 7.1) background worker service into an ordered sequence of four vertical-slice tasks. The pipeline consumes integration domain events from RabbitMQ queue `q.notifications` and fans out notifications across in-app persistence, real-time Server-Sent Events (SSE) via Redis Pub/Sub, and responsive HTML emails via `JavaMailSender` wrapped in Resilience4j. Each task delivers a fully functioning, end-to-end testable capability through all Hexagonal layers (Inbound Listener & DTOs → Core Ports & Domain Orchestration → Outbound Persistence, Email, and Realtime Adapters), starting with direct email delivery and audit logging, expanding to in-app and real-time SSE multi-channel delivery, and concluding with organization and paginated workshop attendee fan-out.

## Architecture Decisions for This Plan

- **Hexagonal Architecture in `apps/workers/`:** Code is partitioned into `inbound` (driving adapters: AMQP listener, DTOs, handlers), `core` (domain models, use case orchestration, port interfaces), and `outbound` (driven adapters: Spring Data JPA, JavaMailSender, Redis Pub/Sub, configuration). All `core` models remain pure Java to satisfy `HexagonalArchitectureTest`.
- **Polymorphic Event Identity & Generic Idempotency:** The core defines a `ConsumedNotificationEvent` contract encapsulating `eventType()` and `idempotencyKey()`. Each event DTO encapsulates its domain-derived identity, enabling generic deduplication in the core orchestration service without branching on event types.
- **Extensible Strategy-Based Event Dispatching:** The AMQP listener delegates message routing to a `NotificationEventDispatcher` that resolves handlers from an open-closed `NotificationEventHandler<T>` registry based on the standard `__TypeId__` header.
- **External Channel Delivery Logging:** The `notification_delivery_logs` table strictly tracks external delivery channels (`channel = EMAIL`). In-app notifications are persisted and audited in `in_app_notifications`. Soft-failures for ephemeral Redis SSE triggers are captured via application logs and Micrometer metrics.
- **Batch Ingestion Header Alignment in T3:** `RabbitMqIngestionEventPublisher` in `batch_ingestion` is updated to attach standard `__TypeId__` headers (`FileCompletedEvent`, `FileRejectedEvent`), ensuring 100% unified dispatching across the system without payload sniffing.

## Task List

### Phase 1: Direct Single-Channel Pipeline Foundation (OTP Flow)

- [x] **T1 — End-to-End Direct Email Delivery Pipeline with Circuit Breaker and Audit Logging**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched:** Inbound (AMQP listener, DTO, handler), Core (event contract, ports, delivery service), Outbound (AMQP config, Thymeleaf renderer, JavaMailSender adapter, delivery log JPA adapter)
  - **Files:**
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/models/DeliveryChannel.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/models/DeliveryStatus.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/in/ConsumedNotificationEvent.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/in/NotificationEventHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/EmailSenderPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/NotificationDeliveryLogPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/usecases/NotificationDeliveryService.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/NotificationListener.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/NotificationEventDispatcher.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/OtpRequestedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/OtpRequestedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/config/NotificationAmqpConfig.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/email/ThymeleafEmailRenderer.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/email/JavaMailSenderAdapter.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/NotificationDeliveryLogEntity.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/JpaNotificationDeliveryLogRepository.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/NotificationDeliveryLogAdapter.java`
    - `apps/workers/src/main/resources/templates/email/otp-activation.html`
    - `apps/workers/src/main/resources/templates/email/otp-password-reset.html`
  - **Acceptance Criteria:**
    - Consuming `OtpRequestedEvent` (with `purpose=ACTIVATION` or `PASSWORD_RESET` and matching `__TypeId__`) renders the respective Thymeleaf template and dispatches an email via `JavaMailSender` (Scenario 1, FR-01, FR-02, FR-16, FR-17, FR-18, FR-28).
    - An audit log row is created in `notification_delivery_logs` with `channel = EMAIL`, transitions from `PENDING` to `SENT` or `FAILED`, and records errors upon failure (FR-22, FR-23, FR-24).
    - Redelivered messages with an already `SENT` audit record for the same `idempotencyKey` are acknowledged without sending duplicate emails or creating duplicate audit entries (Scenario 8, FR-21, BR-12).
    - Outbound email dispatch is protected by Resilience4j circuit breaker `emailProvider`; open circuit fails gracefully as `FAILED` audit status without crashing the listener (FR-19, FR-20, BR-11).
    - OTP values never appear in audit logs or log lines (BR-08).

#### Checkpoint: Direct Email Pipeline Operational

- [x] `OtpRequestedEvent` messages consumed from `q.notifications` deliver emails and persist delivery logs end-to-end.
- [x] Circuit breaker, idempotency deduplication, and template rendering unit tests pass.
- [x] Hexagonal architecture boundary checks for `notifications` core isolation pass.

### Phase 2: Multi-Channel Expansion (In-App Persistence & Real-Time SSE)

- [x] **T2 — Multi-Channel Direct Delivery with In-App Persistence and Redis Real-Time SSE**
  - **Size:** M
  - **Depends on:** T1
  - **Layers touched:** Inbound (DTOs, handlers), Core (in-app port, SSE publisher port, multi-channel orchestration), Outbound (in-app JPA adapter, Redis Pub/Sub adapter, Thymeleaf templates)
  - **Files:**
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/InAppNotificationPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/RealtimeEventPublisherPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/StaffAssignedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/StaffRevokedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/UserProvisionedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/UserStatusChangedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/StaffAssignedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/StaffRevokedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/UserProvisionedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/UserStatusChangedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/InAppNotificationEntity.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/JpaInAppNotificationRepository.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/InAppNotificationAdapter.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/realtime/RedisRealtimePublisherAdapter.java`
    - `apps/workers/src/main/resources/templates/email/staff-assigned.html`
    - `apps/workers/src/main/resources/templates/email/staff-revoked.html`
    - `apps/workers/src/main/resources/templates/email/user-provisioned.html`
    - `apps/workers/src/main/resources/templates/email/user-status-changed.html`
  - **Acceptance Criteria:**
    - Consuming `StaffAssignedToWorkshopEvent` and `StaffRevokedFromWorkshopEvent` persists an in-app notification with `action_url` (`/workshops/{workshopId}` and `/workshops`), publishes a `notification.new` signal to Redis `unihub:stream:user:{userId}`, and sends an email (Scenario 3, Scenario 7, FR-10, FR-11, FR-13, FR-14, FR-31, FR-32).
    - Consuming `UserProvisionedEvent` sends temporary password email, persists welcome in-app notification (`action_url = null`), and emits no SSE (Scenario 2, FR-29, BR-06).
    - Consuming `UserStatusChangedEvent` sends status email and persists in-app notification without SSE (FR-30).
    - Redis publish failures are treated as soft failures: a warning is logged and metrics incremented, but in-app persistence and email delivery complete normally and message is acknowledged (FR-15, FR-27).
    - Duplicate messages do not insert duplicate in-app notification records (FR-12, BR-02).

#### Checkpoint: Multi-Channel Delivery Operational

- [x] All four direct multi-channel event types deliver across In-App, Redis SSE, and Email channels according to their channel matrix.
- [x] In-app idempotency, action URL formatting, and Redis soft-failure isolation verified by unit tests.

### Phase 3: Multi-Recipient Fan-Out & Pipeline Hardening

- [x] **T3 — Organization-Scoped Recipient Fan-Out and Batch Ingestion Event Header Alignment**
  - **Size:** M
  - **Depends on:** T2
  - **Layers touched:** Publisher (`batch_ingestion` alignment), Inbound (DTO, handler), Core (organization recipient resolution port, fan-out logic), Outbound (user query JPA adapter, Thymeleaf templates)
  - **Files:**
    - `apps/workers/src/main/java/com/unihub/workshop/workers/batch_ingestion/outbound/messaging/RabbitMqIngestionEventPublisher.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/OrganizationRecipientResolverPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/FileCompletedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/FileRejectedEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/FileCompletedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/FileRejectedNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/JpaUserRepository.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/OrganizationRecipientResolverAdapter.java`
    - `apps/workers/src/main/resources/templates/email/ingestion-completed.html`
    - `apps/workers/src/main/resources/templates/email/ingestion-rejected.html`
  - **Acceptance Criteria:**
    - `RabbitMqIngestionEventPublisher` attaches `__TypeId__ = "FileCompletedEvent"` and `"FileRejectedEvent"` when publishing to `unihub.events::notifications`.
    - Consuming `FileCompletedEvent` or `FileRejectedEvent` resolves all active organizers where `role = ORGANIZER`, `is_active = true`, and `organization_id` matches the event (Scenario 5, FR-07, BR-05).
    - Fanned-out notifications deliver to each active organizer across In-App (`action_url = /admin/import`), Redis SSE, and Email (FR-35, FR-36).
    - When an organization has zero active organizers, zero notifications are sent, a warning log is recorded, and the message is positively acknowledged without errors (FR-09, BR-07).

- [x] **T4 — Paginated Workshop Attendee Fan-Out and Dead-Letter Error Handling**
  - **Size:** M
  - **Depends on:** T3
  - **Layers touched:** Inbound (DTO, handler, DLQ routing), Core (attendee resolution port, pagination orchestration), Outbound (registration & user query JPA adapter, Thymeleaf templates)
  - **Files:**
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/core/ports/out/WorkshopAttendeeResolverPort.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/WorkshopRescheduledEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/dto/WorkshopCanceledEventDto.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/WorkshopRescheduledNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/inbound/handler/WorkshopCanceledNotificationHandler.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/JpaRegistrationRepository.java`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/notifications/outbound/persistence/WorkshopAttendeeResolverAdapter.java`
    - `apps/workers/src/main/resources/templates/email/workshop-rescheduled.html`
    - `apps/workers/src/main/resources/templates/email/workshop-canceled.html`
  - **Acceptance Criteria:**
    - Consuming `WorkshopRescheduledEvent` and `WorkshopCanceledEvent` resolves only attendees with `status = CONFIRMED` (excluding `PENDING`, `CANCELED`, `WAITLISTED`) in bounded pages (Scenario 4, Scenario 11, FR-08, BR-04, NFR-Performance).
    - Fanned-out notifications deliver to all confirmed students across In-App (`action_url = /workshops/{slug}`), Redis SSE, and Email (FR-33, FR-34).
    - Workshops with zero confirmed registrations log a warning and acknowledge cleanly without dead-lettering (Scenario 6, FR-09).
    - Messages arriving with missing or unmapped `__TypeId__` headers are rejected without requeue and moved to `unihub.dlq`, logging payload excerpts (Scenario 10, FR-03).
    - Transient database outages cause negative acknowledgement for broker-level retry up to DLQ threshold (Scenario 9, FR-25, FR-26, BR-12).

#### Checkpoint: Feature Complete

- [x] All four tasks above (T1–T4) are implemented and unit tested.
- [x] Every acceptance criterion across all 11 scenarios in `SPEC.md` has been verified.
- [x] Architecture guard test passes: `./gradlew :apps:workers:test --tests "com.unihub.workshop.workers.HexagonalArchitectureTest"`.
- [x] Targeted unit tests for notifications worker pass with zero regressions.
- [x] Ready for `/review notification-delivery-pipeline`.

## Risks and Mitigations

| Risk                                                                                                                                                                                  | Impact | Mitigation                                                                                                                                                                                                                                                                      |
| :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **High fan-out memory spike on cancellation:** Rescheduling or canceling large workshops with thousands of registrations could exhaust heap if all recipients are loaded into memory. | High   | Paginate registration queries in `WorkshopAttendeeResolverAdapter` using chunked batches (e.g. 500 records per page) and stream delivery through the pipeline (FR-08, Scenario 11).                                                                                             |
| **SMTP Server degradation or outage:** Slow SMTP responses could tie up listener threads and cause queue lag.                                                                         | Medium | Enforce Resilience4j Circuit Breaker (`emailProvider`) with strict timeouts and failure rate thresholds; email failures log as `FAILED` soft-failures and do not stall the thread (FR-19, FR-20).                                                                               |
| **Cross-module Hexagonal Architecture violation:** Querying `users` or `registrations` tables might inadvertently depend on other worker module packages.                             | Medium | Enforce ArchUnit rules in `HexagonalArchitectureTest`: `notifications` outbound persistence adapters query the shared PostgreSQL schema directly via its own internal Spring Data repositories, never importing classes from `batch_ingestion` or `search_sync` (FR-37, FR-38). |
| **Broker message redelivery duplicate side effects:** Transient network blips when acknowledging messages could cause duplicate emails or duplicate in-app alerts.                    | Medium | Enforce domain-derived `idempotencyKey` checks prior to persisting in-app records and prior to sending emails (FR-05, FR-12, FR-21).                                                                                                                                            |

## Open Questions

None. (All design decisions, idempotency keys, header routing, action URLs, and channel audit logging were resolved and approved in `SPEC.md`).
