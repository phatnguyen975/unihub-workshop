# UniHub Workshop — Agent Rules & Documentation Map

> This file is the single, always-loaded source of truth for any AI agent working in this repository. It tells the agent **what the project is**, **where to find authoritative information**, **how development must proceed** (SDD + TDD), and **what it must never do**.  
> Skills (`.agents/skills/`) and Workflows (`.agents/workflows/`) are loaded on demand and must be read in conjunction with this file — they assume the rules below as background context and do not repeat them.

## 1. Project Overview

**UniHub Workshop** is a backend-focused platform that digitizes a university's "Skills and Career Week" — from workshop discovery and registration (free/paid) to QR-based on-site check-in. It replaces a manual Google-Forms-and-email process that cannot handle scale or concurrency.

Three constraints drive almost every architectural decision in this codebase and must be respected by any code an agent writes:

1. **12,000+ concurrent users** within a 10-minute registration window (60% within the first 3 minutes) — the system must degrade gracefully, never crash or corrupt data.
2. **Exactly-once payment and registration semantics** — no duplicate charges, no overbooking, even under client retries and network drops.
3. **Offline-first check-in** — Check-in Staff must be able to scan and record attendance with zero network connectivity, with deterministic server-side reconciliation afterward.

Three actor roles exist, strictly enforced via RBAC: `STUDENT`, `ORGANIZER` (multi-tenant, scoped to an `organization_id`), `STAFF` (check-in only, explicitly assigned to specific workshops).

## 2. Tech Stack

| Layer                 | Technology                                                                 |
| :-------------------- | :------------------------------------------------------------------------- |
| Backend runtime       | Java 25, Spring Boot 4.0.6, Gradle Groovy                                  |
| Backend architecture  | Event-Driven Modular Monolith via Spring Modulith                          |
| Workers architecture  | Hexagonal Architecture (inbound/core/outbound)                             |
| Web                   | React (TypeScript)                                                         |
| Mobile                | React Native (TypeScript) + SQLite (offline-first)                         |
| API Gateway           | Nginx (pure reverse proxy + load balancer; no rate limiting at this layer) |
| Primary database      | PostgreSQL on Neon (serverless, primary + read replica)                    |
| Cache / rate limiting | Redis (Bucket4j Token Bucket, cache-aside, JTI blacklist)                  |
| Message broker        | RabbitMQ                                                                   |
| Search                | Elasticsearch (CQRS read model)                                            |
| Object storage        | MinIO (S3-compatible)                                                      |
| Resilience            | Resilience4j (circuit breaker)                                             |
| Auth                  | JWT RS256 (asymmetric), Redis-backed `jti` revocation                      |
| Observability         | Micrometer → Prometheus → Grafana                                          |
| Payments              | Stripe (sandbox, with `ChaosMockAdapter` for resilience testing)           |

The repository is a monorepo: `apps/backend/`, `apps/workers/`, `apps/web/`, `apps/mobile/` — each independently deployable.

## 3. Documentation Map

All authoritative project knowledge lives in `docs/`. **Before writing any
spec, contract, or code, the agent must read every document relevant to
the task.** This file only summarizes; `docs/` is the source of truth and
always wins in case of conflict with anything stated here.

