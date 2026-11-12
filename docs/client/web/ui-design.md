# UniHub Workshop — Web UI Design

> **Status:** APPROVED<br>
> **Revision:** 6<br>
> **Prototype Revision:** 6<br>
> **Approved:** Logo/favicon, light and dark palettes, typography, bilingual copy, shared components and representative responsive layouts.<br>
> **Foundation Approval:** Human approved web UI baseline revision 6 and foundation prototype revision 6 on 2026-11-12. Approval covers the representative platform design; capability bundles and implementation require their own gates.

## Scope and Design Direction

This baseline belongs to web. [Experience Map](experience-map.md) and [Delivery Inventory](feature-delivery.md) own platform scope. Use modern cobalt identity, a light neutral canvas and restrained lime accents with distinctive workshop artwork. English is the default language on first load and reload. Vietnamese and English have equal functional coverage. Locale selection changes the current in-memory preview; it is not persisted across reloads. Discovery prioritizes finding and understanding workshops; organizer screens prioritize readable records and focused editing. Detailed capability behavior and implementation follow their own approval gates.

## Rule Registry

| Rule ID    | Criterion                                                                                                      | Verification                              |
| ---------- | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| WEB-UI-001 | Use semantic theme tokens; pair lime with dark ink                                                             | Contrast and both-theme inspection        |
| WEB-UI-002 | Navigation follows role and organization scope; active indication sits directly below its label                | Shell and active-state inspection         |
| WEB-UI-003 | Content reflows without page-level horizontal scrolling; wide tables scroll in a named inner region            | Desktop/tablet/narrow viewport inspection |
| WEB-UI-004 | Show title, date/time, place, fee and availability before registration; no unsupported topic tags              | Card/detail fixture review                |
| WEB-UI-005 | Loading, empty, error, unavailable and success states offer the appropriate next action                        | State scenarios                           |
| WEB-UI-006 | Provide keyboard access, visible focus, labeled inputs, associated errors and named dialogs                    | Keyboard and axe checks                   |
| WEB-UI-007 | Localize naturally; preserve workshop terminology, authored content and route/filter state                     | Vietnamese/English review                 |
| WEB-UI-008 | Reservation, pending payment and issued ticket are distinct; only confirmed payment yields a paid ticket       | Payment outcome scenarios                 |
| WEB-UI-009 | Respect reduced motion; featured carousel is manually operated                                                 | Keyboard/reduced-motion review            |
| WEB-UI-010 | Preserve logo proportions and legibility, including favicon sizes                                              | SVG and browser-tab review                |
| WEB-UI-011 | Scenario and payment simulation controls are separate from product interactions                                | Review toolbar inspection                 |
| WEB-UI-012 | Verify light and dark states independently; do not infer theme approval                                        | Theme review                              |
| WEB-UI-013 | Footer rests at the viewport bottom for short pages and follows content on long pages                          | Wallet and long-list inspection           |
| WEB-UI-014 | Editor occupies organizer content area; navigation remains available and collapses to a menu on narrow screens | Create/edit and responsive review         |

## Semantic Tokens and Typography

| Token           | Light     | Dark      | Role                           |
| --------------- | --------- | --------- | ------------------------------ |
| `--brand`       | `#234BEB` | `#9CADFF` | Action, links, focus           |
| `--brand-hover` | `#1838BB` | `#B9C5FF` | Hover                          |
| `--accent`      | `#DBF76A` | `#DBF76A` | Brand highlight with dark text |
| `--ink`         | `#162142` | `#EDF1FF` | Primary text                   |
| `--muted`       | `#55617B` | `#B2BDD6` | Secondary text                 |
| `--surface`     | `#FFFFFF` | `#111A2E` | Content surface                |
| `--canvas`      | `#F6F8FC` | `#0B1222` | Supporting background          |
| `--line`        | `#DCE2EF` | `#33415B` | Decorative boundaries          |
| `--field-line`  | `#7A859C` | `#7F8DA8` | Control boundaries             |
| `--tint`        | `#EDF1FF` | `#27365A` | Selected surface               |
| `--success`     | `#166345` | `#8EE0B3` | Success foreground             |
| `--danger`      | `#B42335` | `#FFABBA` | Error foreground               |

The identity's cobalt/lime artwork is stable in both themes. Dark mode uses a lighter semantic action color for text/control contrast, not a replacement logo palette. Primary dark-mode buttons pair this light action fill with dark ink. Status chips include text and theme-appropriate backgrounds.

Use Segoe UI, Arial and sans-serif fallbacks, including Vietnamese glyphs. Body is 16 px with 1.55 line height; metadata and operational rows are 13–14 px. Section headings are approximately 26–36 px; discovery headings scale to viewport. Use a 4 px spacing rhythm and differentiated corners: inputs/buttons 8–12 px, cards 16 px, major panels 18–24 px. Avoid clipping translated titles.

## Identity

