# Implementation Plan: Infrastructure Bootstrap and Seed Data

> **Spec:** `specs/infrastructure-bootstrap-and-seed-data/SPEC.md`  
> **Status:** APPROVED

## Overview

This plan builds the five infrastructure concerns in the order their dependencies demand. The `RoutingDataSource` bean is the foundation everything else touches, so it comes first. Flyway configuration correctness and RSA key loading must be confirmed before the `ApplicationRunner` can safely seed data. Seeding is one cohesive capability (organizations → organizers → health validation, all in a single runner). The TTL cleanup scheduler is an independent, additive capability that can be built and verified in isolation after seeding is confirmed working.

No REST endpoints are introduced. Every task is verified through unit tests that observe externally visible behavior (bean initialization outcome, application context state, log output, scheduler invocation result) — not internal function calls.

## Architecture Decisions for This Plan

- **T1 (`RoutingDataSource`) before all others** — Replacing `spring.datasource.url` with `datasource.primary.*` + `datasource.replica.*` and wiring a routing bean are a single, indivisible configuration change. Splitting the YAML change from the bean into separate tasks would leave the application in a broken, non-startable state between them.
- **T2 (Flyway config + workers guard) before T3 (RSA loading)** — Flyway must be confirmed to apply migrations before any `ApplicationRunner` executes. This must be verified before layering further startup concerns. RSA loading happens during context refresh and does not depend on Flyway, but confirming migration ordering first limits blast radius.
- **T3 (RSA) before T4 (seeding)** — RSA key loading is a prerequisite for the JWT infrastructure every downstream module will use. Seeding depends on schema tables existing (Flyway) and is itself a distinct caller-observable capability (rows appear in the DB), so it is a separate task from RSA loading.
- **T4 (seeding + health validation) as one task, not two** — FR-03, FR-04, FR-05, FR-06, FR-07, and FR-09 are all behaviors of a single `ApplicationRunner.run()` method. Health validation (FR-09) is the final step of that same method; splitting them would create an artificial dependency with no independently testable intermediate state.
- **T5 (TTL cleanup) last and depends only on T1** — The scheduler is completely independent of seeding. The three target tables exist after Flyway runs (T2), and the scheduler needs only the `RoutingDataSource` (T1) for correct primary routing. It can be built and verified in isolation.

## Task List

### Phase 1: Foundational DataSource Infrastructure

