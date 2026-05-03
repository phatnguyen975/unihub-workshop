# UniHub Workshop - Project Proposal

## 1. Context & Problem Statement

University A runs a 5-day "Skills and Career Week" with 8-12 concurrent workshops per day across multiple venues. Current operations rely on Google Forms for registration and manual email notifications, which no longer scale with growing participation.

### Current State

- Registrations are collected through separate Google Forms for each workshop.
- Organizers manually verify capacity, consolidate responses, and email confirmations.
- Check-in staff validate attendance on-site without a unified, real-time system.

### Pain Points

- **Scalability limits:** Manual consolidation cannot keep up with high-concurrency demand for popular sessions.
- **Operational bottlenecks:** Organizers spend significant time reconciling registrations and handling duplicates.
- **Error-prone workflows:** Manual data handling increases the risk of overbooking, missed confirmations, and inconsistent attendance records.

### Impact

- **Student experience:** Delayed confirmations, unclear seat availability, and inconsistent communication.
- **Organizational overhead:** High administrative effort for basic tasks that should be automated.
- **Data inconsistency:** Fragmented data sources lead to mismatched attendance and reporting gaps.

### Gap Analysis

A unified, end-to-end digital platform is required to automate registration, payments, notifications, and attendance tracking while enforcing data consistency and fair access during peak traffic.

## 2. Objectives

### Primary Goal

Deliver a secure, scalable UniHub Workshop system that digitizes the full workshop lifecycle from discovery to on-site check-in.

### Functional Objectives

- Automate workshop discovery, registration (free and paid), and QR-based confirmation.
- Provide a centralized admin portal for lifecycle management and real-time analytics.
- Enable offline-capable check-in to ensure attendance capture in unstable networks.
- Deliver AI-assisted workshop summaries from uploaded PDF brochures.
- Integrate nightly student data imports for identity verification.

### Technical Objectives

- Sustain **12,000+ concurrent users** within the first 10 minutes of registration.
- Absorb **traffic spikes concentrated within the first 3 minutes** without overbooking.
- Guarantee **exactly-once payment execution** under retries and gateway timeouts.
- Preserve **offline check-in integrity** and reconcile records after reconnect.

### Business Objectives

- Reduce manual workload for the Organizing Committee.
- Improve student trust through reliable confirmations and fairness.
- Provide accurate, real-time metrics for planning and reporting.

## 3. Target Audience & User Needs

| Actor                  | Who They Are                          | Core Actions                                                                    | What Matters Most                                                |
| :--------------------- | :------------------------------------ | :------------------------------------------------------------------------------ | :--------------------------------------------------------------- |
| **Students**           | Workshop attendees across faculties   | Browse schedules, register (free/paid), receive QR confirmations, check in      | Fair access during spikes, clear availability, fast confirmation |
| **Organizers (Admin)** | Organizing Committee staff            | Create/manage workshops, reschedule/cancel, monitor registration and attendance | Operational control, reliable analytics, minimal manual overhead |
| **Check-in Staff**     | Venue entry staff with mobile devices | Scan QR codes, verify attendance, handle offline mode                           | Speed, offline capability, accurate reconciliation               |

## 4. Scope

### In-Scope

- Dual-client architecture: **React Web App** (Students/Admins) and **Android Native App (Java/XML)** for check-in staff.
- Java backend with Neon (Serverless Postgres) as the primary transactional data store.
- Pluggable notification architecture (email/in-app now, extensible to Telegram/SMS later).
- Async processing via RabbitMQ and outbox workers for notifications, AI summarization, and CSV ingestion.
- Offline-first check-in with local mobile database and server reconciliation.
- AI-powered summarization for uploaded PDF workshop brochures.
- Nightly CSV ingestion pipeline for legacy student data synchronization.
- Role-based access control (RBAC) enforced across API and UI layers.
- Containerized deployment with Docker for consistent local setup and evaluation.

> [!TIP]
> Use modular service boundaries and clear API contracts to enable rapid extension of notification channels and check-in sync logic without core system rewrites.

### Out-of-Scope

- Production-grade bank settlement or direct integration with banking systems beyond a payment gateway abstraction.
- Long-term infrastructure provisioning, monitoring, and maintenance after project delivery.
- Bi-directional integration with the legacy Student Management System (no API access).

## 5. Risks & Constraints

### Technical Risks

- **Concurrency & race conditions:** High-demand sessions must prevent multiple users claiming the final seat. Requires strict transactional controls in PostgreSQL.
- **Traffic spikes:** 12,000 concurrent users in minutes can overload APIs without rate limiting and queuing.
- **Payment instability:** Gateway outages and retries risk double-charging unless idempotency is enforced end-to-end.
- **Offline data integrity:** Check-in data captured offline must be safely reconciled without duplicates or missed attendance.
- **Unidirectional legacy integration:** CSV ingestion must tolerate malformed rows and duplicates without disrupting production data.

### Technical Constraints

- **Backend:** Java with Spring Boot.
- **Web Client:** React with TypeScript.
- **Mobile Client:** Android Native (Java/XML).
- **Primary Database:** Neon (Serverless Postgres).
- **Cache & Rate Limiting:** Redis.
- **Async Messaging:** RabbitMQ.
- **Object Storage:** S3-compatible storage for PDFs and CSV drops.

### Operational Constraints

- **Aggressive Delivery Timeline:** The project is bound by a strict and non-negotiable delivery schedule. This requires highly efficient, tightly sequenced development phases, and strict scope management to ensure timely deployment without compromising system resilience.

## 6. Success Criteria

### Functional Completeness

- React Web App and Android Native App operate as intended for their respective roles.
- Offline check-in sync, AI summarization, and nightly CSV ingestion are fully functional.
- RBAC is enforced consistently across APIs and UIs.

### Performance Benchmarks

- System sustains **12,000 concurrent users in the first 10 minutes**, with **60% within the first 3 minutes**, without crashing or corrupting data.
- Token Bucket rate limiting absorbs bursts and returns controlled `429` responses rather than overwhelming the backend.

### Technical Quality

- **Circuit Breakers**, **Idempotency Keys**, and **Rate Limiting** are implemented in production code (not mocked).
- **No duplicate payments** under retry testing through idempotency key enforcement.
- Core browsing and free registrations remain available during payment gateway outages via circuit breaker fail-fast behavior.
- Verified prevention of overbooking under concurrent registration stress.
