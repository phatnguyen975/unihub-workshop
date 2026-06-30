---
name: modular-boundary-enforcement
description: Verifies that cross-module access goes only through each module's deliberate public surface, never into its internals, and that this is enforced by an automated, build-breaking check rather than left to memory or code review alone. Use when implementing or reviewing code in a modular monolith or any multi-module/multi-package codebase with intended boundaries between its parts — specifically when a change introduces a new module-level class, a call from one module into another, or a new cross-module dependency. Do not use for purely intra-module changes that never cross a module/package boundary.
---

# Modular Boundary Enforcement

## Overview

A modular monolith gets the deployment simplicity of a single application and the team-autonomy and replaceability benefits normally associated with microservices — but only as long as its module boundaries stay real. The moment a module reaches into another module's internals "just this once, because it's faster," the boundary stops being architecture and becomes a polite suggestion. Six months later, every module quietly depends on every other module's implementation details, and the system is a distributed-feeling monolith with none of the benefits of either approach.

**This skill governs how to keep boundaries real:** Every module exposes a single, deliberate public surface; everything else inside it is inaccessible to other modules; and an automated, build-breaking check — not a wiki page, not a diagram, not a reviewer's memory — is what actually enforces this on every change.

## When to Use

- Implementing a new class, type, or component that lives inside one module of a multi-module codebase.
- Writing any code that calls from one module into another.
- Introducing a new cross-module dependency, however small it looks.
- Reviewing a change for a codebase that has declared module boundaries (whether via packages, namespaces, folders, or a framework-level module concept).

## When NOT to Use

- A change entirely contained within one module, with no new or changed call into any other module.
- A genuinely single-module project with no declared boundaries to enforce.

## Core Principles

1. **Every module has exactly one public surface.** Each module exposes a small, deliberate set of types/interfaces/functions as its public API. Everything else inside the module — domain models, repository implementations, internal helper logic — is inaccessible from outside the module. There is no second, informal way to reach a module's internals.
2. **The Dependency Rule applies at the module level, not just within one module's internal layers.** Just as an inner architectural layer should never depend on an outer one, a module's internals should never be depended on by another module's internals. Cross-module dependencies point only at the declared public surface — never deeper.
3. **Enforcement must be automated and build-breaking, not advisory.** A rule that lives only in a document or a diagram erodes the first time someone takes a shortcut under deadline pressure, and nothing catches it. The rule must be expressed as a check that fails the build the moment it's violated — this is what actually protects the boundary over the life of the project, not the team's intentions.
4. **Code review is the last line of defense, not the first.** A human reviewer can miss a boundary violation — it's an easy thing to miss, and reviewing for it consistently is expensive in time and attention. Automated checks exist precisely so that boundary correctness doesn't depend on a reviewer catching it every single time.
5. **Cross-module communication should prefer asynchronous, decoupled mechanisms over direct synchronous calls where the relationship allows it.** A direct call from module A into module B's public surface couples their availability and deployment timing. An event-based or message-based interaction (module A publishes a fact; module B reacts independently) keeps the coupling looser and makes it far cheaper to later extract a module into its own service if that ever becomes necessary — without requiring this skill's project to actually have that goal today.
6. **A boundary violation found late is architectural debt, not a one-off mistake to patch around.** Treat every violation caught by review or by the automated check as something to fix at the root — move the access through the proper public surface — never by quietly widening the public surface just enough to legitimize the shortcut that was already taken.

## The Four Layers of Enforcement

Boundary enforcement is strongest when it is layered — each layer catches violations the one below it would miss.

```
      ▲  Catches what nothing else can — judgment, intent, "is this
      │  the right boundary to begin with"
Code Review
      │
      │  Catches violations automatically, fails the build, runs on
      │  every change — the primary mechanism this skill relies on
Architectural Fitness Functions
      │
      │  Prevents some violations from compiling at all
Access Modifiers / Visibility
      │
      │  Makes violations visually obvious and physically separates
      │  modules — the first, weakest line of defense
Namespace / Package / Folder Structure
      ▼
```

### Layer 1 — Namespace / Package / Folder Structure

Each module occupies one top-level namespace, package, or folder. This doesn't prevent a violation by itself, but it makes one _visible_ — a cross-module reference is immediately recognizable by its path/namespace alone, rather than being indistinguishable from an intra-module reference.

### Layer 2 — Access Modifiers / Visibility

Use the language's own visibility mechanism to make a module's internals genuinely unreachable, not just "not supposed to be reached." Whatever your language calls this (`internal`, `private`, an unexported identifier, a package-private modifier) — apply it to everything in a module that isn't a deliberate part of its public surface. This is the only layer that can stop a violation from compiling or running at all, rather than merely flagging it after the fact.

### Layer 3 — Architectural Fitness Functions (The Primary Mechanism)

A fitness function is an automated, executable check that asserts an architectural rule and fails the build when the rule is violated. This is the layer that actually keeps boundaries real over the life of a project — not because it's clever, but because it runs on every single change, unconditionally, regardless of who's making the change or how much deadline pressure they're under.

