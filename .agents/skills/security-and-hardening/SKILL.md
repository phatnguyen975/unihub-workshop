---
name: security-and-hardening
description: Applies STRIDE threat modeling per trust boundary and the OWASP Top 10 prevention patterns, with explicit treatment of secret management, fail-mode design, and signature verification. Use when implementing or reviewing code that touches authentication, authorization/RBAC, payment integration, webhook handling, file upload, or any input originating from outside the system's trust boundary. Do not use for purely internal logic with no external input, no auth/authz decision, and no money movement.
---

# Security and Hardening

## Overview

Most breaches begin in design, not in code — a missing trust boundary, an authorization check that was assumed rather than enforced, a fail mode nobody chose deliberately. This skill exists to make that design thinking explicit and repeatable: name the trust boundaries a feature actually has, threat-model each one with STRIDE, and apply the specific OWASP Top 10 prevention pattern that boundary needs — before writing the code that's supposed to enforce it.

This is a checklist-and-mindset skill, not a one-time audit. It applies continuously: at design time (when a feature's trust boundaries are first identified), during implementation (as each boundary's control is written), and during review (as a final, full-checklist pass for anything that touches the categories below).

## When to Use

- Implementing or modifying authentication logic (login, token issuance, session/token validation, logout/revocation).
- Implementing or modifying authorization/RBAC logic (any access-control decision, any multi-tenant isolation check).
- Integrating with a payment provider or any flow that moves money.
- Implementing a webhook handler (receiving events pushed from an external system).
- Implementing file upload handling.
- Handling any input that originates from outside the system's trust boundary — a request body, a header, a query parameter, a message payload from a queue, a response from a third-party API.

## When NOT to Use

- Purely internal logic with no external input, no authentication or authorization decision, and no money movement (e.g. a pure calculation function operating only on already-validated, in-process data).

## Core Principles

1. **Name the trust boundary before securing anything.** A trust boundary is any point where data crosses from a domain you don't control into one you do — a request from a client, a webhook from a provider, a file from a user, a response from a third-party API. If you cannot name a feature's trust boundaries explicitly, you are not ready to secure it; you'll be guessing at what needs validation instead of knowing.
2. **Threat-model each boundary with STRIDE, as a lens, not a ceremony.** For each boundary, ask which of the six STRIDE categories apply (see **STRIDE Threat Modeling** section below). This takes minutes, not days, and it is what turns "I should probably validate this" into a specific, justified control.
3. **Write the abuse case next to the use case.** For every feature, ask explicitly: "how would I misuse this?" Then make answering that question's worst-case scenario the first thing you test — not an afterthought once the happy path works.
4. **Validate untrusted input at the boundary, not deep inside the system.** Every value crossing a trust boundary is validated the moment it enters — type, format, range, and business-rule constraints all checked before that value is allowed to influence any decision, query, or downstream call. Code deep inside the system is entitled to assume its inputs are already valid, precisely because the boundary already enforced that.
5. **Authorization is enforced in code, never assumed from context.** "Only the right person would call this" is not a control — it is a hope. Every action that should be restricted to a specific actor, role, or tenant has an explicit, server-side check verifying that restriction on every request, regardless of what the client-side UI does or doesn't show.
6. **Fail mode is a deliberate design decision, scoped to the specific risk, never a default.** When a dependency this control relies on (a cache, a revocation list, a verification service) is unavailable, decide explicitly whether the system fails open (permits, logged and monitored) or fails closed (denies) — based on which failure mode is actually less bad for _this specific_ control. Apply this per control; a single blanket default across every security-relevant path is itself a vulnerability.
7. **Secrets are never hardcoded, logged, or committed — full stop.** Every credential, key, and signing secret lives in environment configuration or a dedicated secret-management mechanism, never in source code, never in a log line, never in version control, even temporarily "to test something."
8. **A signed or verifiable payload is only trustworthy after its signature is verified — every time, not just the first time.** Any payload whose authenticity matters (a webhook event, a signed token, a license payload) must have its signature/MAC verified using a secret or key the sender doesn't control on the receiving side, on every single request — never cached as "already trusted" based on a prior successful verification.

## STRIDE Threat Modeling

For each trust boundary identified, walk through these six categories. Not every category applies to every boundary — the point is to consciously rule categories in or out, not to force-fit all six everywhere.

