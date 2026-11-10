# UniHub Workshop — Agent Instructions

UniHub supports university workshop discovery, registration/payment, event administration and offline-first staff check-in. Product requirements are defined in `docs/product-overview.md` and `docs/product-features.md`, including concurrency, retry safety and offline operation.

## Start with the Task Scope

Paths in these instructions are repository-relative. Read `.agents/rules/shared.md`, then the matching route before editing. When a session starts at the root, explicitly read the relevant app entrypoint; do not assume child `AGENTS.md` files were already loaded.

| Scope                        | Rules and Process                                                                                            | Feature or Artifact Context                                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Backend or Workers           | `apps/{backend,workers}/AGENTS.md`; `.agents/rules/server.md`; `.agents/workflows/server.md`                 | `docs/product-overview.md`, `docs/product-features.md`, `docs/server/`, `specs/server/`, applicable public contracts |
| Web                          | `apps/web/AGENTS.md`; `.agents/rules/client.md`; `.agents/rules/web.md`; `.agents/workflows/client.md`       | `docs/product-overview.md`, `docs/product-features.md`, `docs/client/web/`, `specs/client/web/`                      |
| Mobile                       | `apps/mobile/AGENTS.md`; `.agents/rules/client.md`; `.agents/rules/mobile.md`; `.agents/workflows/client.md` | `docs/product-overview.md`, `docs/product-features.md`, `docs/client/mobile/`, `specs/client/mobile/`                |
| Public Contract              | `.agents/workflows/contracts.md`; `contracts/README.md`                                                      | Approved spec, HTTP standards and interface sources listed in the contract registry                                  |
| Documentation or Agent Setup | Relevant rows above and `docs/README.md`                                                                     | Declared task scope and artifact owner                                                                               |

If the requested scope cannot be established from the task/context, resolve that ambiguity before dependent edits. A task spanning scopes needs an explicit work boundary for each scope.

## Sources and Boundaries

- `docs/README.md` maps ownership and authority. Use the approved product requirements, scope-specific spec, UI baseline and public contract appropriate to the task; surface conflicting requirements rather than silently choosing one.
- Client implementation depends on client models/service interfaces and public contracts. Do not import backend/worker internals or use their database schema as the client wire model.
- Each delivery catalog owns progress and acceptance for its scope: server, web or mobile. Use the relevant catalog and artifact revisions to establish readiness.
- Keep approved artifacts consistent. Changes to scope, specifications, UI baselines or public contracts follow their owning approval process before dependent implementation. A cross-scope task grants write access only to the scopes included in the request.

## Skills and Execution

- `.agents/skill-catalog.md` lists installed skills, scope, triggers and deferred packages. Read only the applicable skill and supporting resources.
- Use the matching `$server-*` workflow for server work. Each stage follows its required human gates; do not chain stages without the required human invocation.
- Use the matching `$client-*` workflow for client work. Within an approved capability scope, continue implementation, verification, technical review and fixes without adding per-file gates. Human approval remains required for product/design dependencies, material changes and acceptance.
- Establish client execution mode explicitly. Mock work uses client models and service fakes; `$client-integrate` requires a separate integration scope, mock acceptance, an approved public contract and an approved integration plan.
- A discovered skill is not an installed runtime. Report exactly which checks ran, their target/evidence, and what remains unverified.

## Shared Safeguards

- Preserve pre-existing user changes and the task's declared write scope. Do not perform unrelated cleanup.
- Never read real `.env` files, private keys or credentials into generated artifacts. Never hardcode, log or commit secrets, or weaken security/tests to make a check pass.
- Follow `autonomous-git-workflow` and the delivery scope in shared rules. A commit is not human acceptance and does not authorize the next gated stage.
