# Capability: Access and Role Navigation

> **Capability:** `web-access`<br>
> **Platform/Mode:** Web / `MOCK`<br>
> **Status:** `APPROVED`<br>
> **Revision:** 1<br>
> **Experience/UI Baseline:** Web experience map revision 1; delivery inventory revision 1; web UI baseline revision 6 and foundation prototype revision 6<br>
> **Approval:** Human approved this specification at revision 1 for Web / `MOCK` on 2026-11-14. Approval covers the specification only; the compatible prototype/task bundle and production implementation remain pending.

## Actors and Preconditions

Visitors enter the public site and sign in. Students use their own activated account. Organizers and staff use provisioned accounts, with temporary-password replacement when required. Organizer access is scoped to its organization; staff web access is limited to assigned-workshop information, account/security and notifications. Scanning, attendance capture and offline synchronization belong to mobile.

The product reference is `authentication-and-session-management`. Initial temporary-password replacement is included by the web delivery scope, with provider semantics referenced from the password-reset contract. Account creation, recovery and authenticated account security remain separate capabilities even though they share the eventual `identity` source module.

There are no hard capability prerequisites. Approved platform scope, foundation and tooling precede the capability bundle. App composition owns routes, role shells and service selection; `identity` owns client identity/session models, operations and transitions. Client access guards improve navigation and privacy; they do not establish provider authorization.

### Authoritative Inputs

| Source                                                                                                                                     | Concern Used                                                                                          |
| ------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| [Product Overview](../../../../docs/product-overview.md) and [Product Features](../../../../docs/product-features.md)                      | Role separation, organization isolation and product authentication scope                              |
| [Experience Map](../../../../docs/client/web/experience-map.md), revision 1                                                                | WS-01; participation in WJ-01, WJ-04, WJ-08, WJ-09 and WJ-10                                          |
| [Delivery Catalog](../../../../docs/client/web/feature-delivery.md), inventory revision 1                                                  | Capability boundary, `identity` ownership, prerequisite and consumer edges                            |
| [Web UI Design](../../../../docs/client/web/ui-design.md), revision 6                                                                      | Approved global appearance, shared interaction/accessibility rules and bilingual coverage             |
| [Source Structure](../../../../docs/client/web/source-structure.md) and [Source Boundaries](../../../../docs/client/source-boundaries.md)  | App/feature/shared ownership and injected fake service boundary                                       |
| [Web Tooling](../../../../docs/client/web/tooling.md)                                                                                      | Existing React/TypeScript/Vite stack, default retry policy and verification commands                  |
| [Authentication Contract](../../../../contracts/http/features/authentication-and-session-management.md), approved, last revised 2026-09-05 | Email/password login, role from service result, temporary/inactive gates, refresh and logout outcomes |
| [Password Contract](../../../../contracts/http/features/password-reset.md), approved, section 3.3                                          | Temporary-password replacement, validation, single-use transition and immediate full session          |

Provider documents supply behavior references only. This spec defines no HTTP adapter, endpoint, wire schema, token storage policy for real credentials or provider implementation change.

### Decision Record

| ID   | Decision                                                                                                                                                                   | Authority                                                                                        |
| ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| D-01 | One `/login` page with email/password, reached from the public home sign-in action. No role selector; the returned identity determines role navigation.                    | Human feedback and confirmation of the shared-route proposal, 2026-11-14                         |
| D-02 | Required temporary-password replacement precedes protected access; successful replacement establishes the session without another login.                                   | Human feedback, 2026-11-14; password contract section 3.3                                        |
| D-03 | MOCK session survives reload in the same tab; no intentional persistence after tab closure, no Remember Me, no cross-tab logout synchronization.                           | Human explicitly selected the tab-scoped proposal, 2026-11-14                                    |
| D-04 | Logout failure/uncertainty locks private content, offers explicit Retry and optional local-session removal, and never claims confirmed service logout.                     | Human explicitly selected the locked-content/local-removal proposal, 2026-11-14                  |
| D-05 | Reserve shared auth routes and role landing paths below; honor a safe authorized intended destination first. Out-of-scope destinations use minimal composition boundaries. | Human explicitly approved the route proposal, 2026-11-14                                         |
| D-06 | JSON supplies synthetic view/scenario data; use deterministic failure/clock controls and DevTools checks for reload/offline behavior.                                      | Human feedback plus client rules requiring resettable fake scenarios; no provider behavior claim |