| Category                   | The Question to Ask                                                                             | Common Mitigation                                                                                                                                           |
| :------------------------- | :---------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **S**poofing               | Can an attacker convincingly pretend to be someone (or something) they're not at this boundary? | Strong authentication; signature/MAC verification for non-interactive senders (webhooks, service-to-service calls)                                          |
| **T**ampering              | Can an attacker modify data in transit or at rest that this boundary trusts?                    | Integrity checks (signatures, checksums), parameterized queries, immutable audit trails for sensitive state changes                                         |
| **R**epudiation            | Could a legitimate actor convincingly deny having performed an action that matters?             | Audit logging tied to an authenticated identity, for actions where "who did this" has real consequences                                                     |
| **I**nformation Disclosure | Can data cross this boundary to someone who shouldn't see it?                                   | Authorization checks before returning data, minimal error detail to clients, encryption in transit and at rest for sensitive fields                         |
| **D**enial of Service      | Can this boundary be overwhelmed or made unusable?                                              | Rate limiting, input size limits, timeouts on anything calling an external dependency                                                                       |
| **E**levation of Privilege | Can an actor at this boundary gain capabilities beyond what they should have?                   | Explicit, server-side authorization checks on every privileged action; never trust a client-supplied role/permission claim without independent verification |

## The Three-Tier Boundary System

### Always Do (No Exceptions)

- **Validate all external input** at the system boundary (API routes, form handlers)
- **Parameterize all database queries** — never concatenate user input into SQL
- **Encode output** to prevent XSS (use framework auto-escaping, don't bypass it)
- **Use HTTPS** for all external communication
- **Hash passwords** with bcrypt/scrypt/argon2 (never store plaintext)
- **Set security headers** (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)
- **Use httpOnly, secure, sameSite cookies** for sessions

### Ask First (Requires Human Approval)

- Adding new authentication flows or changing auth logic
- Storing new categories of sensitive data (PII, payment info)
- Adding new external service integrations
- Changing CORS configuration
- Adding file upload handlers
- Modifying rate limiting or throttling
- Granting elevated permissions or roles

### Never Do

- **Never commit secrets** to version control (API keys, passwords, tokens)
- **Never log sensitive data** (passwords, tokens, full credit card numbers)
- **Never trust client-side validation** as a security boundary
- **Never disable security headers** for convenience
- **Never use `eval()` or `innerHTML`** with user-provided data
- **Never store sessions in client-accessible storage** (`localStorage` for auth tokens)
- **Never expose stack traces** or internal error details to users

## Workflow

### Step 1 — Map This Feature's Trust Boundaries

List every point where data enters from outside this system's control: client requests, webhook deliveries, file uploads, responses from third-party APIs, messages consumed from a queue populated by another system. Name each one explicitly — this list is the scope of everything that follows.

### Step 2 — Run STRIDE Per Boundary

For each boundary from Step 1, walk the six STRIDE categories. Record which apply and what mitigation addresses each. This is the bridge from "this feels risky" to "this specific control addresses this specific threat."

### Step 3 — Write the Abuse Case

For the feature's primary use case, write at least one explicit abuse case: a scenario where someone tries to misuse it for an outcome the feature was never meant to allow. This abuse case becomes the first test written — not a test added later if there's time.

### Step 4 — Apply the Relevant Checklist Sections

Apply whichever sections of [`resources/SECURITY_CHECKLIST.md`](resources/SECURITY_CHECKLIST.md) correspond to what this feature actually touches (authentication, authorization, payment, webhook, file upload, general input handling). Don't apply the entire checklist reflexively to every change — apply the sections the trust-boundary map from Step 1 actually calls for.

### Step 5 — Design the Fail Mode for Every Dependency in the Path

For every external dependency this control relies on (a cache holding a revocation list, a signature-verification service, a rate-limiter store), state explicitly: if this dependency is unavailable right now, does the control fail open or fail closed? Justify the choice against the actual risk of each direction for _this specific_ control — don't default to one answer for every case.

### Step 6 — Verify Secret Handling

Confirm no credential, key, or signing secret is hardcoded, logged, or about to be committed. Run a literal search for common secret-shaped strings before considering this step done — don't rely on memory alone.

### Step 7 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before considering the change complete.

## Authentication

- Verify credentials/tokens using a vetted, standard mechanism — never a hand-rolled comparison or a custom token format invented for this one feature.
- Use constant-time comparison for any secret-equality check (password hash comparison, token comparison) — naive comparison can leak timing information about how much of the value matched.
- Enforce token expiration and validate it on every request — an unexpired-forever token is a permanent credential, not a session.
- Provide and enforce a revocation mechanism for anything long-lived (a refresh token, a session) — a credential that cannot be revoked before its natural expiry is a liability the moment it's compromised.
- Never return a different error message for "user doesn't exist" vs. "password is wrong" — both are information disclosure that helps an attacker enumerate valid accounts.

## Authorization / RBAC

- Every privileged action has its own explicit, server-side check — a check is never satisfied by "the UI doesn't show this button to that role."
- Multi-tenant systems verify the resource being acted on actually belongs to the requester's tenant/organization on every mutating (and most reading) operation — never assume an ID supplied by the client is automatically theirs to act on just because they're authenticated.
- Role/permission claims from a client-supplied token are trusted only to the extent the token's signature has been independently verified — never trust a role claim from an unverified or client-modifiable source.
- Default to deny: a new endpoint or action with no explicit authorization rule should be unreachable, not reachable-by-default until someone remembers to add a check.

## Payment Integration

- Treat every payment-related endpoint with the same idempotency rigor described in `idempotency-and-exactly-once-design` — a retried payment request must never double-charge.
- Never trust a client-supplied amount, currency, or price for anything that affects what's actually charged — recompute or re-validate pricing server-side from the authoritative source, even if the client already displayed a price to the user.
- Treat the payment provider's webhook as the authoritative source of truth for payment outcome, not the synchronous API response alone — see **Webhook Handling** section below for the verification requirements that apply to it.
- Never log full card numbers, full bank account numbers, or other raw payment credentials — log only what's needed for support/debugging (e.g. a masked reference, a provider-issued transaction ID).

## Webhook Handling

- Verify the webhook's signature (HMAC or the provider's documented mechanism) using the provider's signing secret, on every single delivery — never skip verification because "it's probably from the real provider."
- Store the signing secret as a protected environment/secret-management value, never in source code.
- Treat the webhook endpoint as unauthenticated in the traditional sense (no user session) but still require signature verification as its actual authentication mechanism — these are not the same thing, and a webhook endpoint with neither is wide open.
- Make webhook processing idempotent (see `idempotency-and-exactly-once-design`) — providers commonly redeliver the same event, by design, on anything other than a clean success response.
- Respond quickly and defer heavy processing — a webhook handler that blocks on slow downstream work risks the provider's own delivery timeout, which often triggers a retry storm.

