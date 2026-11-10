# Server Rules — Backend and Workers

Applies only to server tasks. Read shared rules and the relevant server workflow stage.

## Testing Standard

- **Unit tests only. No integration tests, anywhere, at any layer.**
- Four-layer test pyramid, all dependencies mocked:
  - **Domain** — pure unit tests, zero framework dependency.
  - **Application** — use case tests with all outbound ports mocked.
  - **Infrastructure** — repository/adapter tests with the persistence or messaging client mocked; assert query construction and mapping logic only. **Never use Testcontainers or a real database/Redis/RabbitMQ instance in any test.**
  - **Presentation** — controller tests with use cases mocked.
- `ApplicationModules.verify()` / equivalent static module-boundary checks are not integration tests (no Spring context, no I/O) and are always permitted and expected.
- **Red → Green → Refactor is mandatory:** a failing test must exist before implementation code is written.


## Module Boundaries (Modular Monolith)

- Backend (`apps/backend/`) modules: `iam`, `workshops`, `registrations`, `payments`, `attendances`, `notifications`, `search` — each follows Clean Architecture (`domain/` → `application/` → `infrastructure/` → `presentation/`), with `api/` (Facade Pattern) as the only public surface other modules may depend on.
- Cross-module calls must go through `api/` facade interfaces or domain events (via the Spring Modulith event publication registry) — never reach into another module's `domain/`, `application/`, or `infrastructure/` package directly.
- Workers (`apps/workers/`) follow Hexagonal Architecture per pipeline (`inbound/` → `core/` → `outbound/`), with `core/` having zero outward dependencies.
- See `docs/server/source-structure.md` for the full per-module directory layout and the `modular-boundary-enforcement` skill for enforcement workflow.
- `modular-boundary-enforcement` is verified concretely via static architecture checks (not integration tests, always permitted under Testing Standard):
  - **Core Backend (`apps/backend/`):** `apps/backend/src/test/java/com/unihub/workshop/ModulithArchitectureTest.java` — verifies Spring Modulith module encapsulation (`ApplicationModules.of(...).verify()`), Clean Architecture layer purity, and public `api/` facade boundaries. Run whenever modifying backend code.
  - **Workers (`apps/workers/`):** `apps/workers/src/test/java/com/unihub/workshop/workers/HexagonalArchitectureTest.java` — verifies Hexagonal Architecture (Ports and Adapters) intra-module isolation, port interface contracts, and inter-worker-module decoupling. Run whenever modifying worker code.


## Server Coding Conventions

These apply to all code regardless of which skill or command produced it.

- **Design patterns over ad-hoc logic.** Where a well-known pattern fits (Strategy for pluggable notification channels, Factory for payment provider adapters, Builder for complex DTOs), use it explicitly rather than reinventing an equivalent structure inline.
- **DRY, but not at the cost of layer purity.** Shared logic belongs in the appropriate shared kernel (`shared/` in backend, `core/` in workers) — never duplicated across modules, but also never pulled across a module boundary just to avoid duplication (see Module Boundaries).
- **Minimize dependencies.** Before adding a library, check whether the existing app dependencies and `README.md` technology stack already solves the problem. Do not introduce a new dependency for something one of the already-approved libraries covers.
- **Low coupling, high cohesion.** A class should depend on abstractions (ports/interfaces) it owns or that are defined in `api/` facades, never on another module's concrete implementation.
- **Clean code baseline.** Small functions with a single responsibility, intention-revealing names, no magic numbers/strings (use named constants or enums), no commented-out code committed.
- **Optimize for maintainability over cleverness.** Prefer the obvious, slightly more verbose solution over a clever one-liner if it is easier for a future reader (human or agent) to understand without additional context.
- **Immutability by default** (Java `record`/`final` fields for domain objects, TypeScript `readonly`/`const`) unless mutation is required and justified.
- **Use Lombok to reduce boilerplate code.** Prefer standard Lombok annotations such as `@Getter`, `@Setter`, `@RequiredArgsConstructor` (not `@Autowired`), and `@Builder` where they improve readability and maintainability. Avoid excessive or unnecessary Lombok usage when it makes the code harder to understand.
- **Use MapStruct for all object-to-object mappings.** Declare MapStruct interfaces annotated with `@Mapper(componentModel = "spring")` at architectural boundaries (such as Entity ↔ Domain Model, DTO ↔ Domain Model / Command) to eliminate boilerplate. Writing manual mapping methods (`toDomain()`, `toEntity()`, static `from()`, manual builder/constructor assembly in adapters and controllers) is prohibited when MapStruct can express them declaratively. Inject generated mapper beans via constructor injection (e.g., using Lombok's `@RequiredArgsConstructor`). Refer to `docs/server/source-structure.md` for the exact folder placement of mappers in each service.
- **No framework leakage into the domain layer.** Domain classes never import Spring, JPA, or any TypeScript framework-specific type.


## Server Safety and Change Control

- **Never introduce an integration test** of any kind, even if it seems like the most direct way to verify something — see Testing Standard.
- **Never silently expand a task's scope.** If implementing a task reveals that the spec or plan is wrong or incomplete, stop and surface this rather than improvising beyond what was approved.
- **Never invent business behavior not present in `docs/`.** If `docs/server/system-design.md` or the applicable decisions indexed in `docs/adrs/README.md` and `docs/server/adrs/README.md` doesn't specify a behavior, ask rather than assume.
- **Fail-mode decisions (fail-open vs fail-closed)** must match the applicable approved decision indexed in `docs/adrs/README.md` or `docs/server/adrs/README.md` — do not change these without an explicit human decision.
- **Never let implementation silently diverge from the approved spec.** If a better approach is discovered mid-build, follow the two-gate improvement-proposal process in `.agents/workflows/server.md` — `server-build` — record it, get it approved, update affected `specs/`, `contracts/` and `docs/` artifacts together, get that update approved, then resume — never implement first and reconcile later.

## Verification Commands

Run the unit suite for the app changed. Backend uses ModulithArchitectureTest; workers use HexagonalArchitectureTest, as part of their unit suite. Static architecture checks do not load external services. In PowerShell use .\gradlew.bat from the app directory; on Linux/macOS use ./gradlew. Do not run or alter the other app's suite for a documentation-only or client-only change.

## Shared Refactor and Git

Use `code-simplification` only on covered, green code with an actual readability opportunity and the approved task scope/test command. Workflow mode creates no HTML report, does not read/fill the HTML template for that purpose and never asks for a report location. Include evidence in the task summary. Use `autonomous-git-workflow` for authorized local task delivery under shared rules; a commit never bypasses the next server gate.