The requirements, acceptance criteria and capability-local design criteria are approved as specification revision 1. Detailed layout and executable slices require the compatible prototype/task bundle approval before implementation. No change to approved global UI is proposed.

## Functional Requirements (FR)

- **FR-001 — Public Entry:** The public home provides a visible Sign In action to `/login` and access to the workshop destination. Browsing content is owned by discovery. An authenticated visitor entering `/login` is sent to the safe intended or role-default destination after restoration completes.
- **FR-002 — Shared Login:** Show labeled email/password fields, a submit action, password visibility toggle, Create Account and Forgot Password links. Support keyboard submit, browser password managers and paste. Do not request role, organization or student identifier. During submission prevent duplicate requests and announce progress.
- **FR-003 — Validation and Failure:** Validate required fields and the documented email/password bounds. Associate errors with fields, focus the first invalid field and preserve non-secret input. Wrong password and unknown email share generic credentials feedback. Disabled account, temporary password, rate limit, network and service-unavailable outcomes have distinct appropriate next actions; no stack traces or backend topology appear in copy.
- **FR-004 — Temporary Password:** A temporary-password login outcome moves to `/change-temporary-password` without opening protected content. Carry email and, for the uninterrupted flow only, the submitted temporary password in memory. Show new password and confirmation, the requirements and a show/hide control. If temporary credentials are missing after reload/direct entry, request email/current temporary password on the same page; never persist a password. Confirmed replacement starts the returned session and resumes allowed intent/default landing without re-entering login credentials.
- **FR-005 — Restore and Renew:** Start in a restoring state. Do not render private data while session validity is unresolved. A valid fake session restores its identity; access expiry with a renewable session permits one coordinated renewal attempt. Expired/revoked/nonrenewable session becomes anonymous with a sign-in explanation and safe intent. Network/service failure becomes a recoverable locked state, not a false expired-session result. No automatic retry loop.
- **FR-006 — Role and Destination:** Select the shell and default landing from the service identity. Anonymous protected navigation goes to login; known wrong-role navigation shows access denied with a permitted destination. Unknown route shows not found. Invalid/unsafe return destinations are discarded. Missing required organization context fails closed rather than selecting an organization or granting broader access.
- **FR-007 — Logout:** On Sign Out immediately hide protected content and pause protected work. On confirmed service completion clear local identity, mock session and private projections, then reach the public entry. On failure/uncertainty keep a locked logout screen with Retry and local removal; retain the locked state across reload. Explain local removal before confirmation; it clears local access but does not claim remote revocation. A new explicit login may establish a new session after local removal.
- **FR-008 — Isolation:** Identity/organization change, expiry, logout and local removal invalidate private projections and obsolete asynchronous work. Late login/restore/renew responses cannot undo logout or replace a newer identity. Back navigation cannot reveal a previous account's content or a password-filled form.
- **FR-009 — Deterministic MOCK:** Inject typed asynchronous fakes, backed by synthetic JSON scenarios and an adjustable clock. Support named success, failure, latency and uncertain-result scenarios plus reset. Review controls stay outside product UI. No live API, credential, provider refresh token or generated real JWT is used.
- **FR-010 — Consistent Navigation and Presentation:** Use the approved baseline for shell, light/dark, English/Vietnamese, focus, form errors and recovery states. Reserve onboarding/recovery links now without implementing those flows. Locale resets to English on reload as specified by baseline revision 6; session persistence does not change that rule.

## Business Rules (BR)

