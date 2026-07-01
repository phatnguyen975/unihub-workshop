# Security Checklist (Supplementary Reference)

Use alongside `SKILL.md`. Apply the sections relevant to what the current feature actually touches — not the entire file reflexively on every change.

## Threat Modeling (Start Here)

Before reaching for any specific control, spend a few minutes thinking like an attacker:

- [ ] Trust boundaries mapped: every point where data enters from outside this system (requests, uploads, webhooks, third-party API responses, queue/event messages).
- [ ] Assets named: what's actually at risk at each boundary (credentials, personal data, payment data, the ability to perform a privileged/admin action, money movement).
- [ ] STRIDE run per boundary (Spoofing, Tampering, Repudiation, Information disclosure, Denial of service, Elevation of privilege).
- [ ] At least one abuse case written next to the feature's use case ("how would I misuse this?").

## Pre-Commit Checks

- [ ] No secret-shaped string is present in the diff (search for patterns like `password`, `secret`, `api_key`, `token` in staged/changed content before committing).
- [ ] `.gitignore` (or this project's equivalent exclusion mechanism) covers environment files, private keys, and any other locally-generated secret material.
- [ ] No debug-only bypass (a hardcoded test credential, a disabled check "just for now") was left in place.

## Authentication

- [ ] Credentials/tokens are verified using a vetted, standard mechanism (an established library or protocol), not a custom comparison.
- [ ] Secret-equality checks (password hash comparison, token comparison) use constant-time comparison, not a naive early-exit comparison.
- [ ] Tokens/sessions have an enforced expiration, checked on every request — not only at issuance.
- [ ] A revocation mechanism exists for any long-lived credential (refresh token, session) and is actually checked on use, not just implemented and unused.
- [ ] Authentication failure responses do not distinguish "account doesn't exist" from "credential is wrong" in a way that helps enumerate valid accounts.
- [ ] Password/credential storage uses a vetted, slow, salted hashing algorithm designed for credential storage — never a fast general-purpose hash, and never plaintext.

## Authorization

- [ ] Every privileged or restricted action has an explicit, server-side authorization check.
- [ ] Multi-tenant resource access verifies the resource actually belongs to the requester's tenant/organization — not inferred from the requester simply being authenticated.
- [ ] Role/permission claims are trusted only after the token or source carrying them has been independently, cryptographically verified.
- [ ] New endpoints/actions default to denied access until an explicit authorization rule grants it — never reachable-by-default.
- [ ] Privilege checks happen on the server for every request, not only once at login or once per session establishment.

## Input Validation

- [ ] Every external input's type, format, range, and business-rule constraints are validated at the boundary where it enters the system.
- [ ] All database queries involving externally-influenced values use parameterized queries / prepared statements — never string concatenation of untrusted input into a query.
- [ ] Output is encoded appropriately for its destination context (HTML, shell, log line, file path) using the framework's standard mechanism — automatic escaping is not bypassed for convenience.
- [ ] Input arriving via a non-HTTP channel (a message queue, an event consumer) receives the same validation discipline as input arriving via an HTTP request.

## Webhook Handling

- [ ] The webhook's signature (HMAC or the provider's documented mechanism) is verified on every delivery, using a secret the sender does not control.
- [ ] The signing secret is stored as a protected environment/secret value, never in source code.
- [ ] Webhook processing is idempotent against redelivery (see `idempotency-and-exactly-once-design`).
- [ ] The handler responds quickly and defers heavy processing, to avoid the provider's own delivery timeout triggering unnecessary retries.
- [ ] Invalid-signature requests are rejected with a generic response, without leaking details about why verification failed.

## Payment Integration

- [ ] No client-supplied amount, currency, or price is trusted directly for what's actually charged — server-side recomputation or validation against the authoritative source is required.
- [ ] Every payment-affecting endpoint follows this project's idempotency design — a retried request cannot double-charge.
- [ ] The payment provider's webhook (not only the synchronous API response) is treated as the authoritative source of truth for the payment's actual outcome.
- [ ] No full card number, full bank account number, or other raw payment credential is logged anywhere, in any environment.

## File Upload

- [ ] File type is validated by inspecting actual content (magic bytes/signature), not solely by trusting the filename extension or `Content-Type` header.
- [ ] A maximum file size is enforced at the boundary, before the file is fully read into memory or written to storage.
- [ ] Uploaded files are stored outside the application's executable path and are never served in a way that allows execution as code.
- [ ] The storage filename/key is generated server-side — the client-supplied filename is never used directly as a storage path.
- [ ] File content is validated as genuinely matching its claimed type (e.g. a claimed PDF actually parses as a valid PDF) before being treated as trustworthy input downstream.

