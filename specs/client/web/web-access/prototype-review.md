# Prototype Review: Web Access

> **Platform/Mode:** Web / `MOCK`
>
> **Prototype Revision:** 2
>
> **Entry Point:** `prototype/index.html`; `http://127.0.0.1:4181/`
>
> **Spec/UI Revisions:** Approved `SPEC.md` revision 1; experience/inventory revision 1; UI baseline and foundation prototype revision 6
>
> **Review/Approval:** Human approved prototype revision 2, including the email-label/password-toggle/cache updates, on 2026-11-15 against spec revision 1 and UI baseline revision 6. Capability bundle approval, implementation and mock acceptance remain pending.

## Artifact Scope

This is the capability-owned interactive preview for WS-01, separate from the approved platform foundation. It covers public sign-in entry, a shared login form, required temporary-password replacement, role-aware navigation, restoration/renewal, expiry/revocation, denied/not-found routes and recoverable logout. All accounts, organizations, credentials and session outcomes are synthetic.

Global design authority remains [Web UI Design](../../../../docs/client/web/ui-design.md) revision 6. The preview references `WEB-UI-001/002/003/005/006/007/009/010/011/012/013`; it changes no global rule or foundation file. The login composition uses the approved cobalt/lime artwork, system typography, semantic colors and input/button patterns. The identity and decorative artwork SVGs are unchanged local copies from the approved foundation assets.

The client route names follow the [Approved Spec](SPEC.md) revision 1. Hash routing is a preview transport so a standard static HTTP server can reload any view: `/login` is `#/login`, for example. This does not change the production route decision.

Register, recovery, workshop and staff destinations are named handoff boundaries with navigation only. Their review status is identified in the separate review toolbar. No registration, recovery, workshop data, payment or staff operation is implemented. The account menu demonstrates identity and Sign Out; profile/security/notification experiences remain with their owning capabilities.

## Preview Setup

From repository root, run:

```powershell
python -m http.server 4181 --bind 127.0.0.1 --directory specs/client/web/web-access/prototype
```

Open `http://127.0.0.1:4181/` or `http://127.0.0.1:4181/#/login`. Use Ctrl+C in the serving terminal to stop. No React build, backend, credentials, package installation or external service is needed. After editing preview assets, hard-reload to avoid reviewing an older cached stylesheet/script.

### Files and Responsibilities

| File                                    | Responsibility                                                                                                |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `prototype/index.html`                  | Semantic shell, isolated review controls, locale popup, confirmation dialog and entrypoint                    |
| `prototype/styles.css`                  | Baseline-aligned tokens, capability-local forms/panels, responsive states and dark variants                   |
| `prototype/app.js`                      | Rendered views, form validation, safe intent registry, UI events and obsolete-operation rejection             |
| `prototype/service.js`                  | Local asynchronous fake, synthetic session clock, outcomes, tab persistence and temporary-password transition |
| `prototype/fixtures/scenarios.json`     | Accounts, fixture credentials, timing values and named service outcomes                                       |
| `prototype/fixtures/en.json`, `vi.json` | Equal product-copy coverage; authored field values are not translated                                         |
| `prototype/assets/`                     | Approved local SVG identity and decorative artwork copies                                                     |
| `prototype/review-checks.js`            | Repeatable UI interaction/assertion replay via the installed Playwright CLI                                   |
| `prototype/visual-checks.js`            | Repeatable six-view locale/theme/viewport axe and overflow matrix                                             |

### Scenario Selection and Reset

