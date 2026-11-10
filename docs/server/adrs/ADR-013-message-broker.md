# ADR-013: Message Broker

> **Scope:** server

- **Context:** Notifications, AI summarization, and CSV ingestion must run asynchronously without blocking registration flows.
- **Decision:** Use RabbitMQ as the message broker.
- **Rationale:** RabbitMQ provides reliable task-queue semantics and straightforward retry/DLQ handling, which fits the background worker model and keeps core request threads free during the registration surge.
- **Alternatives Considered:** Apache Kafka was rejected because its operational footprint and stream-processing model are excessive for simple task routing and DLQ-driven worker retries. Synchronous execution or in-process queues were rejected because they would tie async latency to request threads and undermine scalability.
- **Consequences:** Requires broker monitoring and retry policies, but reduces coupling between synchronous and async workloads.
