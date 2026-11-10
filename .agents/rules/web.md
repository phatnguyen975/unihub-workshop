# Web Rules

Use client rules and `docs/client/web/ui-design.md`. If the baseline does not exist, draft it through `client-foundation` and obtain approval before dependent capability implementation. Read `docs/client/adrs/README.md` for established platform decisions; Use `docs/client/web/source-structure.md` for the feature-oriented source architecture; tooling is selected during app setup.

## Browser and Visual Feedback

Use `playwright-cli` as the browser driver for local prototypes/app previews and Playwright Test author/run/debug work. It needs a compatible installed CLI/browser runtime; a `SKILL.md` alone does not supply either. Use the repository's configured commands and versions when available, not an automatic latest install.

For initial tooling proof or changed capability: open the correct project, take a snapshot and a screenshot, actually view the screenshot, fill/click and assert a named outcome, inspect console/network and a mock failure, then replay a persistent test. Save scenario/viewport/evidence. Until access is proven, report what could not be checked.

For UI work, inspect -> act -> assert -> capture/view -> correct -> verify. Use current snapshot refs/semantic locators for interaction; screenshots assess layout, typography, spacing, content and states against the approved prototype/baseline. Choose viewports from the design/test plan; do not pick a mobile viewport merely to reduce output. Refresh refs after navigation or DOM changes.

Use explicit UI/response assertions for readiness. SSE/persistent connections make blanket networkidle unsuitable. Acceptance journeys operate the UI; page-provided WebMCP/API shortcuts do not prove controls work. Do not hide app defects with fixme/skip, changed acceptance criteria or regenerated visual golden images. Match fixtures, fonts and render environment when comparing screenshots; golden changes need the appropriate design approval.

Review keyboard/focus behavior, responsive overflow, contrast and other applicable accessibility rules. Automated scanners do not certify the entire experience. `web-design-guidelines` is a code audit input: fetch and save its guideline snapshot/hash/time, reconcile it with the approved app baseline, and merge findings into the capability review.

Apply React/composition/performance rules to the actual framework and version. Do not introduce Next.js/RSC/server actions, BFFs, SWR or a UI library solely from an upstream example. Browser viewport emulation is a web/prototype check and does not verify React Native on a phone.
