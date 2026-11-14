# Verification: Web Access

- **Mode:** `MOCK`
- **Implementation Evidence Revision:** 1, 2026-11-15
- **Approved Dependencies:** Spec 1, prototype 2, tasks 1, UI baseline/foundation 6, experience/inventory 1
- **Technical State:** Automated checks passed; required human checks below remain pending
- **Human Mock Acceptance:** Pending; no API integration or production deployment is asserted

## Implementation and Entry

The Vite placeholder has been replaced by React access views at clean BrowserRouter paths. App composition injects the identity fake and coordinates Query cleanup; identity owns models, operation generations, validation, forms and lifecycle. The standard build has no review toolbar. Development adds a lazy-loaded toolbar outside product content, with deterministic scenario, clock, revocation and reset controls.

From repository root:

```powershell
cd apps/web
npm.cmd run dev -- --host 127.0.0.1
```

Open the URL printed by Vite (normally `http://127.0.0.1:5173/`), then choose Sign In. Stop the server with Ctrl+C. No backend, environment credentials or API service is required. The development toolbar is below the product footer; it is not part of product navigation. Standard `npm run build` / `npm run preview` omits that toolbar and still uses the MOCK adapter.

### Synthetic Review Accounts

All use the public example password `UniHubDemo1!` before replacement:

| Email | Role / Entry |
| ----- | ------------ |
| `linh@example.test` | Student → `/workshops` |
| `minh@example.test` | Organizer → `/organizer/workshops` |
| `an@example.test` | Staff → `/staff/workshops` |
| `new.organizer@example.test` | Organizer requiring temporary-password replacement |
| `new.staff@example.test` | Staff requiring temporary-password replacement |
| `unscoped@example.test` | Organizer fixture with invalid missing organization context |

Example permanent password: `NextWorkshop2!`. Use synthetic inputs only. A successful replacement persists its synthetic verifier for this tab, so subsequent login uses the new example password until Reset. No plaintext password or real token is persisted.

Choose a Service scenario **before** the action. Change back to Normal to retry. Advance clock +61s demonstrates renewal; Revoke Session demonstrates unusable session; Reset returns the local fake to its original state. Register, recovery and workshop destinations remain minimal named boundaries, not completed downstream capabilities.

## Commands and Outcomes

Commands run from `apps/web` unless otherwise noted. These results cover the implementation, not provider correctness.

| Check | Result / Target |
| ----- | --------------- |
| Test-first model/navigation | Initial targeted Vitest run failed because access model was absent; after implementation, intent/schema tests passed |
| Test-first lifecycle | Initial targeted fake/controller run failed because implementation was absent; subsequent tests passed |
| Regression for obsolete operation error | Observed rejection from canceled login in a failing test; guarded canceled error propagation and reran the same test successfully |
| Regression for malformed return intent | Three failing cases `%zz`, encoded NUL and invalid UTF-8; decode/validation correction made all three pass |
| `npm run verify:all` | PASS, exit 0: lint, strict typecheck, 46 Vitest tests, production build and 54 Playwright tests (final browser phase 52.9 seconds) |
| Unit/component/integration | 46 tests in seven files; schemas/intent, fake/controller, storage/clock/races, app/query isolation, entry forms and retained tooling checks |
| Browser | Current suites: `tests/journeys/app.spec.ts`, `dialog.spec.ts`, `session.spec.ts`; clean app paths against strict built preview on port 4173 |
| Responsive and axe | Six views × EN/VI × light/dark × 320/390/768/1440 = 96 cases per browser project, run under both projects; two additional dialog variants per project |
| Agent browser inspection | Installed Playwright CLI opened actual React build on loopback, saved snapshot, operated invalid credentials → successful login → confirmed logout/public entry, captured and viewed screenshot; zero page/console errors and zero external-origin requests observed in that replay |
| Guideline audit | Fresh official Web Interface Guidelines snapshot reconciled with approved baseline; see review record |
| Artifact checks | `git diff --check`, scope inspection and artifact link checks; no backend/workers/infra or approved spec/prototype implementation changed |

The two Playwright projects start at 1440×900 and 390×844. Matrix tests deliberately set the four approved widths at height 900. Screenshots use full-page capture. The installed CLI used a temporary config pointing at installed Chromium revision 1254, headless at 1440×1000, with output in OS temporary storage. No browser or dependency was installed for this build.

Some early sandbox browser processes could not finish clean teardown; their owned sessions were interrupted, and the complete configured verification was rerun outside the sandbox. Earlier failing/partial attempts are not counted as final passes.

## Acceptance-Criteria Evidence

