---
name: test-driven-development
description: Drives implementation through Red-Green-Refactor — a failing test always exists before the production code that makes it pass. Defines a layered test pyramid (unit tests across four architectural layers, plus integration and end-to-end tests where warranted), with explicit guidance on what each layer verifies and what it mocks. Use when implementing any business logic, fixing a bug, or changing existing behavior. Do not use for pure configuration changes or dependency-version bumps with no behavior impact.
---

# Test-Driven Development

## Overview

This skill governs how every piece of business logic should be developed using the **Red–Green–Refactor** cycle: write a failing test first, implement the minimum code to make it pass second, structural cleanup third — repeated until the behavior is complete. The test defines the expected behavior before the implementation exists.

TDD is primarily a **unit-level design discipline**. Most of this guide focuses on unit tests across four layers — **Domain, Application, Infrastructure, and Presentation** — with clear boundaries on what to test and what to mock.

Integration and end-to-end (E2E) tests sit above the unit layer and serve a different purpose — validating that real components work together correctly and that critical user flows function end to end. While TDD is practiced mainly at the unit level, the project's own rules is the authoritative source for how much of each pyramid layer that specific project actually maintains — some projects deliberately run unit-tests-only; this skill does not assume either choice, and works correctly under both.

## When to Use

- Implementing any new business logic, however small.
- Fixing a bug (see the **Bug-Fix Discipline** section below — this is non-negotiable, not optional for "obvious" fixes).
- Changing the behavior of existing code, including refactors that are supposed to be behavior-preserving (the existing tests are what prove they actually are).

## When NOT to Use

- Pure configuration changes, documentation updates, dependency-version bumps, or formatting changes that alter no observable behavior.
- Generated boilerplate with zero logic (e.g. an empty DTO with only fields and no validation or behavior) — though once any logic is added (a validation rule, a computed property), it falls back under this skill immediately.

## Core Principles

1. **Red → Green → Refactor, every time, no exceptions.** Write a failing test that captures exactly one behavior. Write the minimum code to make it pass. Improve structure without changing behavior. Repeat.
2. **No code without a failing test that demanded it.** If you cannot point to the test that justified a line of production code, that line should not exist yet.
3. **The Beyoncé Rule — If you liked it, you should have put a test on it.** This cuts both ways for an AI agent specifically: it is not enough to test the new code you just wrote. You must also verify that the existing tests for the behavior your change touches still pass — a change that makes its own new test green while silently breaking three existing tests elsewhere is not a successful change.
4. **DAMP over DRY in test code.** Test code should read like a specification, even at the cost of some duplication between test methods. A test suite so abstracted that you cannot tell what a test verifies without reading three layers of shared helper functions has optimized for the wrong thing. Some repetition in test _setup_ is acceptable and often preferable to a fragile shared fixture that five unrelated tests depend on.
5. **Assert on observable outcomes, never on implementation details.** A test should verify what the code _produces_ (a return value, a thrown exception, a call made to a collaborator with the right arguments) — never _how_ it's internally structured. A test that breaks when you rename a private method without changing behavior is a badly designed test, not a sign the refactor was risky.
6. **Every test must be capable of failing.** A test that cannot fail under any circumstance (e.g. it asserts something trivially true, or it never actually exercises the code path it claims to) provides zero protection and zero documentation value — it is worse than no test, because it creates false confidence.

## The Test Pyramid

The pyramid has three classic tiers — **Unit, Integration, End-to-End** — ordered bottom to top by increasing scope, increasing cost, increasing slowness, and decreasing count. The further up the pyramid, the fewer tests you should have; the foundation should be overwhelmingly unit tests.

```text
          ╱╲
         ╱  ╲         E2E Tests (~5%)
        ╱    ╲        Full user flows, real browser
       ╱──────╲
      ╱        ╲      Integration Tests (~15%)
     ╱          ╲     Component interactions, API boundaries
    ╱────────────╲
   ╱              ╲   Unit Tests (~80%)
  ╱                ╲  Pure logic, isolated, milliseconds each
 ╱──────────────────╲
```

