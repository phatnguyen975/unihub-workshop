# ADR-001: Web Frontend Framework

> **Scope:** client

- **Context:** The web portal serves `STUDENT` and `ORGANIZER` workflows with RBAC-controlled actions, strict data integrity, and rapid iteration requirements.
- **Decision:** Use React with TypeScript for the web application.
- **Rationale:** React enables modular UI composition for role-specific flows, while TypeScript improves refactor safety under evolving APIs. The ecosystem supports reliable state management and integration with REST endpoints, reducing UI defects during the 12,000-user surge.
- **Alternatives Considered:** Angular was rejected because its heavier framework model slows incremental delivery and does not improve sharing with React Native. Vue was rejected for the same reason as the project gains more from a single React/TypeScript component model across web and mobile.
- **Consequences:** Requires disciplined state architecture but accelerates delivery and long-term maintainability.
