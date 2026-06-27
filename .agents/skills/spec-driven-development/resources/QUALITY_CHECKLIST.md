# Spec Quality Checklist

Run this checklist against a drafted `SPEC.md` before presenting it for human approval. Every item must pass — a spec that fails any item is not ready, regardless of how complete it looks at a glance.

## Pre-Flight

- [ ] **Context was gathered before drafting.** The project's existing architecture/design documentation, data model documentation, and any relevant prior decisions were read in full for the area this feature touches — not skimmed, not skipped.
- [ ] **Constraints were extracted explicitly**, in writing, before the first word of the spec was drafted (business rules, data constraints, prior architectural decisions, applicable standards).
- [ ] **The feature name is a use case, not a bare module/component name.** If the supplied name matched a module name verbatim, this was flagged and clarified before proceeding.

## Structure

- [ ] The document's status line is present and starts as `DRAFT`.
- [ ] All ten sections of the template are present.
- [ ] Every section is either filled in with real content, or explicitly marked `N/A` with a one-line reason. No section is silently empty.
- [ ] The Overview is one paragraph. If it sprawled into multiple paragraphs, the feature is likely too large for a single spec — flag this rather than letting the Overview balloon.

## Functional Requirements

- [ ] Every FR is numbered (`FR-01`, `FR-02`, ...).
- [ ] Every FR follows the `The system SHALL <behavior> when <condition>.` pattern — not a vague capability statement.
- [ ] No FR contains "and" joining two unrelated behaviors. (If it does, split it into two FRs.)
- [ ] Every FR is independently testable — you can imagine one specific test that passes or fails for that FR alone, without needing other FRs to also be true.
- [ ] Every FR traces to something in the project's existing documentation, OR is explicitly flagged as a new requirement with no prior basis (so the human reviewer knows to scrutinize it more closely).

## Business Rules

- [ ] Every BR is numbered (`BR-01`, `BR-02`, ...).
- [ ] Every BR came from existing documentation/decisions, not invented ad hoc during spec writing.
- [ ] Any BR whose origin could not be confirmed is marked `[UNCONFIRMED — please verify]`, not silently included as fact.

## State Transitions

- [ ] If the feature touches a stateful entity: every valid transition is listed.
- [ ] Every invalid transition is also listed, with its expected rejection behavior — not just the happy path.
- [ ] If genuinely not applicable, the section says so explicitly rather than being left blank.

## Non-Functional Requirements

- [ ] Concurrency/idempotency behavior is addressed if this feature has any write/mutation behavior with retry or duplicate-trigger risk.
- [ ] Security/access-control expectations are addressed here at a high level (the specific authorization FRs live in section 3, not here — this section covers broader NFR-level security posture like data protection or rate limiting if relevant).
- [ ] Performance expectations are stated in concrete, falsifiable terms where the feature has known scale/load implications — not just "must be fast."

## Acceptance Criteria

- [ ] Every scenario uses Given/When/Then structure.
- [ ] Every scenario traces back to at least one FR.
- [ ] At least one scenario covers a negative/edge case, not only the happy path (unless the feature genuinely has no edge cases worth naming — rare, and should be a conscious decision, not an oversight).
- [ ] No scenario silently introduces new behavior not covered by any FR. (If it does, go back and add the missing FR first.)

## Scope Boundaries

- [ ] "Out of Scope" is non-trivial — it names at least one adjacent capability someone might reasonably assume is included, and states that it is not.
- [ ] "Linked References" lists every document section actually consulted — a future reader should be able to verify traceability without re-deriving it from scratch.

## Security (Rule S-7 specific check)

- [ ] Every authorization/access-control expectation is its own numbered FR — not folded into a Business Rule, and not left as an implicit assumption anywhere in the document.
- [ ] If this feature touches any cross-boundary trust decision (who can act on whose behalf, who can see whose data), that decision is spelled out as an explicit FR, not assumed obvious.

## Implementation-Independence (Rule S-6 specific check)

- [ ] Zero class names, method names, function signatures appear anywhere in the document.
- [ ] Zero SQL, query syntax, or schema DDL appears anywhere in the document.
- [ ] Zero framework-specific terms (e.g. naming a specific library, annotation, or platform API) appear anywhere in the document.
- [ ] Zero concrete endpoint paths/HTTP verbs appear in the spec itself (this belongs in a separate API-contract artifact, derived from this spec — not duplicated here).

## Final Gate

- [ ] "Open Questions / Assumptions" section is either empty, or contains only items the agent is explicitly flagging for the human to resolve before approval — not items the agent silently decided on its own.
- [ ] The agent has presented the spec and stopped — it has not proceeded to planning or implementation without explicit human approval.