## Fail-Mode Design

- [ ] For every security-relevant dependency (revocation-list cache, signature-verification service, rate-limiter store), the fail-open vs. fail-closed decision is stated explicitly.
- [ ] Each fail-mode decision is justified by the specific risk of that control — not copied uniformly from another control's decision.
- [ ] Fail-open decisions are logged and monitored, so a degraded state is visible operationally, not silently tolerated forever.
- [ ] Fail-closed decisions on a frequently-available dependency don't turn a brief, expected blip into a full outage of an otherwise unrelated feature.

## Security Headers (Web-Facing Endpoints)

- [ ] Responses set appropriate transport security (HSTS-equivalent) where served over HTTPS.
- [ ] Responses prevent MIME-type sniffing (`X-Content-Type-Options`-equivalent).
- [ ] Responses prevent clickjacking where the content should never be framed (`X-Frame-Options`-equivalent or a CSP frame-ancestors directive).
- [ ] A Content-Security-Policy (or this platform's equivalent) is set to the most restrictive policy the application can function under.

## CORS Configuration

- [ ] Wildcard origins are not used for any endpoint that relies on authenticated/credentialed requests.
- [ ] Allowed origins are an explicit allowlist, not derived from reflecting the request's own `Origin` header unconditionally.
- [ ] Allowed methods and headers are scoped to what's actually needed, not left maximally permissive by default.

## Data Protection

- [ ] Sensitive fields (PII, payment data, credentials) are encrypted at rest where this project's data classification requires it.
- [ ] Sensitive fields are never included in error messages or stack traces returned to a client.
- [ ] Data retention/deletion requirements (if applicable to this project's regulatory context) are respected by any new field or table storing personal data.

## Dependency Security

- [ ] A new dependency was reviewed for maintenance activity, download/ adoption signal, and whether it genuinely earns its place, before being added.
- [ ] Dependencies are checked against known vulnerability databases as part of this project's normal process, not only at the moment a new one is added.
- [ ] Unfamiliar packages are checked for suspicious install-time scripts before being trusted in the build.

## Error Handling

- [ ] Error responses returned to a client contain no internal implementation detail (stack traces, internal file paths, raw database error messages).
- [ ] Internal logs capture full diagnostic detail separately from what the client receives, so debugging capability isn't sacrificed for the sake of a generic client-facing error.
- [ ] Errors are classified consistently with this project's own error taxonomy (see `api-contract-design`), not invented ad hoc per endpoint.

## OWASP Top 10 Quick Reference

These are prevention patterns mapped to the broad categories, not a ranking to optimize one item at the expense of the others.

| Category                                   | Core Prevention Pattern                                                                                                                                                                    |
| :----------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Broken Access Control                      | Server-side authorization check on every privileged action; default-deny for new endpoints; verify resource ownership/tenant scope, not just authentication                                |
| Cryptographic Failures                     | Use vetted libraries for hashing/encryption, never hand-rolled; encrypt sensitive data in transit and at rest; never log sensitive plaintext                                               |
| Injection                                  | Parameterized queries for all database access; framework auto-escaping for all output encoding; never concatenate untrusted input into a query, command, or template                       |
| Insecure Design                            | Map trust boundaries and run STRIDE _before_ implementation — most breaches begin here, not in the code itself                                                                             |
| Security Misconfiguration                  | Default-deny configuration; no debug/verbose error detail in production responses; secrets in configuration, never in source                                                               |
| Vulnerable and Outdated Components         | Review new dependencies before adding; track known vulnerabilities in existing dependencies; remove unused dependencies                                                                    |
| Identification and Authentication Failures | Vetted authentication mechanism; enforced expiration and revocation; constant-time secret comparison; no account-enumeration via error messages                                            |
| Software and Data Integrity Failures       | Signature/MAC verification for webhooks and any signed payload; integrity checks on data crossing trust boundaries                                                                         |
| Security Logging and Monitoring Failures   | Audit logging for privileged actions and authentication events; fail-open decisions specifically logged and monitored                                                                      |
| Server-Side Request Forgery                | Validate and constrain any server-side outbound request whose destination is influenced by external input; never let untrusted input determine an internal-network-reachable URL unchecked |