Approved SVGs are `foundation-prototype/assets/unihub-logo.svg` and `unihub-symbol.svg`. The U symbol, cobalt field and lime diamond remain consistent. `unihub-logo-dark.svg` provides readable light wordmark text on dark surfaces. The symbol is the SVG favicon. Preserve proportions and whitespace; use the full mark where readable and the symbol for small areas. Wordmark rendering uses the system font stack. Web identity approval does not automatically approve mobile artifacts.

## Layout and Navigation

### Header

Place logo and role-appropriate navigation on the left. Student navigation contains Discover and My Tickets; history/payment links belong to ticket/account context rather than extra global tabs. Organizer uses its own sidebar. Notification, appearance and language controls share a compact bordered shape and consistent hit area; account avatar sits last. The square locale button matches adjacent icon-control dimensions and displays VN/EN and opens a custom popup with Tiếng Việt/Tiếng Anh in Vietnamese and Vietnamese/English in English, an accessible label, selection state and Escape/light-dismiss behavior. Active navigation has a short underline close to the label. A thin theme-aware bottom border separates the header from content. The header sticks to the top on scroll; its opaque background preserves readability. The review toolbar is not sticky.

Account dropdown identifies the account with name/email and groups Profile, Account Security and Sign Out. It dismisses on outside click or Escape. These are entrypoint patterns; detailed account and notification experiences belong to their capabilities. Prototype account actions demonstrate dialogs, not real authentication/profile updates.

### Discovery and Detail

Use a manually controlled carousel with three published workshop examples and a visible position counter. Previous/next controls have accessible labels. Do not auto-rotate or require swipe/hover. Search is followed by a single date-range button opening a custom calendar, plus a price button opening free/paid options. Select a start date then an end date; reversed selections are normalized, and Clear Dates resets both. These controls use styled, right-aligned popovers rather than native select option lists. Date and price triggers use matching leading line icons and omit decorative dropdown arrows and use explicit hover/focus feedback. Calendar navigation uses centered SVG arrows; the selected range and clear action share a bottom row, with clearing aligned right. Topic tags are not part of the workshop model or UI. Cards show the approved concise metadata; deeper content belongs to details.

Free and paid examples share the detail layout. Free registration confirms directly. Paid registration uses a shared-background, connected three-label progress indicator without numeric labels, distinguishing completed/current/upcoming stages with accessible current-step state, without checkmarks. Paid success stays within the same dialog with the progress indicator visible, completed reservation/payment stages and the final ticket stage active. Paid registration shows Reserve → Pay → Ticket; reservation alone does not issue a ticket. Pending and failed payments have no ticket. Provider outcomes are simulated only through the review toolbar, selected before opening the payment flow. Real payment mechanics, deadlines and retry/idempotency rules require the paid-registration bundle and approved contract.

### Organizer

Top-level sidebar entries are Workshop and Members. Rosters, assignments, attendance and brochure actions belong to workshop context; reports can be introduced through their scoped capability. The Members table includes an Actions column with a confirmable suspend/restore example for another member; no self-suspension action is shown. Permissions, affected roles and account-policy consequences require the member-administration capability specification. Do not add infrastructure, CSV upload or invented analytics navigation. The narrow layout uses a menu button with expanded state rather than a row of all links.

Workshop title cells include a 48 px square, rounded banner thumbnail beside the title and location. Member name cells include a circular initials avatar as a fallback when no profile image is available. Thumbnails are decorative alongside visible names and must not widen the page. Banner thumbnails use cover cropping without stretching. Member initials are a decorative fallback beside the full name. The create-workshop action uses a 20 px SVG plus aligned with the label and a 14 px gap. Workshop table headers are centered; title cells are left aligned and other cells centered. Keep title columns wide enough to wrap naturally. Create/edit uses a dedicated content page with the sidebar retained, fields populated when editing, and save/publish actions. Organization ownership is read-only. Banner upload accepts PNG/JPEG/WebP up to 5 MB, shows a local preview and reports invalid input without uploading to a provider. Fields editable in each business state are determined by the capability spec; the foundation demonstrates a draft form without freezing arbitrary draft fields. Publish validation and published-workshop editing require their detailed capability designs.

### Footer and Responsive Behavior

Use a flex-column page shell with a growing main area. Footer remains at the viewport bottom on short views and flows below long content; it must not overlay content. Provide restrained About, Getting Started and Accessibility entrypoints. Legal/contact destinations require actual approved content rather than invented policy claims.

Content width is at most 1280 px with desktop gutters of 32 px, reduced to 20 px on narrow layouts. Stack cards/details on narrow screens, allow filter controls to wrap and contain table scrolling. Keep organizer editing usable without horizontal page scrolling. Menu, account controls and dialogs remain keyboard accessible.

## Components, Variants, States and Interaction

Primary, secondary and text buttons follow theme roles. Selected filters/statuses use concise chips where useful, with labels in addition to color. Native form labels and focus styling remain visible in both themes. Dialog close controls stay at the upper-right corner independently of content above the title. Without introductory content, title and close share the same horizontal centerline. The SVG close glyph is centered within its circular target. Carousel arrows are centered SVGs. Focus indicators use a 2 px ring with 1 px offset, retaining visibility without a wide halo. Success feedback combines a branded visual, descriptive heading, contextual result and clear next action, rather than an isolated checkmark.

