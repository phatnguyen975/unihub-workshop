# Web Tooling

## Scope and Runtime

App-local setup for the Vite/React SPA under the approved [Tooling Plan](tooling-plan.md). The UI foundation and product capabilities require separate approval. Node 24 LTS is the CI baseline (`apps/web/.node-version`); Node 26 is also allowed by the app engine range and was used for local verification. Use `npm ci` from `apps/web/` after checkout. Keep generated dependencies/build/evidence out of Git. On PowerShell, use `npm.cmd`/`npx.cmd` when script execution policy blocks `.ps1` wrappers.

## Package Baseline

These are the versions verified at setup; the lockfile owns exact dependency resolution. Refresh this record when the baseline changes.

| Package                 | Verified Version |
| ----------------------- | ---------------- |
| `react`                 | `19.3.1`         |
| `react-router`          | `8.5.1`          |
| `@tanstack/react-query` | `5.107.1`        |
| `react-hook-form`       | `7.92.0`         |
| `zod`                   | `4.6.7`          |
| `@hookform/resolvers`   | `7.0.5`          |
| `tailwindcss`           | `4.3.5`          |
| `vitest`                | `5.1.0`          |
| `@playwright/test`      | `1.65.0`         |
| `@playwright/cli`       | `0.1.24`         |

## Commands

| Command                           | Purpose                                                        |
| --------------------------------- | -------------------------------------------------------------- |
| `npm run dev`                     | Local Vite server                                              |
| `npm run lint`                    | ESLint and source import boundaries                            |
| `npm run typecheck`               | Strict app/config/browser-test TypeScript checks               |
| `npm test`                        | Vitest unit/component/boundary checks; fails if no tests exist |
| `npm run test:watch`              | Iterative local Vitest feedback                                |
| `npm run build`                   | Typecheck and production Vite build                            |
| `npm run preview`                 | Serve the built SPA locally                                    |
| `npm run browser:install`         | Install Playwright Chromium for browser verification           |
| `npm run test:e2e`                | Chromium journey/accessibility checks against built preview    |
| `npm run test:e2e:ui`             | Interactive browser-test UI                                    |
| `npm run browser:agent -- --help` | App-local installed agent browser CLI                          |
| `npm run verify`                  | Lint, typecheck, unit/component tests and build                |
| `npm run verify:all`              | All code checks plus browser smoke                             |

Run `npm run build` before standalone `test:e2e`; `verify:all` already does so. Browser checks start their own production preview on `127.0.0.1:4173` with strict port allocation and no server reuse. Stop conflicting processes or select an explicitly coordinated test configuration; do not silently test another app. Chromium runs at 1440x900 and 390x844. The smaller browser viewport does not certify native mobile behavior. Test reports, traces and screenshots are ignored under `test-results/` and `playwright-report/`.

## Source and State Ownership

`src/main.tsx` mounts `app/App.tsx` and app styles. `app/routes/` owns route composition/fallbacks; `app/providers/` supplies one Query client per app instance. Form draft state belongs to React Hook Form, schemas to their eventual feature owner and async service state to Query. Avoid competing route-loader caches or application-wide form stores. Queries and mutations default to no automatic retries; a feature may explicitly choose retry semantics only when its approved service/contract behavior is safe. Session-scoped cache keys and logout cleanup belong to future identity/resource capabilities, not the placeholder.

The default production UI remains UniHub Web. Tailwind is installed through the Vite plugin; its utilities do not approve design tokens or a UI library. No product API adapter, credentials, endpoint or active feature mock is created by setup. Future features inject deterministic service fakes through app composition, keeping data adapters away from view imports. Test-only form/service probes demonstrate schema resolution, async loading/failure and isolation without adding product flows or network endpoints.

## Import Boundaries

ESLint enforces source direction for static imports, re-exports and literal lazy imports: shared cannot import app/features, features cannot import app or one another, and source modules cannot reach outside web source into another app/repository internals. App composition consumes feature public exports or explicit `features/<feature>/data/{mock,http}/index.ts` adapter factory entrypoints. The tested local rule is in `tooling/eslint-boundaries.js`. Keep feature-internal imports relative and public composition imports explicit; broad barrels and computed module paths must not bypass review.

## Verification Evidence

Local setup verification: `lint`, `typecheck`, 17 unit/component/boundary tests, build and 2 Chromium smoke tests passed. Browser smoke verifies the heading/title, no horizontal overflow, Tailwind output, accessibility scan, keyboard navigation from unknown route, reload and absence of page/console errors or external requests. Screenshots from both viewports were actually inspected; they show only the centered heading on the home page. Initial unknown-route behavior was observed failing before routing was implemented. This evidence covers tooling/placeholder behavior, not product UX or API conformance.

The app-local agent CLI was also exercised in its own browser session: open the app, inspect its UI snapshot, click the actual fallback return link, capture/view the home screenshot and inspect console output (zero messages). The session and preview were closed after verification. A blank inline favicon prevents a default favicon request from generating a 404. No design golden screenshots are approved by these checks.

## CI

`.github/workflows/ci-web.yml` is called by the PR orchestrator when web or shared CI paths change; it also runs directly for matching pushes to main and manual dispatch. It performs repository Gitleaks scanning, `npm ci`, `verify`, installs Chromium and runs production-preview E2E/accessibility checks. Browser evidence is uploaded for seven days. Jobs have timeouts and minimal permissions; no backend service or deployment is required. [CI Configuration](../../../.github/README.md) owns trigger selection and the required `CI Gate` branch protection check.

## Dependency Audit

At setup, `npm audit --omit=dev` reported zero production findings. Full audit reported ten moderate findings propagated from the existing lint dependency chain through minimatch 10.2.6 (GHSA-g3hv-75wg-m249). Registry suggestions involve major/downgrade changes; no forced audit fix, cross-major override or security-check suppression was applied. Review upstream compatible fixes in a separately scoped dependency update. This is an explicit pending development-tool advisory, not a passing full audit.

## Development Ports

Vite development normally uses port 5173. Playwright uses an isolated production preview on 4173 so browser smoke verifies the built application rather than an unrelated development server. It starts and stops that preview automatically. Strict port binding and disabled server reuse prevent silent target substitution; close a conflicting process before retrying.

## Editor TypeScript Diagnostics

`compilerOptions.types` includes `vite/client`, the Vite-supported client declaration entry. App-local TypeScript resolves it from `apps/web/node_modules/vite/client.d.ts`; it is not a JavaScript module to load with Node `require`. If the editor reports that it cannot find this entry while `npm run typecheck` passes, check the editor's active TypeScript version/project and restart its language server. Use the workspace TypeScript installation under `apps/web/node_modules/typescript`; opening `apps/web` as an editor workspace folder can help an editor that only searches the monorepo root. In VS Code, use TypeScript: Select TypeScript Version and TypeScript: Restart TS Server; reload the window if diagnostics remain stale. Run `npm ci` in the app if dependencies are absent. Do not remove Vite types or add a machine-specific typeRoots path to suppress a diagnostic.

## Versioned Configuration and Ignored Output

Commit `.node-version`: CI reads it to select Node 24, and compatible version managers can reuse it. It does not switch the current terminal's Node automatically. Commit package/lockfile, Vite/Vitest/Playwright/TypeScript/ESLint configurations and the tested boundary rule. Ignore dependencies, build output, browser sessions/reports/traces/screenshots, coverage and real environment files. Build-info files are written under ignored `node_modules/.tmp/`. An IDE-only setting is optional and is not required by runtime or CI.