TDD's Red-Green-Refactor cycle operates almost entirely at the **Unit** tier. Within that tier, this skill further separates unit tests by architectural layer — each layer has a different scope of "the unit" and a different mocking boundary. Adapt the layer _names_ to whatever your project's own architecture documentation calls them; keep the underlying separation of concerns.

```text
                ▲  Fewest tests, narrowest scope per test
                │
Presentation    │  Routing, input validation, auth enforcement,
                │  response shape — use cases mocked
                │
Infrastructure  │  Query construction, mapping, serialization —
                │  the actual database/cache/broker client mocked
                │
Application     │  Use case orchestration, business workflow —
                │  all outbound ports mocked
                │
Domain          │  Pure business rules, invariants, state machines —
                │  zero framework dependency, zero mocks needed
                │
                ▼  Most tests, fastest, cheapest to write and run
```

**The pyramid is bottom-heavy by design:** The Domain layer should have the largest number of tests (cheapest to write, fastest to run, most precise when they fail), and each layer above it should have progressively fewer, because each layer above adds orchestration around logic already proven correct one layer down.

### Layer 1 — Domain

**What lives here:** Entities, value objects, domain events, domain exceptions, business invariants, state machines. Zero framework dependency — no ORM annotations, no dependency-injection annotations, no HTTP types.

**What to test:**

- Every invariant the entity/value object enforces (e.g. a quantity cannot be negative; a status cannot transition from a terminal state).
- Every valid state transition, asserting the resulting state and any domain event produced.
- Every invalid state transition, asserting the correct exception/error is raised — not just that _something_ fails.
- Value object equality and validation rules.
- Pure business calculations, with edge cases (zero, boundary values, empty collections) — not just one representative happy-path number.

**What NOT to test here:** Anything requiring a database, a clock/time source from the OS, network access, or any framework runtime. If a test at this layer needs any of those, the logic is not actually pure domain logic yet — it needs to be separated first.

**Rules for domain tests:**

- No mocks at this layer. If you find yourself wanting to mock something in a domain test, that is a signal the class under test has an unwanted dependency that does not belong in the domain layer.
- Tests must run in milliseconds. No I/O, no network, no database.
- Every domain exception must have at least one test asserting it is thrown under the correct condition.
- Every valid state transition must have a test asserting the new state and any emitted domain events.

**Test count expectation:** Highest of all four layers. Domain logic is where the most edge cases live and where tests are cheapest to write.

### Layer 2 — Application

**What lives here:** Use cases / application services that orchestrate domain objects and call out to outbound ports (repository interfaces, external service interfaces) without knowing their concrete implementation.

**What to test:**

- **The happy path:** Given valid input, the use case calls its collaborators in the correct order, with the correct arguments, and returns the correct result.
- **Every error path:** When a port throws or returns a failure signal, the use case translates it into the correct application-level outcome (not a leaked low-level exception type).
- Orchestration logic only — business _rules_ are the domain layer's job; an application-layer test should not be re-verifying domain invariants that are already covered one layer down (see DAMP vs. redundant coverage in **Anti-Patterns** section).
- Any cross-cutting concern this layer is responsible for (e.g. replaying a cached result when a retry-safe operation is invoked a second time, if that orchestration lives at this layer in your architecture).

**Rules for application layer tests:**

- All outbound ports (repository interfaces, gateway interfaces, clock/ID-generator abstractions) must be mocked. Do not use real repositories, real cache clients, or real HTTP clients.
- Each test covers exactly one scenario from the spec's acceptance criteria.
- Never assert on internal implementation details — assert on observable outcomes (return values, mock interactions, thrown exceptions).

**Test count expectation:** Second-highest. One test per meaningful scenario in the use case's contract — happy path plus every distinct error/edge condition.

### Layer 3 — Infrastructure

**What lives here:** Concrete implementations of the outbound ports defined by the Application layer — repository implementations, cache adapters, external API client adapters, message-broker publishers.

**What to test:**

