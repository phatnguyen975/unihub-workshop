# ADR-002: Authentication Mechanism

> **Scope:** server

- **Context:** The system must scale horizontally, support multiple clients, and avoid shared session state under peak load.
- **Decision:** Use stateless JWT authentication signed with asymmetric cryptography (RS256), role claims, and a `sid` (Session ID) claim binding the access token to `refresh_tokens.id` in PostgreSQL, with revocation based on the `jti` claim stored in a Redis blacklist.
- **Rationale:** RS256 allows the Identity/Auth module to securely hold the private key while gateways and services verify tokens using the public key, reducing the blast radius of edge compromise. Storing `jti` in Redis enables immediate revocation and logout without server-side sessions, preserving throughput during 12,000-user spikes.
- **Alternatives Considered:** Server-side sessions were rejected because shared session state would become a horizontal-scaling bottleneck and complicate zero-trust edge hardening. Symmetric JWT signing (HS256) was rejected because every verifier would need the signing secret, increasing compromise impact and weakening key separation.
- **Consequences:** Requires key management, JWKS publishing, and Redis availability, but eliminates shared session storage and supports secure revocation.