| ID     | Rule                                                                                                                                                                                                                                                                                            | Source                                                                                       |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| BR-001 | Only `STUDENT`, `ORGANIZER` and `STAFF` are supported authenticated roles. Visitors are anonymous, not an additional account role. Role and organization come from the service identity, never a form/query parameter.                                                                          | Product features, experience map revision 1; auth contract                                   |
| BR-002 | A temporary-password outcome creates no ordinary authenticated access. Replacement consumes temporary status once and returns a full session. It is distinct from forgot-password and authenticated password change.                                                                            | Delivery scope; password contract section 3.3; D-02                                          |
| BR-003 | Login email is trimmed, required, valid email, maximum 254 characters. Login password is required/nonblank, maximum 255 characters; do not trim or impose new-password complexity on existing login passwords.                                                                                  | Auth contract login constraints                                                              |
| BR-004 | New permanent password is 8–255 characters with uppercase, lowercase, digit and at least one special character from `[!@#$%^&*(),.?":{}\u007c<>]`; it differs from the temporary password. Confirmation must match and is client-only. No password is silently changed or normalized.           | Password contract section 3.3; confirmation is a capability-local UX criterion                    |
| BR-005 | Unknown email and wrong password have the same user-facing credentials error. Do not add email-existence checks or expose other accounts via fixtures in product UI.                                                                                                                            | Auth contract anti-enumeration                                                               |
| BR-006 | MOCK persistence is tab-scoped and stores synthetic state only. Passwords, confirmation values and temporary credentials never enter URL, browser storage, analytics or logs. Browser tab/session restoration is not a promise of service revocation.                                           | D-03; client security rules                                                                  |
| BR-007 | Success means a typed confirmed outcome. A timeout/offline response does not prove that a password change or logout did not execute. Mutations are not replayed automatically.                                                                                                                  | Provider single-use temporary transition; auth logout failure semantics; client retry policy |
| BR-008 | Return intent must resolve through an explicit internal route registry, be authorized for the resulting role/scope and exclude auth/logout/error loops. Reject external/protocol-relative URLs, dangerous schemes, malformed encodings and unrecognized paths; validate after canonicalization. | D-05; capability-local security requirement                                                  |
| BR-009 | Session/renewal deadlines and retry availability come from fake outcomes/clock. No new fixed business TTL, lockout rule or per-user rate limit is invented. Respect supplied retry delay without automatic resubmission.                                                                        | Auth contract; existing no-auto-retry tooling policy                                         |
| BR-010 | No protected content remains interactive during logout or unresolved restoration/renewal. Local removal is distinct from confirmed service logout.                                                                                                                                              | D-04; FR-005, FR-007                                                                         |

## User Flow and UI State Matrix

### Route and Handoff Registry

Paths are client route decisions, not network endpoints. `/` remains the public entry. These reserved paths are shared with future capability bundles; changing them requires a consumer impact review.

| Route                        | Owner and Behavior in This Capability                                                                |
| ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| `/`                          | Public app composition boundary with Sign In and discovery entry; no workshop catalog implementation |
| `/login`                     | `identity` shared login; preserve safe intent while navigating auth boundaries                       |
| `/change-temporary-password` | `identity` required initial replacement; anonymous limited credential flow, no protected session yet |
| `/register`                  | Reserved for `web-student-onboarding`; minimal named boundary, no registration form/OTP service      |
| `/forgot-password`           | Reserved for `web-account-recovery`; minimal named boundary, no recovery requests or reset service   |
| `/workshops`                 | Student default and public browsing destination; actual browsing owned by discovery                  |
| `/organizer/workshops`       | Organizer default; authoring owns list/actions, access supplies guarded shell and named boundary     |
| `/staff/workshops`           | Staff default; staff-workshops owns list/detail, access supplies guarded shell and named boundary    |

Return intent is a validated client route value, separate from credentials. Preserve allowed path/query state through login and temporary-password replacement. Public workshop destinations do not become role-exclusive merely because they are the student default. A return intent never triggers a booking/payment or other mutation; those owners must ask for their own action confirmation. Future detail routes register their own role/query validators rather than relying on a blanket path prefix.

Minimal named boundaries render only the relevant heading, approved shell where applicable and safe navigation. They have no invented business data, fake successful action or implementation explanation in product copy. Prototype review controls identify these boundaries outside product UI. They are not evidence that the destination capability is implemented.

### Logical Views and Scenarios

All views belong to WS-01; scenario IDs are stable across prototype, tasks and verification.