- **Query/command construction logic** — The adapter builds the correct query, the correct cache key, or the correct outbound request shape for a given input. Assert on the constructed query/request, not on a real round-trip to a real datastore.
- **Mapping logic** — Data coming back from the underlying client/driver (in whatever raw shape the test double returns) is correctly mapped to/from the domain or application-level type.
- **Error translation** — A failure signal from the underlying client (e.g. a constraint violation, a timeout, a non-2xx response) is correctly translated into the application-level exception/error type the Application layer expects.

**What NOT to test here:** Whether the real database actually enforces a given constraint, whether the real cache actually expires a key on schedule, whether the real broker actually delivers a message — those are properties of the external system, not of this adapter's logic, and verifying them requires an integration test, which this skill's governing policy excludes. Test the adapter's _logic_, not the external system's _behavior_.

**Mocking policy:** The underlying client/driver (database driver, cache client, HTTP client, broker client) is mocked or faked at the lowest practical seam — e.g. mock the driver/client object the adapter calls, not a real connection. The adapter's own logic — how it builds a query/request from its inputs and how it maps a response back — is what gets exercised.

**Test count expectation:** Lower than Application — most adapters are thin, and the bulk of meaningful logic to test is in query/mapping construction, not in orchestration.

### Layer 4 — Presentation

**What lives here:** Controllers/handlers, request/response DTOs, input validation rules, route definitions.

**What to test:**

- **Routing:** The correct verb/path maps to the correct handler.
- **Input validation:** Malformed or missing required fields produce the project's defined validation-error response — not a generic 500 or an unhandled exception.
- **Authorization enforcement at this boundary:** Unauthenticated requests and insufficiently-privileged requests are rejected with the project's defined responses for those cases.
- **Response shape:** The success response matches the project's contract exactly (envelope, status code); the error response matches the project's error format exactly.

**Mocking policy:** The use case(s) this layer calls are mocked. This layer is tested as a thin translation boundary between the network protocol and the use case it invokes — it should not be re-verifying business logic that the Application and Domain layers already cover.

**Test count expectation:** Lowest of the four layers — one to a few tests per endpoint/handler covering routing, validation, auth, and response-shape conformance; not a test per business scenario (those belong one or two layers down).

### Integration Tests

**What they verify:** That two or more real components actually cooperate correctly across a real boundary — a real repository implementation against a real (or realistically faked, e.g. containerized) database, a real message consumer against a real broker, a real HTTP client against a real downstream service in a sandboxed environment. Unlike the Infrastructure unit-test layer above (which mocks the underlying client and tests only the adapter's own logic), integration tests intentionally remove that mock to verify the seam itself — that the query you built is actually valid SQL the real database accepts, that the message you serialized is actually deserializable by the real consumer on the other end.

**What to test:**

- That a real persistence round-trip (write then read) produces the data you expect, including database-enforced constraints (uniqueness, foreign keys, cascade behavior) that a mocked unit test cannot verify.
- That a real message published is correctly received and processed by its real consumer, including serialization format compatibility.
- That a real external-service client correctly handles the _actual_ response shape of a sandboxed/staging version of that service — not just the shape your mock assumed it would be.
- Failure modes that only manifest with the real component: connection timeouts, real error responses, real constraint violations.

**What NOT to test here:** Business logic and edge cases already covered by unit tests one tier down. An integration test re-litigating every domain invariant is redundant — it should verify the _integration_, not re-verify logic the unit tests already proved correct in isolation.

**Mocking policy:** Minimal to none for the specific seam under test — that's the point. External services entirely outside your control (third-party APIs you don't operate) are typically still faked via a sandbox/test double at this tier, since you cannot guarantee their availability or state for repeatable tests; internal infrastructure you own (your own database, your own broker) is exercised for real, often via a disposable, ephemeral instance.

**Where they fit relative to TDD:** Usually written _after_ the corresponding unit-level behavior is already proven via Red-Green- Refactor, as a separate verification that the real integration seam behaves as the mocked unit test assumed it would — not as the primary mechanism for driving design decisions.

### End-to-End Tests

**What they verify:** That a complete, user-facing flow works correctly through the real system, end to end — typically driven through the real API or UI, against an environment that resembles production as closely as practical.

