# Modular Boundary Enforcement Quality Checklist

Run this checklist before considering any change that touches module boundaries complete. Every item must pass.

## Identification

- [ ] Every module involved in this change (the module the new code lives in, and any other module it depends on) was identified explicitly before implementation began.
- [ ] Any new cross-module dependency was a deliberate decision, not something discovered implicitly while writing a call and left unexamined.

## Public Surface Discipline

- [ ] Every cross-module reference in this change targets only the target module's declared public surface (its designated interfaces/exported types) — never an internal type.
- [ ] No persistence-bound type (ORM entity, schema-coupled model, repository implementation) from any module is referenced directly from outside that module.
- [ ] If this change required something not currently on a module's public surface, that surface was deliberately and consciously extended — not bypassed.
- [ ] No public surface was widened _solely_ to retroactively legitimize an access pattern that had already been written the wrong way.

## Visibility / Access Control

- [ ] Everything in the touched module(s) that is not a deliberate part of the public surface is marked with the language's strictest available visibility/access modifier for that scope.
- [ ] No new type, function, or class was left at default/public visibility "for now" with the intention of locking it down later.

## Dependency Direction

- [ ] No new circular dependency was introduced between any two modules as a result of this change.
- [ ] If the architecture uses inward-pointing layers within a module (e.g. domain / application / infrastructure), no new dependency points outward from an inner layer to an outer one.
- [ ] The direction of the new dependency (which module depends on which) matches the intended ownership relationship between the two modules, not just whichever direction was more convenient to code.

## Automated Enforcement

- [ ] The project's architectural fitness-function test suite was run after this change and passes.
- [ ] If this change introduces a new kind of boundary risk that no existing fitness function would catch, that gap was explicitly flagged as a finding (and ideally a new fitness function was added to close it) — not silently left unguarded.
- [ ] No existing fitness function was weakened, disabled, or special- cased to make this change's build pass.

## Communication Mechanism

- [ ] The mechanism used for this cross-module interaction (direct synchronous call vs. event/message) matches the actual coupling need — a synchronous call was used only where an immediate answer is genuinely required.
- [ ] If an event/message mechanism was used, the consuming module's reaction to it does not silently assume synchronous, immediate delivery where the underlying mechanism doesn't guarantee that.

## Review Readiness

- [ ] A reviewer could trace this change's cross-module dependencies by reading the import/dependency list alone, without needing to trust that "it's probably fine."
- [ ] Any case where two modules appear to need frequent, deep access to each other's internals was flagged as a possible sign the boundary itself needs reconsideration — not silently worked around.

## Testing Note

- [ ] The test doubles used for this change's tests follow this project's own stated testing policy (see `test-driven-development` and the project's rules file) — this checklist does not impose a separate testing philosophy of its own.