| File                          | What it contains?                                                                                                                                                                                                                                                                                                       | Read this when?                                                                                                |
| :---------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------- |
| `docs/overview.md`            | Business context, problem statement, actor roles, in/out of scope, success criteria                                                                                                                                                                                                                                     | You need to understand **why** a feature exists or whether something is in scope                               |
| `docs/system-design.md`       | Architectural paradigm, C4 containers, **all six core system flows** (registration, payment webhook, workshop lifecycle, search cache-aside, offline sync, AI summarization, CSV ingestion), RBAC sequence diagrams, system protection mechanisms (rate limiting, circuit breaker, idempotency, overbooking prevention) | You are designing or implementing any business flow — this is the single most important file for feature work  |
| `docs/adrs.md`                | Every Architectural Decision Record (ADR-001 to ADR-030) with context, decision, rationale, alternatives considered, consequences                                                                                                                                                                                       | You need to know **why** a technical choice was made, or whether a new decision conflicts with an existing one |
| `docs/database-schema.md`     | Full ERD, every table's column/constraint/index spec, replication-lag routing rules (primary vs replica), mobile SQLite schema                                                                                                                                                                                          | You are adding/modifying a table, writing a query, or need to know a column's exact type/constraint            |
| `docs/api-standards.md`       | Versioning, pagination, CORS, HTTP status code table, mandatory headers, JSON envelope format, RFC 7807 error format, logging format                                                                                                                                                                                    | You are designing any REST endpoint — every endpoint must conform to this file without exception               |
| `docs/project-structure.md`   | Monorepo layout, `.agents/` skill infrastructure, per-module Clean Architecture layout for backend, Hexagonal layout for workers, `infra/` Docker layout                                                                                                                                                                | You need to know which package/folder a new class belongs in, or how modules communicate internally            |
| `docs/system-architecture.md` | High-level Mermaid component diagram of the whole system                                                                                                                                                                                                                                                                | You need a quick visual of how services connect (nginx, Redis, RabbitMQ, ES, Postgres, external services)      |

### Quick lookup — "I want to know X → read Y"

- **What does this feature need to do?** → `docs/overview.md` + `docs/system-design.md` (matching flow section)
- **Why was this technology/pattern chosen?** → `docs/adrs.md`
- **What columns/constraints does this table have?** → `docs/database-schema.md`
- **Should this query go to primary or replica?** → `docs/database-schema.md` in **Replication Lag & Read-After-Write Consistency** section
- **What should this endpoint's request/response/error look like?** → `docs/api-standards.md`
- **Where does this class belong?** → `docs/project-structure.md`
- **What does Spring Modulith vs Hexagonal Architecture mean here?** → `docs/project-structure.md` in **Internal Module Communication** section (per app)
- **How do registration/payment/check-in/search/AI/CSV flows actually work end-to-end?** → `docs/system-design.md` in **Core System Flows** section
- **What's the big picture?** → `docs/system-architecture.md`

## 4. Development Methodology — Spec-Driven + Test-Driven

Every feature in this repository **must** go through this pipeline, driven by five slash commands. **No production code is written without an approved spec.** No business logic is written without a failing test first.

```text
/spec <feature-name> → /plan <feature-name> → /build <feature-name> <task-id> → /review <feature-name>

/review → /fix → /review  (repeat until zero blocking findings)
```

The `/review` is not a terminal step. If it produces findings, the cycle is `/review → /fix → /review` until no blocking findings remain. This is **not an automated loop** — every arrow above is a separate, explicit human-invoked command. The agent does not chain commands on its own initiative — a human reviews the output of `/spec` and `/plan` and explicitly decides to proceed before the next command is run.

> [!NOTE]
> `specs/` and `docs/` are kept consistent _as part of_ `/build` itself, at the moment an improvement is proposed and approved — see section 4.4, "Mid-build improvement proposals." By the time a feature reaches `/review`, `specs/` and `docs/` are already expected to be accurate; `/review` and `/fix` exist to catch implementation defects, not specification drift.

### 4.1 Skills Catalog

