---
name: server-spec
description: "Server spec: execute the spec stage with existing SDD/TDD and human approval gates."
---

# server-spec

Usage: `$server-spec <feature-name>`

Read shared/server rules, the relevant server feature and the `server-spec` section of `.agents/workflows/server.md`. Execute that canonical procedure and its conditional technique calls; do not chain another stage without the required human invocation. Resolve the approved contract via `contracts/http/README.md`. Keep server unit-only and static architecture checks scoped to the app changed.
