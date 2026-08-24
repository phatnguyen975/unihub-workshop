# Spec: <Feature Name>

> **Status:** DRAFT or APPROVED  
> **Owner/area:** <module or component this feature belongs to>  
> **Last revised:** <date> — <one-line reason, only after first approval>

## 1. Overview

<!--
One paragraph. State what this feature does, why it exists (the business
or user need behind it), and which part of the system owns it. No
implementation detail.
-->

## 2. Actors & Preconditions

<!--
List every actor (human role, external system, or scheduled process) that
can trigger this feature, and what must already be true in the system
before this feature can execute.
-->

| Actor | Precondition |
| :---- | :----------- |
|       |              |

## 3. Functional Requirements

<!--
Numbered, testable "SHALL" statements. Each one must be verifiable in
isolation (Rule S-3). Use this exact format:

FR-01: The system SHALL <behavior> when <condition>.

Group related FRs with a sub-heading if there are more than ~8.
-->

- **FR-01:** The system SHALL ...
- **FR-02:** The system SHALL ...

## 4. Business Rules

<!--
Constraints derived from the project's existing documentation/decisions —
never invented ad hoc. Each rule must eventually map to a guard clause or
validation rule in the implementation (Rule S-4).

Format: BR-<number>: <Rule statement>
-->

- **BR-01:** ...
- **BR-02:** ...

## 5. State Transitions

<!--
Required if the feature involves an entity with a status/lifecycle field.
Otherwise write: "N/A — this feature does not introduce or modify any
stateful entity."

List EVERY valid transition and EVERY invalid transition with its
expected rejection behavior (Rule S-5).
-->

| From State | Event/Trigger | To State | Valid? | Rejection Behavior (if invalid) |
| :--------- | :------------ | :------- | :----: | :------------------------------ |
|            |               |          |        |                                 |

## 6. Non-Functional Requirements

<!--
Performance, concurrency, idempotency, rate-limiting, security, reliability, and
observability expectations, and so on. Be concrete and measurable where possible
(e.g. "must remain correct under N concurrent requests for the same
resource" rather than "must handle concurrency").
-->

- **Performance:** ...
- **Concurrency:** ...
- **Idempotency:** ...
- **Rate-limiting:** ...
- **Security:** ...
- **Reliability / Failure modes:** ...
- **Observability:** ...

## 7. Acceptance Criteria

<!--
Gherkin-style Given/When/Then scenarios that define "done." Every scenario
MUST trace back to at least one FR above (Rule S-2). Number scenarios
sequentially.
-->

### Scenario 1: <Title>

```gherkin
Given ...
And ...
When ...
Then ...
And ...
```

<!--
Repeat for each scenario, including negative/edge-case scenarios,
not just the happy path.
-->

## 8. Out of Scope

<!--
Explicitly list what this feature does NOT cover, to prevent scope creep
during planning and implementation. Do not leave this section empty or
trivial — think about adjacent capabilities someone might assume are
included.
-->

## 9. Linked References

<!--
List every section of the project's existing documentation that informed
this spec (architecture docs, data model docs, prior decision records,
API/security standards). This is what makes Rule S-1 verifiable by a
reviewer.
-->

## 10. Open Questions / Assumptions

<!--
Anything that was ambiguous in the original request and required an
assumption to proceed. Also list anything marked [UNCONFIRMED] in the
Business Rules section above. This section should be empty by the time
the spec is APPROVED — every item here must be resolved or explicitly
accepted by the approver first.
-->