**What to test:** Only the **critical paths** — the handful of flows whose failure would be catastrophic or highly visible (e.g. completing a core transaction, signing in). E2E tests are the most expensive and slowest tier; covering every edge case here instead of at the unit tier is a known anti-pattern (see **Anti-Patterns** section).

**Mocking policy:** Little to none — the point of this tier is to exercise the real system as a user would experience it. Genuinely unavailable third-party dependencies outside your control may still be faked, but everything you own should be real.

**Where they fit relative to TDD:** Outside the Red-Green-Refactor cycle itself. They are a final confidence check on top of a foundation of passing unit (and where relevant, integration) tests — not a substitute for them, and not where day-to-day design decisions get made.

## Workflow

### Step 1 — Identify the Single Behavior Under Test

Before writing anything, state in one sentence the single behavior this cycle will add. If the sentence contains "and," it is two behaviors — split it into two cycles.

### Step 2 — RED: Write the Failing Test

Write a test that exercises exactly that one behavior, at the correct architectural layer (see the pyramid above), following your project's test-naming convention (commonly `should<ExpectedOutcome>_when<Condition>` or an equivalent descriptive pattern your project has already adopted). If there are contraints on the input (e.g. a required field, a maximum length, a non-nullable value), must use the domain testing technique to create valid and invalid test cases.

Structure the test body with **Arrange-Act-Assert** — Set up the scenario (Arrange), perform the one action under test (Act), verify the outcome (Assert). Keeping these three parts visually distinct — even with a blank line or comment between them — makes a test readable as a specification at a glance, and makes it obvious when a test is doing too much (e.g. two "Act" steps means it's testing two behaviors, not one).

Run it. **Confirm it fails, and fails for the expected reason** — a missing method, a wrong return value, an unhandled case. A test that "fails" because of a typo in the test itself is not a valid Red step.

### Step 3 — GREEN: Write the Minimum Code to Pass

Write only enough production code to make the failing test pass. Do not implement the next three behaviors you can already see coming — that is scope creep into future RED steps that haven't been written yet, and it produces unverified code (see **Anti-Patterns** section).

Run the full test suite for the unit being changed, not just the new test. Confirm the new test passes and nothing else broke (Beyoncé Rule).

### Step 4 — REFACTOR: Improve Structure Without Changing Behavior

With the test green, look for duplication, unclear naming, or structure that would make the next cycle harder. Improve it. Run the full test suite again after every structural change — refactoring is only safe when tests confirm behavior didn't move.

If a refactor reveals the test itself was poorly designed (e.g. it was accidentally coupled to an implementation detail), fix the test as part of this step — a fragile test is a defect in the test suite, not a reason to avoid refactoring the production code.

### Step 5 — Repeat

Return to Step 1 for the next behavior. Continue until every behavior in the current task's scope (per the spec's Acceptance Criteria, if this work traces to a formal spec) has a corresponding passing test.

### Step 6 — Self-Review Against the Quality Checklist

Before considering the task complete, run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md).

## Bug-Fix Discipline

Fixing a bug follows the same Red-Green-Refactor discipline, with one addition: **The failing test must reproduce the bug before any fix is attempted.**

1. Write a test that captures the exact incorrect behavior currently observed. Run it. Confirm it fails in the way the bug actually manifests (not a different, coincidental failure).
2. Fix the root cause — not the symptom (see [`debugging-and-root-cause-analysis`](../debugging-and-root-cause-analysis/SKILL.md) for how to localize the actual cause before patching anything).
3. Run the new test. Confirm it now passes.
4. Run the full surrounding test suite. Confirm nothing else regressed.

A bug fix with no regression test attached is not considered fixed by this skill's standard — it is a fix that can silently come back.

## What to Mock, and What Not to Mock

This is the single most common source of low-quality test suites, so it gets its own explicit treatment.

**Preference order, most to least preferred:**

1. **Real implementation** — highest confidence; exercises actual logic and catches real bugs a mock can't.
2. **Fake** — a lightweight, in-memory working implementation of a dependency (e.g. an in-memory repository instead of a real database client). Behaves correctly, just without the real I/O.
3. **Stub** — returns canned data for a given input; no real behavior.
4. **Mock (interaction-verifying)** — verifies that specific calls were made with specific arguments. Use this sparingly — it is the _least_ preferred option, not the default.

