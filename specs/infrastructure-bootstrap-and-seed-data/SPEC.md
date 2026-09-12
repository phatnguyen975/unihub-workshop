# Spec: Infrastructure Bootstrap and Seed Data

> **Status:** APPROVED  
> **Domain:** 10 — System Reliability & Infrastructure  
> **Owner/area:** `docs/feature-catalog.md §10.4`  
> **Last revised:** 2026-08-17 — first version

## 1. Overview

This feature implements the five infrastructure concerns that must be resolved before any domain module can initialize:

1. **Flyway schema migration:** `V1__init_schema.sql` (and subsequent migrations) run on the Core Backend first, before any application beans, establishing the full relational schema. The Workers app does not declare Flyway as a compile dependency and therefore never runs migrations.
2. **RSA key pair loading:** `private.pem` and `public.pem` are loaded from `resources/security/` at startup to power JWT RS256 signing and JWKS endpoint exposure.
3. **Organization & root Organizer seeding:** an `ApplicationRunner` reads root organization definitions and initial `ORGANIZER` accounts from JSON arrays in environment variables, then inserts only the records that do not already exist using `INSERT … ON CONFLICT DO NOTHING` semantics, making the operation safe for simultaneous multi-node startup.
4. **TTL cleanup scheduler:** a `@Scheduled` job runs every 6 hours to purge expired rows from `idempotency_keys`, `refresh_tokens`, and `user_verification_tokens` in bounded batches, preventing table bloat.
5. **Primary/Replica DataSource routing:** an `AbstractRoutingDataSource` is configured in **both** the Core Backend and the Workers app; it routes DB connections to the Neon primary or read replica based solely on the active transaction's `readOnly` flag. This is the foundational mechanism that every downstream ADR-015 routing requirement depends on.

No REST endpoints are introduced by this feature. No client (web, mobile) is directly affected. Every concern is a startup process, shared infrastructure bean, or scheduled background task running within the Core Backend or the Workers app.

## 2. Actors & Preconditions

### Actors

| Actor      | Involvement                                                                    |
| :--------- | :----------------------------------------------------------------------------- |
| System     | The Spring Boot application itself — all behaviors are internal startup/cron   |
| Operations | Engineers who configure `.env` / environment variables to control seeding data |

No `STUDENT`, `ORGANIZER`, or `STAFF` users interact with this feature directly.

### Preconditions

- **PC-01:** Both `SPRING_DATASOURCE_PRIMARY_URL` and `SPRING_DATASOURCE_REPLICA_URL` (plus their corresponding `USERNAME` / `PASSWORD` pairs) are defined in the app's `.env` file and are referenced in `application.yml` via environment variable placeholders (e.g. `datasource.primary.url: ${SPRING_DATASOURCE_PRIMARY_URL}`). This keeps all externalized configuration declared centrally in `application.yml`, consistent with how the primary datasource is already configured, rather than scattering `@Value` annotations in config classes.
- **PC-02:** `SEED_ORGANIZATIONS` environment variable contains a valid JSON array (may be an empty array `[]`).
- **PC-03:** `SEED_ROOT_ORGANIZERS` environment variable contains a valid JSON array (may be an empty array `[]`).
- **PC-04:** `resources/security/private.pem` and `resources/security/public.pem` are present and contain valid RSA key material (Core Backend only).
- **PC-05:** Flyway is declared as a compile dependency (`spring-boot-starter-flyway`, `flyway-database-postgresql`) in the Core Backend's `build.gradle` and is configured with `spring.flyway.locations=classpath:db/migration`. Flyway is **not** declared in the Workers app's `build.gradle`.
- **PC-06:** Domain modules (`iam`, `workshops`, etc.) have not yet published any events or written data at the time the `ApplicationRunner` executes.

## 3. Functional Requirements

### FR-01 — Flyway runs before ApplicationRunner (Core Backend only)

The system **SHALL** execute all pending Flyway migrations (beginning with `V1__init_schema.sql`) during the Spring Boot startup lifecycle on the Core Backend, strictly before any `ApplicationRunner` or `CommandLineRunner` bean executes.

