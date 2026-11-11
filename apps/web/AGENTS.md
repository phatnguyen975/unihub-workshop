# Web scope

Paths are repository-relative. Read `.agents/rules/shared.md`, `.agents/rules/client.md`, `.agents/rules/web.md` and the applicable stage in `.agents/workflows/client.md`.

Use `docs/product-overview.md`, `docs/product-features.md`, `docs/client/web/` and `specs/client/web/<capability>/`. The approved web `ui-design.md` and capability prototype govern visual review. If the design foundation is missing, use `client-foundation` to draft and obtain approval before dependent capability implementation.

Use client models and the declared mock/integrated mode. Server implementation documents are references; public contracts govern API integration.

Source ownership follows `docs/client/web/source-structure.md`. Keep Vite mounting in `src/main.tsx` and composition in `src/app/`. Capability/spec IDs do not imply one source module per capability.

Tooling and command ownership: `docs/client/web/tooling.md`. Run `npm run verify` for code checks and `npm run verify:all` for code plus browser verification after Chromium installation. Source boundaries are enforced by the app ESLint rule; no product UI approval is implied by the placeholder tests.