Reach for a mock only when the real implementation is too slow, non-deterministic, or has side effects you cannot control (an external HTTP API, sending an email, calling a payment gateway). Defaulting to interaction-verifying mocks for everything produces tests that pass while production breaks, because they prove the mocks were called as configured — not that the resulting behavior is correct.

**Should mock:**

- Anything that crosses a process or I/O boundary — a database driver, a cache client, an HTTP client, a message-broker client, the filesystem, the system clock (if the logic depends on "now"), a random/ID generator (if determinism matters for the assertion).
- Anything owned by a different layer that the layer under test depends on through an interface/port — the test for layer N mocks layer N+1's interface, never reimplements layer N+1's real logic inline.

**Do NOT mock:**

- Simple, fast, deterministic value objects or pure functions. If a collaborator has no I/O and no meaningful internal state, calling the real thing is both faster to write and a better test than mocking it.
- The class under test's own internal private logic. If you find yourself wanting to mock a private method to isolate "the rest" of the class, the class likely has too many responsibilities — split it instead of mocking around the problem.
- Domain entities and value objects when testing Application-layer orchestration. Use real, simply-constructed domain objects as test fixtures; only the _ports_ (repositories, gateways) get mocked, not the domain model flowing through them.

**Signal you're over-mocking:** If a test has more mock setup lines than assertion lines, or if the test would still pass after deleting the production code's actual logic and replacing it with `return null`, the test is verifying that mocks were called, not that behavior is correct — this is a known failure mode, not a stylistic nitpick.

## What a Good Test Looks Like, vs. a Bad One

| Trait           | Good Test                                                                                       | Bad Test                                                                                                          |
| :-------------- | :---------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------- |
| Scope           | Verifies one behavior                                                                           | Verifies several unrelated things, so a failure doesn't tell you what broke                                       |
| Name            | Describes the behavior and condition (e.g. `shouldRejectTransition_whenAlreadyInTerminalState`) | Describes the method under test only (e.g. `testTransition`), telling you nothing about what's being checked      |
| Assertions      | Asserts on observable outcome (return value, thrown exception, recorded interaction)            | Asserts on internal/private state, or doesn't assert anything meaningful at all                                   |
| Setup           | As DAMP as needed to be readable standalone; shared setup is simple and obviously correct       | Relies on a complex shared fixture five other tests also depend on, so changing it risks breaking unrelated tests |
| Failure message | Failing this test points precisely at what's wrong                                              | Failing this test requires stepping through a debugger to understand what it was even checking                    |
| Determinism     | Same result every run, regardless of order or timing                                            | Flaky — depends on real time, real randomness, or test execution order                                            |
| Mocking         | Mocks only true I/O/process boundaries                                                          | Mocks the class's own collaborators so thoroughly that the test only proves the mocks were configured correctly   |

## Anti-Patterns

