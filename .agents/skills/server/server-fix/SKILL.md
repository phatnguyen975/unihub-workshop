---
name: server-fix
description: "Server fix: execute the fix stage with existing SDD/TDD and human approval gates."
---

# server-fix

Usage: `$server-fix <feature-name>`

Read shared/server rules, the relevant server feature and the `server-fix` section of `.agents/workflows/server.md`. Execute that canonical procedure and its conditional technique calls; do not chain another stage without the required human invocation. Resolve the approved contract via `contracts/http/README.md`. Keep server unit-only and static architecture checks scoped to the app changed.

Use shared `code-simplification` only for covered green code within this approved scope/test command in workflow mode: no HTML report/template loading/save-location question. Follow shared Git delivery rules; commits do not open the next server gate.