The Workers app **SHALL NOT** declare Flyway as a compile dependency. Because `FlywayAutoConfiguration` requires the Flyway library on the classpath to activate, the absence of the dependency is the primary and sufficient guard against accidental migration runs. As an additional defensive safeguard, the Workers app's `application.yml` **SHOULD** include `spring.flyway.enabled: false` — this property has no effect when Flyway is absent from the classpath (Spring Boot ignores it safely, with no startup error), but it makes the intent explicit and prevents accidental migration runs if the dependency is ever added to `build.gradle` in the future.

### FR-02 — RSA key pair loading (Core Backend only)

The system **SHALL** load `private.pem` and `public.pem` from `classpath:security/` at application context startup on the Core Backend, expose the private key for JWT RS256 signing, and expose the public key through the JWKS endpoint (implemented by `shared/security`) so that downstream services can verify tokens without holding the private key.

The system **SHALL** fail fast with a descriptive `IllegalStateException` and **prevent** application startup if either PEM file is missing or contains malformed key material.

### FR-03 — Idempotent organization seeding (Core Backend only)

The system **SHALL** read the `SEED_ORGANIZATIONS` environment variable, parse it as a JSON array of organization definition objects, and for each entry attempt to insert a row into the `organizations` table using `INSERT … ON CONFLICT DO NOTHING` (conflict target: `slug`).

The system **SHALL NOT** update or overwrite organizations that already exist in the database.

The system **SHALL** log at `INFO` level the name of every organization it inserts and `DEBUG` for every organization it skips (already present).

### FR-04 — Idempotent root Organizer seeding (Core Backend only)

The system **SHALL** read the `SEED_ROOT_ORGANIZERS` environment variable, parse it as a JSON array of organizer definition objects, and for each entry:

1. Resolve the target `organization_id` by looking up the organization `slug` specified in the entry (which must already exist in the database — either pre-existing or just seeded by FR-03).
2. Attempt to insert a row into the `users` table using `INSERT … ON CONFLICT DO NOTHING` (conflict target: `email`).
3. Set `role = 'ORGANIZER'`, `is_temporary_password = TRUE`, and a securely hashed system-generated password on the inserted row.
4. When a new root organizer row is inserted, publish a `UserProvisionedEvent` via `ApplicationEventPublisher` (externalized to RabbitMQ `unihub.events::notifications`) with `userId`, `organizationId`, `orgSlug`, `email`, `fullName`, `role` ('ORGANIZER'), and `temporaryPassword` for downstream notification delivery.

The system **SHALL NOT** update or overwrite organizer accounts that already exist in the database.

The system **SHALL** log at `INFO` level the email of every organizer it inserts and `DEBUG` for every organizer it skips (already present).

The system **SHALL** fail fast with a descriptive `IllegalStateException` if a seeded organizer entry references an `organization_slug` that does not exist in the database after the organization seeding step of FR-03.

### FR-05 — Multi-node idempotency under concurrent startup

The system **SHALL** guarantee that simultaneous execution of the `ApplicationRunner` on two or more horizontally scaled backend nodes produces exactly the same final database state as a single-node run — no duplicate organizations, no duplicate accounts, no partial rows.

`INSERT … ON CONFLICT DO NOTHING` at the database level provides this guarantee without application-level locking. The implementation **SHALL NOT** use a check-then-insert pattern (`SELECT` + conditional `INSERT`), as this introduces a TOCTOU race under concurrent startup.

### FR-06 — Seeded organizer password generation

