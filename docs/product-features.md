# UniHub Workshop — Product Features

## Scope

This catalog identifies product capabilities, their purpose and client applicability. Stable feature IDs connect product scope to independently owned server, web and mobile delivery records. Client capabilities reference these IDs and may add supporting UX within approved scope.

Web serves public workshop visitors, students and organization-scoped organizers. Its staff portal is limited to assigned-workshop information, account/security and notifications; QR scanning, durable offline capture and synchronization belong to mobile. Mobile serves provisioned check-in staff only, with assigned-workshop preparation, capture/sync/status and supporting account/notification flows. Students and organizers do not have mobile application journeys in this scope.

Client capability decomposition and order are recorded separately in the platform experience maps and delivery catalogs. A server/background feature does not imply a dedicated client screen, and a provider data relationship does not imply a UI implementation dependency.

## References

- [Overview](product-overview.md)
- [Server Feature Delivery](server/feature-delivery.md)
- [Web Feature Delivery](client/web/feature-delivery.md)
- [Mobile Feature Delivery](client/mobile/feature-delivery.md)

## Domain Index

| #   | Domain                                                                        | Feature Count |
| :-- | :---------------------------------------------------------------------------- | :-----------: |
| 1   | [Identity & Access Management](#1-identity--access-management)                |       8       |
| 2   | [Workshop Lifecycle Management](#2-workshop-lifecycle-management)             |       5       |
| 3   | [Workshop Discovery & Search](#3-workshop-discovery--search)                  |       2       |
| 4   | [Workshop Registration](#4-workshop-registration)                             |       4       |
| 5   | [Payment Processing](#5-payment-processing)                                   |       2       |
| 6   | [On-Site Check-in & Attendance](#6-on-site-check-in--attendance)              |       3       |
| 7   | [Notification System](#7-notification-system)                                 |       2       |
| 8   | [AI-Powered Brochure Summarization](#8-ai-powered-brochure-summarization)     |       1       |
| 9   | [Student Data Synchronization](#9-student-data-synchronization)               |       2       |
| 10  | [System Reliability & Infrastructure](#10-system-reliability--infrastructure) |       5       |

**Total: 34 Features**

## 1. Identity & Access Management

| Feature ID                                    | Purpose                                                                                                                                               | Clients                                             | Delivery                                                                                                                                                            |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `user-registration-and-identity-verification` | Students create their own account, verify their university identity and activate access. Registration eligibility requires a matching student record. | Web                                                 | [Server](server/feature-delivery.md#11-user-registration-and-identity-verification) · [Web](client/web/feature-delivery.md)                                         |
| `authentication-and-session-management`       | Users authenticate, renew access and end sessions within their assigned role and organization.                                                        | Web, Mobile                                         | [Server](server/feature-delivery.md#12-authentication-and-session-management) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md) |
| `public-key-distribution`                     | Authorized clients obtain current public verification keys for signed tokens and offline tickets.                                                     | Mobile (primary consumer), Web (informational only) | [Server](server/feature-delivery.md#13-public-key-distribution) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md)               |
| `password-reset`                              | Users recover account access using the documented identity verification and password reset flow.                                                      | Web                                                 | [Server](server/feature-delivery.md#14-password-reset) · [Web](client/web/feature-delivery.md)                                                                      |
| `organization-member-management`              | Organizers provision and manage members within their organization using the defined role and activation lifecycle.                                    | Web                                                 | [Server](server/feature-delivery.md#15-organization-member-management) · [Web](client/web/feature-delivery.md)                                                      |
| `workshop-staff-assignment`                   | Organizers assign check-in staff to specific workshops and manage those assignments.                                                                  | Web                                                 | [Server](server/feature-delivery.md#16-workshop-staff-assignment) · [Web](client/web/feature-delivery.md)                                                           |
| `active-session-management`                   | Users inspect their active sessions and revoke access from selected sessions.                                                                         | Web, Mobile                                         | [Server](server/feature-delivery.md#17-active-session-management) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md)             |
| `user-profile-management`                     | Users view and maintain their profile, including supported identity and avatar information.                                                           | Web, Mobile                                         | [Server](server/feature-delivery.md#18-user-profile-management) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md)               |

## 2. Workshop Lifecycle Management

| Feature ID                               | Purpose                                                                                                 | Clients                           | Delivery                                                                                                               |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------- | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `workshop-creation-and-draft-management` | Organizers create and edit workshop drafts, speakers, schedules and associated media.                   | Web                               | [Server](server/feature-delivery.md#21-workshop-creation-and-draft-management) · [Web](client/web/feature-delivery.md) |
| `workshop-publishing-and-update`         | Organizers publish eligible workshops and manage allowed lifecycle transitions.                         | Web                               | [Server](server/feature-delivery.md#22-workshop-publishing-and-update) · [Web](client/web/feature-delivery.md)         |
| `workshop-rescheduling`                  | Organizers change workshop schedules under the documented lifecycle and participant notification rules. | Web                               | [Server](server/feature-delivery.md#23-workshop-rescheduling) · [Web](client/web/feature-delivery.md)                  |
| `workshop-cancellation`                  | Organizers cancel workshops with the required registration and payment consequences.                    | Web                               | [Server](server/feature-delivery.md#24-workshop-cancellation) · [Web](client/web/feature-delivery.md)                  |
| `workshop-lifecycle-sweeper`             | Workshop lifecycle changes scheduled by time are applied automatically.                                 | None (background scheduled tasks) | [Server](server/feature-delivery.md#25-workshop-lifecycle-sweeper)                                                     |

## 3. Workshop Discovery & Search

| Feature ID                  | Purpose                                                                      | Clients | Delivery                                                                                                  |
| --------------------------- | ---------------------------------------------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------- |
| `workshop-catalog-browsing` | Students browse and filter available workshops and inspect workshop details. | Web     | [Server](server/feature-delivery.md#31-workshop-catalog-browsing) · [Web](client/web/feature-delivery.md) |
| `workshop-full-text-search` | Students search workshops by text and supported filters.                     | Web     | [Server](server/feature-delivery.md#32-workshop-full-text-search) · [Web](client/web/feature-delivery.md) |

## 4. Workshop Registration

| Feature ID                                     | Purpose                                                                                                             | Clients                                                        | Delivery                                                                                                                     |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `free-workshop-registration`                   | Eligible students register for free workshops subject to capacity and registration rules and receive their ticket.  | Web                                                            | [Server](server/feature-delivery.md#41-free-workshop-registration) · [Web](client/web/feature-delivery.md)                   |
| `paid-workshop-registration`                   | Eligible students complete payment to register for paid workshops without duplicate charges or reservations.        | Web                                                            | [Server](server/feature-delivery.md#42-paid-workshop-registration) · [Web](client/web/feature-delivery.md)                   |
| `registration-reconciliation-and-cancellation` | Registration state is reconciled with payment outcomes; permitted cancellations release the applicable reservation. | Web (Student ticket view & Organizer workshop attendee roster) | [Server](server/feature-delivery.md#43-registration-reconciliation-and-cancellation) · [Web](client/web/feature-delivery.md) |
| `workshop-registration-monitoring-and-roster`  | Organizers monitor registrations and inspect the participant roster for workshops they manage.                      | Web (Organizer workshop management dashboard)                  | [Server](server/feature-delivery.md#44-workshop-registration-monitoring-and-roster) · [Web](client/web/feature-delivery.md)  |

## 5. Payment Processing

| Feature ID                   | Purpose                                                                                                           | Clients                                      | Delivery                                                                                                   |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `payment-webhook-handling`   | Payment provider outcomes are reconciled with registrations and tickets, including duplicate or delayed delivery. | None (called by Stripe, not by user clients) | [Server](server/feature-delivery.md#51-payment-webhook-handling)                                           |
| `payment-history-and-status` | Students inspect payment history and the current status of their transactions.                                    | Web                                          | [Server](server/feature-delivery.md#52-payment-history-and-status) · [Web](client/web/feature-delivery.md) |

## 6. On-Site Check-in & Attendance

| Feature ID                       | Purpose                                                                                                  | Clients                                                       | Delivery                                                                                                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `mobile-offline-checkin`         | Assigned staff obtain workshop information needed to prepare on-site check-in.                           | Mobile (Check-in Staff app), Web (Staff portal)               | [Server](server/feature-delivery.md#61-mobile-offline-checkin) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md)         |
| `attendance-sync-reconciliation` | Staff synchronize attendance captured on their devices and receive deterministic reconciliation results. | Mobile (caller), Web (organizer views attendance reports)     | [Server](server/feature-delivery.md#62-attendance-sync-reconciliation) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md) |
| `live-attendance-monitoring`     | Organizers monitor attendance for their workshops with supported live updates.                           | Web (Organizer dashboard), Mobile (assigned Staff monitoring) | [Server](server/feature-delivery.md#63-live-attendance-monitoring) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md)     |

## 7. Notification System

| Feature ID                       | Purpose                                                                                                        | Clients                                                              | Delivery                                                                                                                                                     |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `notification-delivery-pipeline` | Participants and organizers receive notifications for the supported workshop, registration and payment events. | None (background worker pipeline, no direct client HTTP interaction) | [Server](server/feature-delivery.md#71-notification-delivery-pipeline)                                                                                       |
| `in-app-notification-management` | Users view and manage their in-app notification inbox and read state.                                          | Web, Mobile                                                          | [Server](server/feature-delivery.md#72-in-app-notification-management) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md) |

## 8. AI-Powered Brochure Summarization

| Feature ID                  | Purpose                                                                                      | Clients | Delivery                                                                                                  |
| --------------------------- | -------------------------------------------------------------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------- |
| `ai-brochure-summarization` | Organizers request a summary of a workshop brochure and inspect the result or failure state. | Web     | [Server](server/feature-delivery.md#81-ai-brochure-summarization) · [Web](client/web/feature-delivery.md) |

## 9. Student Data Synchronization

| Feature ID                      | Purpose                                                                                       | Clients                                                                                                                                                                                             | Delivery                                                                                                |
| ------------------------------- | --------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `nightly-student-csv-ingestion` | University student directory data is refreshed through scheduled CSV ingestion.               | None (backend-only scheduled job). The reject artifact is uploaded to MinIO/S3 and its key is included in an organization-scoped notification event; there is no direct UI for uploading CSV files. | [Server](server/feature-delivery.md#91-nightly-student-csv-ingestion)                                   |
| `ingestion-reject-report`       | Authorized operators inspect ingestion outcomes and obtain reports of rejected input records. | Web                                                                                                                                                                                                 | [Server](server/feature-delivery.md#92-ingestion-reject-report) · [Web](client/web/feature-delivery.md) |

## 10. System Reliability & Infrastructure

| Feature ID                               | Purpose                                                                                                                  | Clients                                            | Delivery                                                                                                                                                     |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `distributed-rate-limiting`              | The platform protects high-demand operations from excessive request traffic with documented failure behavior.            | All (transparent middleware)                       | [Server](server/feature-delivery.md#101-distributed-rate-limiting)                                                                                           |
| `resilience-platform-setup`              | The platform handles unavailable or slow dependencies through its resilience policies and controlled failure testing.    | None (transparent platform base)                   | [Server](server/feature-delivery.md#102-resilience-platform-setup)                                                                                           |
| `observability-platform-setup`           | Operators observe service health, traffic, resource usage and failures through the monitoring stack.                     | None (infrastructure dashboards for DevOps)        | [Server](server/feature-delivery.md#103-observability-platform-setup)                                                                                        |
| `infrastructure-bootstrap-and-seed-data` | The platform initializes required organization, account and operational prerequisites and maintains expiring records.    | None (startup process + scheduled background task) | [Server](server/feature-delivery.md#104-infrastructure-bootstrap-and-seed-data)                                                                              |
| `real-time-event-streaming-hub`          | Clients receive the supported authenticated live event updates without treating them as a replacement for durable state. | Web and Mobile (all authenticated roles)           | [Server](server/feature-delivery.md#105-real-time-event-streaming-hub) · [Web](client/web/feature-delivery.md) · [Mobile](client/mobile/feature-delivery.md) |
