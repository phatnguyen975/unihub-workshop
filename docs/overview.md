# UniHub Workshop - Project Proposal

## 1. Executive Summary

University A hosts the annual "Skills and Career Week" spanning 5 days, featuring 8-12 concurrent workshops daily across multiple locations. Current manual processes using Google Forms and emails are failing to scale. **UniHub Workshop** is a secure, scalable platform designed to digitize the entire workshop lifecycle - from discovery and registration to QR-based on-site check-in. It will automate operations, manage high-concurrency peak traffic gracefully, and deliver actionable analytics for organizers.

## 2. Business Context & Problem Statement

### Current State

- Registrations are collected through fragmented Google Forms for each workshop.
- Organizers manually verify capacity, consolidate responses, and send email confirmations.
- Check-in staff validate attendance on-site using paper lists or spreadsheets without a unified, real-time system.

### Pain Points & Impact

- **Scalability Limits:** Manual consolidation cannot keep up with high-concurrency demand.
- **Operational Bottlenecks:** Organizers spend significant time reconciling registrations and handling duplicates, resulting in high administrative overhead.
- **Error-Prone Workflows:** Manual handling risks overbooking, mixed attendance records, and delayed confirmation emails, degrading the student experience.

> [!NOTE]
> **Gap Analysis:** A unified, end-to-end digital platform is required to automate registration, payments, notifications, and attendance tracking while enforcing strict data consistency and fair access during traffic spikes.

## 3. Project Objectives & Vision

### Primary Goal

Deliver a secure, scalable UniHub Workshop system that digitizes the full workshop lifecycle from discovery to on-site check-in.

### Functional Objectives

- **Discovery & Verification:** Automate workshop exploration, free/paid registration, and QR-based confirmations. Integrate nightly legacy student data imports.
- **Administration:** Provide a centralized admin portal for full lifecycle management, capacity control, and real-time attendance analytics.
- **On-site Mobility:** Enable robust offline-capable check-in to ensure attendance capture in unstable network environments.
- **AI Integration:** Deliver AI-assisted workshop summaries by automatically parsing uploaded PDF brochures.

### Technical & Business Objectives

- Sustain **12,000+ concurrent users** without crashing, absorbing massive 3-minute traffic spikes via rate limiting.
- Guarantee **exactly-once payment execution** and eliminate data loss using strict idempotency and circuit breakers.
- Reduce manual workload for the Organizing Committee and improve student trust through transparency and speed.

## 4. Target Audience & Roles

| Role               | Description                                        | Core Actions                                                                                            | What Matters Most                                                          |
| :----------------- | :------------------------------------------------- | :------------------------------------------------------------------------------------------------------ | :------------------------------------------------------------------------- |
| **Student**        | Attendees from various faculties                   | Browse schedules, register (free/paid), receive QR confirmations, check-in                              | Fair access, clear availability, fast confirmation                         |
| **Organizer**      | Faculty, Department, or Club admins (Multi-tenant) | Create/manage workshops, provision staff, monitor analytics strictly within their assigned Organization | Operational control, strict data isolation, minimized manual work          |
| **Check-in Staff** | Venue entry personnel with mobile devices          | Scan QR codes, verify attendance, operate in offline mode                                               | Speed, offline-resilience (Cannot self-register; provisioned by Organizer) |

> [!IMPORTANT]
> Access must be strictly verified using Role-Based Access Control (RBAC). Check-in Staff are restricted purely to QR scanning layers, while Students can only browse and register.

## 5. Detailed Scope & Core Features

### In-Scope

#### Workshop Discovery & Registration

- **Dynamic Catalog:** Real-time seat availability, room layouts, and speaker bios.
- **Unified Registration:** Support for both free and paid workshop tiers.
- **QR Confirmation:** Successful bookings generate unique QR codes for fast venue access.

#### Notification System

- **Immediate Alerts:** Confirmations sent via in-app notifications and email.
- **Future-Proofing:** Pluggable notification architecture designed to easily integrate new channels (e.g., Telegram, SMS) with minimal core changes.

#### Administration & Access Control

- **Admin Portal:** Centralized UI for creating, updating, rescheduling, or canceling events.
- **Role-Based Access Control (RBAC):** Strict enforcement of permissions, ensuring clear separation of actions between Students, Organizers, and Check-in Staff.

#### On-site Check-in

- **React Native Mobile App:** Dedicated interface for Check-in Staff to scan student QR codes.
- **Offline-First Resilience:** Utilizing SQLite as a robust local database to record scans with zero network coverage, incrementally reconciling attendance when connectivity is restored.

#### AI-Powered Summarization

- **Automated Processing:** Organizers can upload PDF workshop brochures for automated content extraction.
- **Asynchronous Execution:** AI generation of concise workshop descriptions operates via background workers, preventing any blocking of core synchronous user actions.

