---
name: api-contract-design
description: Defines the request/response contract, error semantics, and side effects of every endpoint before implementation starts, so the interface is designed deliberately rather than discovered by writing handler code. Use when a feature exposes or changes a network-facing interface (REST endpoint, RPC method, webhook, or any boundary another system calls). Do not use for internal function signatures between layers of the same service — those are implementation detail, not a network contract.
---

# API Contract Design

## Overview

This skill produces a precise, implementation-independent **API contract** for every endpoint a feature exposes or modifies — before any handler, controller, or route code is written. The contract is a promise to every future caller: what to send, what comes back, and exactly how it fails.

This skill assumes a [`spec-driven-development`](../spec-driven-development/SKILL.md) spec already exists for the feature. The contract translates the spec's Functional Requirements into a concrete, callable interface — it does not invent new behavior the spec didn't already describe.

This skill produces exactly one artifact: `specs/<feature-name>/API_CONTRACT.md`.

## When to Use

- A feature introduces a new endpoint, RPC method, or webhook.
- A feature changes the request or response shape of an existing endpoint.
- A feature changes error behavior, status codes, or side effects of an existing endpoint.
- Any boundary that a different system, a different team, or a different client (web, mobile, another service) will call across a network.

## When NOT to Use

- Internal function or method signatures between layers of the _same_ service (e.g. a use case calling a repository interface). That is implementation detail governed by the project's own architecture conventions, not a network contract.
- A change that only affects internal logic with no observable change to request/response shape, status codes, or side effects.

If in doubt, ask _"Could this interface have a caller I don't control and can't redeploy in lockstep?"_ If yes, it needs a contract.

## Core Principles

1. **Contract-first, not code-first.** The interface is designed and reviewed before a single line of handler code exists. Writing the handler first and "documenting what it does" afterward inverts the purpose of a contract — it can only describe behavior the implementer guessed at, not behavior that was deliberately decided.
2. **The contract is the spec for the boundary.** Once approved, an endpoint's shape is the source of truth. The implementation must match it; if the implementation needs to differ, the contract is updated first, explicitly — never silently.
3. **Hyrum's Law governs every decision here.** _"With a sufficient number of consumers of an interface, every observable behavior — documented or not — will eventually be depended upon by somebody."_ This means:
   - Do not expose incidental behavior (field ordering, internal IDs, timing characteristics) that isn't a deliberate part of the contract, because once observable, it becomes a de facto commitment.
   - Every field, every status code, every error shape in this contract is something you are committing to support, not just something that happens to be true today.
4. **Every endpoint traces back to the spec.** An endpoint with no corresponding Functional Requirement is either undocumented scope creep or a sign the spec is incomplete — fix the spec, don't just add the endpoint.
5. **Design for one version at a time.** Prefer extending an existing contract over forking a parallel version. Multiple simultaneously supported versions of the same interface multiply maintenance cost and create "diamond dependency" problems for clients that depend on two different consumers of your API expecting different versions.
6. **Boundary inputs are never trusted.** Anything crossing this contract from outside the service's trust boundary (request bodies, headers, query parameters, path parameters) must be validated against the contract's stated rules — the contract defines what "valid" means, the implementation enforces it.

## Workflow

### Step 1 — Confirm the Spec Exists and Is Approved

Locate `specs/<feature-name>/SPEC.md`. If it does not exist or is still in `DRAFT` status, **stop** — invoke `spec-driven-development` first. A contract designed against an unapproved or missing spec has nothing authoritative to trace back to.

### Step 2 — Identify the Global Standards That Apply

Before drafting any endpoint, locate and read the project's own API standards documentation (commonly found under `docs/` or referenced from the project's root rules file). Extract, in writing, the standards that apply to this contract — at minimum:

- URI structure / versioning convention
- The success response shape (envelope, or bare resource — whichever this project uses)
- The error response shape (e.g. RFC 7807 Problem Details, or another standard the project has already adopted)
- Pagination convention for collection endpoints, if any
- Authentication/authorization header conventions
- Idempotency-key conventions, if the project has one
- Date/time and other primitive-value formatting conventions

Do not invent a different convention for this contract just because it seems cleaner — consistency across the whole API surface is more valuable than a locally "nicer" choice. If an existing convention is genuinely wrong for this case, that is an improvement proposal for the project's standards, handled outside this skill.

### Step 3 — Draft the Contract, One Endpoint at a Time

Use [`resources/API_CONTRACT_TEMPLATE.md`](resources/API_CONTRACT_TEMPLATE.md). For every endpoint:

