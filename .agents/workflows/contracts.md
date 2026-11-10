# Public Contract Workflow

Read shared rules, `contracts/README.md` and `contracts/http/README.md`. Resolve the authoritative interface source before drafting or changing a consumer/provider. The registry identifies each interface source and format; HTTP standards govern wire conventions. Client mock models/data needs are not approved interfaces, and provider internals are not a contract.

## Authoring and Change Control

Use the approved feature specification and `api-contract-design` for network interface design. Produce DRAFT artifacts in the format/location selected by the registry; human approval is required before implementation relies on them. Preserve request/response/error semantics, authentication/authorization requirements, idempotency/retry/side effects and SSE behavior, not only schema fields. Do not invent a method/path or authorization rule to complete an implementation.

Provider changes follow server-spec/plan/build gates, including the two-gate improvement-proposal process when changing approved artifacts during a build. Keep the contract, affected server specification/task plan and relevant documentation synchronized through those gates. Contract-only work does not authorize backend/infra code edits.

Consumer integration requires explicit `client-integrate` scope, mock acceptance, an approved public contract and an approved integration plan. Map wire data to client models behind the client service interface; do not import entity/DTO/internal provider code. Report contract gaps and stop dependent work rather than silently changing the provider or redesigning accepted UI.

## OpenAPI Transition

When OpenAPI work is explicitly authorized, use `contracts/http/openapi.yaml` with `$ref` `paths/` and `components/{schemas,parameters,responses,securitySchemes}/` as needed. Supplemental behavior notes belong in `contracts/http/behavior/` when needed. Select the OpenAPI version and validation/generation tooling in that task; do not create placeholder YAML or fake schemas before it.

Verify parity with existing approved Markdown contracts, including side effects, retry/reconciliation and SSE semantics. Obtain approval and update the registry for the converted interface scope before YAML becomes authoritative. Retain unmatched Markdown as authority until converted; archive/remove only superseded schema after parity/approval. Avoid two competing schemas for the same operation. Generated provider docs do not independently prove conformance to an approved contract.
