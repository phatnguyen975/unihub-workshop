# UI Design: <Platform>

> **Status:** <DRAFT|APPROVED>  
> **Revision:** <revision>  
> **Approval:** <explicit human decision/date/scope; pending until granted>

## Scope and Design Direction

Identify one platform, approved experience-map revision, audience and design goals. Describe representative foundation scenarios; do not create a shared web/mobile baseline.

## Rule Registry

Allocate stable platform-prefixed IDs such as WEB-UI-001 or MOB-UI-001. Each row specifies an observable criterion, scope and verification method. Reference detailed sections without duplicating conflicting rules.

| Rule ID | Criterion | Applies to | Verification |
| ------- | --------- | ---------- | ------------ |

## Semantic Tokens and Typography

Specify token roles and concrete values, font sources/fallbacks, type scale, spacing, color/contrast and relevant themes. Identify units and scaling behavior for the selected platform.

## Layout and Navigation

Define content widths, grids, spacing, navigation hierarchy and responsive behavior. For mobile include safe areas, keyboard, orientation and back navigation where relevant.

## Components, Variants, States and Interaction

Describe reusable controls, variants, state changes, focus/disabled/loading behavior and feedback. Distinguish design criteria from an unselected implementation library.

## Content and Feedback

Set language, terminology, labels, error/recovery copy, time/currency formatting and destructive-action confirmation criteria. Keep implementation explanations outside product copy.

## Accessibility and Motion

Define applicable semantic, contrast, focus, target-size, text-scaling, assistive-technology and reduced-motion criteria, with automated and human verification responsibilities.

## Platform Differences

Describe meaningful Android/iOS or browser/input differences for this platform. Mark inapplicable items explicitly; HTML mobile previews do not establish native behavior.

## Verification Criteria and Human Review

List representative preview scenarios/viewports, screenshot comparison criteria and human approval scope. Record baseline/prototype revisions and pending inspection; never pre-fill approval.

Identify `docs/client/<platform>/foundation-prototype/index.html` and its revision. Record the exact local HTTP preview command, working directory, URL, named synthetic JSON scenarios and reset steps. Follow the Prototype Artifacts conventions in `specs/client/README.md`: plain HTML/CSS/JavaScript, no application build or live API. List which global patterns and rule IDs each representative screen/state demonstrates, the screenshots actually inspected and interaction outcomes. Keep required native-device evidence separate from HTML preview evidence.

Foundation approval covers these representative patterns and the platform rules. Each capability owns its detailed prototype and bundle approval under `specs/client/<platform>/<capability-id>/`; do not record blanket approval for unreviewed capability journeys.

## Approved Scoped Exceptions and Dependent Artifacts

Record exception ID, rule, reason, owner, approval and affected capabilities. Track impact of changes to components/prototypes/visual baselines; preserve prior accepted revisions.

Reuse rule IDs in capability specs; add only genuinely new shared criteria here. Change a rule at its source after approval/impact review. Design criteria require explicit foundation approval before dependent implementation.
