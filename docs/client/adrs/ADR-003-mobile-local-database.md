# ADR-003: Mobile Local Database

> **Scope:** client

- **Context:** Offline check-in requires durable local storage and deterministic sync.
- **Decision:** Use a modern JSI-based SQLite library for React Native (e.g., `expo-sqlite` or `react-native-quick-sqlite`) as the mobile persistence layer.
- **Rationale:** SQLite provides predictable transactional behavior and supports offline-first queues that can be reconciled reliably. It aligns with the React Native stack and supports high-volume scan buffering without data loss. Using modern JSI bindings ensures synchronous, high-speed inserts without the React Native Bridge overhead, which is critical for maintaining fast QR scanning speeds at the door.
- **Alternatives Considered:** AsyncStorage was rejected because it lacks transactional querying and cannot support deterministic offline reconciliation at scan volume. WatermelonDB was rejected because the project benefits more from a simpler, broadly supported SQLite layer for controlled sync semantics.
- **Consequences:** Requires schema migrations on mobile, but delivers durable offline behavior.