- Use **Sample account** to select a fixture; selecting it resets this tab's synthetic session and opens login. Expand **Review credentials & boundaries** for its email and public example password. This selector never signs in or grants a role.
- Use **Service scenario** before an operation. Changing it preserves current review state so selecting **Normal** can demonstrate recovery. Failure controls are not product controls.
- **Reset** restores the selected fixture, normal service outcome, original clock, anonymous state and unused temporary-password status. It clears synthetic activation verifiers as well as sessions in this tab. It does not change other tabs or real accounts.
- **Advance clock +61s** demonstrates access expiry with renewal. **Revoke session** demonstrates a confirmed unusable session. The fixture clock/timing values are review values, not proposed business TTLs.
- Example login password: `UniHubDemo1!`; new-password example: `NextWorkshop2!`. These are public synthetic inputs, not real credentials. `linh@example.test`, `minh@example.test` and `an@example.test` represent student, organizer and staff. `new.organizer@example.test` and `new.staff@example.test` require initial replacement.
- JSON fixtures are loaded once. Subsequent operations run locally; there are no auth HTTP requests. The fake checks browser connectivity and also has deterministic offline/unavailable outcomes. Browser Offline during an already loaded page can be reviewed; full offline app-shell reload is outside scope.
- Reload preserves only this tab's versioned synthetic scenario/session/activation state. Passwords stay out of URL/history/storage; only a non-security digest of the synthetic replacement input is retained for fake credential matching after reload. Never enter real credentials. Separate tabs are independent; browser duplicate/reopen behavior is not a service-revocation guarantee.

## Scenario Coverage

All rows use `prototype/fixtures/scenarios.json` plus EN/VI copy files. Default fake latency is 450 ms; `slow` is 2.5 seconds, and `rate` has a three-second review countdown. Every row can be replayed without server APIs. The interaction replay contains real assertions; boundary headings do not establish downstream capability implementation.

| Screen/Scenario | Fixture Setup                                                                     | Interaction and Expected Outcome                                                                                                                       | Viewport                                       |
| --------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------- |
| WS-01 / WA-01   | `student`, `normal`, public `/`                                                   | Sign In opens common form; no role picker                                                                                                              | Desktop/narrow                                 |
| WA-02           | `student`, `normal`                                                               | Submit empty/invalid form, correct fields; focus first invalid field, toggle password by keyboard/mouse                                                | Desktop/narrow                                 |
| WA-03           | `student` / `organizer` / `staff`, `normal`                                       | Fill actual login fields; one pending submit; role-specific landing and reload persistence                                                             | Desktop; organizer also 390 px                 |
| WA-04           | `invalid` or wrong input; `disabled`                                              | Generic credentials error clears secret; disabled account never opens protected view                                                                   | Desktop                                        |
| WA-05           | `new-organizer` / `new-staff`, `normal`                                           | Login gates to temporary replacement with email/transient credential carried forward                                                                   | Desktop/narrow                                 |
| WA-06           | Temporary account, `normal`                                                       | Invalid complexity, equal/mismatched passwords show associated errors; reload requires temporary credential again                                      | Desktop/narrow                                 |
| WA-07           | Temporary account, `normal`                                                       | Valid replacement enters role area immediately; new fake credential works after reload/logout                                                          | Desktop                                        |
| WA-08           | Temporary account, `lost-password`                                                | Change commits with lost response; uncertainty offers login with new password; no auto replay                                                          | Desktop                                        |
| WA-09           | Authenticated account, `normal`, advance clock                                    | Locked checking/renewal state then same identity; session restored on reload                                                                           | Desktop                                        |
| WA-10           | Authenticated account, revoke control                                             | Login explanation and safe destination; old identity no longer displayed                                                                               | Desktop                                        |
| WA-11           | `offline` / `unavailable`; real browser Offline                                   | Login/restore/renew unavailable state; selecting Normal and explicit retry recovers; real offline logout remains locked                                | Desktop                                        |
| WA-12           | `rate`                                                                            | Disable submission during displayed delay; no automatic login; then explicit retry                                                                     | Desktop                                        |
| WA-13           | Student to protected organizer route; `missing-org`; unknown route; unsafe `next` | Correct denied/missing-organization/not-found cases; reject external intent; preserve registered inert `q` context                                     | Desktop; denied and not found also four widths |
| WA-14           | Authenticated account, `normal`                                                   | Account → Sign Out locks immediately, clears local session after confirmation                                                                          | Desktop                                        |
| WA-15           | Authenticated account, `logout-failure` / `logout-lost`                           | Unconfirmed locked view persists through reload; retry after committed/lost result completes cleanup                                                   | Desktop/tablet/narrow                          |
| WA-16           | Unconfirmed logout                                                                | Open local-removal dialog, Escape/cancel returns focus; confirmation removes only local access and reports accurately                                  | Desktop/tablet/narrow                          |
| WA-17           | `slow`; navigate away during login                                                | Old result cannot establish session after navigation; no previous identity reappears                                                                   | Desktop                                        |
| WA-18           | `normal`, login                                                                   | Open register/recovery boundaries and return to shared login; no adjacent operation                                                                    | Desktop; register also four widths             |
| WA-19           | EN/VI, light/dark                                                                 | Preserve authored input when changing locale; English on reload; keyboard access, focus, dialog behavior and named navigation                          | 320/390/768/1440 px                            |
| WA-20           | `normal` / `storage`; reset; fresh independent tab                                | Reload preserves permitted fake state; simulated persistence failure warns and does not restore old identity; reset and independent tabs stay isolated | Desktop                                        |