| #   | Skill                                    | Phase        | Purpose                                                                                                |
| :-- | :--------------------------------------- | :----------- | :----------------------------------------------------------------------------------------------------- |
| 1   | `spec-driven-development`                | Spec         | Write FR/BR/AC-based spec before any code                                                              |
| 2   | `api-contract-design`                    | Spec         | Design REST contract conformant to `docs/api-standards.md`                                             |
| 3   | `task-planning-and-breakdown`            | Plan         | Break approved spec into vertical-slice tasks — one capability per task, spanning every layer it needs |
| 4   | `test-driven-development`                | Build        | Red-Green-Refactor; 4-layer mock-based unit testing                                                    |
| 5   | `incremental-implementation`             | Build        | Build one task's vertical slice layer by layer, innermost-to-outermost                                 |
| 6   | `modular-boundary-enforcement`           | Build        | Keep cross-module access through `contracts/` only; run `ModulithArchitectureTest`                     |
| 7   | `database-schema-evolution`              | Build        | Safe, additive-only schema migrations                                                                  |
| 8   | `idempotency-and-exactly-once-design`    | Build        | Exactly-once semantics for retry-risk mutations                                                        |
| 9   | `security-and-hardening`                 | Build/Review | OWASP/STRIDE checklist for auth, payment, webhook, input                                               |
| 10  | `performance-and-efficiency-engineering` | Build/Review | N+1 queries, indexing, unbounded results, hot-path I/O, caching                                        |
| 11  | `code-review-and-quality`                | Review       | Multi-axis review before merge                                                                         |
| 12  | `debugging-and-root-cause-analysis`      | Fix/Build    | Reproduce → localize → hypothesize → fix root cause → regression test                                  |

### 4.2 `/spec <feature-name>`

**Skills invoked:**

- `spec-driven-development` — always runs. Produces `specs/<feature-name>/SPEC.md` (Overview, Actors & Preconditions, Functional Requirements as FR-XX SHALL statements, Business Rules as BR-XX, State Transitions, Non-Functional Requirements, Acceptance Criteria in Gherkin, Out of Scope, Linked References into `docs/`).
- `api-contract-design` — runs only if the feature exposes or changes a REST endpoint. Produces `specs/<feature-name>/API_CONTRACT.md`, fully conformant with `docs/api-standards.md`.

**Agent responsibility:** Read every relevant file in `docs/` (per the Documentation Map above) before drafting either artifact. Cross-reference every requirement against the actual flow described in `docs/system-design.md` — do not invent behavior that contradicts it.

**Human verification gate (mandatory, blocking):** The human reads `SPEC.md` (and `API_CONTRACT.md` if produced) and explicitly approves before `/plan` may be run. If acceptance criteria are not testable, or a requirement is ambiguous, the agent must stop and ask rather than guess.

**Feature naming:** The agent does **not** choose feature names. The human supplies `<feature-name>` based on a business flow (e.g. `workshop-registration`, `payment-webhook-handling`, `offline-checkin-sync`) — typically one of the flows already named in `docs/system-design.md` in **Core System Flows** section. One module commonly contains several features; the agent must treat the supplied name as a use case, never assume it maps 1:1 to a module. If a supplied name is ambiguous or matches a bare module name (e.g. `registrations`), the agent must ask for clarification instead of guessing scope.

> [!NOTE]
> The human must read and approve these artifacts first — this is a hard gate, not a suggestion. The agent does not self-approve its own spec. If the human approves, the spec becomes the contract for implementation, then you change the status to `APPROVED`. If the human rejects it, revise these artifacts with the human's feedback and fix them until approval is granted.

### 4.3 `/plan <feature-name>`

**Skill invoked:**

- `task-planning-and-breakdown` — always runs. Reads the approved `SPEC.md` or `API_CONTRACT.md` and produces `specs/<feature-name>/tasks.md` — an ordered list of vertical-slice tasks, each one a complete, independently end-to-end-testable capability spanning every Clean Architecture layer it needs (Domain → Application → Infrastructure → Presentation), never a task scoped to a single layer. Each task has acceptance criteria stated as externally observable behavior and the expected file list across all layers it touches.

**Human verification gate (mandatory, blocking):** The human reviews `tasks.md` for correct ordering, scope, and granularity (no task should span more than ~10 files) before `/build` may begin.

### 4.4 `/build <feature-name> <task-id>`

Run once per task in `tasks.md`, in order.

**Skills invoked, conditionally:**

