# Task Plan: <Capability>

> **Platform/mode:** <web|mobile; MOCK>  
> **Status:** <DRAFT|APPROVED>  
> **Spec/prototype/UI revisions:** <matching bundle>  
> **Approval:** <pending or explicit decision/scope>

## Dependency Order

Map the capability to cohesive source feature owners and identify cross-feature composition responsibilities. Record the platform source-guide revision; capability IDs are not automatically source directory names.

List stable task IDs in executable order and prerequisite artifact/tooling revisions. This file owns MOCK tasks; integration tasks belong in integration-plan.md.

## Vertical Slices

Each task delivers a usable observable slice. Link AC/scenario IDs, dependencies, file boundaries, test-first behavior and verification commands. A checked box means its stated work and required verification are complete; pending native evidence remains explicit.

- [ ] <task ID and caller-observable capability>
  - **Depends on:** <IDs or none>
  - **File scope:** <expected files/boundary>
  - **Acceptance:** <observable outcomes>
  - **Test-first:** <testable behavior; appropriate exception if config/style>
  - **Verification:** <commands, scenarios and evidence>

## Verification Matrix

For each requirement/scenario name the test layer, target, expected assertion, required versus optional status and human responsibility. Mark unavailable tooling or device prerequisites rather than inventing a command.

| Requirement/scenario | Unit/component | Browser/native | Human | Pending prerequisites |
| -------------------- | -------------- | -------------- | ----- | --------------------- |

## Risks, Exclusions and Approval-Sensitive Changes

List concrete risks, mitigation, excluded work and changes requiring amended approval. Preserve task history and link the explicit bundle approval.
