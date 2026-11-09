# ADR-020: Batch Processing Strategy

> **Scope:** server

- **Context:** Nightly CSV ingestion must tolerate malformed rows and duplicates without failing the entire pipeline.
- **Decision:** Use Spring Batch with streaming ItemReader/ItemProcessor/ItemWriter, chunk commits, and local reject staging uploaded to S3 on completion.
- **Rationale:** Streaming prevents OOM for large files, chunk commits provide restartability via `JobRepository`, and local reject staging avoids unsupported S3 append operations while preserving audit artifacts.
- **Alternatives Considered:** Loading the whole CSV into memory was rejected because large nightly drops would risk OOM and long GC pauses. Direct S3 append or per-row uploads were rejected because object storage is immutable and those patterns would increase API chatter, latency, and operational fragility.
- **Consequences:** Requires job monitoring and local ephemeral storage on workers.
