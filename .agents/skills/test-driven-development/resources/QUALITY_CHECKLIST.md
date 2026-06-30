# Test-Driven Development Quality Checklist

Run this checklist before considering any task, bug fix, or behavior change complete. Every item must pass.

## Process Discipline

- [ ] For every new behavior added, a test was written and run **before** the corresponding production code existed.
- [ ] Every such test was confirmed to **fail for the expected reason** (not a typo, not a compile error in the test itself) before the implementation was written.
- [ ] After implementation, the new test passes, and it was confirmed to pass for the right reason (the assertion actually exercises the intended behavior).
- [ ] A refactor step was performed (or consciously skipped because there was genuinely nothing to improve) after each Green step, and the full test suite was re-run after any structural change.

## Layer Placement

- [ ] Every test lives at the correct architectural layer for what it's actually verifying (Domain / Application / Infrastructure / Presentation, or your project's equivalent terms).
- [ ] No business rule that belongs to the Domain layer is re-verified redundantly in an Application, Infrastructure, or Presentation test.
- [ ] Domain-layer tests use zero mocks and zero framework dependency.
- [ ] Application-layer tests mock every outbound port — no real repository, cache client, or external client is wired in.
- [ ] Infrastructure-layer tests mock the underlying driver/client and verify query/request construction and response mapping — they do not attempt to verify a real external system's behavior (that would be an integration test if the project applies integration test).
- [ ] Presentation-layer tests mock the use case(s) they call and verify routing, validation, auth enforcement, and response shape — not business logic that belongs one or two layers down.

## Test Quality

- [ ] Every test name describes the behavior and the condition under which it holds — not just the name of the method under test.
- [ ] Every test verifies exactly one logical behavior. Tests covering multiple unrelated scenarios in one method have been split.
- [ ] Every assertion checks an observable outcome (return value, thrown exception/error, a recorded call to a mocked collaborator with the expected arguments) — never a private field or internal call sequence that isn't part of the unit's actual contract.
- [ ] **Sanity check performed:** For each new test, mentally (or actually) replace the implementation's core logic with an obviously wrong stub (e.g. `return null`, `return true` unconditionally) and confirm the test would fail. If it wouldn't, the test isn't actually verifying the behavior it claims to.
- [ ] No test depends on execution order, real wall-clock time, real randomness, or any other source of non-determinism. Every test produces the same result every time it runs, in any order.
- [ ] No test was left in a "flaky, just rerun it" state. A flaky test was either fixed or its root cause (shared mutable state, timing dependency, unmocked clock/random source) was identified and eliminated.

## Mocking Discipline

- [ ] Every mock corresponds to a true I/O/process boundary (database, cache, broker, external HTTP/RPC call, filesystem) or another layer's port/interface — not the class under test's own internal private logic.
- [ ] For each test double used, the _least_ powerful option that still gets the job done was chosen — a real implementation or fake was preferred over an interaction-verifying mock wherever the real/fake option was fast, deterministic, and side-effect-free.
- [ ] No test has more lines of mock setup than lines of meaningful assertion. (If it does, re-examine whether the test is actually verifying behavior or just verifying that mocks were configured and called as expected.)
- [ ] Domain entities and value objects used as test fixtures in Application-layer tests are real, simply-constructed instances — not mocked.

## DAMP vs. DRY Balance

- [ ] Test setup is as explicit and self-contained as is reasonable for readability — shared fixtures are used for genuine, simple, obviously-correct repeated setup, not as a way to hide what a test actually depends on.
- [ ] No single shared fixture or helper is depended on by so many unrelated tests that changing it for one test's needs risks silently breaking others.
- [ ] Parameterized tests are used for the same behavior across multiple input/boundary values, rather than many near-duplicate test methods — where the parameter table itself effectively documents the specification.

## Bug Fixes Specifically

- [ ] Every bug fix in this change has a regression test that reproduces the original incorrect behavior.
- [ ] That regression test was confirmed to fail (reproducing the bug) _before_ the fix was applied.
- [ ] The fix addresses the root cause identified through localization — not a surface symptom that happens to make the one failing test pass while leaving the underlying defect in place for a slightly different input.

## Beyoncé Rule (Regression Safety)

- [ ] The full test suite for the affected module/component was run after the change — not just the newly written test(s).
- [ ] Any pre-existing test that started failing as a result of this change was investigated and resolved (either the change has an unintended side effect that needs fixing, or the old test's expectation was genuinely outdated and was updated deliberately, not silently deleted to make the suite pass).

## Pyramid Balance Check

- [ ] The bulk of new test coverage for this change is at the unit tier — integration and end-to-end tests, if any were added, supplement unit coverage rather than substitute for it.
- [ ] No integration or end-to-end test was added to compensate for a missing unit test for a specific behavior; the corresponding unit test exists.
- [ ] If an integration test was added, it verifies a real seam (a real persistence round-trip, a real message round-trip, a real external client against a sandboxed dependency) — not business logic already covered by a unit test one tier down.
- [ ] If an end-to-end test was added, it covers a genuinely critical path — not an edge case that belongs at the unit tier.
- [ ] Whatever this specific project's own rules define as its testing policy (e.g. unit-only, or a specific pyramid ratio) was followed — this skill does not override the project's policy in either direction.
