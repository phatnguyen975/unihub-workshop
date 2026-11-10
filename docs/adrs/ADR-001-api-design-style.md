# ADR-001: API Design Style

> **Scope:** shared

- **Context:** Two clients (web and mobile) must integrate quickly and reliably during accelerated delivery.
- **Decision:** Use RESTful APIs for core services.
- **Rationale:** REST provides clear resource modeling, simpler observability, and predictable caching semantics. It reduces implementation risk compared to GraphQL while maintaining predictable performance during traffic spikes.
- **Alternatives Considered:** GraphQL was rejected because query flexibility would complicate authorization, caching, and mobile sync determinism under high concurrency. gRPC was rejected because it would add client complexity without improving the project’s browser-first and offline-first integration needs.
- **Consequences:** Complex screens may require multiple endpoints, but integration remains straightforward and debuggable.
