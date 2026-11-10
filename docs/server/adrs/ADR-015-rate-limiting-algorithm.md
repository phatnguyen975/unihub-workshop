# ADR-015: Rate Limiting Algorithm

> **Scope:** server

- **Context:** Registrations can spike to 12,000 concurrent users within minutes.
- **Decision:** Use Bucket4j Token Bucket rate limiting in the Core Backend Monolith filter layer with Redis-backed counters. Nginx acts as a pure forwarding proxy and does not perform rate limiting.
- **Rationale:** Token Bucket smooths bursts while allowing controlled short-term spikes. Placing enforcement in the backend filter layer preserves a dumb gateway and centralizes logic under consistent authentication and telemetry. Redis-backed shared counters ensure consistent enforcement across all horizontally scaled backend nodes (Node 1 and Node 2).
- **Alternatives Considered:** Leaky bucket and fixed-window counters were rejected because they either smooth too aggressively or allow burst edges that are too coarse for the 3-minute spike window. Gateway-only enforcement was rejected because the core backend needs first-class control and observability for authenticated traffic shaping. Nginx-level rate limiting was rejected because it cannot distinguish authenticated users and lacks access to JWT claims required for per-user bucket keying.
- **Consequences:** Requires tuning refill rates and burst sizes per endpoint category (registration endpoints, auth/credential endpoints, SSE stream endpoints, vs default-tier fallback). When Redis is unavailable, the rate limiter operates in fail-open mode (see [server ADR-006](ADR-006-caching-and-rate-limiting-storage.md) failure mode) — all fail-open decisions are logged and emitted as metrics for alerting.
