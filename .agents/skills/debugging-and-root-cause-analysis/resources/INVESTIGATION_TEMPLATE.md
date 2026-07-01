# Investigation: <Short Description of the Failure>

> [!NOTE]
> Use this template for a non-trivial investigation — a production incident, a defect that took meaningful effort to localize, or anything worth a durable record for future reference. A quick, obvious one-line bug fix does not need this document; the regression test alone is sufficient record for it.

## Symptom

<!--
What was actually observed — the failing test name, the error message,
the user-visible incorrect behavior. Quote the exact evidence, not a
paraphrase.
-->

## Reproduction

<!--
The exact, minimal steps that trigger the failure reliably. If full
reproduction wasn't achievable, state that explicitly here along with
what was tried (see SKILL.md Step 1's timing/environment/state-dependent
guidance) and what's being monitored instead.
-->

**Steps:**

1. ...
2. ...

**Reproduces:** Reliably / Intermittently / Could not reproduce — see note below

## Localization

<!--
Which layer/component the failure was traced to, and the reasoning that
narrowed it there (what was checked at each boundary along the way).
-->

**Localized to:** <layer/component>

**Reasoning:**

## Hypothesis

<!--
What was believed to be the cause, stated explicitly, BEFORE the fix was
written. Include how it was verified or falsified.
-->

**Hypothesis:**

**Verification method:**

**Result:** Confirmed / Disproven (if disproven, note what was tried next)

## Root Cause

<!--
The actual mechanism producing the wrong behavior — not merely where it
became visible. Contrast with the symptom above if that distinction is
non-obvious.
-->

## Fix

<!--
What was changed, and why this is the narrowest change the diagnosis
supports — not a broader rewrite of adjacent code that merely looked
related.
-->

**Files changed:**

**Description:**

## Regression Guard

<!--
The test added to prevent recurrence. State explicitly that it was
confirmed to fail before the fix and pass after.
-->

**Test added:** `<file path or test name>`

**Confirmed fails before fix:** Yes
**Confirmed passes after fix:** Yes

## Verification

<!--
What was run to confirm the fix is safe — the regression test, the
surrounding suite, and (for a production incident) any runtime/
monitoring confirmation.
-->

- [ ] Regression test passes
- [ ] Surrounding/affected test suite passes
- [ ] Broader test suite passes (Beyoncé Rule)
- [ ] (If production incident) Runtime behavior confirmed corrected in the affected environment

## Follow-Up (Optional)

<!--
Anything noticed during this investigation that's worth addressing
separately but is out of scope for this fix — per
incremental-implementation's "Noticed But Not Touching" pattern.
-->