| Skill                                    | Always runs? | Condition                                                                                                           |
| :--------------------------------------- | :----------- | :------------------------------------------------------------------------------------------------------------------ |
| `incremental-implementation`             | Yes          | Every task                                                                                                          |
| `test-driven-development`                | Yes          | Every task — Red → Green → Refactor, strictly unit-test-only (see section 5)                                        |
| `modular-boundary-enforcement`           | Conditional  | Task involves a cross-module call or new module-level class                                                         |
| `database-schema-evolution`              | Conditional  | Task adds/modifies a table, column, index, or constraint                                                            |
| `idempotency-and-exactly-once-design`    | Conditional  | Task is a mutation endpoint with retry risk (payment, registration, webhook, batch sync)                            |
| `security-and-hardening`                 | Conditional  | Task touches auth, RBAC, payment, webhook, file upload, or any external input                                       |
| `performance-and-efficiency-engineering` | Conditional  | Task touches a database query, a loop over a collection with per-item I/O, or a call to an external/slow dependency |

**Agent responsibility:** Implement exactly the current task, nothing beyond its declared scope. Stay within Clean Architecture layering as defined in `docs/project-structure.md`. Run the full unit test suite before considering a task complete.

**Human verification:** Not a hard gate between every sub-step within a single task, but the human is expected to review the diff after each task before the next one starts. The agent must not silently start the next task without this being clear in its output.

> [!NOTE]
>
> - `ModulithArchitectureTest` (see section 6) must pass as part of the full unit test suite run at the end of every `/build` task and again before `/review` — it is included in "the full unit test suite" referenced throughout this document, not a separate manual step.
> - If a test fails unexpectedly during a task (not yet a formal review finding), the agent applies `debugging-and-root-cause-analysis` inline before continuing.

