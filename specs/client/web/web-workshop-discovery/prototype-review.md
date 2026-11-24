# Prototype Review: Workshop Discovery and Details

> **Platform/Mode:** Web / `MOCK`<br>
> **Prototype Revision:** 3<br>
> **Entry Point:** [prototype/index.html](prototype/index.html)<br>
> **Spec/UI Revisions:** [Approved spec revision 1](SPEC.md), web experience/inventory 1, [UI baseline and foundation prototype revision 6](../../../../docs/client/web/ui-design.md) with the approved React access shell addendum<br>
> **Review/Approval:** Human approved prototype revision 3 for Web / `MOCK` on 2026-11-25. The [approved bundle](tasks.md) pairs it with spec revision 1 and task plan revision 1.

## Artifact Scope

The plain HTML/CSS/JavaScript preview covers public catalog and detail, search and filters, paging, browser history, recovery and the local sign-in handoff in WS-02. The home and sign-in pages demonstrate entry and exit. The production access capability owns actual auth behavior. The preview uses existing foundation identity and poster assets with a local synthetic brochure. All mock data and copy load from local JSON.

Fragment paths let static hosting support direct links, reload and Back without rewrite rules. The prototype detail route is `#/workshops/:slug`; the spec proposes `/workshops/:slug` for the app. The sign-in fixture accepts the synthetic account shown on screen. The temporary-password scenario simulates the extra step without storing passwords or submitting a service mutation.

## Preview Setup

From the repository root, serve on loopback:

```powershell
python -m http.server 4176 --bind 127.0.0.1
```