Empty login results, camera permissions, native queues and actual offline app-shell availability are Not Applicable. Browser zoom and assistive-technology checks remain pending below. The source handles malformed stored state defensively; deliberately corrupt-storage and browser duplicate/reopen tests are not claimed as completed.

## Visual and Interaction Evidence

### Target and Commands

Verification target: localhost port 4181, prototype revision 1, approved spec revision 1 and UI baseline revision 6. Browser: installed Playwright Chromium 158 / revision 1254; app-local Playwright CLI 0.1.24. Runtime: Node 26.13.0 and Python 3.15.2. No dependency was installed or upgraded.

The CLI initially looked for unavailable system Google Chrome. The verified run used the already installed Chromium executable through a temporary CLI config with `browserName: chromium`, `headless: true`, `executablePath` pointing to `ms-playwright/chromium-1254/chrome-win64/chrome.exe` under the current user's LocalAppData, and viewport 1440×1000. Browser sessions, config, snapshots and screenshots were written to an OS temporary evidence directory.

Use the installed CLI executable under `apps/web/node_modules/.bin/playwright-cli.cmd`. From an OS temporary evidence working directory:

```powershell
# Resolve these absolute paths from repository root before changing directory.
$browserCli = (Resolve-Path apps/web/node_modules/.bin/playwright-cli.cmd).Path
$replay = (Resolve-Path specs/client/web/web-access/prototype/review-checks.js).Path
$matrix = (Resolve-Path specs/client/web/web-access/prototype/visual-checks.js).Path
$axe = (Resolve-Path apps/web/node_modules/axe-core/axe.min.js).Path
# Open the session using a local CLI config appropriate to the installed browser.
& $browserCli -s=web-access open http://127.0.0.1:4181/ --config=cli.config.json
& $browserCli -s=web-access --raw run-code --filename=$replay
```

Before the matrix, use a temporary `run-code --filename=init-axe.js` function that calls `page.context().addInitScript({path: <resolved $axe path>})`. Then run `run-code --filename=$matrix` in the same session. `visual-checks.js` uses that local axe runtime; it does not download a script or package. Both replay files operate the visible controls and assert named outcomes. They are prototype review checks, not production test suites.

### Results

| Check                               | Result and Limits                                                                                                                                                                                                                                                                             |
| ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| JavaScript syntax                   | `node --check` for `app.js`, `service.js`, `review-checks.js` and `visual-checks.js`; PASS                                                                                                                                                                                                    |
| Fixture consistency                 | EN/VI key parity, valid JSON, scenario/account references and unchanged SVG copies; PASS                                                                                                                                                                                                      |
| Interaction replay                  | WA-01–WA-20 scenario families exercised with the specific assertions recorded in `review-checks.js`; PASS for recorded assertions only                                                                                                                                                        |
| Browser/network                     | Replay observed no page/console errors or requests to external origins; JSON/assets served locally                                                                                                                                                                                            |
| Responsive/axe matrix               | Login, temporary replacement, unconfirmed logout, denied, not found and register boundary × EN/VI × light/dark × 320/390/768/1440 px; 96 combinations plus two local-removal dialog checks (EN/light/1440 and VI/dark/320), zero applicable axe violations and zero page horizontal overflows |
| Keyboard                            | Tab through login fields/password toggle/submit, keyboard activation, skip-to-main, dialog Escape/focus restoration and narrow organizer menu assertion; PASS for these interactions                                                                                                          |
| Production checks                   | Not run: no app source, dependencies or runtime configuration changed; this evidence establishes no production capability behavior                                                                                                                                                            |
| Browser zoom / assistive technology | NOT_RUN; actual browser 200% zoom and screen-reader review remain for human review/planned capability verification                                                                                                                                                                            |

