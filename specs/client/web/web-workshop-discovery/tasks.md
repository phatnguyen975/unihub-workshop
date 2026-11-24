# Task Plan: Workshop Discovery and Details

> **Capability:** `web-workshop-discovery`<br>
> **Platform/Mode:** Web / `MOCK`<br>
> **Status:** `APPROVED`<br>
> **Revision:** 1<br>
> **Bundle:** [Approved spec revision 1](SPEC.md), [approved prototype revision 3](prototype-review.md), this task plan revision 1<br>
> **Platform Dependencies:** Web experience map and delivery inventory revision 1, web UI baseline and foundation prototype revision 6 with the approved React access shell addendum<br>
> **Approval:** Human approved this coherent Web / `MOCK` bundle on 2026-11-25. Capability lifecycle/readiness are `APPROVED / READY_FOR_BUILD`.

## Dependency Order

The [Web Delivery Catalog](../../../../docs/client/web/feature-delivery.md) lists no hard capability prerequisite. Discovery will reuse the implemented shell and sign-in handoff from `web-access`, spec 1 / prototype 2 / tasks 1, accepted MOCK code `72d1160`. The [access acceptance record](../web-access/verification.md#human-mock-acceptance-2026-11-16) is historical evidence. Reverify the touched access flows against the new candidate. Workshop publishing/search provider data and coordinated registration fixtures are data relationships. Free and paid registration are downstream consumers.

[Web Source Structure](../../../../docs/client/web/source-structure.md) at the current baseline `e483d71` assigns composition to `src/app/` and discovery behavior to the cohesive `src/features/workshops/` owner. App composition injects the workshop fake and owns route wiring and review controls. `workshops` owns public client projections, criteria, cursor semantics, read contracts, fake data and feature views. Identity continues to own session and safe access behavior. Use route-specific validation through an app-owned composition seam or existing allowed public exports; avoid a dependency from identity internals to workshops internals. Shared UI, locale and theme remain reusable. Follow the existing ESLint source boundary policy.

Execute WD-T01 → WD-T02 → WD-T03 → WD-T04 → WD-T05. File paths below are relative to `apps/web/` unless stated otherwise. Check off each slice after its required evidence is recorded. Use `verification.md` in the capability bundle for exact code candidate, revision, commands, findings, screenshots inspected and pending checks.

## Vertical Slices

- [ ] **WD-T01 — Open and browse the public catalog.**
  - **Depends on:** Approved bundle.
  - **File scope:** `src/features/workshops/` public exports, summary model, read interface, deterministic mock adapter, catalog page and UI; `src/app/routes/AppRoutes.tsx` and app service composition; scoped copy/styles and `tests/journeys/`.
  - **Acceptance:** Home and Discover open `/workshops` within the existing shell. Published records expose title, event date/time, place, price and seats. The manually controlled featured carousel uses up to three ordered public fixtures, labeled controls and a position indicator. Omit carousel when none are available. Loading, globally empty and transient list failure have recoverable presentation. Covers the catalog portion of AC-001, AC-002 and AC-008 / WD-01 and WD-07.
  - **Test-first:** Assert public-only stable results and carousel eligibility and bounds before implementing reads/interaction. Keep fixtures and delay/failure injection deterministic.
  - **Verification:** Run `npm test` for the slice and persist a browser journey for home → Discover, carousel keyboard operation and an empty or failed read. Inspect catalog against prototype revision 3.

- [ ] **WD-T02 — Search, filter and navigate results by cursor.**
  - **Depends on:** WD-T01.
  - **File scope:** Workshops criteria, URL and cursor handling, read service/fake, catalog controls, scoped locale/style changes and journey tests.
  - **Acceptance:** Combine text, event-local inclusive dates and price. Validate long input, normalize reversed/same-day selections and leave incomplete selections unapplied. Clear individual criteria or reset after no matches. Use two equal 44 × 44 px labeled arrow controls with inactive endpoints, stable filter-bound keyset cursors and Previous/Next behavior. Reload restores supported criteria and cursor; stale or mismatched cursor keeps valid criteria and returns to the initial results. A changed filter starts a new result set. Retry keeps its inputs and late reads cannot overwrite the active view. Covers AC-003, the catalog portion of AC-004 and AC-008 / WD-02, WD-03 and WD-07.
  - **Test-first:** Exercise the 200-character boundary, whitespace, date boundary across UTC and event-local days, tie ordering, cursor invalidation and page limits. Use controlled deferred completions for stale responses rather than timing-based pass criteria.
  - **Verification:** Run `npm test`; replay search, range and price selection, empty reset, forward/back across three result sets and invalid cursor recovery in Chromium.

- [ ] **WD-T03 — Open public details and return to the same results.**
  - **Depends on:** WD-T02.
  - **File scope:** Workshops detail projection/read, detail page and local brochure asset; app route composition; workshop unit/component and browser tests.
  - **Acceptance:** List cards and featured items open `/workshops/:slug`. Direct and reloaded detail links work. Free, paid and full share the approved layout, with descriptions, speaker information and brochure only where present. Missing and nonpublic slugs use equivalent unavailable feedback. Retry reuses the slug. Browser Back and the in-page return action recover the originating catalog criteria and cursor; direct detail entry has a safe catalog return. Covers AC-001, AC-004–006, detail portion of AC-008 / WD-03–05 and WD-07.
  - **Test-first:** Assert equivalent unavailable outcomes, optional-field handling and retry with the same identifier. Cover removal of stale detail data when a newer route wins.
  - **Verification:** Run `npm test`; replay direct and card entry, brochure view/download, full and optional-content cases, retry and catalog restoration. Inspect detail at narrow and wide widths.

- [ ] **WD-T04 — Resume workshop details after sign-in.**
  - **Depends on:** WD-T03.
  - **File scope:** Workshops detail CTA/state, app route composition, narrow existing identity validator/call-site changes where needed, access regression tests and cross-feature browser tests.
  - **Acceptance:** Guests on an available workshop can sign in and return to the same validated slug, including after required temporary-password replacement. Signed-in students see the registration-unavailable message. Nonstudent roles receive no student registration action; full workshops show the full state. A lost session leaves the public route usable. Reuse the accepted access form and avatar/menu, with sign-out returning the viewer to public behavior. Covers AC-007 / WD-06 and the approved prototype revision 3 header.
  - **Test-first:** Extend intent tests for the exact detail route, supported catalog criteria, wrong-role and malformed/external intent. Cover session transition without triggering writes, plus guest, student, organizer and staff display. Preserve existing access role landing and authorized routes.
  - **Verification:** Run `npm test`; replay guest-to-student sign-in, temporary password, unsafe fallback, expiry and avatar popup open/dismiss/sign-out. Re-run existing access and shell suites affected by route changes.

- [ ] **WD-T05 — Verify the composed MOCK journey and prepare acceptance evidence.**
  - **Depends on:** WD-T01–04.
  - **File scope:** Discovery and related access journeys, scoped discovery UI fixes, app development review wiring and capability `verification.md`.
  - **Acceptance:** The composed journeys satisfy AC-001–010 at the approved revisions. Scenario selection/reset stays outside product content. Standard build retains discovery MOCK data access and excludes the development scenario control module. Both languages and themes preserve filter state and reflow with long content. Close technical findings within scope and record required human checks awaiting `client-accept`.
  - **Test-first:** Add a behavior regression for any in-scope defect where existing checks would miss recurrence. Use screenshot review for presentation and named keyboard/axe checks for accessibility.
  - **Verification:** Run `npm run verify:all` from `apps/web/`, inspect production preview console/network and built artifacts, review narrow/wide screenshots, and record the results. Use the six-axis technical review across correctness, design, security, performance, test coverage and style.

## Verification Matrix

**Required** checks establish technical or human evidence as specified. All rows start `NOT_RUN` for the implementation candidate. The static prototype replay is a design reference.

| Coverage              | Unit/Component                                                                      | Chromium and Artifact Checks                                                                                                                          | Human Evidence                                                                                                                              |
| --------------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| AC-001–002 / WD-01    | Required: published results, feature order/bounds, empty carousel                   | Required: entry, card facts, keyboard movement and position                                                                                           | Compare against approved banner/cards during visual review                                                                                  |
| AC-003–004 / WD-02–03 | Required: validation, event-local range, deterministic ordering and cursor recovery | Required: search/filter, no results/reset, previous/next at boundaries, Back, reload, filtered cursor invalidation                                    | Evaluate readable controls and result feedback                                                                                              |
| AC-005–006 / WD-04–05 | Required: detail projection, no private leakage, optional fields                    | Required: direct links, free/paid/full, brochure operations, equivalent unavailable state                                                             | Review detail and brochure presentation                                                                                                     |
| AC-007 / WD-06        | Required: exact intent validation and access behavior                               | Required: ordinary and temporary-password sign-in, expiry, role isolation, avatar and sign-out                                                        | Review wording and access handoff                                                                                                           |
| AC-008 / WD-03/05/07  | Required: isolated, resettable failures and controlled out-of-order reads           | Required: observable loading, empty/error/offline, retry with preserved criteria and obsolete result protection                                       | Review recovery copy                                                                                                                        |
| AC-009 / WD-08        | Required: labels, interaction states and focus behavior where component-testable    | Required: keyboard, axe, long EN/VI content, both themes, 320/390/768/1440 px and shell 650/651 px. Capture and actually view narrow/wide screenshots | Required: real browser 200% zoom and screen reader review on catalog/filter/detail, with result/error announcements and Escape/focus return |
| AC-010 / WD-01/07     | Required: deterministic mock usage and reset                                        | Required: lint boundary checks, standard build, browser request assertions and absence of review control code from emitted app assets                 | Automated evidence covers this check                                                                                                        |

From `apps/web/` use `npm run verify` for lint, typecheck, Vitest and production build, or `npm run verify:all` to add Playwright. Standalone browser replay uses a current build followed by `npm run test:e2e`. Use existing Chromium projects for baseline journeys and discovery viewport cases for the additional widths. If Chromium is unavailable, use the repo's `npm run browser:install` when authorized; if blocked, still run code checks and record the missing browser checks and steps for replay. If real zoom or assistive technology is unavailable, provide reproducible human review steps and retain `VERIFICATION_PENDING` until that evidence is supplied. Native device testing is inapplicable to this web-only scope.

## Risks, Exclusions and Approval-Sensitive Changes

- Keep filter/date/cursor validation consistent across service requests, URLs and history. Use complete criteria and cursor in the list query key, and slug in the detail query key. Prevent old completions from replacing current state. Keep synthetic cursor contents internal to MOCK.
- Keep the access path allowlisted and route-specific. Preserve existing role boundaries and regression-test public browsing across access transitions. Avoid coupling public discovery failures or data to authenticated cache state.
- Keep release asset/network checks separate from development scenario checks. Use synthetic assets and accounts. Restrict brochure URLs to approved local mock assets and preserve unavailable feedback for nonpublic fixtures.
- The contract has no brochure field and expects UTC bounds. These are tracked integration mapping gaps; this plan covers MOCK only.
- Exclude onboarding, booking, reservations, payment, tickets, publishing, real API, workers, infrastructure, broader shell redesign and platform-wide acceptance. Material behavior, design or scope changes require the owning approval. This plan requests bundle approval only; technical verification and human MOCK acceptance follow in their own stages.