- [ ] **T1 — `RoutingDataSource` bean initialized in both apps; `application.yml` restructured to `datasource.primary.*` + `datasource.replica.*`; workers `spring.flyway.enabled: false` added (FR-10, BR-10, BR-11, BR-12, BR-13, AC-13, AC-14, AC-15)**
  - **Size:** M
  - **Depends on:** —
  - **Layers touched (backend):** Shared Config (infrastructure beans), `application.yml` (externalized config)
  - **Layers touched (workers):** Shared Config (infrastructure beans), `application.yml` (externalized config)
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` — remove `spring.datasource.url`; add `datasource.primary.*` and `datasource.replica.*` blocks with env-var placeholders; add `datasource.*.hikari.maximum-pool-size` properties; add `spring.flyway.enabled: true`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/DataSourceProperties.java` — `@ConfigurationProperties("datasource")` record binding `primary` and `replica` sub-groups (url, username, password, hikari)
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/TransactionAwareRoutingDataSource.java` — `AbstractRoutingDataSource` subclass; `determineCurrentLookupKey()` reads `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`; returns `"replica"` when `true`, `"primary"` otherwise
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/DataSourceRoutingConfig.java` — `@Configuration`; defines `primaryDataSource()` and `replicaDataSource()` as `HikariDataSource` beans; defines `routingDataSource()` as `@Primary DataSource` targeting both
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/DataSourceRoutingConfigTest.java` — unit tests: routing bean is `TransactionAwareRoutingDataSource`; `readOnly=true` transaction → key `"replica"`; `readOnly=false` transaction → key `"primary"`; no active transaction → key `"primary"`
    - `apps/workers/src/main/resources/application.yml` — same YAML restructuring as backend; add `spring.flyway.enabled: false`
    - `apps/workers/src/main/java/com/unihub/workshop/workers/shared/config/DataSourceProperties.java` — `@ConfigurationProperties` record (workers package)
    - `apps/workers/src/main/java/com/unihub/workshop/workers/shared/config/TransactionAwareRoutingDataSource.java` — mirrors backend routing logic (workers package)
    - `apps/workers/src/main/java/com/unihub/workshop/workers/shared/config/DataSourceRoutingConfig.java` — mirrors backend config (workers package)
    - `apps/workers/src/test/java/com/unihub/workshop/workers/shared/config/DataSourceRoutingConfigTest.java` — mirrors backend routing tests (workers package)
  - **Acceptance Criteria:**
    - The backend application context exposes a `@Primary DataSource` bean that is an instance of `TransactionAwareRoutingDataSource` (FR-10, AC-13).
    - The workers application context exposes the same type as the `@Primary DataSource` bean (AC-15, FR-10).
    - A `@Transactional(readOnly = true)` call causes `determineCurrentLookupKey()` to return `"replica"` (AC-14, FR-10, BR-11).
    - A `@Transactional` (default) or `@Transactional(readOnly = false)` call causes the key to return `"primary"` (AC-13, FR-10, BR-11).
    - A call with no active Spring transaction causes the key to return `"primary"` (FR-10, BR-12).
    - `spring.datasource.url` no longer appears in either `application.yml` (BR-13).
    - `spring.flyway.enabled: false` is present in `apps/workers/src/main/resources/application.yml` (BR-10, NFR-11).

#### Checkpoint: Phase 1

- [ ] `DataSourceRoutingConfigTest` passes in both `apps/backend` and `apps/workers`.
- [ ] Neither `application.yml` contains `spring.datasource.url`; both contain `datasource.primary.*` and `datasource.replica.*`.
- [ ] `spring.flyway.enabled: false` is present in the workers `application.yml`.
- [ ] Full unit test suite passes in both apps (including `ModulithArchitectureTest` in backend).
- [ ] Build is clean (`./gradlew test` green for both apps).

### Phase 2: Startup Correctness — Flyway Order and RSA Key Loading

- [ ] **T2 — Flyway confirmed to apply migrations before `ApplicationRunner` on Core Backend; workers Flyway guard verified by automated test (FR-01, BR-09, BR-10, NFR-11, AC-01, AC-02)**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Shared Config (`application.yml` Flyway properties), test infrastructure
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` — add `spring.flyway.locations: classpath:db/migration`; `spring.flyway.out-of-order: false`; `spring.flyway.validate-on-migrate: true`
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/FlywayOrderTest.java` — verifies that after context refresh the `organizations` table exists (Flyway ran before any `ApplicationRunner`); uses `JdbcTemplate` against a test-scoped datasource
    - `apps/workers/src/test/java/com/unihub/workshop/workers/shared/config/WorkersFlywayDisabledTest.java` — asserts `spring.flyway.enabled` property value is `false`; asserts `FlywayAutoConfiguration` bean is absent from the workers application context
  - **Acceptance Criteria:**
    - After Core Backend context refresh with a fresh schema, the `organizations` table exists in the database, confirming Flyway applied `V1__init_schema.sql` before any `ApplicationRunner` (AC-01, FR-01).
    - `FlywayAutoConfiguration` bean is absent from the workers application context (AC-02, FR-01, BR-10).
    - An automated test asserts `spring.flyway.enabled: false` is present in `apps/workers/src/main/resources/application.yml` (AC-02, NFR-11).
    - `spring.flyway.out-of-order` is `false` in the backend configuration (BR-09).

- [ ] **T3 — RSA key pair loaded at startup; application fails fast with a descriptive `IllegalStateException` if either key is absent or malformed (FR-02, NFR-06, AC-08)**
  - **Size:** S
  - **Depends on:** T2
  - **Layers touched:** Shared Config (bean initialization, `application.yml`), `shared/security` (key exposure contract), test
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` — add `rsa.private-key: classpath:security/private.pem` and `rsa.public-key: classpath:security/public.pem`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/security/RsaKeyProperties.java` — `@ConfigurationProperties("rsa")` record holding `privateKey: RSAPrivateKey` and `publicKey: RSAPublicKey`; bound via Spring Security's `RsaKeyConverters`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/RsaKeyConfig.java` — `@Configuration` that validates both fields on `RsaKeyProperties` are non-null; throws `IllegalStateException` with a descriptive message if either is null; exposes the validated bean
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/RsaKeyConfigTest.java` — three test cases: (1) valid PEM files → bean present, both keys non-null; (2) missing `private.pem` → `IllegalStateException` before `ApplicationRunner` phase; (3) malformed PEM content → `IllegalStateException`
  - **Acceptance Criteria:**
    - With both valid PEM files on the classpath, `RsaKeyProperties` bean is present and both `privateKey` and `publicKey` fields are non-null (FR-02).
    - With `private.pem` absent from `resources/security/`, the application context fails to refresh and an `IllegalStateException` with a descriptive message is thrown; the `ApplicationRunner` is never reached (AC-08, FR-02).
    - RSA private key material does not appear in any Actuator `env` or `configprops` response — the current `application.yml` only exposes `prometheus,health`, so the Actuator constraint is satisfied by existing config; the test asserts no additional exposure is introduced (NFR-06).

#### Checkpoint: Phase 2

- [ ] `FlywayOrderTest` and `WorkersFlywayDisabledTest` pass.
- [ ] `RsaKeyConfigTest` passes for all three cases (valid, missing, malformed).
- [ ] Full unit test suite passes in both apps.
- [ ] Build is clean.

### Phase 3: Idempotent Seeding and Startup Health Validation

- [ ] **T4 — `ApplicationRunner` seeds organizations and root organizers idempotently using `INSERT … ON CONFLICT DO NOTHING`; logs at INFO/DEBUG; fails fast on unknown org slug; validates at least one org exists post-seed (FR-03, FR-04, FR-05, FR-06, FR-07, FR-09, BR-01–BR-08, AC-03–AC-07, AC-09, AC-12)**
  - **Size:** M
  - **Depends on:** T3
  - **Layers touched:** Application (`ApplicationRunner` orchestration), Infrastructure (raw JDBC `INSERT … ON CONFLICT DO NOTHING` via `JdbcTemplate`), Shared Config (config-properties binding), test
  - **Files:**
    - `apps/backend/src/main/resources/application.yml` — add `seed.organizations: ${SEED_ORGANIZATIONS:[]}` and `seed.root-organizers: ${SEED_ROOT_ORGANIZERS:[]}`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/SeedProperties.java` — `@ConfigurationProperties("seed")` record binding `organizations: String` and `rootOrganizers: String` (raw JSON strings)
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/seed/OrganizationSeedEntry.java` — record: `name`, `slug`, `description`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/seed/OrganizerSeedEntry.java` — record: `email`, `displayName`, `orgSlug`
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/seed/PasswordGenerator.java` — generates 24-char cryptographically secure random password; hashes with `Argon2PasswordEncoder`; returns both plaintext and hash
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/seed/SeedRunner.java` — `ApplicationRunner`; (1) parses `SEED_ORGANIZATIONS` JSON; for each: `INSERT INTO organizations … ON CONFLICT (slug) DO NOTHING` via `JdbcTemplate`; logs INFO on insert / DEBUG on skip; (2) parses `SEED_ROOT_ORGANIZERS` JSON; for each: resolves `organization_id` by slug (fail-fast `IllegalStateException` if absent); inserts `users` row with `role='ORGANIZER'`, `is_temporary_password=true`, hashed password via `ON CONFLICT (email) DO NOTHING`; logs plaintext at INFO exactly once; (3) queries `COUNT(*) FROM organizations`; logs WARN if zero
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/seed/SeedRunnerTest.java` — unit tests (mocked `JdbcTemplate`): org seeded on first run; org skipped on re-run; organizer seeded with `role='ORGANIZER'` (no `ROLE_` prefix), `is_temporary_password=true`; organizer skipped on re-run; unknown `orgSlug` → `IllegalStateException`; plaintext logged exactly once; empty arrays → zero inserts, WARN logged; concurrent run simulation — second identical insert does not raise exception
  - **Acceptance Criteria:**
    - First startup with `SEED_ORGANIZATIONS=[{"name":"Engineering","slug":"engineering"}]` inserts one row and logs `"Seeded organization: engineering"` at INFO (AC-03, FR-03).
    - Restart with same env produces no new row and logs a DEBUG skip message; no exception (AC-04, FR-03).
    - First startup with a matching organizer entry inserts a `users` row with `role = 'ORGANIZER'` (no `ROLE_` prefix), `is_temporary_password = TRUE`, and a non-empty Argon2 hash; plaintext is logged at INFO exactly once and is not stored in the database (AC-05, FR-04, FR-06, FR-07, BR-05).
    - Restart with the same organizer entry produces no new row, leaves existing row unchanged, raises no exception (AC-06, FR-04).
    - Two concurrent `SeedRunner.run()` calls with the same org slug produce exactly one row in `organizations` with no unique-constraint exception propagated (AC-07, FR-05).
    - An organizer entry referencing an unknown `org_slug` causes `IllegalStateException` with a descriptive message (AC-09, FR-04).
    - Empty `SEED_ORGANIZATIONS` and `SEED_ROOT_ORGANIZERS` produce zero insertions and exactly one WARN log (AC-12, FR-09).
    - `created_by` and `updated_by` on all seeded rows are `NULL` (BR-08).

#### Checkpoint: Phase 3

- [ ] `SeedRunnerTest` passes all cases: first run, re-run, unknown slug, concurrent run, empty arrays.
- [ ] Full unit test suite passes.
- [ ] Build is clean.

### Phase 4: TTL Cleanup Scheduler

- [ ] **T5 — `@Scheduled` TTL cleanup job purges expired rows from `idempotency_keys`, `refresh_tokens`, and `password_reset_tokens` in bounded 500-row batches routed to primary; logs deleted row counts at INFO (FR-08, BR-06, BR-07, NFR-04, NFR-08, AC-10, AC-11)**
  - **Size:** S
  - **Depends on:** T1
  - **Layers touched:** Infrastructure (`JdbcTemplate` writes routed via `RoutingDataSource`), Shared Config (`@Scheduled` registration), test
  - **Files:**
    - `apps/backend/src/main/java/com/unihub/workshop/shared/config/TtlCleanupScheduler.java` — `@Component` with `@Scheduled(cron = "0 0 */6 * * *")`; annotated `@Transactional` (default, not `readOnly`) so `RoutingDataSource` routes to primary; for each of the three tables: loops `JdbcTemplate.update("DELETE FROM <table> WHERE expires_at < NOW() LIMIT 500")` until result = 0; accumulates total per table; logs `"TTL cleanup: deleted {} rows from {}"` at INFO per table
    - `apps/backend/src/test/java/com/unihub/workshop/shared/config/TtlCleanupSchedulerTest.java` — unit tests (mocked `JdbcTemplate`): 1200 expired rows → `update()` called 3 times (500, 500, 200) per table; 0 expired rows → `update()` called once returning 0, loop exits cleanly; non-expired rows → zero rows deleted; `@Transactional` annotation present on scheduler method (verified via reflection); INFO log emitted per table with correct count
  - **Acceptance Criteria:**
    - With 1200 expired rows in `idempotency_keys`, the scheduler issues three successive bounded DELETEs (500 + 500 + 200) and logs 1200 deleted rows at INFO for that table (AC-10, FR-08, BR-06).
    - With 10 expired and 50 non-expired rows in `refresh_tokens`, exactly 10 rows are deleted; the 50 non-expired rows are untouched (AC-11, FR-08).
    - The scheduler method carries `@Transactional` (not `readOnly`), ensuring `RoutingDataSource` routes all deletes to primary (BR-07, FR-08).
    - The scheduler emits one INFO log line per table per run with the count of deleted rows (NFR-08).

#### Checkpoint: Feature Complete

- [ ] All five tasks (T1–T5) are checked off.
- [ ] Every acceptance criterion across all tasks has been verified by passing tests.
- [ ] Full unit test suite passes (`./gradlew test` for both `apps/backend` and `apps/workers`).
- [ ] `ModulithArchitectureTest` passes — no unexpected cross-module dependencies introduced.
- [ ] Ready for `/review infrastructure-bootstrap-and-seed-data`.

## Risks and Mitigations

| Risk                                                                                                                                                                                                                                     | Impact | Mitigation                                                                                                                                                                                                                                           |
| :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Spring Boot 4.x `AbstractRoutingDataSource` behavior change — `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` may differ from Spring Framework 5.x behavior documented in older tutorials                             | High   | Read Spring Boot 4.0.6 / Spring Framework 6.x Javadoc before coding T1; write the routing unit test first (Red step) to catch any behavioral deviation before implementation                                                                         |
| HikariCP `DataSourceAutoConfiguration` conflict — Spring Boot may still attempt to create a `DataSource` bean from the now-removed `spring.datasource.url`, causing a context-load failure                                               | High   | Exclude `DataSourceAutoConfiguration` in both apps' `@SpringBootApplication` or ensure the `@Primary` `RoutingDataSource` bean fully pre-empts auto-configuration; validate by loading the full application context in a `@SpringBootTest` during T1 |
| Neon cold-start causing Flyway to hang on startup — serverless Neon can have a cold-start delay; if the JDBC connection blocks indefinitely, the Flyway phase may hang the JVM instead of failing cleanly                                | Medium | Set `spring.datasource.hikari.connection-timeout` (e.g. 10 000 ms) and confirm clean failure behavior during T2; NFR-09 requires the application to fail cleanly, not hang                                                                           |
| RSA PEM loading API change in Spring Security 6.x — `RsaKeyConverters` or related utilities may have moved packages compared to Spring Security 5.x documentation                                                                        | Medium | Check Spring Security 6.x Javadoc for `RsaKeyConverters.pkcs8()` / `RsaKeyConverters.x509()` before T3; write the malformed-PEM test case first to confirm the exception path works as specified                                                     |
| Argon2 encoding CPU cost at startup — hashing 50 accounts with default Argon2 parameters may exceed the NFR-03 5-second `ApplicationRunner` budget on a cold Neon primary                                                                | Medium | Use current Spring Security defaults for `Argon2PasswordEncoder`; benchmark with 50 accounts during T4; if too slow, reduce iterations only for the seeding path and document the tradeoff in an improvement proposal                                |
| Workers `DataSourceAutoConfiguration` exclusion missing — workers `@SpringBootApplication` may not yet exclude the auto-config; with no `spring.datasource.url` present, auto-config may fail to wire any `DataSource` and crash startup | Medium | Confirm workers context loads cleanly with the new YAML structure during T1 test; add `exclude = DataSourceAutoConfiguration.class` to workers' `@SpringBootApplication` if needed                                                                   |

## Open Questions

None — all design decisions in the spec are resolved and implementation ordering above is deterministic given the approved FRs and the existing `application.yml` / `build.gradle` state.