#### Student Data Synchronization

- **Nightly CSV Imports:** Scheduled ingestion jobs to synchronize student identities from the legacy Student Management System for identity verification during registration.

### Out-of-Scope

- Production-grade bank settlement beyond a standard payment gateway abstraction.
- Long-term infrastructure provisioning and post-delivery maintenance.
- Bi-directional integration with the legacy Student Management System (as no legacy APIs exist).

## 6. Architecture & Technical Vision

The system relies on a modular, decoupled architecture prioritizing resilience during high availability scenarios. The core application is designed as an **"Event-Driven Modular Monolith"**.

### Technology Stack

- **Client Applications:** React (TypeScript) for Web, and React Native (TypeScript) with SQLite for offline-first Mobile check-in.
- **Edge / API Gateway:** **Nginx** acts as the reverse proxy, load balancer, and API gateway, handling routing and edge-level rate limiting.
- **Core Backend:** Java & Spring Boot. The architectural foundation utilizes **Spring Modulith** to enable the "Event-Driven Modular Monolith" and internal event registry.
- **Data Persistence:** Serverless Neon PostgreSQL (for strict ACID transactions), Elasticsearch (Search Engine), and S3-compatible Object Storage for unstructured assets.
- **Asynchronous & Caching:** RabbitMQ for decoupled message processing, and Redis for distributed caching.

> [!TIP]
> Use modular service boundaries and clear API contracts to enable pluggable architectures, avoiding tight coupling across business domains.

## 7. Technical Challenges & Risk Mitigation

- **High-Concurrency Race Conditions:** Popular workshops face hundreds of simultaneous registration requests. Strict data consistency mechanisms must guarantee that multiple users cannot claim the final available seat, with specific concurrency controls deferred to the Design phase.
- **Massive Traffic Spikes:** Expected 12,000 concurrent users within the first 10 minutes (60% within 3 minutes). The platform requires robust edge or application-level rate limiting to gracefully absorb bursts and maintain stability. Detailed strategies are defined in the architectural design.
- **Payment Resilience & Idempotency:** Gateway outages must not halt discovery or free registrations. The system requires strict idempotency mechanisms to prevent duplicate processing, and resilient integration patterns to isolate gateway failures from core services. Implementation details are deferred to the Design phase.
- **Offline Data Integrity:** Check-in records captured in zero-connectivity zones must be aggressively reconciled. The backend must employ idempotent synchronization mechanisms to gracefully reject duplicate attendance sync requests triggered by network instability, ensuring data integrity without service disruption.
- **Unidirectional Legacy Integration:** Relying strictly on nightly CSV exports introduces the danger of ingesting malformed files and duplicate records. The platform mandates a highly resilient, fault-tolerant batch pipeline designed to isolate and skip bad rows automatically, avoiding total job crashes and safeguarding production data integrity.

## 8. Deliverables

### Blueprint Phase Artifacts

- **System Design & C4 Diagrams:** Context, Container, and component dependency visuals.
- **Database Schema:** SQL/NoSQL strategy justification, partial unique indexes, TTL cleanup routines, and Elasticsearch integration for high-performance full-text search operations.
- **Critical Flow Logic:** Documented steps for Paid Workshop Registration, Offline Sync, and CSV Ingestion including detailed recovery paths.
- **Access Control Model:** Defined RBAC enforcement matrix across API endpoints, mobile, and web interfaces.
- **Protection Designs:** Documented implementation choices for rate-limiting, idempotency, and circuit breakers.

### Implementation Phase Artifacts

- **Production-Ready Source Code:** Fully functional Java/Spring backend, React frontend, and React Native mobile application.
- **Demonstrable Resilience Tools:** Actual execution (not mocked) of circuit breaker boundaries, token-bucket limits, and idempotent keys.
- **Bootstrapping Docs & Seed Data:** Comprehensive README instructions and auto-populated database seeds allowing seamless evaluation and interactive testing.

## 9. Success Criteria

### Functional Completeness

- All client apps (Web, Mobile) serve their respective actor operations.
- Offline check-in sync, AI PDF parsing, and nightly legacy CSV ingestions run flawlessly.
- RBAC completely regulates system security and bounds role actions.

### Performance & Technical Quality

- **Sustained Scale:** Gracefully handles 12,000+ users in 10 minutes without service degradation or data corruption.
- **No Double Payments:** Strict idempotency completely prevents duplicate billing under retry simulations.
- **Gateway Fault Tolerance:** Unrelated services (like browsing) operate unimpeded during simulated Stripe/payment provider downtime.
- **Zero Overbooking:** Database constraints and transactions empirically block any negative capacity allocation under concurrent stress tests.