Open [Home](http://127.0.0.1:4176/specs/client/web/web-workshop-discovery/prototype/index.html) or [Catalog](http://127.0.0.1:4176/specs/client/web/web-workshop-discovery/prototype/index.html#/workshops). Keep the repository root as the HTTP root so shared assets load. The scenario bar is above the product shell. Selecting a scenario runs a fresh catalog read, resets carousel position and local auth, and restores its simulated failure if configured. Reset repeats the selected scenario. Theme and language remain selected for reviewing the same scenario across appearances. Reload returns the scenario to normal while retaining supported route state and tab preferences.

## Scenario Coverage

All scenario keys below refer to [fixtures/scenarios.json](prototype/fixtures/scenarios.json). Use the date picker in November 2026.

| Journey                             | Setup and Interaction                                                                                                                                                                                            | Expected Outcome                                                                                                                                                                                          |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| WD-01 Catalog and carousel          | Open home in `normal`, enter Explore workshops and press Next in featured with the keyboard. Switch to `no-featured`.                                                                                            | Public rows show workshop facts. Featured advances manually with a position counter. Empty featured collection hides the carousel.                                                                        |
| WD-02 Search and filters            | In `normal`, search for `design`, choose Free and select Nov 30 then Nov 29. Repeat with Nov 29 twice and enter an overlong or no-match query. Select `empty` separately.                                        | Reversed/same-day dates normalize. The late UTC workshop appears on Nov 29 local time. Validation, no-match reset and globally empty catalog give distinct feedback.                                      |
| WD-03 History and paging            | In `normal`, search for `design`, choose Free, select Next and open Accessible Design Workshop. Use Back and reload. Open `#/workshops?q=design&cursor=stale`.                                                   | Back and reload restore the cursor and filters. A stale cursor falls back to the initial results and keeps the search.                                                                                    |
| WD-04 Details and brochure          | Open `#/workshops/design-sprint`, `ai-lab`, `career-studio` and `design-access` under the detail path.                                                                                                           | Shared layout supports free, paid, full and optional-content cases. Only Design Sprint has a brochure.                                                                                                    |
| WD-05 Unavailable details           | Open `#/workshops/missing` and `#/workshops/private-draft`. Select `detail-error`, open Design Sprint and Retry.                                                                                                 | Unknown and nonpublic use the same unavailable feedback. Retry recovers the selected detail.                                                                                                              |
| WD-06 Sign-in handoff               | From an available detail, sign in with the shown student account. Repeat in `temporary-password` and enter a synthetic replacement when prompted. Use `organizer` and `staff` to inspect the same public detail. | Student returns to the same detail and sees that registration is unavailable. Organizer and staff can view the public page without the student action. Invalid external intent falls back to the catalog. |
| WD-07 Loading and recovery          | Choose `slow`, `list-error` or `offline`. In `race`, submit `design` and immediately submit `ai`.                                                                                                                | Loading appears; the first failure can be retried. The newer query remains visible when the older result resolves.                                                                                        |
| WD-08 Responsive and accessible use | Switch locale/theme, open date filters on a narrow viewport, press Escape and open the long-titled Design Systems detail.                                                                                        | Route/criteria remain selected, Escape returns focus to the opener, and long content reflows.                                                                                                             |

## Visual and Interaction Evidence

Run the replay script while the local server is up. It uses the existing web workspace Playwright and axe packages, with no app build.

```powershell
node --check specs/client/web/web-workshop-discovery/prototype/app.js
node specs/client/web/web-workshop-discovery/prototype/review-checks.cjs
```

- Chromium replay passed against prototype revision 1. It exercised catalog navigation, keyboard carousel, query validation, combined filtering, same-day local date filtering, page/history restoration, stale page reset, brochure controls, full/unavailable detail, sign-in and temporary-password return, unsafe intent fallback, empty and failure recovery, stale results, locale/theme switching and Escape focus restoration. The run captured no console/page errors, failed HTTP responses or external requests.
- The replay checked horizontal overflow at 320, 390, 650, 651, 768 and 1440 px. It ran axe and captured screenshots for catalog and detail at 1440 × 900 (EN/light), and open calendar and long detail at 320 × 844 (VI/dark). All four screenshots were actually viewed. The script prints the disposable screenshot directory.
- The scenario table includes additional human review steps beyond the replay. Real browser zoom, assistive technology and subsequent standard application build checks remain pending. This evidence applies to the static HTML preview.

## Findings, Revisions and Approval Scope

| Finding                                                                       | Related Scenario  | Resolution                                                                                              |
| ----------------------------------------------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------- |
| WD-P01 Calendar labels for single-digit days omitted the visible number form. | WD-08, WEB-UI-006 | Include the visible day number and the localized full date. The axe replay passed after the correction. |
| WD-P02 Poster height stayed fixed at narrow widths.                           | WD-08, WEB-UI-003 | Let height follow the image ratio. Replayed checks and inspected the new narrow and wide screenshots.   |

### Revision 2

The visual corrections bring the banner, cards and detail closer to foundation revision 6. The sign-in handoff follows the approved Access prototype form pattern. Header actions use a full-size theme icon and a filled sign-in button. Search and sign-in hover use brand hover shades. Price and language choices stack vertically.

For WD-03, use Previous/Next and the filter-bound keyset cursor in the URL. The stale-cursor review link is `#/workshops?q=design&cursor=stale`. The replay also covers hover shades, vertical price options and password visibility. The desktop sign-in screenshot supplements the four baseline review views.

### Revision 3

After the simulated sign-in, the header replaces Sign in with the user's initials. The account popup shows the fixture name and email plus Sign out. The replay verifies keyboard opening, Escape and focus restoration, outside dismissal and the transition back to Sign in.

The previous and next controls are equal 44 × 44 px icon targets with accessible labels and disabled endpoints. The fixture cursor is bound to the filter and the last workshop's start time and slug. The replay verifies movement across three result sets, restoration of the previous cursor, stale or filter-mismatched cursor recovery, Back and reload. This models cursor navigation on the synthetic dataset. Provider behavior is verified separately during integration.

The Chromium and axe replay passed. The account popup and catalog pagination screenshots were captured and viewed. The user approved prototype revision 3 against spec revision 1 and baseline revision 6 on 2026-11-25, then approved the coherent MOCK bundle with task plan revision 1.