Empty/error/loading states are reusable and context-specific. Retry preserves relevant search state; no-results offers clearing filters. Native dialogs trap focus, dismiss by Escape and restore focus. Draft persistence and simulated payment results are in-memory preview state only. No real ticket, payment or API operation is performed.

## Content and Feedback

Use direct, familiar Vietnamese: “Quản lý workshop”, “Thành viên”, “Bản nháp”, “Phí tham gia”. Retain workshop and established technical/product names where natural. Avoid decorative translations such as “không gian tổ chức”. English copy conveys the same action and state without word-for-word translation. Keep strings in JSON; locale change does not translate user-authored input.

Format dates/currency through Intl. Show the workshop time zone in details. Do not present a reservation or pending payment as a completed registration. Product copy describes the task; review instructions and simulated outcomes belong to the review toolbar.

## Accessibility and Motion

Target applicable WCAG 2.2 AA criteria: readable text contrast, semantic landmarks/headings, clear input labels, visible keyboard focus and meaningful status text. Aim for 44 px product hit targets. Carousel controls work without automatic motion. Respect reduced-motion settings. Automated scans supplement visual, keyboard, zoom and assistive-technology review; no automated result constitutes complete accessibility certification.

## Platform Differences

This is a web baseline. Native scanning, camera permissions, offline queues and native safe areas belong to mobile. Browser viewport checks do not certify a native mobile UI. Favicon verification includes human review of the browser tab as viewport screenshots omit browser chrome.

## Verification Criteria and Human Review

### Preview Setup

From repository root:

```powershell
python -m http.server 4180 --bind 127.0.0.1 --directory docs/client/web/foundation-prototype
```

`py` may be used with the same arguments. Open `http://127.0.0.1:4180/`; stop with Ctrl+C. No application build, backend or credentials are required. `app.js` owns content rendering and local scenario state; `shell.js` owns shell, theme, carousel, editor and representative payment interactions; `controls.js` owns popup positioning, calendar controls, banner preview and the common dialog header. Fixtures live under `fixtures/` and SVGs under `assets/`.

### Review Scenarios

| ID    | Scenario                                                                     | Expected Result                                                                  |
| ----- | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| WF-01 | VI/EN and light/dark across all views                                        | Readable localized content and consistent controls                               |
| WF-02 | Carousel previous/next                                                       | Published workshop changes and counter updates                                   |
| WF-03 | Search, date bounds and price                                                | Matching results, recovery from empty results                                    |
| WF-04 | Error/loading/full                                                           | Recoverable feedback and unavailable full-capacity action                        |
| WF-05 | Free registration with invalid/valid email                                   | Field error followed by confirmation/ticket                                      |
| WF-06 | Select Payment simulation pending/success/failure before opening paid detail | Reservation, waiting state, then ticket only for success                         |
| WF-07 | Account menu, notification, appearance and locale                            | Consistent shell, accessible dismissal and toggles                               |
| WF-08 | Organizer create/edit, Members and narrow menu                               | Full-page editor, contextual read-only organization, collapsed mobile navigation |
| WF-09 | Short wallet and long catalog                                                | Footer at bottom without overlap                                                 |
| WF-10 | Keyboard, small viewports and both themes                                    | Named dialog, usable table scrolling, no page overflow                           |

### Evidence and Approval

Verification covers default English, locale switching, light/dark appearance, discovery filtering, calendar bounds, carousel movement, free and simulated paid registration, persistent paid-success progress, editor banner selection, member actions and account dropdown dismissal. The revision 6 verification matrix passed 80 combinations: discovery/organizer/members/editor/wallet × English/Vietnamese × light/dark × 320/390/768/1440 px, with axe scans and page-overflow assertions. Default English, banner thumbnails, SVG create icon, member avatars and narrow navigation were checked; desktop organizer/member and narrow organizer screenshots were actually viewed. Automated checks supplement visual inspection. Evidence is scoped to these synthetic patterns; no provider behavior or capability implementation is certified.

Human approved the complete representative web foundation at baseline revision 6 and prototype revision 6 on 2026-11-12. Browser zoom and assistive-technology checks remain unverified and must be planned where required for capability implementation; design approval does not claim those checks passed. Capability readiness is `WAITING_BUNDLE` until compatible specs, prototypes and plans are approved. No capability implementation or mock acceptance is implied.

## Approved Scoped Exceptions and Dependent Artifacts

No approved scoped exceptions. Capability specs reference stable rule IDs and exact baseline revisions, adding local requirements only. Shared changes require impact review and updated approval; detailed prototypes remain under `specs/client/web/<capability-id>/prototype/`. The foundation does not authorize production app implementation or public contract changes.

Progress-pattern reference: [Carbon Progress Indicator](https://www.carbondesignsystem.com/building-blocks/core/components/progress-indicator/guidelines).
