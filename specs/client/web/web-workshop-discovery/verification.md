# Verification: Workshop Discovery MOCK

> **Capability:** `web-workshop-discovery` · **Platform / mode:** Web / MOCK
> **Approved bundle:** spec 1, prototype 3, task plan 1 · **Candidate:** `feat/web-workshop-discovery`

## Slice Evidence

- **WD-T01 — Public catalog:** `npm run verify` passed with 59 Vitest tests and a production build. Chromium journeys covered Home → Discover, keyboard carousel controls, empty catalog, failed list read and retry.
- **WD-T02 — Search and filters:** `npm run verify` passed with 60 Vitest tests and a production build. Browser journeys covered text and price filters, reload restoration, stale cursor recovery, forward/back navigation and reversed inclusive dates.
- **WD-T03 — Public details:** `npm run verify` passed with 61 Vitest tests and a production build. Browser journeys covered direct detail entry, optional content, brochure links, equivalent unavailable states and return-link restoration. A service regression test verifies retry uses the same slug.
- **WD-T04 — Sign-in return:** `npm run verify` passed with 62 Vitest tests and a production build. Five targeted Chromium journeys covered student return, temporary-password replacement, organizer/staff access, full workshops and continued public access after session expiry.
- **WD-T05 — Composed journey:** all five tasks are checked complete in [tasks.md](tasks.md). Final candidate checks are recorded below.

## Final Candidate Verification

- `npm run verify:all` exited successfully: ESLint, TypeScript, 62 Vitest tests, production build and 106 Chromium browser tests passed across desktop and 390 px projects.
- `npm run format:check` passed.
- The composed locale/theme journey preserved `q=design` and found no horizontal overflow in `<main>` at 320, 390, 650, 651, 768 and 1440 px.
- Automated axe checks on catalog and detail passed with WCAG 2.0, 2.1 and 2.2 A/AA tags.
- `npx playwright test --config playwright.production.config.ts` passed the production catalog/detail smoke test. It reported no page errors, external requests or review controls.
- A search of the built `dist` output found no `unihub:workshop-scenario`, `Workshop scenario`, `MOCK review` or `Reset workshop scenario` markers. The discovery MOCK data remains available in the production app.
- Wide English and narrow Vietnamese dark catalog screenshots were captured and visually inspected during WD-T05. Their files and the `evidence` folder were subsequently removed at the user's request.

## UI Approval and Pending MOCK Verification

The user approved the current UI on 2026-11-27 after the Tailwind and shared access layout changes. Full MOCK acceptance remains `VERIFICATION_PENDING`.

The follow-up alignment with prototype revision 3 restored the catalog hero, workshop artwork, card hierarchy, filter panels and detail layout. The date panel uses browser-native date fields. The shared access shell and sign-in forms continue to follow the approved React access baseline.

- Perform the planned real-browser 200% zoom and screen-reader review for catalog, filters and detail, including announcements and focus return.
- Record the accessibility results before closing MOCK acceptance.
