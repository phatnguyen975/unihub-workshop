# Prototype Review: <Capability>

> **Platform:** <platform>  
> **Entry point:** <prototype path/local preview>  
> **Spec/UI revisions:** <references>  
> **Review/approval:** <pending or explicit human decision/scope>

## Artifact Scope

Use this record for one capability's `specs/client/<platform>/<capability-id>/prototype/`. Identify its approved platform `ui-design.md` revision and rule IDs; the platform foundation preview is a design dependency, not a replacement for this capability preview.

## Preview Setup

Record the prototype directory, exact local HTTP server command, working directory, port and entry URL. Explain how to select each scenario and reset local state. Verify that the plain HTML/CSS/JavaScript preview loads JSON fixtures and assets without starting React/Expo or a backend. List required local assets and any unavailable preview dependencies.

## Scenario Coverage

Identify mock data/reset steps, navigation entry, screen/state IDs, actions and assertions. Cover representative failure/recovery and relevant viewports; keep scenario controls outside product UI.

Name the synthetic JSON fixture file and scenario key for each row. Specify the initial state, action sequence and observable result; include simulated delays or failures where relevant. Distinguish fixture data from JavaScript interaction logic and keep reset behavior reproducible.

| Journey/state | Setup | Interaction | Expected outcome | Viewport |
| ------------- | ----- | ----------- | ---------------- | -------- |

## Visual and Interaction Evidence

Record preview command, revision, viewport, screenshot actually viewed and named interaction outcome. Describe unavailable checks as pending and link reproducible scenarios.

Record screenshots actually viewed and outcomes inspected; cite global rule IDs instead of copying the checklist.

## Findings, Revisions and Approval Scope

Assign finding IDs, affected rules/scenarios and resolution status. Record the exact prototype/spec/baseline revisions and explicit human decision; do not infer approval from opening the preview.

Mobile HTML review does not certify native device behavior.
