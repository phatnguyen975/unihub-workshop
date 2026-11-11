---
name: client-discover
description: "Client discover: Platform scope/journeys, screen inventory and ordered capability dependencies for human approval."
---

# client-discover

Usage: `$client-discover <web|mobile>`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-discover` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): none; use shared context/requirements skills.

Shared concerns: `context-priming`, `grilling`; use shared workflow mode and caller scope.

Gate: Approve scope/journeys.