| Anti-Pattern                                                                                                                       | Why It Fails                                                                                                                                                                                                                                                                                                                                                                                  |
| :--------------------------------------------------------------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Writing the production code first, then writing a test that exercises whatever was written                                         | Inverts TDD entirely — the test can only confirm the code does what it does, not that it does what it _should_. This produces tests with zero design value and zero ability to catch the bug they should have caught                                                                                                                                                                          |
| Writing one giant test that covers the happy path and five edge cases at once                                                      | A failure tells you "something in this scenario is wrong" instead of pointing at the specific behavior that broke; also makes the test harder to read as a specification                                                                                                                                                                                                                      |
| Re-verifying the same business rule at every layer                                                                                 | A rule that belongs to the Domain layer should be tested once, there. Re-testing it again in the Application or Presentation layer test inflates the suite without adding protection, and means the rule has to be kept in sync in multiple places when it changes                                                                                                                            |
| Mocking so heavily that the test only proves "the mocks were called as configured"                                                 | See the over-mocking signal above — this provides false confidence and breaks on harmless refactors that change _how_ a result is produced without changing _what_ is produced                                                                                                                                                                                                                |
| Asserting on an implementation detail (a private field, an internal call sequence that isn't part of the contract)                 | Couples the test to _how_ the code works rather than _what_ it guarantees; turns every safe refactor into a wave of unrelated test failures                                                                                                                                                                                                                                                   |
| Skipping the RED step "because the implementation is obviously correct"                                                            | The most dangerous bugs are the ones that look obviously correct. The Red step is what proves the test can actually fail — skip it, and you may have a test that would pass even against broken code                                                                                                                                                                                          |
| Treating a flaky test as "fine, just rerun it" instead of fixing it                                                                | A flaky test is a test that has stopped doing its job. Every rerun-until-green erodes trust in the whole suite and trains the team (or the agent) to ignore failures                                                                                                                                                                                                                          |
| Writing the majority of test coverage as integration or end-to-end tests instead of unit tests                                     | Inverts the pyramid — the suite becomes slow, brittle, and expensive to maintain, and failures become harder to localize (a failing E2E test tells you "something in this long flow broke," not which specific behavior). The fix is not zero integration/E2E tests; it's making sure unit tests carry the bulk of the coverage and the upper tiers stay focused on seams and critical paths. |
| Adding an integration or end-to-end test to compensate for a missing unit test, instead of writing the unit test that should exist | A higher-tier test that passes does not tell you _which_ unit-level behavior is correct — when it eventually fails, you're debugging a much larger surface area than necessary. Write the unit test for the specific behavior first; add the higher-tier test for the seam or flow it's actually meant to verify.                                                                             |

## Best Practices

- Name tests as behavior specifications, not as method-under-test labels. A reader should understand what the system guarantees by reading test names alone, without opening the test bodies.
- Keep one logical assertion concern per test. Multiple `assert`/`expect` statements are fine if they all verify facets of the _same_ outcome (e.g. checking several fields of one returned object) — but don't bundle unrelated scenarios into one test method. A test named "validates input correctly" that asserts an empty value is rejected, _and_ that whitespace is trimmed, _and_ that a too-long value is rejected, is really three tests wearing one name — split it into three, each independently nameable and independently failing.
- When a test needs complex setup, prefer a small, explicit builder or factory function over a deeply shared fixture — this keeps DAMP intact while still reducing repetition.
- Write the test's assertion before its setup, mentally, even if you type them in the conventional order. Knowing exactly what you're proving keeps the setup minimal and relevant.
- When a test for a bug fix is added, name it so the bug is traceable later (e.g. reference an issue number or a one-line description of the defect, per your project's convention).
- Prefer parameterized tests for the same behavior across multiple input values (especially boundary values), rather than copy-pasting near identical test methods — this is one of the few cases where reducing duplication does not sacrifice DAMP, because the parameter table itself reads as the specification.
- Run the _entire_ affected test suite before declaring a cycle done — not just the test you just wrote. The Beyoncé Rule is only honored if you actually check the things you'd be sad to find out were broken.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] Every new behavior has a test that was written and confirmed failing _before_ the implementation code existed.
- [ ] Every test is at the correct architectural layer, mocking exactly what that layer's policy requires — no more, no less.
- [ ] No test mocks anything other than a true I/O/process boundary or another layer's port/interface.
- [ ] No business rule is re-verified redundantly across multiple layers.
- [ ] Every test name describes the behavior and condition, not just the method under test.
- [ ] The full surrounding test suite was run after the change, not just the new test (Beyoncé Rule).
- [ ] No test asserts on a private/internal implementation detail.
- [ ] No flaky test was left "to rerun later" — every test is deterministic.
- [ ] Every bug fix has a regression test that reproduces the original failure before the fix and passes after.

## Output

This skill does not produce a standalone document artifact — its output is the test code itself, committed alongside the production code it drives, following the project's own test file location and naming conventions (see your project's architecture/structure documentation for the exact paths).

**Worked example:** [`examples/example-tdd-cycle.md`](examples/example-tdd-cycle.md) walks through a complete Red-Green-Refactor cycle across all four **unit test** layers, for the same domain-neutral "resource reservation with capacity limit" feature used in the `spec-driven-development` and `api-contract-design` skill examples — illustrative only, with pseudocode rather than any specific language or framework syntax.
