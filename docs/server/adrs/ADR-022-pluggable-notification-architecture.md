# ADR-022: Pluggable Notification Architecture

> **Scope:** server

- **Context:** The system must support email and in-app notifications, with future channel expansion.
- **Decision:** Use a provider abstraction with strategy-based adapters and brokered delivery.
- **Rationale:** Adapters decouple core workflows from delivery channels, allowing new channels without schema changes and preserving reliability through retry semantics.
- **Alternatives Considered:** A monolithic notification service was rejected because channel-specific failures and release cadence would become tightly coupled. Direct provider calls from business workflows were rejected because they would spread vendor logic across the codebase and weaken retry isolation.
- **Consequences:** Requires adapter contracts and configuration management but avoids vendor lock-in.
