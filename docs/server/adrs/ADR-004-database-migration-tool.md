# ADR-004: Database Migration Tool

> **Scope:** server

- **Context:** Schema changes must be deterministic and safe under production load; tenant seeding must be dynamic and idempotent.
- **Decision:** Use Flyway strictly for schema (DDL) migrations. Seed root tenants and initial `ORGANIZER` accounts via JSON arrays in environment variables parsed by a Spring Boot `ApplicationRunner` during startup.
- **Rationale:** DDL-only migrations prevent version drift from mutable seed data. Environment-driven JSON seeding enables idempotent creation at startup, avoids migration failures, and supports adding new root tenants by updating configuration rather than rewriting SQL migrations.
- **Alternatives Considered:** Liquibase was rejected because the project does not need a heavier migration abstraction for this mostly DDL-driven schema lifecycle. Embedding seed data directly in migrations was rejected because mutable tenant bootstrap data should remain idempotent and environment-driven rather than tied to schema revision history.
- **Consequences:** Requires startup validation and operational controls around environment configuration, but reduces migration risk and preserves 12-factor principles.