Minimum rule set every modular codebase should express as fitness functions:

- No module may depend on another module's internal package/namespace — only on its declared public surface.
- No circular dependency exists between modules.
- A module's persistence/data-access types (repository implementations, ORM entities, schema-bound types) are never referenced from outside that module.
- If the architecture uses inward-pointing layers within each module (e.g. Clean/Hexagonal), the inner layer never imports or depends on anything from an outer layer — dependencies point inward only.

These rules are written once as code, run as part of the normal test suite, and fail exactly like any other failing test — no special process, no separate "architecture review" step that's easy to skip under time pressure.

### Layer 4 — Code Review

Code review catches what automated checks structurally cannot: Whether a boundary is drawn in the _right_ place to begin with, whether a new public-surface addition is actually necessary or is quietly leaking an implementation detail, whether two modules that keep needing to talk to each other directly should actually be merged or restructured. This layer depends on human judgment and is therefore the most expensive and the least reliable as a sole defense — it is the last layer, applied on top of the first three, never a substitute for them.

## Workflow

### Step 1 — Identify the Module(s) Involved

Before writing the change, identify which module the new/changed code lives in, and whether it needs anything from a different module. If it does, name that other module explicitly — don't discover the cross-module dependency implicitly while writing the call.

### Step 2 — Locate the Target Module's Declared Public Surface

Find what the target module has actually declared as its public surface — its public interfaces, exported types, or whatever mechanism this project's architecture uses to mark "this is what other modules may depend on." If nothing the new code needs is part of that declared surface, **stop** — this is a signal that either:

- The target module needs to deliberately extend its public surface (a conscious design decision, not an accidental side effect of one caller's convenience)
- The dependency shouldn't exist in this direction at all, and the design needs rethinking.

Either way, this is a decision to make explicitly, not a problem to route around by reaching past the declared surface.

### Step 3 — Choose the Right Communication Mechanism

Per **Core Principle #5**, prefer the loosest coupling the relationship genuinely allows:

- If module B only needs to _react_ to something that happened in module A, and doesn't need an immediate synchronous answer, use an event/message — A publishes a fact, B subscribes and reacts independently.
- If module B genuinely needs a synchronous answer to proceed (a query, a validation it cannot proceed without), call through A's declared public surface directly — but only that surface, never its internals.

### Step 4 — Implement Through the Public Surface Only

Write the cross-module call so that it imports/depends on only the target module's declared public surface. Apply Layer 2 enforcement (visibility modifiers) on the target module's internals if they aren't already locked down — don't leave a path open that the fitness function in Step 5 would have to catch instead of the compiler.

### Step 5 — Run the Architectural Fitness Functions

Run the project's existing fitness-function test suite (see **Layer 3** above). If this project doesn't have one yet for the rule this change might affect, that absence is itself a finding — flag it, don't just proceed without a check existing to catch a regression of this exact mistake later.

### Step 6 — Self-Review Against the Quality Checklist

Run through [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md) before considering the change complete.

## What Belongs on a Module's Public Surface (and What Doesn't)

| Belongs on the public surface                                                                                 | Stays internal                                                                   |
| :------------------------------------------------------------------------------------------------------------ | :------------------------------------------------------------------------------- |
| A small set of interfaces/contracts representing what the module _offers_ to do for others                    | Concrete implementations of those contracts                                      |
| Data shapes specifically designed for cross-module exchange (a deliberately stable, minimal "exported" shape) | Persistence-bound types (ORM entities, schema-coupled models)                    |
| Domain events the module publishes for others to react to                                                     | The internal logic that decides when and how those events are produced           |
| Explicitly designated "facade" or "API" entry points                                                          | Repository implementations, internal helper functions, internal validation logic |

**Rule of thumb:** If exposing something on the public surface would require every consumer to also understand the module's internal storage model or internal workflow to use it correctly, it's not ready to be public — it's an internal detail wearing a public label.

## Quality Checklist

Condensed version — full checklist lives in [`resources/QUALITY_CHECKLIST.md`](resources/QUALITY_CHECKLIST.md):

- [ ] Every module involved in this change was identified explicitly before implementation began.
- [ ] Every cross-module reference in this change targets only the target module's declared public surface — never an internal type, repository implementation, or persistence-bound model.
- [ ] No new circular dependency was introduced between modules.
- [ ] Visibility modifiers (Layer 2) lock down anything in the touched module(s) that isn't a deliberate part of the public surface.
- [ ] The project's architectural fitness-function suite was run and passes after this change.
- [ ] If this change's rule has no corresponding fitness function yet, that gap was flagged explicitly rather than silently left unguarded.
- [ ] The communication mechanism chosen (direct call vs. event/message) matches the actual coupling need, not just whichever was easiest to write.
- [ ] No public surface was widened solely to legitimize a shortcut that had already been taken — any new public surface addition is a deliberate design decision, documented as such.

## Anti-Patterns

| Anti-Pattern                                                                                                                                      | Why It Fails                                                                                                                                                                                                     |
| :------------------------------------------------------------------------------------------------------------------------------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reaching directly into another module's repository/data-access layer because it's faster than going through its public surface                    | The single most common real-world boundary violation — it silently couples two modules' persistence models, so a schema change in one module can break another module that was never supposed to know it existed |
| Treating module boundaries as a documentation/diagram exercise with no automated check                                                            | A rule that exists only on a wiki page or in a diagram has no enforcement mechanism — it survives exactly until the first deadline-pressured shortcut, and nothing will catch it when that happens               |
| Widening a module's public surface just enough to legitimize a violation that was already committed                                               | Turns an accidental leak into a permanent commitment — once something is public, removing it later is a breaking change for whoever started depending on it in the meantime                                      |
| Relying on code review alone to catch boundary violations                                                                                         | Reviewers can and do miss this category of mistake consistently — it requires checking an import/dependency graph mentally, which doesn't scale with codebase size or reviewer attention                         |
| Defaulting every cross-module interaction to a direct synchronous call because it's the most familiar pattern                                     | Couples every consumer's availability and timing directly to the producer, even in cases where a simple "this happened, react if you care" event would have been sufficient and far less coupled                 |
| Letting two modules that constantly need to call into each other's internals stay separate "because that's how the folders are already organized" | If two modules can't function without reaching past each other's boundary constantly, that's a signal the boundary itself may be drawn in the wrong place — not a problem to keep working around indefinitely    |

## Best Practices

- Design a module's public surface deliberately and minimally — start with the smallest set of contracts that satisfies a real, current caller, and extend it only when a genuine new need arises, not speculatively.
- Keep persistence-bound types (anything coupled to a specific schema or ORM mapping) out of the public surface entirely, even if it would save a mapping step for a caller today — that coupling becomes very expensive to undo once another module depends on it.
- When two modules need to exchange data across the boundary, define a dedicated, minimal shape for that exchange rather than reusing either module's internal domain model directly — this keeps each module free to evolve its internals without breaking the other.
- Write the fitness function for a new boundary rule _before_ the feature that would violate it exists, whenever you can anticipate the risk — the same Red-Green-Refactor instinct from `test-driven-development` applies here: Prove the rule can fail before trusting that it holds.
- When a fitness-function violation is caught, fix it by routing the access through the correct public surface (or creating the missing, deliberate piece of public surface) — never by disabling or weakening the check itself to make the build pass.

## Common Rationalizations

| Rationalization                                                                                                     | Reality                                                                                                                                                                                                                                                     |
| :------------------------------------------------------------------------------------------------------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "It's just one field from the other module's internal model, it's not worth a whole new public interface for that." | One field today becomes a dependency on that module's entire internal shape tomorrow, the moment that module needs to change it. The cost of a small, deliberate exchange shape now is much lower than the cost of untangling an accidental coupling later. |
| "We don't have an architecture test for this yet, so it's not technically a violation."                             | Absence of a check is not permission — it's a gap. The principle (public-surface-only access) still applies; flag the missing check as a finding rather than treating its absence as a green light.                                                         |
| "The reviewer will catch it if it's actually a problem."                                                            | Reviewers reliably miss this category of issue — it requires tracing an import graph by eye, which doesn't scale. This is exactly the kind of rule that needs to be a build-breaking check, not a hope.                                                     |
| "These two modules already talk to each other constantly, one more direct call doesn't change anything."            | Each individual call feels small; the accumulated coupling is what eventually makes the modules impossible to evolve, test, or potentially extract independently. The pattern matters more than any single instance of it.                                  |
| "I'll widen the public interface to include this so it's not technically a violation anymore."                      | This legitimizes the leak instead of fixing it — the public surface should be designed deliberately for genuine cross-module needs, not retroactively reshaped to match whatever a specific caller happened to reach for.                                   |

## A Note on Testing Strategy at Module Boundaries

Some testing philosophies for modular monoliths recommend using _real_ collaborators (a real in-process data store, real internal code paths) within a single module's own test, and reserving mocks specifically for the module boundary itself. This is a coherent and well-regarded approach in its own right — but it is a different testing philosophy from a strict unit-tests-only-with-everything-mocked policy.

This skill governs _boundary correctness_, not testing strategy — it is compatible with either philosophy. If this project's own testing policy (see `test-driven-development` and the project's own rules) specifies unit-tests-only with full mocking, follow that policy for how you test; this skill only governs _where the boundary is_ and _how it's enforced_, not what kind of test double you use when verifying behavior near it.

## Output

This skill does not produce a standalone document artifact. Its output is the cross-module code itself, implemented through the correct public surface, plus a passing run of the project's architectural fitness functions.

**Worked example:** [`examples/example-boundary-check.md`](examples/example-boundary-check.md) shows a deliberately introduced boundary violation, how a fitness function catches it, and how it's corrected — continuing the domain-neutral "resource reservation" example used across the `spec-driven-development`, `api-contract-design`, `task-planning-and-breakdown`, and `incremental-implementation` skill examples. Illustrative only, with pseudocode rather than any specific language, framework, or fitness-function library syntax.
