# UniHub - Project Requirements

## Project Overview

### 1. Context

Every year, University A hosts the "Skills and Career Week." This event spans 5 days, featuring 8-12 concurrent workshops daily across multiple locations. Currently, the Organizing Committee (OC) manages registrations via Google Forms and manually sends notifications via email - a workflow that is no longer sustainable as the event continues to scale.

To address these challenges, the OC aims to develop the **UniHub Workshop** system to digitalize the entire end-to-end lifecycle, from initial registration to on-site check-in.

### 2. User Groups

| Group                  | Description                                                                                                    |
| :--------------------- | :------------------------------------------------------------------------------------------------------------- |
| **Students**           | Browse workshop schedules, register for sessions, receive booking confirmations, and perform on-site check-in. |
| **Organizers (Admin)** | Create and manage workshop; monitor registration counts and real-time attendance metrics.                      |
| **Check-in Staff**     | Verify and record student attendance at workshop entrances using a dedicated mobile application.               |

### 3. System Requirements

#### 3.1. Workshop Discovery & Registration

- **Browsing:** Students can view a comprehensive catalog of workshops, including speaker bios, venue locations, room layouts, and real-time seat availability.
- **Registration:** The system supports both free and paid workshop tiers.
- **Confirmation:** Upon successful registration, the system generates a unique QR code for each student to facilitate on-site check-in.

#### 3.2. Notification System

- **Immediate Alerts:** Registration confirmations are sent instantly via in-app notifications and email.
- **Extensibility:** The architecture must follow a pluggable pattern to support future notification channels (e.g., Telegram) with minimal core logic modifications.

#### 3.3. Administration & Access Control

- **Admin Portal:** A dedicated internal web interface allows Organizers to manage the workshop lifecycle (Create, Update, Reschedule, or Cancel).
- **Role-Based Access Control (RBAC):** Access is strictly enforced based on the following roles:
  - **Students:** Restricted to browsing and registration only.
  - **Organizers:** Full administrative permissions for workshop management and access to analytics/reporting.
  - **Check-in Staff:** Access limited exclusively to the QR scanning and attendance verification modules.

#### 3.4. On-site Check-in

- **Mobile Scanning:** Staff use a mobile application to scan student QR codes at venue entrances.
- **Offline Resilience:** To account for unstable campus network coverage, the app must support an **offline-first mode** - allowing attendance records to be captured locally and automatically synchronized once connectivity is restored.

#### 3.5. AI-Powered Summarization

- **Automated Processing:** Organizers can upload PDF workshop brochures.
- **AI Integration:** The system will automatically parse, clean, and extract text from these documents, utilizing an AI model to generate concise summaries for workshop detail pages.

#### 3.6. Student Data Synchronization

- **Legacy Integration:** Due to the lack of an API in the current Student Management System, the system must integrate with nightly CSV exports.
- **Data Ingestion:** UniHub Workshop will implement a scheduled ingestion job to import student records for identity verification during the registration process.

### 4. Key Technical Challenges

- **Concurrency & Race Conditions:** High-demand workshops (e.g., 60-seat capacity) may face hundreds of simultaneous registration attempts. The system must implement robust concurrency control to ensure data consistency and prevent overbooking—specifically ensuring no two users can successfully claim the final available seat.
- **High-Traffic Management & Fairness:** The platform anticipates a massive surge of 12,000 concurrent users within the first 10 minutes of registration, with 60% of traffic concentrated in the initial 3-minute window. The backend API must be protected against overwhelming request volumes and client-side retries through effective rate limiting and queuing mechanisms to ensure fair access for all students.
- **Payment Resilience & Service Availability:** In the event of payment gateway outages, the system must maintain core functionality, allowing students to view schedules and event information. The paid registration workflow must handle transaction timeouts gracefully - preventing double-charging while ensuring that non-payment-related services remain operational during prolonged outages.
- **Offline Data Integrity:** Check-in staff operating in campus zones with poor or no connectivity must be able to verify attendance. The system must guarantee that offline check-in records are preserved and accurately reconciled with the central database once a network connection is re-established.
- **Unidirectional Legacy Integration:** Due to the lack of API access to the legacy system, UniHub must rely exclusively on scheduled CSV data exports. The ingestion pipeline must be resilient enough to handle malformed files and duplicate records without interrupting the production environment or compromising live data.

## Implementation Tasks

### 1. Blueprint

#### 1.1. System Design Document

Provide a comprehensive description of the overall system architecture, including primary components, communication protocols, and the underlying architectural rationale. The document must address the following core inquiries:

- What are the fundamental building blocks of the system?
- How do these components interface and exchange data?
- How is the overall system stability impacted by the failure of an individual component?

#### 1.2. C4 Diagram

Develop the first two levels of the C4 model to visualize the system architecture:

