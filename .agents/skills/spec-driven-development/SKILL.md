---
name: spec-driven-development
description: Writes a precise, implementation-independent specification (actors, functional requirements, business rules, state transitions, acceptance criteria) before any code is written. Use when starting a new feature, when a requirement is ambiguous, or when a change has architectural impact. Do not use for one-line bug fixes or config-only changes with no behavior impact.
---

# Spec-Driven Development

## Overview

This skill turns a vague feature request into a precise, testable specification **before** a single line of production code is written. The spec is the contract between business intent and implementation: code must satisfy the spec, and the spec must satisfy the project's own architecture and design documents.

A spec is **language-agnostic and implementation-independent**. It describes _what_ the system must do and under what conditions — never _how_ it will be coded (no class names, no method signatures, no SQL, no framework references).

This skill produces exactly one artifact: `specs/<feature-name>/SPEC.md`.

## When to Use

- Starting a new feature, however small it may seem.
- A requirement is ambiguous, underspecified, or contradicts something already built.
- A change has architectural impact (new entity, new state machine, new cross-boundary interaction, new external integration).
- The user or a previous review finding asks to formalize behavior that currently only exists as tribal knowledge or scattered comments.
- The task would take more than 30 minutes to implement.

## When NOT to Use

- One-line bug fixes with an obviously correct, narrow scope, or typo corrections.
- Pure configuration, dependency-version, or formatting changes that do not alter observable behavior.
- Cosmetic refactors that change no external behavior (these belong to a refactoring/code-quality workflow, not a new spec).

If in doubt, prefer writing the spec. A short spec costs minutes; a missing spec costs hours of rework once the gap surfaces during implementation or review.

## Core Principles

1. **The spec is the contract.** Once approved, it is the source of truth for what "done" means. Implementation deviations are defects in the code, not in the spec — unless the spec itself is later found to be wrong, in which case the spec is revised explicitly and visibly, never silently.
2. **One feature, one spec.** Never blend two unrelated features into one `SPEC.md`. If a request turns out to bundle multiple independent capabilities, split it before continuing.
3. **Every requirement must be traceable and testable.** A requirement that cannot be expressed as a pass/fail test is not yet specific enough to write.
4. **Pre-flight context gathering is mandatory.** A spec written without reading the project's own architecture, design, and standards documentation is a guess, not a spec.
5. **The spec never describes implementation.** No class names, method signatures, SQL, or framework-specific terms. A spec should remain valid even if the implementation language or framework changed tomorrow.

## Workflow

### Step 1 — Pre-Flight Context Gathering

Before drafting anything, gather context in this order:

1. **Identify the feature** in one line — name, the part of the system it belongs to, and the primary actor(s) involved.
2. **Locate and read the project's own authoritative documentation** — architecture/design docs, data model docs, API standards, and any record of prior architectural decisions. Most projects centralize this under a `docs/` directory or equivalent, often indexed in a root-level agent rules file. Read every section relevant to this feature in full; do not skim.
3. **Extract constraints explicitly**, in writing, before drafting the spec. At minimum, capture:
   - Business rules that already exist and apply to this feature.
   - Data constraints (uniqueness, foreign keys, valid value ranges) on any entity this feature touches.
   - Prior architectural decisions that constrain this feature (do not contradict an accepted decision without flagging it explicitly).
   - Any global API, security, or compliance standard that applies.

This step has a hard gate: **do not proceed to Step 2 until constraints are explicitly listed out**, not just read silently. Gaps skipped here reliably resurface as defects during implementation — see the **Anti-Patterns** section below.

### Step 2 — Confirm Feature Naming and Scope

The agent does not invent the feature name — it is supplied by the human, typically as a kebab-case business-flow name (e.g., `user-authentication`), not a bare module or component name.

- If the supplied name matches a module/component name verbatim (e.g., `users`), **stop and ask** whether this is meant to be one specific use case within that module, since a module commonly contains several distinct features.
- If the request bundles what looks like more than one independent capability, propose splitting it into separate specs before drafting.

### Step 3 — Draft the Spec

Use the structure in [`resources/SPEC_TEMPLATE.md`](resources/SPEC_TEMPLATE.md). Do not skip sections; mark a section `N/A` with a one-line reason if it truly does not apply (e.g. "5. State Transitions — N/A, this entity has no lifecycle states").

