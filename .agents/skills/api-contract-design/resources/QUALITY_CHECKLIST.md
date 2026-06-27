# API Contract Quality Checklist

Run this checklist against a drafted `API_CONTRACT.md` before presenting it for human approval. Every item must pass — a contract that fails any item is not ready, regardless of how complete it looks at a glance.

## Pre-Flight

- [ ] **The feature's spec exists and is `APPROVED`.** A contract drafted against a missing or unapproved spec has no authoritative source to trace back to.
- [ ] **The project's existing API standards were read and extracted explicitly** before drafting began — not assumed from memory or invented fresh for this contract.

## Structure

- [ ] The document's status line is present and starts as `DRAFT`.
- [ ] The "Global Standards Applied" section lists only standards that actually exist in the project's own documentation — nothing invented for this contract specifically.
- [ ] Every endpoint follows the repeating endpoint block structure (Description, Required Role, Idempotency Requirement, Request, Response, Side Effects, Spec Traceability).

## Per-Endpoint Checks

For **every** endpoint in the contract:

- [ ] The URI/method follows the project's existing versioning and naming convention (Rule A-1).
- [ ] The required role/authorization is stated explicitly — not implied (Rule A-9).
- [ ] The idempotency requirement is explicitly decided as Required or Not Required, with a one-line reason either way (Rule A-4). A mutating endpoint with retry risk (payment, financial transaction, anything triggered by a client that might legitimately retry on timeout) must not be left undecided.
- [ ] Every request field that has a validation constraint states that constraint explicitly (format, required/optional, range, length).
- [ ] The success response includes the exact status code, following the project's convention for that type of operation (create/update/delete/read) (Rule A-2).
- [ ] **All** possible error status codes are enumerated, each with the exact condition that triggers it. No "etc." or "other errors may occur" (Rule A-6).
- [ ] Every error response uses the project's one adopted error format consistently with every other endpoint in this contract and in the rest of the project (Rule A-3).
- [ ] Side effects beyond the immediate response are documented, or the section explicitly states "None".
- [ ] The endpoint traces to at least one Functional Requirement (and ideally an Acceptance Criteria scenario) in the spec (Rule A-7). If it doesn't, either the endpoint is undocumented scope creep, or the spec is missing something — resolve which, don't just proceed.

## Collection Endpoints Specifically

- [ ] Pagination mechanism is documented explicitly, including default and maximum page/window size (Rule A-5).
- [ ] Filtering and sorting parameters, if any, are documented with their accepted values.

## Cross-Cutting Consistency (Hyrum's Law check)

- [ ] No internal identifiers, internal error messages, or stack traces are exposed in any response, unless that exposure is a deliberate, documented part of the contract.
- [ ] Field names used for the same concept across multiple endpoints in this contract are spelled and typed identically — no `created_at` in one endpoint and `createdAt`/`creationDate` in another for the same concept.
- [ ] No endpoint introduces a response envelope, error shape, or pagination style that differs from the rest of the contract or the rest of the project's existing API surface, without an explicit, justified reason recorded.
- [ ] If this contract changes an _existing_ endpoint, the change is additive/backward-compatible, or the breaking nature of the change is called out explicitly with a justification for why a new version (rather than an extension) is necessary.

## Final Gate

- [ ] Every item flagged as an assumption (an authorization rule that had to be guessed, a standard that couldn't be confirmed) is listed explicitly for the human reviewer — not silently decided.
- [ ] The agent has presented the contract as `DRAFT` and stopped — it has not proceeded to writing handler/controller code without explicit human approval.
- [ ] (After approval) The status line has been updated from `DRAFT` to `APPROVED` with the approval date recorded, before implementation begins.
