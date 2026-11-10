# Code Review: <Feature Name / Change Description>

> **Spec:** <caller spec path> <!-- omit if no associated feature spec -->  
> **API Contract:** <approved public contract path, if applicable> <!-- omit if not applicable -->  
> **Reviewed change size:** <approximate line count> <!-- per Change Sizing guidance -->  
> **Scope / mode:** <server | client-workflow | standalone>  
> **Status:** REVIEWED — <awaiting human disposition | returned to authorized caller>

## Summary

| Severity   | Count    |
| :--------- | :------: |
| Critical   | <number> |
| Should-fix | <number> |
| Nit        | <number> |
| FYI        | <number> |

<!-- One or two sentences: overall impression — is this change in good
shape, does it need targeted fixes before merge, or does it need
significant rework? -->

## Reviewability

<!--
State whether the change's size allowed a full, single-pass review. If
it exceeded the "too large" threshold, say so here explicitly and note
whether this review is therefore partial/scoped.
-->

- **Change size:** <within ideal range | acceptable | too large — review scoped to: ...>

## Axis-by-Axis Findings

<!--
Every axis gets a sub-section, even if empty. An axis with nothing to
report still gets an explicit "No issues found." — never omitted.
-->

### 1. Correctness

<!-- Reference: <caller spec path>, <approved public contract path, if applicable> -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found. Verified against FR-XX through FR-YY.

### 2. Design

<!-- Reference: this project's architecture/structure documentation -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found.

### 3. Security

<!-- Reference: server `security-and-hardening` or client `security-review`, if this change touches
auth/RBAC/payment/webhook/upload/external input -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found. <!-- or: Not applicable — this change touches no security-relevant surface. -->

### 4. Performance

<!-- Reference: server `performance-and-efficiency-engineering` or applicable client performance skills, if this change touches
database queries, collection processing, external calls, caching, or frequently executed paths -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found. <!-- or: Not applicable — this change has no performance-sensitive surface. -->

### 5. Test Coverage

<!-- Reference: this project's test-driven-development layer policy -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found. All new behavior has a corresponding test at the correct layer.

### 6. Style

<!-- Reference: this project's coding conventions -->

- **[<Severity>]** <Finding description and concrete impact> — `<file:line>`

_or:_ No issues found.

## Critical Findings Requiring Immediate Attention

<!--
If any Critical findings exist, restate them here as a flat, prioritized
list so they cannot be missed — even though they also appear under their
respective axis above. Omit this section entirely if there are no
Critical findings.
-->

1. <Critical finding, restated>

## Human Disposition

<!--
Filled in by the human reviewer after reading this review, not by the
agent that produced it. This is the authoritative record of what was
confirmed, disputed, or added.
-->

| Finding             | Agent Severity | Human Decision                  | Notes |
| :------------------ | :------------- | :------------------------------ | :---- |
| <Finding reference> | <Severity>     | Confirmed / Disputed / Deferred |       |

**Additional findings identified by the human reviewer (not caught by this review):**

- ...

## Caller Disposition (Authorized Client Workflow Only)

| Finding | In-scope action                       | Regression evidence |
| ------- | ------------------------------------- | ------------------- |
| <ID>    | <fixed / pending / requires approval> | <check/result>      |

This table is technical disposition, not human acceptance. Server mode uses Human Disposition above before `server-fix`.