Fill in this order, since each section depends on the previous one:

1. **Overview** — one paragraph: what this feature does, why it exists, which part of the system owns it.
2. **Actors & Preconditions** — every actor who can trigger this feature, and what must already be true for it to execute.
3. **Functional Requirements (FR)** — numbered, testable "SHALL" statements. See **Writing Rules** section below.
4. **Business Rules (BR)** — constraints derived from existing project documentation, not invented. Each BR maps to a guard clause or validation rule in the eventual implementation.
5. **State Transitions** — if any entity touched by this feature has a status/lifecycle field, enumerate every valid transition and every invalid transition with its expected rejection behavior. Mark `N/A` otherwise.
6. **Non-Functional Requirements (NFR)** — performance, concurrency, idempotency, security, and reliability expectations that apply.
7. **Acceptance Criteria** — Gherkin-style Given/When/Then scenarios. Every scenario must map back to at least one FR.
8. **Out of Scope** — explicit negative scope to block scope creep later.
9. **Linked References** — list every external document section that informed this spec, so a future reader can verify traceability.

### Step 4 — Self-Review Against the Quality Checklist

Before presenting the spec, run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) against the draft. Fix any failing item before moving on. Do not present a spec that fails its own checklist.

### Step 5 — Present as DRAFT and Stop

Set the document status to `DRAFT`. Present the spec and explicitly call out:

- Any assumption made due to ambiguity in the original request.
- Any business rule or constraint that could not be confirmed from existing documentation.
- Any section marked `N/A` and why.

**Then stop.** Do not proceed to planning or implementation. A human must read and approve this artifact first — this is a hard gate, not a suggestion. The agent does not self-approve its own spec. If the human rejects it, revise the spec with the human's feedback and repeat Step 5 until approval is granted.

### Step 6 — On Approval, Flip Status to APPROVED

Once the human approves, update the document's status line from `DRAFT` to `APPROVED` and record the approval date. Do not silently treat an unapproved draft as final — the status line is the single source of truth for whether implementation may proceed against this spec.

## Writing Rules

These rules govern the content quality of every spec produced by this skill — they are not optional style preferences.

| #   | Rule                                                                                                                                                                                                              |
| :-- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| S-1 | Every Functional Requirement (FR) must be traceable to at least one statement in the project's existing documentation, or explicitly flagged as a new requirement with no prior basis.                            |
| S-2 | Every Acceptance Criterion (scenario) must be traceable to at least one FR. An acceptance criterion with no parent FR means either the FR list is incomplete or the criterion is out of scope.                    |
| S-3 | Every FR must be independently testable. If an FR cannot be verified in isolation from other FRs, decompose it into smaller FRs.                                                                                  |
| S-4 | Business Rules (BR) are non-negotiable constraints, not suggestions. They must map directly to a guard clause, validation rule, or constraint in the eventual implementation.                                     |
| S-5 | State transitions must be exhaustive: every valid transition AND every invalid transition (with its expected rejection behavior) must be listed. An incomplete state table is a defect in the spec.               |
| S-6 | The spec contains zero implementation details — no class names, method signatures, SQL, endpoint paths, or framework-specific terms. (Endpoint design belongs to a separate API-contract artifact, not the spec.) |
| S-7 | Security and access-control requirements (authorization checks, identity validation, tenant/ownership isolation) are explicit, numbered FRs — never an implicit assumption left unstated.                         |

## Quality Checklist

A spec is ready to present only when **all** of the following are true. The full checklist with explanations lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md); the condensed version:

- [ ] Pre-flight context gathering was performed and constraints were explicitly listed before drafting began.
- [ ] Every FR is a numbered, testable "SHALL" statement.
- [ ] Every FR traces to existing documentation or is flagged as net-new.
- [ ] Every acceptance criterion traces to at least one FR.
- [ ] State transitions (if applicable) are exhaustive, including invalid transitions and their rejection behavior.
- [ ] Zero implementation details appear anywhere in the document.
- [ ] Security/access-control requirements are explicit FRs, not implied.
- [ ] Out of Scope section exists and is non-trivial (not just "N/A").
- [ ] Every section is either filled in or marked `N/A` with a reason.
- [ ] Linked References section lists every source document consulted.
- [ ] The document's status line is `DRAFT` until a human has explicitly approved it.

## Anti-Patterns