## File Upload

- Validate file type by inspecting actual content (magic bytes/signature), not solely by trusting the client-supplied filename extension or `Content-Type` header — both are trivially spoofable by the client.
- Enforce a maximum file size at the boundary, before the file is fully read into memory or written to storage.
- Store uploaded files outside the application's executable path, and serve them in a way that never allows an uploaded file to be executed as code by the server.
- Generate the storage filename/key server-side — never use the client-supplied filename directly as a storage path (path traversal risk: `../../etc/passwd`-style filenames).
- Scan or otherwise validate file content appropriate to the file type expected (e.g. confirm a PDF actually parses as a valid PDF) before treating it as trustworthy input to any downstream processing.

## General Untrusted Input

- Validate every external input's type, format, range, and business-rule constraints at the boundary — not deep inside business logic that assumes its inputs are already valid.
- Use parameterized queries / prepared statements for every database operation involving any externally-influenced value — never construct a query by concatenating untrusted input into it.
- Encode output appropriately for its destination context (HTML, a shell command, a log line) using the framework's standard encoding/escaping mechanism — don't bypass automatic escaping for convenience.
- Apply the same validation discipline to input arriving via a message queue or event consumer as to input arriving via an HTTP request — "it came from our own internal broker" does not exempt it if the message's ultimate origin includes anything outside this system's trust boundary.

## Quality Checklist

Condensed version — full checklist (including the OWASP Top 10 quick-reference mapping) lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] Every trust boundary this feature introduces or touches was named explicitly.
- [ ] STRIDE was walked for each boundary, with an explicit mitigation recorded for every applicable category.
- [ ] At least one abuse case was written and tested for the feature's primary use case.
- [ ] Every external input is validated at the boundary — type, format, range, and business-rule constraints — before influencing any decision or query.
- [ ] Every privileged/restricted action has an explicit, server-side authorization check — never inferred from UI behavior alone.
- [ ] No secret, key, or credential is hardcoded, logged, or about to be committed.
- [ ] Every dependency this control relies on has an explicit, risk-justified fail-open or fail-closed decision.
- [ ] Every webhook/signed payload handler verifies its signature on every delivery, using a secret the sender doesn't control.
- [ ] Every payment-affecting endpoint follows this project's idempotency design for retry-safety.
- [ ] Every file upload path validates actual file content, enforces a size limit, and never uses a client-supplied filename as a storage path.

## Anti-Patterns

