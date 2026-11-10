---
name: client-review
description: "Client review: Six-axis findings, visual/interaction checks and verification evidence; corrections route to client-fix only within authorized scope."
---

# client-review

Usage: `$client-review <platform> <capability> [task-id]`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-review` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `verification-before-completion`, `ui-ux-pro-max`, `playwright-cli`, `design-system-patterns`, `responsive-design`, `accessibility-compliance`, `javascript-testing-patterns`, `vercel-react-best-practices`, `vercel-composition-patterns`, `vercel-react-native-skills`, `web-design-guidelines`, `agent-device`, `react-native-testing`, `security-review`.

Conditional packages: `react-native-best-practices`, `expo-native-ui`, `expo-design-system`, `expo-animation`. Check catalog condition, installed availability and version before calling; deferred packages are recipes, not installed skills.

Shared concerns: `code-review-and-quality`, `debugging-and-root-cause-analysis`; use shared workflow mode and caller scope.

Gate: Review writes its declared findings/evidence only; code fixes need caller authorization. Material UX/spec changes require approval.