| Anti-Pattern                                                                                                                                                                                  | Why It Fails                                                                                                                    |
| :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------ |
| Writing the spec from the request text alone, without reading existing project documentation                                                                                                  | Produces a spec that looks complete but silently contradicts decisions already made elsewhere in the system                     |
| Embedding implementation details ("Create a `RegistrationService` class with a `register()` method...")                                                                                       | Locks the spec to one implementation; makes the spec useless as an independent contract; violates S-6                           |
| Writing FRs as vague capability statements ("The system shall support registration") instead of precise conditional statements ("The system SHALL reject registration when capacity is zero") | Untestable; two different engineers would implement different behavior from the same FR                                         |
| Skipping the "Out of Scope" section                                                                                                                                                           | Scope creep during implementation, because nothing was ever declared explicitly excluded                                        |
| Listing only the happy-path state transition and omitting invalid/rejected transitions                                                                                                        | The most common source of unhandled-edge-case bugs; an incomplete state table guarantees a missing branch later                 |
| Treating security/authorization as implicit ("Of course only the owner can do this")                                                                                                          | Implicit assumptions are the first thing lost between spec and implementation; must be an explicit, numbered FR                 |
| Proceeding to planning/implementation without an explicit human approval of the spec                                                                                                          | Defeats the entire purpose of the gate — approval exists precisely to catch wrong assumptions while they are still cheap to fix |

## Best Practices

- Prefer many small, precise FRs over one large, compound FR. If an FR contains "and," consider splitting it.
- Write FRs as `SHALL` statements with an explicit trigger condition: `The system SHALL <behavior> when <condition>.` This format is what makes S-3 (independent testability) achievable in practice.
- When a business rule's origin is unclear, write it down anyway and mark it `[UNCONFIRMED — please verify]` rather than omitting it silently.
- Keep the **Overview** section to one paragraph. If it needs more, the feature is probably too large for one spec.
- When in doubt about scope boundaries, over-specify "Out of Scope" rather than under-specify it — it costs nothing to state the obvious exclusion and prevents a real argument three weeks later.
- Treat every spec as a living document only until implementation starts. After approval, changes to the spec must be visible and explicit (a new revision note), never a silent edit.

## Keeping the Spec Alive

The spec is a living document, not a one-time artifact:

- **Update when decisions change** — If you discover the data model needs to change, update the spec first, then implement.
- **Update when scope changes** — Features added or cut should be reflected in the spec.
- **Commit the spec** — The spec belongs in version control alongside the code.
- **Reference the spec in PRs** — Link back to the spec section that each PR implements.

## Common Rationalizations

| Rationalization                                                                                        | Reality                                                                                                                                                                                                                                           |
| :----------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| "This feature is simple enough, I can skip the spec and go straight to code."                          | Simple features are exactly where unstated assumptions hide best — there's no review checkpoint forcing them to surface. The spec for a simple feature takes minutes; skipping it risks discovering a wrong assumption after the code is written. |
| "I already understand the requirement, writing it down is redundant."                                  | Understanding in your own head is not the same as a precise, testable, reviewable artifact someone else (or you, later) can verify against. The spec is not for you — it's the contract everyone else checks the implementation against.          |
| "I'll just note the implementation approach in the spec to save time later."                           | This violates S-6 and defeats the purpose of having a separate, implementation-independent contract. If the chosen approach turns out wrong, the spec becomes wrong too, instead of staying valid.                                                |
| "The acceptance criteria are obviously covered by the FRs, I don't need to write them out explicitly." | "Obviously covered" is precisely the kind of claim that breaks under review. If it's truly obvious, writing it down costs almost nothing; if it's not obvious once written, you just caught a gap.                                                |
| "I'll mark security as covered by the existing auth system, no need for an explicit FR."               | The existing system enforcing _authentication_ does not guarantee this specific feature enforces the right _authorization_ rule (e.g., ownership, tenant isolation). This must be its own explicit FR per S-7.                                    |

## Output

**Location:** `specs/<feature-name>/SPEC.md`

**Template:** [`resources/SPEC_TEMPLATE.md`](resources/SPEC_TEMPLATE.md)

**Worked example:** [`examples/example-spec.md`](examples/example-spec.md) shows a complete, filled-in spec for a generic "resource reservation with capacity limit" feature — illustrative only, not tied to any specific project's domain.
