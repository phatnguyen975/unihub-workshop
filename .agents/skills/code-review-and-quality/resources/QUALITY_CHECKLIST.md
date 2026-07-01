# Code Review Quality Checklist

Run this checklist against a drafted review before presenting it. Every item must pass.

## Reviewability

- [ ] The change's approximate size was checked against the Change Sizing guidance.
- [ ] If the change exceeded the "too large" threshold, this was stated explicitly as the review's first finding, with the review's scope limitation (if any) noted alongside it.

## Reference Artifacts

- [ ] The feature's `SPEC.md` and `API_CONTRACT.md` (if any) were located and used as the correctness reference — not the reviewer's own unstated assumption of intended behavior.
- [ ] This project's architecture/structure documentation was consulted for the Design axis, not assumed from general convention alone.
- [ ] This project's coding-convention documentation (if any) was consulted for the Style axis.

## Six-Axis Coverage

- [ ] Correctness was reviewed against the spec/contract, including edge cases and error paths, not only the happy path.
- [ ] Design was reviewed for architectural layering compliance and module-boundary compliance (cross-referencing `modular-boundary-enforcement` where the change crosses a module boundary).
- [ ] Security was reviewed explicitly if the change touches authentication, authorization, payment, webhook handling, file upload, or any external input — and explicitly marked "Not applicable" with a one-line reason if it genuinely doesn't.
- [ ] Performance was reviewed when applicable using `performance-and-efficiency-engineering` criteria, including query efficiency, scalability risks, unbounded processing, external dependency latency, caching decisions, and algorithmic complexity.
- [ ] Performance was explicitly marked "Not applicable" with a reason if the change has no performance-sensitive surface.
- [ ] Test Coverage was reviewed for both presence (are there tests) and correctness of placement (are they at the right architectural layer, mocking what that layer's policy requires).
- [ ] Style was reviewed for naming, formatting, comment quality, and absence of dead/commented-out/debug code.
- [ ] Every one of the six axes has an entry in the output — "No issues found" is a valid, complete entry; a missing axis section is not.

## Severity Discipline

- [ ] Every finding has exactly one severity label (Critical, Should-fix, Nit, or FYI).
- [ ] Every Critical finding is restated in the "Critical Findings Requiring Immediate Attention" section, not only buried in its axis subsection.
- [ ] No finding's severity was inflated or deflated relative to its actual described impact (e.g. a genuine security gap is not labeled a Nit; a pure naming preference is not labeled Critical).

## Finding Quality

- [ ] Every finding states the concrete impact of the issue, not just an instruction to change something.
- [ ] Every finding references a specific location (file/line or equivalent) where practical, not a vague "somewhere in this change."
- [ ] No finding silently assumes a fix without stating what's actually wrong — a recipient should be able to understand the problem from the finding alone, without needing to ask "why."

## Boundaries of This Skill

- [ ] No fix was applied to the code by this review — only findings were produced for the human to confirm, dispute, or act on.
- [ ] The review's summary counts (by severity) are presented before the detailed findings, so a human can triage at a glance before reading further.
- [ ] If reviewing AI-agent-written code specifically, whether tests were genuinely written test-first was checked or explicitly flagged as unconfirmable from the diff alone — not silently assumed either way.