The system **SHALL** generate a cryptographically secure random temporary password for every seeded `ORGANIZER` account. The password **SHALL** be hashed with Argon2 (the project's mandated algorithm per `docs/database-schema.md §users`) before being stored. The plaintext password **SHALL** be logged at `INFO` level exactly once during the seed run so that the operations team can retrieve it for the initial handover. The plaintext **SHALL NOT** be stored anywhere in the database.

### FR-07 — `is_temporary_password = TRUE` for all seeded accounts

The system **SHALL** set `is_temporary_password = TRUE` on every newly inserted root `ORGANIZER` row, consistent with the zero-trust provisioning model (ADR-028). This flag forces the account holder to change the password on first login.

### FR-08 — TTL cleanup scheduler (Workers only)

The system **SHALL** register a `@Scheduled` job in the Workers app that executes periodically (configurable via `app.ttl-cleanup.cron` / `TTL_CLEANUP_CRON`, defaulting to every 6 hours: `0 0 */6 * * *`) and purges expired rows from the three TTL-managed tables:

| Table                      | Delete predicate     |
| :------------------------- | :------------------- |
| `idempotency_keys`         | `expires_at < NOW()` |
| `refresh_tokens`           | `expires_at < NOW()` |
| `user_verification_tokens` | `expires_at < NOW()` |

The system **SHALL** execute each table's purge as a bounded `DELETE` (`DELETE FROM <table> WHERE id IN (SELECT id FROM <table> WHERE expires_at < NOW() LIMIT 500)`) to avoid long-running transactions and table locks. The system **SHALL** repeat the bounded delete in a loop until zero rows are affected.

The system **SHALL** log at `INFO` level the number of rows deleted per table per execution.

All TTL cleanup deletes **SHALL** be routed to the **primary** database (per `docs/database-schema.md §4`), as they are write operations. The `RoutingDataSource` established by FR-10 routes them correctly when the usecase method is annotated `@Transactional` (default, not `readOnly`).

### FR-09 — Startup health validation (Core Backend only)

The system **SHALL** verify, as a final step of the `ApplicationRunner`, that at least one organization record exists in the `organizations` table and log a `WARN` message if the table is empty after seeding, to alert operators of a misconfigured environment.

### FR-10 — Primary/Replica DataSource routing (Core Backend AND Workers)

The system **SHALL** configure a `RoutingDataSource` extending Spring's `AbstractRoutingDataSource` in **both** `apps/backend/shared/config` and `apps/workers/shared/config`.

Both primary and replica datasource properties **SHALL** be declared in `application.yml` via environment variable placeholders, using the keys already present in both apps' `.env.example` files:

```yaml
# application.yml (both backend and workers)
datasource:
  primary:
    url: ${SPRING_DATASOURCE_PRIMARY_URL}
    username: ${SPRING_DATASOURCE_PRIMARY_USERNAME}
    password: ${SPRING_DATASOURCE_PRIMARY_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${SPRING_DATASOURCE_PRIMARY_HIKARI_MAXIMUM_POOL_SIZE:10}
      minimum-idle: 2
      connection-timeout-ms: 30000
      idle-timeout-ms: 600000
      max-lifetime-ms: 1800000
      pool-name: primary-pool
  replica:
    url: ${SPRING_DATASOURCE_REPLICA_URL}
    username: ${SPRING_DATASOURCE_REPLICA_USERNAME}
    password: ${SPRING_DATASOURCE_REPLICA_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${SPRING_DATASOURCE_REPLICA_HIKARI_MAXIMUM_POOL_SIZE:10}
      minimum-idle: 2
      connection-timeout-ms: 30000
      idle-timeout-ms: 600000
      max-lifetime-ms: 1800000
      pool-name: replica-pool
```

The routing key resolution **SHALL** be based exclusively on the current Spring transaction context:

| Transaction annotation             | Resolves to                       |
| :--------------------------------- | :-------------------------------- |
| `@Transactional` (default)         | Primary DataSource                |
| `@Transactional(readOnly = false)` | Primary DataSource                |
| `@Transactional(readOnly = true)`  | Replica DataSource                |
| No active transaction              | Primary DataSource (safe default) |

The `RoutingDataSource` **SHALL** be registered as the primary `DataSource` bean so that Spring Data JPA, HikariCP, and JDBC templates transparently use it without per-repository configuration.

Two underlying `HikariDataSource` beans **SHALL** be defined: one for the primary JDBC URL (with default pool name `primary-pool`) and one for the replica JDBC URL (with default pool name `replica-pool`), each with independently tuned HikariCP pool parameters (`maximum-pool-size`, `minimum-idle`, `connection-timeout-ms`, `idle-timeout-ms`, `max-lifetime-ms`, `pool-name`) configured via `application.yml` properties and tagged for Prometheus/Grafana pool metrics (`database-hikaricp.json`).

The existing `spring.datasource.url` key in both `application.yml` files **SHALL** be replaced by the `datasource.primary.*` / `datasource.replica.*` structure above as part of this feature's implementation.

## 4. Business Rules

- **BR-01:** Seeding is driven entirely by environment variables. No seed data is embedded in Flyway migration scripts. Required by ADR-008 to keep schema migrations DDL-only and seed data environment-driven.
- **BR-02:** Organizations are identified by `slug` for conflict detection. `slug` carries a `UNIQUE` constraint in `organizations`. Using `name` for conflict detection is forbidden — names can be updated and are less stable.
- **BR-03:** Root `ORGANIZER` accounts are identified by `email` (CITEXT, UNIQUE) for conflict detection. Comparison is case-insensitive.
- **BR-04:** `is_temporary_password = TRUE` is mandatory for every seeded account. The first-login password change flow (feature: `password-reset`) enforces this flag.
- **BR-05:** `ROLE_` prefixes are strictly forbidden. The `role` column value must be `'ORGANIZER'` (not `'ROLE_ORGANIZER'`). See `docs/database-schema.md §1 — Strict Role Naming`.
- **BR-06:** The TTL cleanup job must use bounded `DELETE` batches to prevent holding long-running table locks. An unbounded `DELETE FROM <table> WHERE expires_at < NOW()` on a large table is an operational risk.
- **BR-07:** The TTL cleanup job must target the primary database. The `@Transactional` annotation (not `readOnly`) on the scheduler method ensures `RoutingDataSource` selects primary.
- **BR-08:** `created_by` and `updated_by` on seeded `organizations` and `users` rows are `NULL`, representing system-generated records. This is the only permitted case for `NULL` on these audit fields per `docs/database-schema.md §organizations`.
- **BR-09:** Flyway's `spring.flyway.out-of-order=false` must remain the default on the Core Backend. Out-of-order migrations can corrupt the schema.
- **BR-10:** Flyway must not be declared as a compile dependency in the Workers app's `build.gradle`. This is the primary guard against accidental migration runs. Adding `spring.flyway.enabled: false` in the workers' `application.yml` is a recommended secondary safeguard; it is inert when Flyway is not on the classpath and does not cause any startup error or warning.
- **BR-11:** The `RoutingDataSource` routing key must be determined solely from the Spring transaction `readOnly` flag. Application code must never reference data-source qualifiers, connection names, or Spring's `@Qualifier` annotation to bypass routing — these would circumvent the centralized routing contract and create hidden primary/replica assumptions in domain code.
- **BR-12:** The no-active-transaction default route is **primary**. Replica connections must never receive uncommitted or unguarded writes; failing toward the primary when transaction context is absent is the only safe default.
- **BR-13:** Both `datasource.primary.*` and `datasource.replica.*` properties must be declared in `application.yml` via environment variable placeholders (not via `@Value` in config classes). This keeps all externalized configuration centralized in `application.yml`, consistent with the existing pattern for RabbitMQ, Redis, and other infrastructure properties.

## 5. State Transitions

This feature does not define a user-visible domain entity with a state machine. The relevant state changes are the startup lifecycle and the recurring cleanup cycle.

### 5.1 Startup Lifecycle Sequence

```
[Process Start — Core Backend]
      │
      ▼
[Flyway applies pending migrations on primary DB]          ← FR-01
      │ success
      ▼
[RoutingDataSource bean initialized (primary + replica)]   ← FR-10
      │
      ▼
[RSA key pair loaded into Spring context]                  ← FR-02
      │ success (fail-fast on error)
      ▼
[ApplicationRunner executes]
      │
      ├──► [Parse SEED_ORGANIZATIONS JSON]
      │         │
      │         └──► for each entry:
      │                INSERT INTO organizations … ON CONFLICT (slug) DO NOTHING  ← FR-03, FR-05
      │
      ├──► [Parse SEED_ROOT_ORGANIZERS JSON]
      │         │
      │         └──► for each entry:
      │                Resolve org_id by slug (fail-fast if not found)         ← FR-04
      │                INSERT INTO users … ON CONFLICT (email) DO NOTHING      ← FR-04, FR-05, FR-06, FR-07
      │
      └──► [Validate ≥1 org row exists; log WARN if empty]                     ← FR-09

[Process Start — Workers app]
      │
      ▼
[Flyway NOT on classpath; spring.flyway.enabled: false recommended (inert)]    ← FR-01
      │
      ▼
[RoutingDataSource bean initialized (primary + replica)]   ← FR-10
```

### 5.2 DataSource Request Routing

```
[Incoming DB operation inside a Spring-managed transaction]
      │
      ▼
[RoutingDataSource.determineCurrentLookupKey()]
      │
      ├── readOnly = true  ──► Replica HikariDataSource
      └── readOnly = false ──► Primary HikariDataSource
          (or no active transaction — safe default)
```

### 5.3 TTL Cleanup Cycle (every 6 hours)

```
[Scheduler fires at 0 0 */6 * * *]
      │
      ▼  @Transactional (readOnly = false) → RoutingDataSource → Primary
[Bounded loop: DELETE FROM idempotency_keys WHERE id IN (SELECT id FROM idempotency_keys WHERE expires_at < NOW() LIMIT 500)]
      │  (repeat until 0 rows affected)
      ▼
[Bounded loop: DELETE FROM refresh_tokens WHERE id IN (SELECT id FROM refresh_tokens WHERE expires_at < NOW() LIMIT 500)]
      │  (repeat until 0 rows affected)
      ▼
[Bounded loop: DELETE FROM user_verification_tokens WHERE id IN (SELECT id FROM user_verification_tokens WHERE expires_at < NOW() LIMIT 500)]
      │  (repeat until 0 rows affected)
      ▼
[Log total rows deleted per table at INFO]
```

## 6. Non-Functional Requirements

| ID     | Category      | Requirement                                                                                                                                                                                                       |
| :----- | :------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| NFR-01 | Correctness   | The seed operation must be idempotent: running it 100 times against a populated database must produce zero additional rows and zero errors.                                                                       |
| NFR-02 | Safety        | The seed operation must never overwrite existing production data. Only `ON CONFLICT DO NOTHING` is permitted for seed paths. `ON CONFLICT DO UPDATE` is forbidden.                                                |
| NFR-03 | Performance   | The `ApplicationRunner` must complete in under 5 seconds on a cold Neon primary with up to 20 organizations and 50 accounts seeded.                                                                               |
| NFR-04 | Performance   | Each TTL cleanup batch must not hold a lock for longer than 1 second per DELETE. The 500-row `LIMIT` in the subquery per batch achieves this when `expires_at` is indexed (all three TTL tables have this index). |
| NFR-05 | Security      | Plaintext temporary passwords must never appear in stored logs, database fields, or HTTP responses. They may only appear in the startup `INFO` log for the initial operator handover.                             |
| NFR-06 | Security      | RSA private key material must never be logged or included in Actuator responses. Actuator `env` and `configprops` endpoints must be secured or excluded from public exposure.                                     |
| NFR-07 | Startup Order | On the Core Backend, Flyway must complete before `RoutingDataSource` is used for seeding. On the Workers app, `RoutingDataSource` is available immediately on startup without a Flyway phase.                     |
| NFR-08 | Observability | The TTL cleanup job must emit a log line at `INFO` per table per run with the count of deleted rows.                                                                                                              |
| NFR-09 | Resilience    | If Neon PostgreSQL is transiently unavailable at startup, the application must fail cleanly (not hang) so the orchestrator (Docker/K8s) can restart the pod.                                                      |
| NFR-10 | Correctness   | The `RoutingDataSource` must transparently handle connection acquisition for all Spring Data JPA repositories, `JdbcTemplate` usages, and `@Scheduled` jobs without per-class configuration.                      |
| NFR-11 | Safety        | A CI check or unit test must verify that `spring.flyway.enabled: false` is present in the workers' `application.yml` as a documented safeguard, so the setting is not silently removed in future refactors.       |

## 7. Acceptance Criteria

### AC-01: Flyway applies the schema before seeding (Core Backend)

```gherkin
Given a fresh database with no tables
When the Core Backend application starts
Then Flyway applies V1__init_schema.sql before the ApplicationRunner executes
And the organizations and users tables exist in the database
And no seed insertion errors occur due to missing tables
```

### AC-02: Flyway does not run on Workers startup

```gherkin
Given the Workers application starts against the same Neon database
And Flyway is not declared as a dependency in the workers' build.gradle
When the workers application context initializes
Then no Flyway migration is attempted
And no Flyway-related error appears in the workers logs
And spring.flyway.enabled is false in the workers application.yml as a documented safeguard
```

### AC-03: Organizations are seeded on first start

```gherkin
Given SEED_ORGANIZATIONS contains a JSON array with two entries (slug "engineering", slug "business")
And neither organization exists in the database
When the Core Backend starts
Then both organizations are inserted into the organizations table
And the application logs "Seeded organization: engineering" and "Seeded organization: business" at INFO
```

### AC-04: Organization seeding is a no-op on restart

```gherkin
Given SEED_ORGANIZATIONS contains one organization (slug "engineering")
And that organization already exists in the database
When the Core Backend starts
Then no new row is inserted into the organizations table
And no error or exception is raised
And the application logs at DEBUG that the organization was skipped
```

### AC-05: Root organizers are seeded on first start

```gherkin
Given SEED_ROOT_ORGANIZERS contains an entry (email "admin@eng.unihub.edu", org_slug "engineering")
And the "engineering" organization exists in the database
And no user with email "admin@eng.unihub.edu" exists
When the Core Backend starts
Then a user row is inserted with role = 'ORGANIZER' and is_temporary_password = TRUE
And the role column value is exactly 'ORGANIZER' (no ROLE_ prefix)
And a hashed temporary password is stored (plaintext is never stored in the database)
And the plaintext temporary password is logged at INFO exactly once
```

### AC-06: Root organizer seeding is a no-op on restart

```gherkin
Given SEED_ROOT_ORGANIZERS contains an entry with email "admin@eng.unihub.edu"
And a user with that email already exists in the database
When the Core Backend starts
Then no new user row is inserted
And the existing user's password, role, and is_temporary_password are not changed
And no error or exception is raised
```

### AC-07: Multi-node concurrent seeding produces no duplicates

```gherkin
Given SEED_ORGANIZATIONS contains one entry (slug "engineering")
And that organization does not yet exist in the database
When Node 1 and Node 2 both start simultaneously and both execute the ApplicationRunner
Then exactly one organization row with slug "engineering" exists in the database
And no unique constraint violation appears in either node's logs
```

### AC-08: Missing RSA key fails startup immediately

```gherkin
Given private.pem is absent from resources/security/
When the Core Backend attempts to start
Then the application context fails to refresh
And an IllegalStateException with a descriptive message is thrown
And the application does not reach the ApplicationRunner phase
```

### AC-09: Unknown org slug in organizer seed fails fast

```gherkin
Given SEED_ROOT_ORGANIZERS contains an entry with org_slug "nonexistent"
And no organization with slug "nonexistent" exists in the database after the org seeding step
When the Core Backend starts
Then an IllegalStateException is thrown during the ApplicationRunner
And the application fails startup with a descriptive error message referencing the unknown slug
```

### AC-10: TTL cleanup removes expired rows in bounded batches

```gherkin
Given the idempotency_keys table contains 1200 rows with expires_at < NOW()
And the cleanup batch size is 500
When the TTL cleanup scheduler fires
Then all 1200 expired rows are deleted across three successive DELETE operations (500 + 500 + 200)
And the scheduler logs the total rows deleted at INFO
```

> **Design Note (ADR):** All `DELETE` batches within a single scheduler run execute inside **one** `@Transactional` method, not per-batch transactions. This was chosen because: (1) PostgreSQL's `WHERE id IN (SELECT … LIMIT 500)` only row-locks the 500 targeted rows, so a wrapping transaction does not block concurrent writes to other rows; (2) each Neon serverless commit is a network round-trip — a single commit per run is significantly cheaper than N commits for N batches; (3) TTL cleanup is fully idempotent — if the run is interrupted, the next scheduled run (6 hours later) will clean the remaining rows with no data loss.

### AC-11: TTL cleanup does not delete non-expired rows

```gherkin
Given refresh_tokens contains 50 rows with expires_at > NOW() and 10 rows with expires_at < NOW()
When the TTL cleanup scheduler fires
Then exactly 10 rows are deleted
And the 50 non-expired rows remain untouched
```

### AC-12: Empty seed arrays are valid and produce a WARN

```gherkin
Given SEED_ORGANIZATIONS is "[]"
And SEED_ROOT_ORGANIZERS is "[]"
When the Core Backend starts
Then no rows are inserted into organizations or users
And no error or exception is raised
And the application logs a WARN message that no organizations exist
```

### AC-13: Write operations route to primary (Core Backend)

```gherkin
Given the RoutingDataSource is initialized with a primary and a replica DataSource
And a Spring-managed @Transactional method (readOnly = false) performs a DB write
When the method executes
Then the write is sent to the primary DataSource connection
And no write is attempted against the replica DataSource
```

### AC-14: Read-only operations route to replica (Core Backend)

```gherkin
Given the RoutingDataSource is initialized with a primary and a replica DataSource
And a Spring-managed @Transactional(readOnly = true) method performs a SELECT query
When the method executes
Then the query is sent to the replica DataSource connection
And the primary DataSource receives no connection request for this operation
```

### AC-15: Workers write operations route to primary

```gherkin
Given the Workers app RoutingDataSource is initialized
And a Spring-managed @Transactional method in a worker use case performs a DB write
When the method executes
Then the write is sent to the primary DataSource connection
And no write is attempted against the replica DataSource
```

## 8. Out of Scope

| Item                                     | Reason                                                                                           |
| :--------------------------------------- | :----------------------------------------------------------------------------------------------- |
| `distributed-rate-limiting` (10.1)       | Bucket4j + Redis filter setup is a separate catalogued feature.                                  |
| `resilience-platform-setup` (10.2)       | Resilience4j circuit breaker configuration is a separate catalogued feature.                     |
| `observability-platform-setup` (10.3)    | Prometheus/Grafana provisioning is a separate catalogued feature.                                |
| Any REST endpoint                        | This feature introduces zero HTTP endpoints.                                                     |
| JWKS endpoint implementation             | Owned by `shared/security`, consumed by `authentication-and-session-management`.                 |
| Organizer self-registration              | Root organizers are seeded by operations, not via a sign-up flow.                                |
| First-login password reset flow          | Covered by the `password-reset` feature.                                                         |
| MinIO bucket initialization              | Covered by `infra/minio/init/create-buckets.sh` (infrastructure layer).                          |
| RabbitMQ exchange/queue definitions      | Covered by `infra/rabbitmq/definitions.json.template`.                                           |
| Spring Batch `JobRepository` table setup | Managed by Spring Batch's own `schema-postgresql.sql`, not this feature's Flyway migration.      |
| Connection pool tuning values            | Pool sizes are configurable via `application.yml` properties; specific tuning is an ops concern. |
| Replica lag monitoring / failover        | Neon-managed concern; the application layer assumes the replica is available when configured.    |

## 9. Linked References

| Reference                                                                    | Relevance                                                                               |
| :--------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------- |
| `docs/feature-catalog.md §10.4`                                              | Authoritative feature entry — module ownership, DB tables, ADR refs, key constraints    |
| `docs/system-design.md §5 — Account Provisioning & Security (Bootstrapping)` | Bootstrapping flow and multi-node idempotency rationale                                 |
| `docs/adrs.md — ADR-005`                                                     | RS256 auth: private key signing + public key JWKS exposure                              |
| `docs/adrs.md — ADR-008`                                                     | Flyway for DDL-only migrations; environment-driven seed data for idempotency            |
| `docs/adrs.md — ADR-015`                                                     | Primary-replica routing strategy; this feature implements the routing infrastructure    |
| `docs/adrs.md — ADR-028`                                                     | Zero-trust provisioning: `is_temporary_password = TRUE` for all seeded accounts         |
| `docs/database-schema.md §organizations`                                     | Table columns, constraints, nullable `created_by` note for system records               |
| `docs/database-schema.md §users`                                             | Table columns, `is_temporary_password`, role constraint (`ORGANIZER`), Argon2 hash      |
| `docs/database-schema.md §idempotency_keys`                                  | TTL target — `expires_at` index and primary-routing requirement                         |
| `docs/database-schema.md §refresh_tokens`                                    | TTL target — `expires_at` index                                                         |
| `docs/database-schema.md §user_verification_tokens`                          | TTL target — `expires_at` index                                                         |
| `docs/database-schema.md §1 — Overview & Database Principles`                | TTL cleanup job mandate (every 6 hours), strict role naming, no `ROLE_` prefix          |
| `docs/database-schema.md §4 — Replication Lag & Read-After-Write`            | Seeding and TTL cleanup writes must target primary; replica for non-critical reads      |
| `docs/project-structure.md §Core Backend`                                    | Package locations: `shared/config`, `shared/security`, `iam` module                     |
| `docs/project-structure.md §Background Workers`                              | Worker `shared/config` location for the workers-side `RoutingDataSource` bean           |
| `apps/backend/.env.example`                                                  | Confirms `SPRING_DATASOURCE_REPLICA_*` keys already exist for `application.yml` mapping |
| `apps/workers/.env.example`                                                  | Confirms `SPRING_DATASOURCE_REPLICA_*` keys already exist for `application.yml` mapping |
