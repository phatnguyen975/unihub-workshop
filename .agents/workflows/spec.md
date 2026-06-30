---
description: Write an approved feature spec (and API contract, if the feature has a network interface) before any code is written.
---

# /spec — Write the Feature Specification

## Usage

```
/spec <feature-name>
```

`<feature-name>` is supplied by the human as a business-flow name (e.g. `payment-webhook-handling`, `offline-checkin-sync`), not a bare module name. If the supplied name is ambiguous or matches a module name verbatim, stop and ask for clarification before drafting anything — do not guess scope.

## What This Command Does

1. **Invoke `spec-driven-development`** (always).
   - Read every relevant document in this project's documentation directory before drafting anything. Cross-reference every requirement against the project's own architecture/design documentation — do not invent behavior that contradicts it.
   - Produce `specs/<feature-name>/SPEC.md`: Overview, Actors & Preconditions, Functional Requirements (FR-XX SHALL statements), Business Rules (BR-XX), State Transitions, Non-Functional Requirements, Acceptance Criteria (Gherkin), Out of Scope, Linked References into the project's documentation.
   - Set the document's status to `DRAFT`.
2. **Invoke `api-contract-design`** (only if this feature exposes or changes a network-facing interface — a REST endpoint, an RPC method, a webhook).
   - Requires `SPEC.md` to already exist; do not draft a contract against a spec that doesn't exist yet or is still being written.
   - Produce `specs/<feature-name>/API_CONTRACT.md`, conformant with this project's own API standards documentation.
   - Set the document's status to `DRAFT`.
3. **Present both artifacts and stop.**
   - Explicitly call out: any assumption made due to ambiguity in the original request, any business rule or constraint that could not be confirmed from existing documentation, any endpoint whose authorization rule required a guess.
   - Do **not** proceed to `/plan`. Do **not** write any implementation code. This command's only output is the document(s) above.

## Human Verification Gate

The human reads `SPEC.md` (and `API_CONTRACT.md`, if produced) and explicitly approves before `/plan` may be run.

- **Approved** → The agent updates the status line(s) from `DRAFT` to `APPROVED`, recording the approval date. `/plan` may now be invoked.
- **Changes requested** → The agent revises the artifact(s) per the human's feedback and re-presents them. Repeat until approved. Status remains `DRAFT` throughout this loop.

The agent never self-approves its own spec or contract. If acceptance criteria are not testable, or a requirement is ambiguous, the agent stops and asks rather than guessing.

## Notes for the Agent

- Do not chain into `/plan` on your own initiative, even if both artifacts look complete and correct to you. Approval is the human's decision, not a formality to infer from the absence of objections.
- If the feature request actually bundles more than one independent capability, propose splitting it into separate `/spec` invocations before drafting a single, overloaded spec.
- Run each artifact's own quality checklist (inside the corresponding skill's `resources/` folder) before presenting — do not present a draft you have not already self-reviewed.
