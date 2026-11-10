# Capability: <Name>

> **Platform:** <web|mobile>  
> **Status:** <DRAFT|APPROVED>  
> **Revision:** <revision>  
> **Experience/UI baseline:** <paths and approved revisions>  
> **Approval:** <pending or explicit human decision identifying the bundle>

## Actors and Preconditions

Identify capability ID, product feature IDs, actors, entry conditions and approved dependency revisions. Distinguish authentication/access UX from provider enforcement.

## Functional Requirements (FR)

Write stable FR-001 statements describing observable user/service behavior. Include relevant failure and recovery behavior; avoid binding requirements to an unselected library.

## Business Rules (BR)

Give stable BR-001 IDs and their product/decision sources. State validation and transitions without silently extending business policy; flag missing decisions.

## User Flow and UI State Matrix

Use stable screen/scenario IDs. For each action/state record transition, feedback, allowed next actions and baseline rule references, including applicable error/offline states.

| Screen/action | State/scenario | Expected behavior | Rule references |
| ------------- | -------------- | ----------------- | --------------- |

## Data/Action/Error Requirements

Describe client-owned fields, types, nullability, operations and typed failure/retry needs with deterministic sample scenarios. Mark contract gaps as proposals, not endpoints.

Client-model needs here do not define an approved wire contract.

## Platform, Security and Offline Concerns

Select applicable keyboard/accessibility, permissions, sensitive storage, cancellation, concurrency and offline/relaunch cases. State what requires native evidence.

## Acceptance Criteria

Assign AC-001 IDs linked to FR/BR and scenarios. Use Given/When/Then with observable outcomes and failure cases. Separate code-testable criteria from visual/human criteria.

## Baseline References and Scoped Design Additions

Reference global UI rule IDs/revision and capability prototype. Add only capability-local details; reusable rule changes need approval at the platform baseline.

## Out of Scope and Dependencies

Declare exclusions, cross-capability dependencies, unresolved decisions and blocked work. Identify exact spec/prototype/task revisions submitted as one approval bundle.
