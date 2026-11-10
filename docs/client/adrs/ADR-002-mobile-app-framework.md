# ADR-002: Mobile App Framework

> **Scope:** client

- **Context:** Check-in `STAFF` require offline-first scanning with fast QR processing and deterministic sync under poor connectivity.
- **Decision:** Build the mobile app with React Native (TypeScript).
- **Rationale:** React Native aligns the mobile stack with the web ecosystem, enabling shared TypeScript domain models and reducing duplicated logic. It still supports native camera integration and offline persistence while accelerating delivery and reducing long-term maintenance compared to separate native codebases.
- **Alternatives Considered:** Native iOS/Android apps were rejected because they would double the codebase and slow offline-sync feature parity. Flutter was rejected because it does not materially improve integration with the existing React/TypeScript web stack or shared domain model strategy.
- **Consequences:** Requires careful native module selection for camera and storage performance, but improves developer velocity and consistency across clients.
