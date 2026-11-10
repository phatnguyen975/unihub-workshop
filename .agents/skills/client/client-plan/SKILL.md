---
name: client-plan
description: "Client plan: Vertical-slice tasks and test/verification plan including native access fallback."
---

# client-plan

Usage: `$client-plan <platform> <capability> [--mode mock|integration]`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-plan` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `superpowers-tdd`, `javascript-testing-patterns`, `react-native-testing`, `playwright-cli`, `agent-device`, `security-review`.

Shared concerns: `context-priming`; use shared workflow mode and caller scope.

Gate: Approve the consistent mock spec/prototype/task bundle or the separate integration plan for the selected mode.
