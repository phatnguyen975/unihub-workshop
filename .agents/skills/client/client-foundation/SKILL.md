---
name: client-foundation
description: "Client foundation: Platform ui-design.md and representative prototype."
---

# client-foundation

Usage: `$client-foundation <web|mobile>`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-foundation` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `frontend-design`, `ui-ux-pro-max`, `design-system-patterns`, `responsive-design`, `accessibility-compliance`, `playwright-cli`, `vercel-react-native-skills`.

Conditional packages: `expo-native-ui`, `expo-design-system`. Check catalog condition, installed availability and version before calling; deferred packages are recipes, not installed skills.

Shared concerns: `context-priming`, `grilling`; use shared workflow mode and caller scope.

Gate: Approve platform design baseline.
