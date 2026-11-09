# ADR-003: Offline QR Verification Cryptography

> **Scope:** shared

- **Context:** QR codes could be forged offline, and `STAFF` devices must verify authenticity without network access.
- **Decision:** Use PKI-based asymmetric cryptography for QR payload signing and verification.
- **Rationale:** The backend signs QR payloads with a private key; the mobile app stores the current public key in SQLite and verifies signatures offline. This supports key rotation to invalidate compromised keys globally.
- **Alternatives Considered:** Symmetric encryption or HMAC were rejected because the mobile client would need to hold the secret key, which is unacceptable if the app is decompiled. Online-only verification was rejected because offline check-in must work without network access.
- **Consequences:** Requires secure key management and periodic public key refresh on login.
