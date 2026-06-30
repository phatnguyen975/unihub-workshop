# Implementation Plan: <Feature Name>

> Spec: specs/<feature-name>/SPEC.md  
> API Contract: specs/<feature-name>/API_CONTRACT.md <!-- omit this line if not applicable -->  
> Status: DRAFT or APPROVED

## Overview

<!-- One paragraph: what this plan builds, in plan terms
(not spec terms — assume the reader has already read the spec). -->

## Architecture Decisions for This Plan

<!--
Decisions specific to HOW this plan sequences or structures the work —
not general architectural decisions (those belong in the project's own
decision-record documentation, referenced here if relevant, not
duplicated).
-->

- <Decision> — <one-line rationale>

## Task List

<!--
Each task is a complete VERTICAL SLICE through every layer one specific
capability needs — never a single architectural layer on its own.
Group tasks into phases by RISK or DEPENDENCY between capabilities
(e.g. "core flow" before "edge cases," or "the riskiest capability
first"), never by architectural layer. Each task uses the compact
format below: a checkbox line for scanning, followed by an indented
detail block. This keeps the list skimmable as a checklist while still
carrying full detail for whoever picks up the task.
-->

### Phase 1: <Phase Name — e.g. "Core Reservation Flow">

- [ ] **T1 — <Capability delivered, as a caller-observable outcome, one line>**
  - **Size:** S or M
  - **Depends on:** — <!-- or: T<n> -->
  - **Layers touched:** <e.g. Domain, Application, Infrastructure, Presentation>
  - **Files:** <expected file path 1>, <expected file path 2>, ...
  - **Acceptance Criteria:**
    - <Externally observable condition, e.g. "calling X with Y produces Z", traced to FR-XX / endpoint>
    - <Another externally observable condition>

- [ ] **T2 — <Capability delivered, one line>**
  - **Size:** S or M
  - **Depends on:** T1
  - **Layers touched:** <layers>
  - **Files:** <expected file path>
  - **Acceptance Criteria:**
    - <Externally observable condition>

#### Checkpoint: <Phase Name>

- [ ] <Condition that must hold before Phase 2 starts — e.g. "all Phase 1 capabilities pass their acceptance criteria end-to-end; build is clean">

### Phase 2: <Phase Name — e.g. "Edge Cases and Secondary Capabilities">

- [ ] **T3 — <Capability delivered, one line>**
  - **Size:** S or M
  - **Depends on:** T1
  - **Layers touched:** <layers>
  - **Files:** <expected file path>
  - **Acceptance Criteria:**
    - <Externally observable condition>

#### Checkpoint: <Phase Name>

- [ ] <Condition>

<!-- Repeat Phase + Checkpoint blocks until every capability from the
spec/contract has a task. Phases group capabilities by risk/dependency,
not by layer — a single phase can (and usually will) contain tasks that
each individually span all the same layers. -->

### Phase N: <Final Phase>

- [ ] **T<k> — <Capability delivered, one line>**
  - **Size:** S or M
  - **Depends on:** T<...>
  - **Layers touched:** <layers>
  - **Files:** <expected file path>
  - **Acceptance Criteria:**
    - <Externally observable condition, traced to an API_CONTRACT.md endpoint if applicable>

#### Checkpoint: Feature Complete

- [ ] All tasks above are checked off.
- [ ] Every acceptance criterion across all tasks has been verified end-to-end.
- [ ] Full test suite passes (per the project's testing policy).
- [ ] Ready for `/review`.

## Risks and Mitigations

| Risk               | Impact              | Mitigation                     |
| :----------------- | :------------------ | :----------------------------- |
| <Risk description> | High / Medium / Low | <Concrete mitigation approach> |

## Open Questions

<!--
Anything genuinely requiring human input before or during implementation.
Leave this section explicitly empty ("None.") if there are none — do not
omit the heading, so a reader can confirm it was consciously checked.
-->
