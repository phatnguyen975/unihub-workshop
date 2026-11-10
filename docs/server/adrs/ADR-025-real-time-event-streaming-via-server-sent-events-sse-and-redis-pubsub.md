# ADR-025: Real-Time Event Streaming via Server-Sent Events (SSE) and Redis Pub/Sub

> **Scope:** server

- **Context:** Several asynchronous operations (AI brochure summarization completion, in-app notification bell updates, live attendance monitoring during on-site check-in, payment confirmation) require pushing updates from the server to web clients in real time. Short polling introduces high request chatter, redundant database queries, and latency, while a full duplex connection protocol may be unnecessarily complex.
- **Decision:** Use a unified Server-Sent Events (SSE) endpoint (`GET /api/v1/stream/events`) multiplexed per authenticated user session, backed by Redis Pub/Sub for cross-node event distribution across horizontally scaled Spring Boot backend instances.
- **Rationale:**
  - **Unidirectional Push:** UniHub's real-time requirements are purely server-to-client notifications. Client actions (e.g., mark notification as read, trigger AI job) remain standard REST mutations. SSE fits this unidirectional model natively without the overhead of duplex protocols.
  - **HTTP/2 and Standard Infrastructure Compatibility:** SSE operates over standard HTTP/1.1 and HTTP/2, traversing corporate firewalls, proxies, and load balancers without requiring WebSocket upgrade handshakes. Nginx acts as a forwarding reverse proxy with `proxy_buffering off;`.
  - **Horizontal Scalability via Redis Pub/Sub:** Backend nodes run statelessly behind Nginx. When an event occurs on Node A or in a background Worker, publishing to Redis Pub/Sub ensures Node B (holding the user's active `SseEmitter`) receives and streams the event to the client.
  - **Multiplexed Single-Connection:** A single client maintains only one SSE stream for all event types (`notification.new`, `ai.summary.completed`, `attendance.updated`, `registration.updated`, `payment.confirmed`), preventing socket exhaustion on client devices and reverse proxies.
  - **Native Reconnection:** SSE clients handle reconnects with backoff and may send `Last-Event-ID` as a hint. Redis Pub/Sub is transient and the hub has no historical event store; reconnecting does not replay missed events. Clients recover business state through the owning domain's REST baseline.
- **Alternatives Considered:**
  - _WebSockets:_ Rejected because UniHub does not require bidirectional streaming (client-to-server messaging). WebSockets introduce stateful connection management, custom ping-pong frames, firewall traversal issues, and higher operational complexity.
  - _Short Polling:_ Rejected as primary strategy because it creates wasteful HTTP traffic, increases Neon DB and Redis load, and delays user feedback by the polling interval (3-5 seconds). Retained strictly as fallback for environments where streaming is disrupted.
- **Consequences:** Requires periodic server heartbeats (`: ping\n\n` every 15s) to prevent intermediate proxy timeout, Nginx buffering disabled on the stream location, and explicit resource cleanup on client disconnection to prevent memory leaks in `SseEmitterRegistry`.