| View/Action               | Scenario | Expected Behavior and Next Actions                                                                                                                          | UI Rules                                    |
| ------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| Public entry              | WA-01    | Visitor sees Sign In, reaches shared login; public workshop entry does not require authentication                                                           | 002, 003, 006, 007, 010, 013                |
| Login validation          | WA-02    | Missing/invalid fields block service call; labeled errors, first invalid focus, show/hide and paste usable                                                  | 005, 006, 007                               |
| Login submission          | WA-03    | Loading and duplicate-submit protection; each of three identities reaches its role default; no role chooser                                                 | 002, 005, 006                               |
| Credentials/disabled      | WA-04    | Same error for wrong password/unknown email; disabled-account outcome blocks access; clear password, retain email                                           | 005, 006, 007                               |
| Temporary gate            | WA-05    | No protected session; navigate to replacement, retain email and transient temporary credential                                                              | 002, 005, 006                               |
| Replacement validation    | WA-06    | Complexity, same-as-current and confirmation errors; direct/reload entry requests missing credentials without persisting them                               | 005, 006, 007                               |
| Replacement success       | WA-07    | Single successful change starts returned session, clears secret fields, resumes safe intent/default; no second login                                        | 002, 005, 006                               |
| Replacement lost response | WA-08    | Do not claim failure meant no change; no auto replay. Explain uncertainty, offer explicit login with new password or explicit retry of current step         | 005, 006, 007                               |
| Restore/renew             | WA-09    | Loading blocks private content; valid restore succeeds; concurrent expiry triggers one renewal and applies only current generation                          | 002, 005, 006                               |
| Expired/revoked           | WA-10    | Confirmed unusable session clears private state, routes to login with explanation and safe intent                                                           | 002, 005, 006                               |
| Offline/service error     | WA-11    | Login/restore/renew report unavailable state, keep private content locked, explicit retry; recovery uses deterministic fake outcome                         | 005, 006, 007                               |
| Rate limit                | WA-12    | Show actionable rate-limit feedback; honor supplied retry delay and then enable explicit retry; no automatic submission                                     | 005, 006, 007                               |
| Access/not found/intent   | WA-13    | Anonymous protected route → login; wrong role/missing org → access denied; unknown route → not found; unsafe intent → safe default                          | 002, 005, 006                               |
| Logout success            | WA-14    | Lock immediately; confirmed completion clears session/private data; Back and reload cannot reopen prior private view                                        | 002, 005, 006                               |
| Logout failure            | WA-15    | Network/service failure or lost response retains locked view across reload; Retry cannot expose private content or falsely report success                   | 005, 006, 007                               |
| Local removal             | WA-16    | Confirm distinction from service revocation, clear local session, reach public entry with accurate feedback; no claim other tabs/devices were signed out    | 005, 006, 007                               |
| Account/race isolation    | WA-17    | Late response after logout, rapid navigation or another login cannot restore old identity/private state; separate account/org fixtures remain isolated      | 002, 005, 006                               |
| Adjacent auth boundaries  | WA-18    | Register/Forgot Password and return-to-login navigation retain safe intent without implementing their operations                                            | 002, 005, 006                               |
| Visual/accessibility      | WA-19    | All access states usable in VI/EN, both themes, narrow/desktop, keyboard, zoom and announced errors/statuses                                                | 001, 003, 005, 006, 007, 009, 010, 012, 013 |
| Tab persistence/reset     | WA-20    | Reload restores fake session or pending logout lock; fresh independent tab starts anonymous; reset clears synthetic state and clock for reproducible review | 005, 011                                    |

Rule numbers in the last column refer to `WEB-UI-<number>` at baseline revision 6. Empty results are Not Applicable to credential forms; do not create a meaningless empty-login screen. Offline attendance, permissions for camera and device queues are Not Applicable to web access.

## Data/Action/Error Requirements

### Client Models

These are client-owned needs; names describe behavior and may be refined without inventing wire DTOs. Use discriminated outcomes rather than optional-field-heavy state objects.

