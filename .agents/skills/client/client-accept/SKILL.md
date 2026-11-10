---
name: client-accept
description: "Client accept: Reviewable preview, verification matrix, evidence."
---

# client-accept

Usage: `$client-accept <platform> <capability|all-mock>`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-accept` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `playwright-cli`, `accessibility-compliance`, `agent-device`, `verification-before-completion`.

Shared concerns: `autonomous-git-workflow`; use shared workflow mode and caller scope.

Gate: Human acceptance; mock completion stops here.