> [!IMPORTANT]
>
> **Mid-build improvement proposals:** If, while implementing a task, the agent identifies a way the current approach in `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, or any document in `docs/` could be improved — a better design, a stronger pattern, or a constraint discovered only during implementation — the agent must **stop coding immediately** and write the proposal into `specs/<feature-name>/improvement-proposal.md`. The agent never implements an improvement first and explains it afterward.
>
> Every proposal must state:
>
> - **What** would change, and in which file(s) — `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, and/or specific files under `docs/`.
> - **Why** — a concrete, verifiable justification: a cited best practice, an existing pattern already used elsewhere in this codebase, a documented limitation discovered while coding, or a direct conflict with an existing ADR or constraint. A proposal with no verifiable justification, or one based only on stylistic preference, must not be submitted.
>
> This goes through a **two-gate approval**, run in this exact order:
>
> 1. **Gate 1 — Approve the proposal.** The human reads `improvement-proposal.md` and replies Approve or Reject.
>    - **Reject** → The agent discards the proposal and continues implementing exactly per the existing approved spec. No files are changed.
>    - **Approve** → The agent proceeds to Gate 2. It does not touch any code yet.
> 2. **Gate 2 — Approve the synchronized artifacts.** The agent updates every affected file in `specs/<feature-name>/` **and** every affected file in `docs/` in the same pass, then presents the diff. The human reviews and replies Approve or Reject.
>    - **Reject** → The human states what is still out of sync or incorrect; the agent corrects and re-presents the diff. Repeat until approved.
>    - **Approve** → `specs/` and `docs/` are now the new source of truth. The agent may resume coding the current task **only if** the approved change does not require a new task. If the approved change requires a new task (or removes/reorders an existing one), the agent updates `tasks.md` accordingly and **stops** — it does not start a new task on its own initiative. The human must explicitly invoke `/build <feature-name> <task-id>` to proceed.
>
> This is the **only** mechanism that keeps `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, and `docs/` consistent with each other and with the actual implementation.

### 4.5 `/review <feature-name>`

Run after all tasks for a feature are complete.

**Skills invoked:**

- `code-review-and-quality` — always runs. Multi-axis review (correctness against the approved `SPEC.md`/`API_CONTRACT.md`/`tasks.md`, layer/module boundary adherence, test coverage, style, coding rules), findings classified by severity.
- `security-and-hardening` — re-run in full-checklist mode if the feature touches auth, payments, webhooks, or RBAC.
- `performance-and-efficiency-engineering` — invoked when the feature touched any database query, a loop over a collection, or a call to an external/slow dependency during `/build`. Re-checks specifically for N+1 query shapes, missing indexes, unbounded results, and unguarded hot-path I/O — independently of whatever was already checked during `/build`, since this category of defect is easy to miss once and obvious once flagged.

**Scope:** By this point, `SPEC.md`, `API_CONTRACT.md`, `tasks.md`, and `docs/` are assumed accurate — any legitimate deviation already went through the improvement-proposal flow in section 4.4. `/review` therefore checks the implementation **against** these artifacts; it does not re-open or re-question the artifacts themselves. If the agent believes it has found a genuine specification gap rather than an implementation defect, it must say so explicitly in `review-notes.md` and flag that the human may need to restart an improvement proposal retroactively — but `/review` itself never edits `specs/` or `docs/`.

**Output:** `specs/<feature-name>/review-notes.md`, listing every finding found by the agent, classified by severity (Critical / Should-fix / Nit / FYI). The agent presents this summary and stops — it does not fix anything yet.

**Human verification gate (mandatory, blocking):** The human reads `review-notes.md`, confirms or disputes each AI-found finding, and adds any additional findings the AI missed. The combined, human-confirmed list is the only valid input to `/fix`.

### 4.6 `/fix <feature-name>`

Run whenever `/review` produced a human-confirmed list of findings.

**Skill invoked:**

- `debugging-and-root-cause-analysis` — always runs, once per finding. **For each finding:** reproduce the minimal failing case → localize the cause → form a hypothesis → fix the root cause (not the symptom) → add a regression test that fails before the fix and passes after.

**Rule:** `/fix` only ever changes code and tests to bring the implementation back into line with the already-approved `SPEC.md` / `API_CONTRACT.md` / `tasks.md`. It never edits `specs/` or `docs/` — those are only ever changed through the improvement-proposal flow in section 4.4. A fix is never applied without a regression test, even for findings that look trivial. This is the same Red-Green-Refactor discipline as `/build` — a review finding is not exempt from TDD.

**After all findings are fixed:** The agent reports completion and the human re-runs `/review` on the same feature. Repeat `/review → /fix → /review` until zero blocking findings remain. The feature is then considered complete.

If `/review → /fix` repeats more than twice for the same feature without converging, treat this as a signal that the feature was under-specified or the task breakdown was too coarse — stop and revisit `/spec` or `/plan` rather than continuing to patch.

## 5. Testing Standard

- **Unit tests only. No integration tests, anywhere, at any layer.**
- Four-layer test pyramid, all dependencies mocked:
  - **Domain** — pure unit tests, zero framework dependency.
  - **Application** — use case tests with all outbound ports mocked.
  - **Infrastructure** — repository/adapter tests with the persistence or messaging client mocked; assert query construction and mapping logic only. **Never use Testcontainers or a real database/Redis/RabbitMQ instance in any test.**
  - **Presentation** — controller tests with use cases mocked.
- `ApplicationModules.verify()` / equivalent static module-boundary checks are not integration tests (no Spring context, no I/O) and are always permitted and expected.
- **Red → Green → Refactor is mandatory:** a failing test must exist before implementation code is written.

## 6. Module Boundaries (Modular Monolith)

- Backend (`apps/backend/`) modules: `iam`, `workshops`, `registrations`, `payments`, `attendances`, `notifications`, `search` — each follows Clean Architecture (`domain/ → application/ → infrastructure/ → presentation/`), with `contracts/` as the only public surface other modules may depend on.
- Cross-module calls must go through `contracts/` interfaces or domain events (via the Spring Modulith event publication registry) — never reach into another module's `domain/`, `application/`, or `infrastructure/` package directly.
- Workers (`apps/workers/`) follow Hexagonal Architecture per pipeline (`inbound/ → core/ → outbound/`), with `core/` having zero outward dependencies.
- See `docs/project-structure.md` for the full per-module directory layout and the `modular-boundary-enforcement` skill for enforcement workflow.
- `modular-boundary-enforcement` is verified concretely via `apps/backend/src/test/java/com/unihub/workshop/ModulithArchitectureTest.java` — running `ApplicationModules.of(...).verify()` — which must pass before any feature touching module boundaries is considered complete. This is a static architecture check, not an integration test, and is always permitted under section 5.

## 7. Coding Conventions (Java & TypeScript)

These apply to all code regardless of which skill or command produced it.

- **Design patterns over ad-hoc logic.** Where a well-known pattern fits (Strategy for pluggable notification channels, Factory for payment provider adapters, Builder for complex DTOs), use it explicitly rather than reinventing an equivalent structure inline.
- **DRY, but not at the cost of layer purity.** Shared logic belongs in the appropriate shared kernel (`shared/` in backend, `core/` in workers) — never duplicated across modules, but also never pulled across a module boundary just to avoid duplication (see section 6).
- **Minimize dependencies.** Before adding a library, check whether the existing stack (see section 2) already solves the problem. Do not introduce a new dependency for something one of the already-approved libraries covers.
- **Low coupling, high cohesion.** A class should depend on abstractions (ports/interfaces) it owns or that are defined in `contracts/`, never on another module's concrete implementation.
- **Clean code baseline.** Small functions with a single responsibility, intention-revealing names, no magic numbers/strings (use named constants or enums), no commented-out code committed.
- **Optimize for maintainability over cleverness.** Prefer the obvious, slightly more verbose solution over a clever one-liner if it is easier for a future reader (human or agent) to understand without additional context.
- **Immutability by default** (Java `record`/`final` fields for domain objects, TypeScript `readonly`/`const`) unless mutation is required and justified.
- **No framework leakage into the domain layer.** Domain classes never import Spring, JPA, or any TypeScript framework-specific type.
- **TypeScript-specific (for `apps/web/` or `apps/mobile/`):** Strict mode enabled (`strict: true` in `tsconfig.json`), no `any` without an explicit justifying comment, prefer discriminated unions over optional-field-heavy types for state modeling.

## 8. Safety Guardrails

- **Never commit, push, or run any other git write operation.** The human owns version control exclusively.
- **Never hardcode, log, or commit secrets** — API keys, the RSA `private.pem`, `.env` file contents, database credentials, webhook signing secrets. These are git-ignored; do not attempt to read them into generated documentation or examples.
- **Never weaken a security control to make a test pass** (e.g. disabling JWT verification, bypassing RBAC checks, skipping webhook signature validation) — fix the test or the design instead.
- **Never introduce an integration test** of any kind, even if it seems like the most direct way to verify something — see section 5.
- **Never silently expand a task's scope.** If implementing a task reveals that the spec or plan is wrong or incomplete, stop and surface this rather than improvising beyond what was approved.
- **Never invent business behavior not present in `docs/`.** If `docs/system-design.md` or `docs/adrs.md` doesn't specify a behavior, ask rather than assume.
- **Fail-mode decisions (fail-open vs fail-closed)** must match what `docs/adrs.md` already specifies — do not change these without an explicit human decision.
- Never apply a fix from `/fix` without the regression-test discipline described in section 4.6 — a review finding is never patched directly.
- **Never let implementation silently diverge from the approved spec.** If a better approach is discovered mid-build, follow the two-gate improvement-proposal process in section 4.4 — record it, get it approved, update `specs/` and `docs/` together, get that update approved, then resume — never implement first and reconcile later.