| Model                    | Required Fields and Constraints                                                                                                                                                                                 |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Identity`               | Nonempty synthetic `id`, `email`, `displayName`; `role` enum; organization reference required for organizer/staff, nullable for student; no server entities                                                     |
| `ReturnIntent`           | Validated internal path plus route-approved query state; nullable when absent/rejected; no credentials or side-effect instruction                                                                               |
| `LoginInput`             | Email and password strings; transient secret lifetime only                                                                                                                                                      |
| `TemporaryPasswordInput` | Email/current/new password; confirmation is UI-only; all secret values transient                                                                                                                                |
| `MockSession`            | Opaque synthetic session ID, identity reference, clock-based access and renewal deadlines, revision/generation; no real token                                                                                   |
| `AccessState`            | `restoring`, `anonymous`, `authenticating`, `temporary-password-required`, `authenticated`, `renewing`, `restore-unavailable`, `logging-out`, `logout-unconfirmed`; each variant holds only its relevant fields |
| `AccessError`            | Typed kind, safe localized message key, optional field errors, optional retry delay and mutation certainty; no raw exception                                                                                    |
| `MockScenario`           | Stable scenario ID, account/org references, fixed clock origin, operation latency/outcome and simulated network availability; reset seed                                                                        |

### Service Operations

| Operation                         | Typed Outcomes and Lifecycle                                                                                                                           |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `login(input)`                    | Authenticated session; temporary-password required; invalid credentials; disabled; validation; rate limited; unavailable/unknown result                |
| `replaceTemporaryPassword(input)` | Authenticated session; validation/same password; invalid credentials; no-longer-temporary; rate limited; unavailable/unknown commit outcome            |
| `restoreSession()`                | Valid session; renewable session; absent; expired/revoked; unavailable; pending logout lock. A browser-stored identity is not itself proof of validity |
| `renewSession()`                  | Updated session; expired/revoked; unavailable. Coordinate one attempt and reject obsolete generation results                                           |
| `logout()`                        | Confirmed completion; confirmed already unusable; unavailable/uncertain. The fake must represent each distinctly; generic errors never become success  |
| `clearLocalSession()`             | Explicit local cleanup without service-revocation assertion; invalidate all outstanding identity operations                                            |
| `resetScenario(id)`               | Review/test-only reset of fake accounts, sessions, persistence, outcomes and clock; unavailable as product action                                      |

No component selects or constructs adapters. App composition selects fake factories; models/service operations remain reusable by future identity capabilities. Public consumers receive scoped identity/access context and callbacks, not credentials or adapter internals.

### Synthetic Persistence and Network Behavior

For MOCK, use a namespaced, versioned tab-session record, normally through `sessionStorage`, to retain only synthetic session/scenario references and non-secret pending state across reload. Keep the fake's synthetic account transition overlay in the same tab boundary so a completed temporary-password change remains completed after reload. No real password is stored: use deterministic test-only credential matching/transition state for the fake. Review fixture examples are demonstrably synthetic and never real user accounts.

A pending logout marker must survive reload before private rendering and must not be bypassed by changing a route. A rejected/unavailable storage operation falls back to memory for the running page; warn that reload will require sign-in rather than reporting persistence as successful. Malformed/version-incompatible state becomes anonymous safely. Never infer actual server revocation from tab closure. Browser-provided duplicate/reopen/session restoration may copy tab state; independent tab sessions and lack of cross-tab synchronization are the explicit MOCK boundary, not an account security guarantee.

Credentials are cleared on terminal failure, success, cancellation/navigation away and teardown; email/safe intent may remain for recovery. In an uninterrupted temporary flow, keeping the current temporary password in memory avoids asking twice. Reloading that page requires it again. After an uncertain replacement, avoid blind resubmission: explain that the password may already have changed and permit login with the new password. An already-completed fake transition never executes a second activation.

Use both injected offline/unavailable outcomes and browser connectivity checks in the fake boundary. DevTools Offline alone does not reliably fail an in-memory operation; the named fake scenarios are the reproducible oracle. Do not require downloading a new JSON fixture to switch an already-loaded review to offline. A full app reload while HTTP assets cannot be loaded is not guaranteed to work: offline app-shell caching/service-worker installation is outside scope.

### Integration Gaps Kept Separate

The public login/refresh/password contracts reference bearer/session credentials. They do not select the real web storage/cookie/CSRF model. Refresh identity reconstruction, restore/revocation detection, multi-tab coordination and handling a logout retry after the service already committed require a separate approved integration design. No `/me`, session-introspection endpoint or idempotency guarantee is invented here. Typed fake outcomes do not approve missing provider behavior.

## Platform, Security and Offline Concerns

- **Input and Navigation:** Validate route/query input through the registry, render copy as text and reject unsafe URL schemes/redirects. Never return a secret through URL/history/state snapshots. Do not store password-bearing form data in navigation history.
- **Account Boundaries:** Clear/cancel scoped query state when identity changes or access locks; an older request generation cannot publish data into a new session. No UI role toggle or manually selected organization grants access. A synthesized invalid/missing identity must fail closed.
- **Uncertainty and Retries:** No automatic login, password-change or logout mutation retries. Safe restoration/renewal has a bounded coordinated attempt; explicit retry follows a named typed outcome. A mutation timeout is modeled separately from confirmed rejection. Keep local removal available during persistent offline logout failure.
- **Accessible Forms:** Use visible labels, correct email/current-password/new-password autocomplete, semantic buttons, named show/hide states and associated errors. Password managers/paste work. On route change focus the main heading; on validation focus the first invalid control. Loading/error/status feedback is announced without repeated focus theft. Confirmations use the approved dialog dismissal/focus restoration pattern.
- **Responsive and Localized:** Preserve baseline tokens/logo, English-first reload, equal VI/EN content, both themes, reduced motion, footer behavior and usable narrow layouts. Verify 320, 390, 768 and 1440 px, keyboard order, contrast and 200% zoom; include assistive-technology review in the plan rather than treating axe as certification.
- **Offline Scope:** Do not grant new access or expose cached private data while restoration/renewal is unresolved. This is access recovery, not an offline-first web feature. Native verification is Not Applicable. Real offline queues and mobile account/device policy remain outside scope.

## Acceptance Criteria

Code-testable criteria below require later model/service/component and mock-browser evidence; this document does not claim they have run.

| ID     | Given / When / Then                                                                                                                                                                                                                                          | Trace                                        |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------- |
| AC-001 | Given a visitor at home, when Sign In is activated by mouse or keyboard, then the shared login opens without a role chooser and basic auth links are available.                                                                                              | FR-001, FR-002; D-01; WA-01                  |
| AC-002 | Given empty/invalid login fields, when submitted, then no login operation runs and associated errors focus the first invalid field; valid inputs follow the documented bounds without trimming passwords.                                                    | FR-003; BR-003; WA-02                        |
| AC-003 | Given each supported identity, when login succeeds, then only its role-appropriate default or safe authorized intent opens; repeated submit during pending login starts one operation.                                                                       | FR-002, FR-006; BR-001, BR-008; WA-03, WA-13 |
| AC-004 | Given unknown email or wrong password, when login is rejected, then the same credentials message appears with no private session; disabled-account outcome also cannot open protected content.                                                               | FR-003; BR-005; WA-04                        |
| AC-005 | Given provisioned temporary credentials, when login returns the temporary gate, then replacement opens without protected access; valid replacement establishes the returned session directly and clears all secret fields.                                   | FR-004; BR-002; WA-05, WA-07                 |
| AC-006 | Given replacement input, when password constraints/confirmation fail or the page reloads without transient credentials, then errors or missing-credential inputs appear; no stored password is restored.                                                     | FR-004; BR-004, BR-006; WA-06                |
| AC-007 | Given replacement committed but its response was lost, when uncertainty is reported, then there is no automatic replay or second activation, and login with the new password can recover the simulated account.                                              | FR-004, FR-009; BR-007; WA-08                |
| AC-008 | Given a valid tab-scoped fake session, when reloaded, then private content waits for restore and the same identity returns; absent/invalid stored state becomes anonymous.                                                                                   | FR-005, FR-009; BR-006; WA-09, WA-20         |
| AC-009 | Given concurrent access-expiry triggers, when renewal is needed, then only one renewal runs; confirmed expiry/revocation clears private state while unavailable renewal stays locked and retryable.                                                          | FR-005, FR-008; BR-009; WA-09, WA-10, WA-11  |
| AC-010 | Given a rate-limit/offline/service-error scenario, when an operation fails, then actionable feedback and permitted explicit retry appear without a submission loop or false authenticated success.                                                           | FR-003, FR-005; BR-009; WA-11, WA-12         |
| AC-011 | Given an anonymous protected request, wrong-role request, missing organizer context, unknown route or unsafe intent, when navigation resolves, then login/access-denied/not-found/default behavior follows the appropriate case and exposes no private data. | FR-006; BR-001, BR-008; WA-13                |
| AC-012 | Given an authenticated session, when Sign Out succeeds, then private content locks immediately, local session/private projections clear and Back/reload cannot reveal the old account.                                                                       | FR-007, FR-008; BR-010; WA-14                |
| AC-013 | Given logout failure or lost response, when retrying or reloading, then the logout lock remains and no success is claimed; confirmed already-unusable outcome can complete cleanup without a false active session.                                           | FR-007; BR-007, BR-010; WA-15                |
| AC-014 | Given unconfirmed logout, when local removal is confirmed, then local access clears with an accurate non-revocation explanation, and no other tab/device logout is claimed.                                                                                  | FR-007; D-04; WA-16                          |
| AC-015 | Given an old in-flight login/renew/restore and a newer logout or account change, when the old result arrives, then it cannot restore the old identity or private projection.                                                                                 | FR-008; WA-17                                |
| AC-016 | Given auth entry/adjacent boundaries, when moving between login, register and forgot-password entrypoints, then safe intent is retained without running another capability's operation; login never auto-submits an intended booking/payment.                | FR-006, FR-010; BR-008; WA-18                |
| AC-017 | Given a named fake scenario and clock, when reset and replayed, then outcomes match; no live provider request occurs, fake controls stay outside product UI and raw secrets never enter storage/URL/logs.                                                    | FR-009; BR-006; WA-20                        |
| AC-018 | Given tab storage unavailable/corrupt or a fresh independent tab, when opening the app, then there is no false restored session; in-memory operation remains usable with an accurate persistence limitation.                                                 | FR-005, FR-009; BR-006; WA-20                |

### Visual and Human Criteria

- **AC-019:** Given WA-01 through WA-20, when reviewing applicable screens in VI/EN, light/dark and the specified viewport widths, then layout, identity, tokens, footer, controls, errors and focus follow baseline revision 6. Zoom and assistive-technology evidence remain required in the plan. Traces: FR-010; WA-19; applicable `WEB-UI` rules.
- **AC-020:** Given locked logout and uncertain password-change screens, when reviewing copy in both locales, then users can distinguish confirmed completion, unknown result, retry and local-only removal without technical/provider explanations. Traces: FR-003, FR-004, FR-007; WA-08, WA-15, WA-16.

## Baseline References and Scoped Design Additions

Reference web UI baseline revision 6; the platform foundation remains unchanged. Use `WEB-UI-001/002/003/005/006/007/009/010/011/012/013`. Discovery metadata, payment steps and organizer editing rules are Not Applicable to the access forms and do not authorize new flows here.

Local additions specified here are the shared access form, required temporary-password form, access/restore/logout locked states, route-error views and the confirmation for local removal. Reuse baseline button/input/dialog/status patterns; capability prototype will determine their detailed layout. This does not create a new global pattern or change the approved shell. Notification/account-settings entrypoints remain owned by their future capabilities; Sign Out belongs to this capability.

The capability prototype will live at `prototype/index.html` alongside deterministic JSON scenarios and a `prototype-review.md` record. These artifacts do not yet exist; no prototype revision, viewed screenshot, accessibility pass or approval is claimed.

## Out of Scope and Dependencies

Excluded: real API integration or authentication tokens, backend/workers/infra edits, mobile/scanning, offline app-shell caching, onboarding/OTP/recovery implementation, authenticated profile/security/session-list management, workshop content/actions, payments/tickets, member provisioning, notifications/reports and analytics dashboards. No OAuth/SSO/MFA, multi-role switching, organization picker or Remember Me policy is added.

`web-access` has no hard capability prerequisite. Its consumers are `web-student-onboarding`, `web-account-recovery`, `web-account-settings`, `web-ticket-wallet`, `web-free-registration`, `web-paid-registration`, `web-payment-history`, `web-member-administration`, `web-workshop-authoring`, `web-staff-workshops`, `web-notifications` and `web-ingestion-reports`. Required outputs are scoped identity/session operations, protected-navigation behavior and safe auth handoffs; consumer plans must name exact approved/verified outputs. Minimal destination boundaries and fixture identities do not count as implemented consumer capabilities.

### Open Decisions and Gates

The three clarification questions are resolved by explicit human selections: D-03, D-04 and D-05. Common login and immediate post-replacement session follow D-01/D-02. Human approved specification revision 1 on 2026-11-14. No unresolved product choice currently blocks prototype preparation; prototype layout and task-plan decisions remain subject to bundle review.

Before dependent implementation, prepare the capability prototype and MOCK `tasks.md`, verify their consistency with this spec and obtain human approval identifying all compatible revisions. `SPEC.md` revision 1 alone is not an implementation approval. No prototype/task revision is submitted as approved here. The catalog remains `DRAFT / WAITING_BUNDLE`.

Future integration must separately settle real credential persistence, restore identity/provider coverage and cross-tab/session coordination; this specification does not block MOCK design on server implementation readiness or authorize integration work.
