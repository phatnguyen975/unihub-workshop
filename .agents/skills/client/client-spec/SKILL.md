---
name: client-spec
description: "Client spec: Capability requirements, acceptance criteria, UI states, data needs and relevant abuse cases."
---

# client-spec

Usage: `$client-spec <platform> <capability>`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-spec` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `ui-ux-pro-max`, `accessibility-compliance`, `javascript-testing-patterns`, `react-native-testing`, `security-review`.

Shared concerns: `context-priming`, `grilling`; use shared workflow mode and caller scope.

Gate: Capability artifact bundle before implementation.