| Criteria / Scenarios | Automated Evidence and Assertions | Human Portion |
| -------------------- | -------------------------------- | ------------- |
| AC-001/002; WA-01/02 | App component tests; browser entry, first-invalid focus, Email label and visible eye/eye-off state | Included in visual review; Edge native reveal check pending |
| AC-003/004; WA-03/04 | Three-role login and reload/logout journeys; invalid/inactive feedback; negative fake-operation tests | Role landing pages deliberately remain boundaries |
| AC-005/006; WA-05/06/07 | Temporary-role journey; exact schema rules; direct/reload credential scrubbing; immediate session | Compare temporary form to prototype 2 |
| AC-007; WA-08 | Fake single-use/lost-commit tests and browser recovery with new synthetic credential | Bilingual uncertainty copy review pending human acceptance |
| AC-008/009; WA-09/10 | Clock/renewal coordination tests; stored identity, expired/revoked outcome; browser reload/renewal/expiry suite | No real business TTL or provider renewal claim |
| AC-010; WA-11/12 | Five typed failure journeys, rate button blocked during delay, browser offline logout; offline restoration and explicit retry scenario | Offline app-shell availability excluded |
| AC-011; WA-13 | Role guard matrix and denied screenshot; malformed/external/auth-loop intent tests; unknown route recovery | Missing organization is distinct from wrong-role student |
| AC-012/013; WA-14/15 | Confirmed logout clears session and reaches public entry; lost response keeps reload lock; retry completes | Other tabs/devices are not claimed signed out |
| AC-014; WA-16 | Local removal confirm/cancel/Escape/focus and accurate feedback; two dialog axe/overflow/position checks | Native assistive-technology dialog review pending |
| AC-015; WA-17 | Deferred old login result and old error discarded; coordinated restore; actual composition test removes seeded private Query projection | No downstream resource capability was built for the test |
| AC-016; WA-18 | Register boundary → login → reload → allowed query intent; no business mutation auto-submitted | Recovery/register operations remain excluded |
| AC-017; WA-20 | Deterministic fake storage/reset/delays; no stored plaintext password; CLI observes no external requests; review toolbar compiled out of standard build | Public fake inputs only; no real credentials |
| AC-018; WA-20 | Malformed/version-invalid/unknown identity and throwing storage tests; browser corrupt state and independent tab scenario | Browser duplicate/reopen behavior is not remote revocation evidence |
| AC-019; WA-19 | 96-case matrix per browser project plus two dialogs, keyboard/focus and actually viewed screenshots | Actual browser 200% zoom/reflow and screen reader still pending |
| AC-020; WA-08/15/16 | Typed/localized uncertain password, logout and local-removal actions; viewed local-removal dialog | Human review of both locales pending acceptance |

### Actually Viewed Screenshots

- CLI `final-react-login.png`: English/light desktop; shorter Email label, approved artwork, typography and footer placement.
- `temporary-vi-dark-390.png`: long Vietnamese temporary-password requirements and responsive fields.
- `logout-en-light-1440.png`: locked state, absent account menu, distinct Retry/local removal actions.
- `denied-vi-dark-390.png`: student permission error and permitted destination, not a false missing-organization message.
- `not-found-vi-dark-390.png` and `register-en-light-1440.png`: recovery/boundary layouts.
- `local-removal-vi.png`: narrow dark confirmation dialog, reviewed before and after the Tailwind centering correction.

Screenshots and traces are disposable evidence under the configured ignored Playwright output directory or OS temp. This record plus executable tests is the durable reproduction record; no golden images were approved or generated automatically as an acceptance substitute.

## Required Human Checks and Acceptance Preparation

| Check | Steps / Expected Result | Status / Owner |
| ----- | ----------------------- | -------------- |
| Browser zoom | In a real desktop browser at 200%, operate login, temporary-password and local-removal flow; no clipping, overlapping controls or unreachable actions | NOT_RUN; human reviewer |
| Screen reader | NVDA + Chromium (or equivalent actual AT): labels/errors/status, first-invalid focus, locale popup, logout lock and dialog name/description/focus return are understandable | NOT_RUN; human reviewer |
| Edge reveal | Enter a password in Edge, check only custom visibility button appears and toggles its visual/accessibility state | NOT_RUN in Edge; Chromium custom toggle tests passed |
| Human MOCK review | Exercise three roles, initial password replacement, errors/retries and local logout distinction against spec 1 / prototype 2 / UI 6 | Pending explicit human decision |

No native/mobile evidence is applicable. Pending human checks cannot be converted into passing results by axe, viewport emulation or tests. If those checks reveal in-scope defects, correct through the authorized client-fix path and rerun affected evidence. Material behavior/global UI changes require owning approval.

## Delivery State

Implementation slices WA-T01–WA-T05 have code and automated evidence. WA-T06 has technical review and acceptance preparation; its mandatory human checks remain pending. Keep lifecycle `VERIFICATION_PENDING` and do not claim `VERIFIED` or `ACCEPTED` until all applicable required evidence and human decisions are recorded. No next capability, real integration or publication is authorized by this record.

## Technical Review

The full six-axis findings report is local `review-notes.md`, ignored by the repository's `specs/**/review-notes.md` rule. It was not force-added. This retained summary records the review and disposition without depending on an ignored file in published documentation.

| Axis | Review Outcome |
| ---- | -------------- |
| Correctness | Fixed fresh-session expiry copy, canceled-operation errors and explicit public navigation after logout; red/green regression and browser evidence recorded above |
| Design | App composition selects the fake; public feature surfaces and shared dependency direction pass ESLint; no provider/internal imports; DEV review toolbar is lazy and absent from standard build |
| Security | Malformed encoded intent/control characters rejected; transient secrets, safe storage fallback, immediate locks and account/query isolation checked; no real provider authorization asserted |
| Performance | Coordinated renewal, no automatic mutation retries, countdown updates only while active; no new dependency; observed output about 450 kB JS / 140 kB gzip and 16 kB CSS / 5 kB gzip |
| Coverage | 46 unit/component/client-internal tests and 54 persistent browser tests; real zoom, AT and Edge-specific review still pending |
| Style/Visual | Footer/root flex layout, heading weight and dialog centering corrected; actual screenshots inspected against prototype 2 and UI 6 |

Resolved should-fix findings WR-01/02/03/04/06/07 concern the fixes above. No unresolved critical/should-fix implementation finding remains from this self-review. WR-05 records pending human evidence; there is no independent maintainer or human MOCK acceptance claim.

Official guideline snapshot: `https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md`, fetched 2026-11-14T19:05:00Z; SHA-256 `d246b026f4f29b5823a9cc857f9edf3d2507002e055e32040cadeaf3b38e0234`. Approved app rules override upstream automatic locale detection and Title Case advice. No extra product scope/library was inferred from the audit.
