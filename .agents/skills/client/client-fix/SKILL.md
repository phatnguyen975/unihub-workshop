---
name: client-fix
description: "Client fix: Reproduced defect corrected with appropriate regression evidence."
---

# client-fix

Usage: `$client-fix <platform> <capability> [finding-id]`

Read root/shared/client rules and the matching web/mobile profile. Resolve platform/capability, current mode and approved artifact revisions; ask only about material unresolved inputs.

Read the common Scope, Artifact Ownership, Revision and Record Management, Execution and Verification, and Verification by Mode sections of `.agents/workflows/client.md`, then execute the `client-fix` section of `.agents/workflows/client.md`; its preconditions, outputs and human gates are authoritative. Do not infer approval from this invocation or a passing check.

Technical skill pool (load only matching concerns/platforms): `superpowers-tdd`, `verification-before-completion`, `ui-ux-pro-max`, `responsive-design`, `accessibility-compliance`, `javascript-testing-patterns`, `vercel-react-best-practices`, `vercel-composition-patterns`, `vercel-react-native-skills`, `react-native-testing`, `playwright-cli`, `agent-device`, `security-review`.

Conditional packages: `react-native-best-practices`, `vitest`. Check catalog condition, installed availability and version before calling; deferred packages are recipes, not installed skills.

Shared concerns: `debugging-and-root-cause-analysis`, `code-simplification`, `autonomous-git-workflow`; use shared workflow mode and caller scope.

Gate: Explicit fix request or authorized build scope; spec/design changes require approval.

Refactor uses `code-simplification` only for covered green code with the approved scope/test command: no HTML report, report-template loading or save-location question. Use `code-review-and-quality` for technical review and shared Git delivery rules.
