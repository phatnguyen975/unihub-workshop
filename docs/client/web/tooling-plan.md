# Web Tooling Setup Plan

> **Status:** APPROVED<br>
> **Revision:** 1<br>
> **Approval:** Human approved the package/configuration scope on 2026-11-11. Setup evidence and outstanding checks are recorded in [Web Tooling](tooling.md); product UI and capability implementation require separate approval.

## Scope and Prerequisites

Configure the existing React/TypeScript/Vite application in `apps/web/` using the approved [Experience Map](experience-map.md), [Capability Inventory](feature-delivery.md), [Source Structure](source-structure.md) and [Client Source Boundaries](../source-boundaries.md). Keep the UniHub Web placeholder until design foundation is approved. Work is app-local; no mobile, backend, worker, infrastructure or public contract implementation changes.

## Package Selection

| Concern              | Proposed Packages                                                                                       | Responsibility                                                                                                          |
| -------------------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Routing              | `react-router`                                                                                          | Browser URL/navigation, route composition and fallbacks; declarative SPA mode, not framework/server mode                |
| Remote/Service State | `@tanstack/react-query`                                                                                 | Async service results, cache identity and invalidation; query functions call injected feature services, including mocks |
| Form State           | `react-hook-form`                                                                                       | Form drafts, field errors and submission lifecycle                                                                      |
| Validation           | `zod`, `@hookform/resolvers`                                                                            | Typed client schemas, public config validation and form resolver integration                                            |
| Styling              | `tailwindcss`, `@tailwindcss/vite`                                                                      | Vite-integrated utilities and later approved app tokens; no preselected design-system values                            |
| Unit/Component Tests | `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` | Deterministic model/service and accessible component behavior                                                           |
| Browser Tests        | `@playwright/test`, `@axe-core/playwright`                                                              | Persistent journeys, viewport checks, browser diagnostics and supplemental automated accessibility checks               |
| Agent Browser Driver | `@playwright/cli`                                                                                       | App-local browser inspection used by the installed skill; no global installation                                        |
| Import Boundaries    | ESLint built-in restrictions plus a compatible boundary rule implementation if needed                   | Prevent server/mobile imports, feature-internal cross-imports and shared-to-feature/app dependencies                    |

Retain the existing React, Vite, TypeScript and ESLint baseline. Resolve stable releases against actual package peer/engine metadata before installing, then commit exact resolutions through the app lockfile. Do not bypass peer conflicts with force or legacy-peer-deps, choose beta versions merely to satisfy tooling, or change the system Node/Git configuration. If a material package compatibility conflict requires replacing the chosen stack, present that change before installation.

Do not add Axios, a global state library, a UI component library, icon pack, animation library, date library, payment SDK or OpenAPI generation tooling without a demonstrated feature/foundation requirement. Native browser APIs remain available behind typed service adapters. Network interception such as MSW can be introduced during adapter/integration testing when needed; initial mocks implement client service interfaces without inventing HTTP endpoints.

## Ownership and Mock Boundary

Routing owns URLs and route-level composition; Query owns async resource state, not form drafts or authorization policy. React Hook Form owns form state; Zod validates typed boundaries. Features retain client models/service interfaces. Create no product endpoints or role workflows during tooling setup. Production components do not branch on fixture/scenario flags. Mock composition must not silently fall back to a live provider.

## Implementation Sequence

1. Verify installed versions and package compatibility; install runtime/development packages with the app lockfile.
2. Move the minimal App entry into `src/app/App.tsx` and global CSS into `src/app/styles/`; retain `src/main.tsx` as the sole Vite mount. Configure the same source alias in TypeScript, Vite and test tooling.
3. Wire Tailwind's Vite plugin, minimal router and isolated Query provider. Preserve the placeholder's observable UI; no product feature or design baseline is implied.
4. Configure strict app/test TypeScript checking, ESLint/import restrictions, Vitest setup/cleanup and app-local scripts. Add helpers/configuration only when their intended caller exists.
5. Configure Playwright for the local app, deterministic test ports, ignored reports/traces/screenshots and a reproducible browser install command. Default smoke coverage uses Chromium; optional additional browsers follow the approved verification matrix.
6. Add meaningful tooling verification: route fallback/navigation behavior, provider/test isolation, form/schema integration using test-only fixtures, and persistent browser smoke assertions. Keep any setup probe controls outside production UI. Demonstrate mock isolation through a test seam, not a fake product feature.
7. Run lint, typecheck, tests, build and browser smoke; inspect the actual app screenshot and console/network output. Record exact results and any missing browser access. Update readiness only for gates evidenced by the completed setup.

## Verification and Completion

The implementation establishes app scripts for `lint`, `typecheck`, unit/component tests, production build and browser tests. A combined local verification script covers deterministic code checks; browser/runtime checks remain explicitly named so a missing browser install cannot silently skip them. Screenshot capture without image inspection is not a visual review. Automated accessibility checks supplement human review of later product journeys.

Add a scoped web CI workflow when local commands are proven, with secret scan, lockfile installation, code checks and browser smoke/artifact upload. Preserve existing backend/worker CI. CI green does not approve a platform UI baseline or capability bundle.

Document Node/package requirements, installed version sources, commands, mock/test boundaries, browser prerequisites and verification results in the web guide. Unavailable checks are pending rather than passing. Maintain capability lifecycle `DRAFT`; once tooling is proven the next unmet gate is the unapproved platform design foundation, not implementation approval.

## Human Gates and Delivery

Approve package/configuration scope before implementing this plan. Routine configuration and verification within that scope proceed without per-file gates. Product/design/material architecture changes follow their owning approval process. No push or deployment is part of configuration work unless requested. After setup verification, invoke `client-foundation web` to propose the platform design direction and representative interactive prototype for human approval.

## Primary References

- [Tailwind with Vite](https://tailwindcss.com/docs/installation/using-vite)
- [React Router Declarative Installation](https://reactrouter.com/start/declarative/installation)
- [TanStack Query Installation](https://tanstack.com/query/latest/docs/framework/react/installation)
- [React Hook Form Resolvers](https://react-hook-form.com/advanced-usage/custom-hook-with-resolver)
- [Vitest Guide](https://vitest.dev/guide/)
- [Playwright Installation](https://playwright.dev/docs/intro)
- [Playwright Agent CLI](https://playwright.dev/agent-cli/installation)
