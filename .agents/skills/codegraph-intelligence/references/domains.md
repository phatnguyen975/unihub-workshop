# Domain Guidance

Routing is two-tier: map the task to a domain here, then to the specific use case inside it. Each domain follows the same shape: trigger signals, primary commands, required input, how to read the output, how it feeds back into the task, and known limitations.

## Contents

- [A. Onboarding / Context Priming](#a-onboarding--context-priming)
- [B. Debugging & Root Cause Tracing](#b-debugging--root-cause-tracing)
- [C. Safe Refactoring](#c-safe-refactoring)
- [D. Code Review / Change Impact Assessment](#d-code-review--change-impact-assessment)
- [E. Architecture & Pattern Audit](#e-architecture--pattern-audit)
- [F. Code Hygiene](#f-code-hygiene-derived-best-effort)
- [G. Data-flow Tracing](#g-data-flow-tracing-structural-only-not-security-analysis)
- [H. CI / Test Impact Optimization](#h-ci--test-impact-optimization)

## A. Onboarding / Context Priming

- **Trigger signals:** "what does this repo/module do", "give me an overview of X", starting work in an unfamiliar codebase or service.
- **Primary commands:** `explore` (broad survey questions), `files` (structure), `status` (index coverage/health — also confirms there's actually something indexed to query).
- **Required input:** a target scope — a module/directory name or a broad natural-language question. No symbol name needed.
- **Reading the output:** treat `explore`'s result as a structural map, not a tutorial — it shows what connects to what and the actual source, not a prose explanation of intent. `files` gives layout without a filesystem scan.
- **Feeding it back:** use the map to decide where to look next for the real task, instead of opening files at random. This domain rarely stands alone — it's usually the first step before B, C, or E.
- **Limitations:** purely structural — no business-logic summary.

## B. Debugging & Root Cause Tracing

- **Trigger signals:** "how does X reach Y", "trace this error", "why does calling X eventually break Y", bug reports that span multiple files or layers, including mobile bugs crossing the JS↔native bridge.
- **Primary commands:** `explore` with the flow phrased as a question (fastest path for "how does A reach B"), `callers`/`callees` for one-hop expansion when the flow needs manual stepping, `node` to read a specific suspect symbol's full source and its immediate trail.
- **Required input:** either a natural-language description of the flow, or a specific symbol name once a suspect is identified.
- **Reading the output:** pay attention to edges marked as heuristic (dynamic-dispatch inferences — callbacks, interface→implementation, framework re-render triggers) versus edges taken directly from the AST (definite). Heuristic edges are still useful for debugging but are inferences, not guarantees.
- **Feeding it back:** use the returned source directly to reason about the bug — don't re-read the same files with a plain file-read tool. For cross-language bugs (iOS/React Native), CodeGraph's bridging edges can show a call path that a single-language parser would miss entirely.
- **Limitations:** this is static tracing, not a runtime debugger — it shows what _can_ call what, not what _did_ call what during a specific failing run. Confirm a traced path against the actual failure (logs, reproduction) rather than assuming it's the active path.

## C. Safe Refactoring

- **Trigger signals:** "is it safe to rename/change this", "what breaks if I change this signature", restructuring a module while keeping routes/API mappings intact.
- **Primary commands:** `query` to pin down the exact target symbol first (if not already known precisely), `impact` to compute the blast radius before touching it, `explore` to inspect the surrounding relations when the change is structural (e.g. moving a symbol between modules).
- **Required input:** the exact symbol being changed (name or, if ambiguous, resolved via `query` first).
- **Reading the output:** `impact`'s result is the full set of transitive callers reachable from the symbol — treat every listed call site as something that needs to be checked or updated, not just the direct callers.
- **Feeding it back:** use the blast radius to scope the actual refactor (which files need touching) and to decide whether the change is small enough to do directly or needs a staged rollout. For route/API changes specifically, confirm route-to-handler edges still resolve correctly after the change.
- **Limitations:** impact analysis is static — reflection, string-based dynamic lookups, or anything not expressible as a parseable reference may not surface. Treat a clean `impact` result as "no statically-visible breakage," not an absolute guarantee.

## D. Code Review / Change Impact Assessment

- **Trigger signals:** "what does this diff/PR affect", "summarize the impact of these changes", reviewing someone else's change before approving it.
- **Primary commands:** `query` to resolve symbol names mentioned in the diff to exact graph entries, `impact` on each changed symbol to get its blast radius, `affected` (fed the changed file list, e.g. from `git diff`) to get the impacted tests, `node` to pull the actual changed symbol's source for the reviewer to read in context.
- **Required input:** a diff or a list of changed files (required — this domain can't run without knowing what changed).
- **Reading the output:** combine `impact` (what else might break) with `affected` (what tests actually cover that risk) to form a complete picture of review risk — neither alone is sufficient.
- **Feeding it back:** turn the combined result into a short review note: what the change touches structurally, what's covered by existing tests, and what isn't. Use the Output format template in `SKILL.md` for this.
- **Limitations:** same static-analysis caveats as Refactoring (C) — this assesses structural risk, not correctness or business-logic risk.

## E. Architecture & Pattern Audit

- **Trigger signals:** "do all X implement Y consistently", "map out our API surface", "what are our module boundaries", "is this pattern used consistently across the codebase".
- **Primary commands:** `query` to list all symbols of a given kind across the codebase (e.g. all classes implementing a given interface, all routes), `explore` to inspect the relations around a specific symbol in depth, `node` to pull full source for symbols under audit.
- **Required input:** the kind of symbol or pattern being audited (e.g. "all controllers", "all routes"), not necessarily a single symbol name.
- **Reading the output:** look at `extends`/`implements`/`imports` edges to judge consistency — e.g. a controller that doesn't show an `implements` edge to the expected interface is a real inconsistency, not a false negative, since these edges come from the AST. Route detection covers 17+ backend frameworks and several frontend routers, including `navigates` edges for frontend routing.
- **Feeding it back:** turn findings into a concrete list of outliers (symbols that don't match the expected pattern) rather than a general impression — this is what makes the audit actionable.
- **Limitations:** covers structural/typed patterns only; purely naming-convention or stylistic patterns (not expressed as an AST relationship) aren't detectable this way.

## F. Code Hygiene _(derived, best-effort)_

Not an advertised CodeGraph feature — this is a reasonable use of its base capabilities, with correspondingly lower confidence than the domains above. Flag findings here as candidates to double-check, not confirmed facts.

- **Trigger signals:** "find unused/dead code", "check for duplicate/colliding names".
- **Primary commands:** `query` to list exported symbols of a given kind, `callers` on each to check for zero results.
- **Required input:** scope (a module, a file, or the whole project).
- **Reading the output:** a symbol with zero callers is a _candidate_ for dead code, not a confirmed one — it may be a public API entry point, used via reflection/dynamic dispatch, or invoked from outside the indexed scope (e.g. from a separate repo or a non-indexed file type).
- **Feeding it back:** present candidates as "worth checking," with the caveat above, rather than recommending removal outright.
- **Limitations:** static call-graph analysis cannot distinguish "truly unused" from "used in a way the graph can't see." Always flag this uncertainty when surfacing a finding from this domain.

## G. Data-flow Tracing _(structural only, not security analysis)_

Explicitly **not** a security or taint-analysis feature — CodeGraph has no sanitization-awareness. This domain only maps structural adjacency.

- **Trigger signals:** "trace this route to where the data ends up", a first-pass map of how an entry point reaches a sensitive operation (e.g. a database call), as a starting point for a real security review.
- **Primary commands:** `explore`, phrased as a flow question from the entry-point (route/handler) forward.
- **Required input:** the entry point (route or handler) to start from.
- **Reading the output:** treat the result as "these are the places this request _can_ structurally reach," not an actual data-flow or taint analysis. There is no information here about sanitization, validation, or whether untrusted input is actually dangerous at any given point.
- **Feeding it back:** use it to scope where a human (or a dedicated security tool) should look next — never present this domain's output as a completed security assessment.
- **Limitations:** see above — narrower and more caveated than any other domain here. Don't let a task described as "security review" lean on this domain alone.

## H. CI / Test Impact Optimization

- **Trigger signals:** "which tests should I run for this change", wiring a CI step to skip unaffected tests, speeding up a test suite for a given diff.
- **Primary commands:** `affected`, fed the changed file list (typically from `git diff`).
- **Required input:** a list of changed files, or a diff to derive them from.
- **Reading the output:** the result is the set of test files CodeGraph considers impacted by the given changes — static, not based on historical flakiness or runtime coverage data.
- **Feeding it back:** use the result to scope a targeted test run instead of running the full suite, e.g. `git diff --name-only | codegraph affected --stdin`.
- **Limitations:** as with Refactoring (C), coverage is as good as the static call graph — dynamically dispatched test triggers may not be caught.