### Viewed Screenshots

Screenshots were actually opened and inspected, not merely generated. The final visual set covers:

- `login-en-light-1440.png`: full artwork, balanced form, approved identity, spacing and short-page footer.
- `temporary-vi-dark-390.png`: long Vietnamese heading/password help, labeled fields and primary action without clipping.
- `logout-en-light-768.png`: explicit unconfirmed state, Retry and secondary local removal, no private account control.
- `denied-vi-dark-320.png`: readable permission message and permitted destination; distinct from missing organizer context.
- `not-found-vi-light-390.png`: localized recovery action and footer position.
- `register-en-dark-1440.png`: deliberately minimal adjacent boundary with return navigation.
- `local-removal-dialog.png`: confirmation wording distinguishes local removal from service revocation; named dialog and explicit cancellation.
- `local-removal-vi-dark-320.png`: wrapped dialog title/body, aligned close target and usable localized actions at the narrowest viewport.
- `organizer-menu-390.png`: retained role shell with a usable expanded narrow menu and scoped identity.

Screenshots/snapshots are disposable local evidence, not committed golden images. This record plus the scripts/scenarios is the reproducible evidence; temporary files are not a durable evidence location. No screenshot baseline or human design approval is inferred.

## Findings, Revisions and Approval Scope

| ID    | Finding                                                                            | Resolution                                                                                                                          |
| ----- | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| WP-01 | Intro artwork clipped because intrinsic image height did not follow rendered width | Fixed aspect handling; inspected full artwork in final desktop login screenshot                                                     |
| WP-02 | Credentials failure was announced twice via alert and separate live region         | Removed duplicate error announcement; replay checks the visible failure and recovery                                                |
| WP-03 | Dark secondary local-removal button inherited dark primary text                    | Added scoped secondary color override; verified computed style/current stylesheet and repeated matrix with zero contrast findings   |
| WP-04 | Hash-router skip link could be interpreted as a route                              | Keep route unchanged and focus main explicitly; keyboard replay asserts focus                                                       |
| WP-05 | Wrong-role student navigation incorrectly described missing organization           | Limit missing-organization feedback to scoped organizer/staff identities; replay and viewed denied screenshot distinguish the cases |
| WP-06 | Dialog close target sat above the first title line                                 | Aligned the 44 px target to the heading; inspected EN desktop and VI 320 px dark screenshots and checked both with axe              |

Prototype revision 1 browser evidence is retained above. Human approved the current prototype as revision 2 on 2026-11-15; the MOCK task plan does not yet exist and capability lifecycle/readiness remains `DRAFT` / `WAITING_BUNDLE`. The accepted spec and global UI baseline remain unchanged. Requested visual/flow corrections can be made in this prototype scope; material spec/global UI changes require the owning approval process. No plan, production build, real integration, push or human acceptance is implied by this review.

## UI Feedback Update ? 2026-11-15

Changed the English email field label to Email, suppressed the native Edge password reveal control, and added distinct eye/eye-off SVG states to the existing accessible visibility toggle. Updated replay locators for the shorter label. Earlier browser evidence above predates these small changes; this update has syntax validation but has not yet been replayed in a browser.

Installed `lucide-react` 1.73.0 in `apps/web` for the future React build; package and lockfile are updated. This plain HTML prototype retains local SVG markup and does not require React. Bundle approval remains pending.
`npm run verify` after dependency installation: PASS (lint, typecheck, 17 tests and production build). Prototype JavaScript syntax and `git diff --check` also passed; browser replay for this UI feedback remains pending.

## Prototype Revision 2 Approval

Human approved the current prototype on 2026-11-15 after reviewing the UI feedback updates. Revision 2 includes the Email label, native reveal suppression, eye/eye-off toggle and fresh translation loading. Revision 1 automated browser evidence remains historical; revision 2 has syntax/diff checks and the recorded human design decision, without a claimed fresh full browser replay. The MOCK task plan and coherent bundle approval remain pending.