- **Level 1 - System Context:** Illustrate the "big picture" of the UniHub Workshop ecosystem, identifying primary user personas and integrated external systems.
- **Level 2 - Container:** Decompose the system into distinct containers (e.g., Web App, Mobile App, Backend API, Database, Message Broker). This diagram must specify the proposed technology stack and the communication protocols used between containers.

#### 1.3. High-Level Architecture Diagram

Develop a comprehensive architectural overview that illustrates the data flow and interdependencies between system components. This diagram must highlight:

- **Integration Points:** Clear visualization of interfaces with external dependencies, including the Legacy System, Payment Gateways, and AI Model services.
- **Offline Resilience:** The end-to-end workflow for offline check-ins, emphasizing how data is captured and subsequently reconciled.

#### 1.4. Database Design

Define the primary data categories within the system and propose an optimized database strategy (e.g., SQL, NoSQL, or Polyglot Persistence). The design must include:

- **Justification:** Technical rationale for the chosen database types based on specific data characteristics (e.g., ACID compliance for transactions vs. flexibility for AI-generated summaries).
- **Schema Design:** Detailed schema definitions for core entities to ensure data integrity and performance.

#### 1.5. Critical Business Logic Flow

Provide in-depth documentation for at least two of the following critical workflows:

- **Paid Workshop Registration:** The complete lifecycle from the initial "Register" trigger to the issuance of the unique QR code.
- **Offline Check-in & Synchronization:** Procedures for managing attendance during network outages and the subsequent data reconciliation logic.
- **Nightly CSV Data Ingestion:** The pipeline for importing and validating student records from the legacy system.

For each selected workflow, specify the sequential processing steps, the participating components, and the **Error Handling & Recovery** strategies for mid-process failures.

#### 1.6. Access Control Design

Design a comprehensive authorization model for the system. This section must define user groups and their associated permission sets, detailing how the system enforces these rules across various access points (API endpoints, Admin Portal, and Mobile App).

- **Model Selection:** You may implement **Role-Based Access Control (RBAC)** or propose an alternative authorization framework with a technical justification.
- **Enforcement Strategy:** Explain the mechanism for verifying permissions at each layer to ensure secure access.

#### 1.7. System Protection Mechanisms

For each technical challenge identified below, present the chosen solution, describe its internal mechanics, and justify its selection based on the project’s specific constraints. While specific techniques are suggested, alternative solutions are welcome if supported by a sound technical argument:

- **Traffic Spike Management:** How will the backend API be protected from being overwhelmed by 12,000 concurrent registration attempts?
  - **Suggested Techniques:** Rate Limiting strategies such as Fixed Window, Sliding Window, Token Bucket, or Leaky Bucket.
- **Third-Party Service Resilience:** How should the system respond to persistent payment gateway failures to prevent a total service collapse?
  - **Suggested Techniques:** Implementation of the **Circuit Breaker** pattern (Closed, Open, and Half-Open states) integrated with **Graceful Degradation** strategies.
- **Transaction Idempotency:** How can the system guarantee that a payment transaction is executed exactly once, even in the event of multiple client-side retries?
  - **Suggested Techniques:** Implementation of **Idempotency Keys**, including details on key generation logic, storage mechanisms, duplicate detection workflows, and Time-To-Live (TTL) configurations.

### 2. Implementation

Deliver a fully operational and executable software package that realizes the entire system architecture described in the Blueprint. The implementation must satisfy the following criteria:

- **Comprehensive Business Features:** Full programmatic implementation of all functional requirements, including Workshop Discovery and Registration, Notification Services, Admin Portal, On-site Check-in, AI-powered Summarization, and CSV Data Synchronization.
- **Real-world Technical Mechanisms:** The security and resilience strategies designed in Blueprint sections 1.6 and 1.7 must be fully functional within the source code. The use of stubs, mock-ups, or purely simulated logic for these critical mechanisms is strictly prohibited.
- **Bootstrap & Deployment Documentation:** A comprehensive README file providing clear, step-by-step instructions for repository cloning and system initialization. The documentation must be detailed enough to allow reviewers to deploy and run the system without requiring additional technical support.
- **Seed Data & Initialization:** Integrated seed data or automated scripts to populate the system with sample records, enabling immediate interactive testing and verification post-deployment.

## Blueprint Template

The **Blueprint** phase serves as the technical foundation of the project, focusing on architectural design, system resilience, and interface definitions before the implementation begins. This folder contains the comprehensive documentation required to align technical decisions with business objectives.

```text
blueprint/
├── proposal.md      # Context, Problem Statement, Objectives
├── design.md        # Architecture, Diagrams, Technical Decisions
│
├── specs/
│   ├── auth.md      # Access Control Specifications
│   ├── payment.md   # Payment Flow Specifications
│   ├── checkin.md   # Offline Check-in Specifications
│   └── ...          # Other Feature Specifications
│
└── apis/
    ├── auth.md      # Access Control API Contracts
    ├── payment.md   # Payment Flow API Contracts
    ├── checkin.md   # Offline Check-in API Contracts
    └── ...          # Other Feature API Contracts
```