1. **Resource and method.** State the URI and HTTP method (or RPC method name). Confirm it follows the project's naming and versioning convention from Step 2.
2. **Required role / auth.** State exactly who is allowed to call this — never leave authorization implicit (see Rule A-9 below).
3. **Idempotency requirement.** Decide explicitly whether this endpoint needs an idempotency mechanism (see [`idempotency-and-exactly-once-design`](../idempotency-and-exactly-once-design/SKILL.md) if it does — any mutating endpoint with retry risk needs this decided _before_ the contract is finalized, not bolted on after implementation).
4. **Request shape.** Headers, path parameters, query parameters, and body — with an explicit validation rule for every field that has one (format, required/optional, value range, max length).
5. **Success response.** The exact shape returned on success, with the exact status code, following the project's response envelope convention from Step 2.
6. **Every error case, named explicitly.** Do not write "other errors may occur" — enumerate every status code this endpoint can return and the exact condition that produces it. See Rule A-6 below.
7. **Side effects, if any.** If a successful call triggers something beyond the response itself — an event published, a downstream system notified, a record created elsewhere — document it here. A caller (or a future maintainer) should never have to read the implementation to discover that calling this endpoint also does something else.
8. **Spec traceability.** State which Functional Requirement(s) and Acceptance Criteria scenario(s) this endpoint satisfies.

### Step 4 — Validate Against Contract Design Rules

Run through **Contract Design Rules** section below against the full draft. Every rule must hold for every endpoint in the contract.

### Step 5 — Self-Review Against the Quality Checklist

Before presenting, run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md). Fix any failing item before moving on.

### Step 6 — Present as DRAFT and Stop

Set the document status to `DRAFT`. Present the contract and explicitly call out:

- Any global standard that could not be confirmed from existing project documentation.
- Any endpoint whose authorization rule required an assumption.
- Any endpoint that does not cleanly trace to an existing FR.

**Then stop.** Do not proceed to planning or implementation. A human must read and approve this artifact first — this is a hard gate, not a suggestion. Do not write handler/controller code against a `DRAFT` contract. If the human rejects it, revise the contract with the human's feedback and repeat Step 6 until approval is granted.

### Step 7 — On Approval, Flip Status to APPROVED

Once the human approves, update the document's status line from `DRAFT` to `APPROVED` and record the approval date. Do not silently treat an unapproved draft as final — the status line is the single source of truth for whether implementation may proceed against this contract.

## Contract Design Rules

These rules govern the content of every contract this skill produces. They are deliberately framework- and protocol-agnostic — apply the underlying principle even if your project's specific conventions (status codes, header names, envelope shape) differ from the illustrative examples.

| #    | Rule                                                                                                                                                                                                                                                                                                                                                                                                      |
| :--- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| A-1  | Every endpoint follows the project's existing URI/versioning convention. No ad hoc exceptions for "just this one."                                                                                                                                                                                                                                                                                        |
| A-2  | Every endpoint that creates, updates, or deletes a resource specifies the correct success status code for that operation, per the project's own standards (e.g. typically 201 for creation, 204 for deletion with no body, 200/202 as appropriate elsewhere).                                                                                                                                             |
| A-3  | Every error response uses the project's adopted error format consistently — never a one-off shape for a single endpoint.                                                                                                                                                                                                                                                                                  |
| A-4  | Idempotency requirements are decided explicitly per endpoint (Step 3.3) — never left as an unstated assumption for any mutating endpoint with retry risk.                                                                                                                                                                                                                                                 |
| A-5  | Every collection endpoint documents its pagination mechanism explicitly, including default and maximum page/window size.                                                                                                                                                                                                                                                                                  |
| A-6  | Every endpoint lists all possible error status codes explicitly, each with the exact condition that triggers it. "Other errors may occur" is never an acceptable substitute for enumerating them.                                                                                                                                                                                                         |
| A-7  | Every endpoint traces back to at least one Functional Requirement in the spec. An endpoint with no FR is either scope creep or a sign the spec needs revising first.                                                                                                                                                                                                                                      |
| A-8  | All timestamps and other formattable primitives use one consistent format throughout the contract, matching the project's existing convention.                                                                                                                                                                                                                                                            |
| A-9  | The required role / authorization rule for every endpoint is stated explicitly — never implied by "well, only the right person would call this."                                                                                                                                                                                                                                                          |
| A-10 | Fields, status codes, and behaviors not part of the deliberate contract are not exposed as if they were. If the implementation happens to also do something observable (e.g. an internal ID leaks into the response, or behavior depends on internal field order), call it out and either contract it deliberately or eliminate it — do not let Hyrum's Law turn an accident into a permanent commitment. |

## Quality Checklist

Condensed version — full checklist with explanations lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] The feature's `SPEC.md` exists and is `APPROVED`.
- [ ] The project's existing API standards were read and extracted before drafting began.
- [ ] Every endpoint follows the project's URI/versioning convention.
- [ ] Every endpoint states required role/authorization explicitly.
- [ ] Every endpoint enumerates all error status codes with their exact trigger condition — none deferred to "etc".
- [ ] Every mutating endpoint has an explicit idempotency decision (needed or not, and why).
- [ ] Every collection endpoint documents pagination explicitly.
- [ ] Every endpoint traces to at least one FR in the spec.
- [ ] Side effects beyond the immediate response are documented per endpoint, not left to be discovered in code.
- [ ] The document's status line is `DRAFT` until a human has explicitly approved it.