| Anti-Pattern                                                                                                                              | Why It Fails                                                                                                                                                                                                                      |
| :---------------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Relying on "the UI doesn't show this to that role" as the authorization control                                                           | Client-side UI is not a security boundary — anyone can call the underlying endpoint directly, bypassing whatever the UI chooses to render                                                                                         |
| Trusting a client-supplied price, amount, or role claim without independent server-side verification                                      | A client can send any value it wants; trusting it for anything that affects money movement or privilege is a direct path to fraud                                                                                                 |
| Skipping webhook signature verification because "it's probably from the real provider"                                                    | An unauthenticated endpoint with no signature check is reachable by anyone who discovers the URL, not only the legitimate provider                                                                                                |
| Validating file type by filename extension or `Content-Type` header alone                                                                 | Both are fully controlled by the client and trivially spoofable; a malicious file can claim to be anything                                                                                                                        |
| Constructing a database query by concatenating untrusted input directly into the query string                                             | The textbook SQL-injection vector — parameterize every query instead, with no exceptions for "this one value feels safe"                                                                                                          |
| Choosing a single fail-open or fail-closed default and applying it to every security-relevant dependency uniformly                        | The right fail mode depends on the specific risk of each control — defaulting one way everywhere either creates unacceptable risk (fail-open on a revocation check) or unacceptable fragility (fail-closed on a low-stakes cache) |
| Logging full payment card numbers, full tokens, or full passwords "temporarily, for debugging"                                            | Logs persist far longer than the debugging session, are read by more people than intended, and routinely end up in less-protected storage than the primary system itself                                                          |
| Treating "we have a WAF / rate limiter in front of this" as a substitute for input validation and authorization in the application itself | Perimeter controls are a layer, not a replacement — they commonly have gaps, get misconfigured, or simply don't understand the application's own business rules the way in-application checks do                                  |

## Best Practices

- Run the trust-boundary mapping and STRIDE pass at design time, before writing the feature's implementation — security decisions made after the code already exists tend to retrofit controls around an architecture that wasn't designed to make them clean to add.
- Keep the abuse case as a real, executable test, not just a thought exercise written in a comment — if it's worth thinking about, it's worth a test asserting the abuse actually fails.
- When in doubt about an authorization rule, default to the most restrictive interpretation and have a human explicitly loosen it, rather than guessing toward a more permissive default.
- Treat every new third-party dependency as new attack surface — review what it can access and whether it's still actively maintained, not just whether it solves today's problem.
- Document the fail-mode decision for each security-relevant dependency somewhere durable (the project's own architecture decision records, if it has them) — so a future change to that dependency's behavior doesn't silently flip a previously deliberate choice back to whatever the library's default happens to be.

## Common Rationalizations

| Rationalization                                                                                        | Reality                                                                                                                                                                                                                                                                                              |
| :----------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "This is an internal endpoint, only our own frontend calls it."                                        | Anyone who can inspect network traffic from your own frontend can call the same endpoint directly. "Internal" describes who you expect to call it, not who is actually capable of calling it.                                                                                                        |
| "We'll add proper authorization once we know which roles we actually need."                            | A new endpoint reachable by default until someone remembers to lock it down is reachable by everyone in the meantime. Default to deny from the first commit, then loosen deliberately.                                                                                                               |
| "The payment provider already verifies the signature on their end, we don't need to check it again."   | The provider verifies _its own_ outbound signature when sending the webhook — your service still must verify that signature on receipt, or anyone who finds your webhook URL can post fabricated events directly to it.                                                                              |
| "It's just a temporary hardcoded key while I get this working, I'll move it to config before merging." | "Temporary" hardcoded secrets are exactly the ones that end up committed — put it in configuration from the very first commit; the discipline costs nothing and removes the risk entirely.                                                                                                           |
| "Fail-closed is always the safer choice, so I'll just do that everywhere."                             | Fail-closed on every dependency turns every transient outage of a low-stakes control (e.g. a rate-limiter cache) into a full outage of the feature it protects. The safer choice is the one justified by _this_ control's actual risk, evaluated individually — not a single rule applied uniformly. |

## Output

This skill does not produce a standalone document artifact for most uses — its output is the implemented controls themselves, the abuse-case test(s), and a security-focused findings report.

**Worked example:** [`examples/example-threat-model.md`](examples/example-threat-model.md) walks through trust-boundary mapping and STRIDE analysis for the webhook handler in the "resource reservation" example's payment flow, continuing the domain-neutral example used across the other skill examples. Illustrative only, with pseudocode rather than any specific language or framework syntax.
