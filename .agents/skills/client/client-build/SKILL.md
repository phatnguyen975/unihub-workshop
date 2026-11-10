---
name: client-build
description: "Client build: Working mock capability, tests, screenshots actually inspected and interaction evidence."
---

# client-build

Usage: `$client-build <platform> <capability> [task-id]`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-build` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `superpowers-tdd`, `verification-before-completion`, `ui-ux-pro-max`, `design-system-patterns`, `responsive-design`, `accessibility-compliance`, `javascript-testing-patterns`, `vercel-react-best-practices`, `vercel-composition-patterns`, `vercel-react-native-skills`, `react-native-testing`, `playwright-cli`, `agent-device`, `security-review`.

Conditional packages: `react-native-best-practices`, `expo-ui`, `expo-router`, `vitest`, `react-navigation`, `expo-native-ui`, `expo-design-system`, `expo-animation`. Check catalog condition, installed availability and version before calling; deferred packages are recipes, not installed skills.

Shared concerns: `code-simplification`, `debugging-and-root-cause-analysis`, `autonomous-git-workflow`; use shared workflow mode and caller scope.

Gate: Only scope/design changes; no per-file gate; inaccessible native UI routes to human review.

Refactor uses `code-simplification` only for covered green code with the approved scope/test command: no HTML report, report-template loading or save-location question. Use `code-review-and-quality` for technical review and shared Git delivery rules.