## Anti-Patterns

| Anti-Pattern                                                                                                                              | Why It Fails                                                                                                                                                     |
| :---------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Writing the handler code first, then describing what it does as the "contract"                                                            | Inverts contract-first design; the contract ends up reflecting accidental implementation choices instead of a deliberate interface decision                      |
| Leaving authorization as "obviously only the owner can call this" without stating it                                                      | Implicit assumptions are the first thing lost between design and implementation, and the easiest thing for a reviewer to miss without an explicit line to check  |
| Listing only the happy-path response and writing "standard error handling applies" for everything else                                    | Violates Rule A-6; different engineers will invent different error shapes for the same untested condition                                                        |
| Inventing a new response envelope or error shape "just for this endpoint" because it seemed cleaner                                       | Fragments the API surface; consistency is what lets clients write generic handling code instead of per-endpoint special cases                                    |
| Designing pagination, filtering, or sorting ad hoc per endpoint instead of following one project-wide convention                          | Forces every client to special-case each endpoint instead of writing one generic list-handling routine                                                           |
| Treating idempotency as "we'll add a retry-safe key if it becomes a problem in production"                                                | By then it is a breaking change for every existing caller; idempotency behavior is part of the contract and must be decided at design time, not patched in later |
| Exposing internal identifiers, internal error messages, or stack traces in the response because "it was easy to just return what's there" | Violates Hyrum's Law immediately — once a client can parse it, removing it later is a breaking change, even if it was never meant to be part of the contract     |

## Best Practices

- Design the error taxonomy once, for the whole API surface, not once per endpoint. An endpoint-specific error shape is a sign the taxonomy is incomplete, not a sign this endpoint is special.
- When in doubt about an authorization rule, write the most restrictive version and flag it for the human reviewer to loosen explicitly — never guess toward a more permissive default.
- For any endpoint accepting a collection of items in one request (bulk operations), decide and document up front whether a partial failure is all-or-nothing or whether the response reports a per-item outcome. Do not leave this to be decided by whatever the database driver happens to do.
- Prefer extending an existing endpoint's contract (additive, backward-compatible fields) over introducing a parallel "v2" of the same resource, unless the change is genuinely breaking. Every additional simultaneously-supported version is a permanent maintenance cost, not a one-time decision.
- Document side effects even when they feel "implementation detail" — if a successful call causes something to happen elsewhere in the system, that is observable behavior a caller may eventually depend on or need to account for.
- When two endpoints in the same contract serve a similar shape of resource, use the same field names and types for the same concept in both. Inconsistent naming for the same concept across endpoints is one of the most common sources of integration bugs for API consumers.

## Common Rationalizations

| Rationalization                                                                                                 | Reality                                                                                                                                                                                                                                                                     |
| :-------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "I'll just build the endpoint and document it after — it's faster."                                             | This is the exact inversion this skill exists to prevent. Once code exists, "documenting" it just describes whatever was convenient to implement, not what callers should be able to rely on. Designing first is what makes the interface deliberate instead of accidental. |
| "The error cases are obvious from context, I don't need to enumerate every status code."                        | "Obvious" is exactly the kind of claim that breaks down the moment two different engineers implement the same endpoint independently and produce two different error shapes for the same untested condition. Enumerate them.                                                |
| "This is an internal endpoint, only our own frontend calls it, so the contract doesn't need to be this strict." | Hyrum's Law does not care who the caller is. Your own frontend, your mobile team, and your future self six months from now are all "somebody" who will depend on whatever this endpoint actually does, documented or not.                                                   |
| "I'll add idempotency later if it turns out we need it."                                                        | Adding an idempotency key after callers already exist is a breaking change for every one of them. The decision belongs at contract-design time, even if the answer is "not needed" — that's still a deliberate decision, not a gap.                                         |
| "A slightly different response shape for this one endpoint is fine, it's a special case."                       | Every endpoint feels like a special case to the person designing it in isolation. Consistency is what lets API consumers write one generic client instead of one per endpoint — that value disappears the first time an exception is made.                                  |

## Output

**Location:** `specs/<feature-name>/API_CONTRACT.md`

**Template:** [`resources/API_CONTRACT_TEMPLATE.md`](resources/API_CONTRACT_TEMPLATE.md)

**Worked example:** [`examples/example-api-contract.md`](examples/example-api-contract.md) shows a complete, filled-in contract for the same domain-neutral "resource reservation with capacity limit" feature used as the worked example in the `spec-driven-development` skill — illustrative only, not tied to any specific project's domain or framework.
