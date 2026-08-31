-- =============================================================================
-- UniHub Workshop — Initial Database Schema
-- PostgreSQL (Neon) · Flyway DDL-only migration
-- =============================================================================


-- =============================================================================
-- EXTENSIONS
-- =============================================================================
-- pgcrypto: provides gen_random_uuid() used in default values for all UUID primary keys
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- citext: case-insensitive text type used for email columns to enforce unique logins
CREATE EXTENSION IF NOT EXISTS citext;


-- =============================================================================
-- SHARED TRIGGER FUNCTION
-- =============================================================================
-- A single reusable function attached to every table via a trigger.
-- This guarantees updated_at accuracy independently of the application layer.
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;


-- =============================================================================
-- TABLE: organizations
-- =============================================================================
-- Logical tenant units (faculties, departments, clubs).
-- Every business table is scoped by organization_id to enforce multi-tenancy.
-- Root organizations are seeded at startup via ApplicationRunner before any
-- users exist, which is why created_by / updated_by are nullable here.
CREATE TABLE organizations (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID         NULL,
    updated_by UUID         NULL,

    CONSTRAINT pk_organizations PRIMARY KEY (id),
    CONSTRAINT uq_organizations_name UNIQUE (name),
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

-- Index: admin listing / filtering by status
CREATE INDEX idx_organizations_status ON organizations (status);

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: users
-- =============================================================================
-- All platform actors: students, organizers, and check-in staff.
-- RBAC is enforced via the role column; ROLE_ prefix is strictly forbidden.
-- student_record_id links a STUDENT user to their authoritative directory entry;
-- NULL means the student has not been verified and cannot register for workshops.
CREATE TABLE users (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id       UUID         NOT NULL,
    role                  VARCHAR(16)  NOT NULL CHECK (role IN ('STUDENT', 'ORGANIZER', 'STAFF')),
    email                 CITEXT       NOT NULL,
    phone_number          VARCHAR(20)  NULL,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(200) NOT NULL,
    avatar_url            TEXT         NULL,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    is_temporary_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at         TIMESTAMPTZ  NULL,
    student_record_id     UUID         NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NULL,
    updated_by            UUID         NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED
);

-- Index: tenant-scoped queries (most common filter in the application)
CREATE INDEX idx_users_organization_id ON users (organization_id);
-- Index: role-based admin queries (e.g., list all STAFF in an org)
CREATE INDEX idx_users_role ON users (role);
-- Index: enforces 1-to-1 link, excludes NULLs (students without a directory match)
CREATE UNIQUE INDEX idx_users_student_record_id_nn
    ON users (student_record_id)
    WHERE student_record_id IS NOT NULL;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_organizations_updated_by FOREIGN KEY (updated_by)
        REFERENCES users (id) DEFERRABLE INITIALLY DEFERRED;


-- =============================================================================
-- TABLE: refresh_tokens
-- =============================================================================
-- Long-lived tokens used to re-issue access JWTs without re-authentication.
-- Raw token is NEVER stored; only its hash. Tokens are rotated on each use.
-- expires_at is managed by the TTL cleanup job (every 6 hours).
CREATE TABLE refresh_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    device_info VARCHAR(255) NULL,
    ip_address  VARCHAR(45)  NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID         NULL,
    updated_by  UUID         NULL,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: session management (e.g., revoke all sessions for a user)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
-- Index: TTL cleanup sweeper queries (WHERE expires_at < now())
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TRIGGER trg_refresh_tokens_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: password_reset_tokens
-- =============================================================================
-- One-time tokens for self-service password recovery flows.
-- Token is stored as a hash only. used_at must be set on first successful reset.
CREATE TABLE password_reset_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID         NULL,
    updated_by  UUID         NULL,

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_password_reset_tokens_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_password_reset_tokens_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: TTL cleanup sweeper queries
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

CREATE TRIGGER trg_password_reset_tokens_updated_at
    BEFORE UPDATE ON password_reset_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: student_directory
-- =============================================================================
-- Authoritative student roster ingested nightly from the legacy CSV export.
-- This is a VALIDATION source, not an authentication source.
-- The Spring Batch job injects organization_id implicitly; CSVs must not
-- contain organization_id or status columns.
CREATE TABLE student_directory (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    student_number  VARCHAR(50)  NOT NULL,
    email           CITEXT       NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    faculty         VARCHAR(200) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    source_file_key TEXT         NULL,
    imported_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         NULL,
    updated_by      UUID         NULL,

    CONSTRAINT pk_student_directory PRIMARY KEY (id),
    CONSTRAINT uq_student_directory_org_number UNIQUE (organization_id, student_number),
    CONSTRAINT uq_student_directory_org_email UNIQUE (organization_id, email),
    CONSTRAINT fk_student_directory_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_student_directory_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_student_directory_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: enrollment status validation during registration
CREATE INDEX idx_student_directory_status ON student_directory (status);
-- Index: audit queries and data retention sweeps
CREATE INDEX idx_student_directory_imported_at ON student_directory (imported_at);

CREATE TRIGGER trg_student_directory_updated_at
    BEFORE UPDATE ON student_directory
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE users
    ADD CONSTRAINT fk_users_student_record FOREIGN KEY (student_record_id)
        REFERENCES student_directory (id) DEFERRABLE INITIALLY DEFERRED;


-- =============================================================================
-- TABLE: ingestion_job_reports
-- =============================================================================
-- Tenant-scoped, application-owned reporting metadata for each final nightly
-- CSV source-file outcome. This is deliberately separate from Spring Batch's
-- framework-managed batch_* tables so organizer report queries can filter and
-- paginate by organization without reading serialized execution context.
CREATE TABLE ingestion_job_reports (
    id                        UUID        NOT NULL DEFAULT gen_random_uuid(),
    job_execution_id          BIGINT      NULL,
    organization_id           UUID        NOT NULL,
    source_file_key           TEXT        NOT NULL,
    outcome                   VARCHAR(32) NOT NULL CHECK (outcome IN ('COMPLETED', 'FILE_REJECTED')),
    processed_row_count       INTEGER     NOT NULL DEFAULT 0 CHECK (processed_row_count >= 0),
    rejected_row_count        INTEGER     NOT NULL DEFAULT 0 CHECK (rejected_row_count >= 0),
    file_rejection_source_key TEXT        NULL,
    reject_artifact_key       TEXT        NULL,
    completed_at              TIMESTAMPTZ NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_ingestion_job_reports PRIMARY KEY (id),
    CONSTRAINT uq_ingestion_job_reports_execution UNIQUE (job_execution_id),
    CONSTRAINT chk_ingestion_job_reports_file_rejection
        CHECK (outcome = 'COMPLETED'
            OR (processed_row_count = 0 AND rejected_row_count = 0
                AND file_rejection_source_key IS NOT NULL AND reject_artifact_key IS NULL)),
    CONSTRAINT chk_ingestion_job_reports_completed_artifacts
        CHECK (outcome = 'FILE_REJECTED' OR file_rejection_source_key IS NULL),
    CONSTRAINT fk_ingestion_job_reports_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

-- Index: supports stable, tenant-scoped cursor pagination for organizer report views
CREATE INDEX idx_ingestion_job_reports_org_completed
    ON ingestion_job_reports (organization_id, completed_at DESC, id DESC);

CREATE TRIGGER trg_ingestion_job_reports_updated_at
    BEFORE UPDATE ON ingestion_job_reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: workshops
-- =============================================================================
-- Core entity for the platform. Lifecycle: DRAFT → PUBLISHED → COMPLETED / CANCELED.
-- version column is used for optimistic locking (@Version in JPA) to prevent
-- lost updates when multiple ORGANIZERs edit the same workshop concurrently.
CREATE TABLE workshops (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID           NOT NULL,
    title            VARCHAR(255)   NOT NULL,
    slug             VARCHAR(255)   NOT NULL,
    description      TEXT           NOT NULL,
    cover_image_url  TEXT           NOT NULL,
    location         VARCHAR(255)   NOT NULL,
    timezone         VARCHAR(64)    NOT NULL,
    starts_at        TIMESTAMPTZ    NOT NULL,
    ends_at          TIMESTAMPTZ    NOT NULL,
    capacity         INTEGER        NOT NULL CHECK (capacity >= 0),
    price            NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    currency         CHAR(3)        NOT NULL DEFAULT 'USD',
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'COMPLETED', 'CANCELED')),
    version          INTEGER        NOT NULL DEFAULT 0,
    published_at     TIMESTAMPTZ    NULL,
    canceled_at      TIMESTAMPTZ    NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by       UUID           NOT NULL,
    updated_by       UUID           NOT NULL,

    CONSTRAINT pk_workshops PRIMARY KEY (id),
    CONSTRAINT uq_workshops_slug UNIQUE (slug),
    CONSTRAINT chk_workshops_ends_after_starts CHECK (ends_at > starts_at),
    CONSTRAINT fk_workshops_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_workshops_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_workshops_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: tenant-scoped listing (most frequent query path)
CREATE INDEX idx_workshops_organization_id ON workshops (organization_id);
-- Index: lifecycle sweeper (hourly job marks PUBLISHED → COMPLETED when ends_at < now())
CREATE INDEX idx_workshops_status ON workshops (status);
-- Composite index: calendar view and date-range search filters
CREATE INDEX idx_workshops_org_starts_at ON workshops (organization_id, starts_at);

CREATE TRIGGER trg_workshops_updated_at
    BEFORE UPDATE ON workshops
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: workshop_speakers
-- =============================================================================
-- Speakers attached to a workshop; used for bio display and scheduling
-- collision detection (same speaker in two overlapping workshops is blocked).
CREATE TABLE workshop_speakers (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    workshop_id  UUID         NOT NULL,
    name         VARCHAR(200) NOT NULL,
    bio          TEXT         NULL,
    avatar_url   TEXT         NULL,
    linkedin_url TEXT         NULL,
    email        CITEXT       NOT NULL,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   UUID         NOT NULL,
    updated_by   UUID         NOT NULL,

    CONSTRAINT pk_workshop_speakers PRIMARY KEY (id),
    CONSTRAINT fk_workshop_speakers_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_workshop_speakers_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_workshop_speakers_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: loading speakers for a workshop detail page
CREATE INDEX idx_workshop_speakers_workshop_id ON workshop_speakers (workshop_id);
-- Index: speaker overlap detection and deduplication checks across workshops
CREATE INDEX idx_workshop_speakers_email ON workshop_speakers (email);

CREATE TRIGGER trg_workshop_speakers_updated_at
    BEFORE UPDATE ON workshop_speakers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: workshop_assignments
-- =============================================================================
-- Explicit mapping of STAFF users to workshops they are authorized to scan.
-- STAFF cannot check in at any workshop they are not explicitly assigned to,
-- even within the same organization (zero-trust assignment boundary).
CREATE TABLE workshop_assignments (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    workshop_id    UUID         NOT NULL,
    staff_user_id  UUID         NOT NULL,
    assigned_by    UUID         NOT NULL,
    assigned_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NOT NULL,
    updated_by     UUID         NOT NULL,

    CONSTRAINT pk_workshop_assignments PRIMARY KEY (id),
    CONSTRAINT uq_workshop_assignments_workshop_staff UNIQUE (workshop_id, staff_user_id),
    CONSTRAINT fk_workshop_assignments_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_workshop_assignments_staff FOREIGN KEY (staff_user_id) REFERENCES users (id),
    CONSTRAINT fk_workshop_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_workshop_assignments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_workshop_assignments_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: look up all workshops assigned to a STAFF member (workload view)
CREATE INDEX idx_workshop_assignments_staff_user_id ON workshop_assignments (staff_user_id);

CREATE TRIGGER trg_workshop_assignments_updated_at
    BEFORE UPDATE ON workshop_assignments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: workshop_brochures
-- =============================================================================
-- Metadata for PDFs uploaded by ORGANIZERs and stored in MinIO / S3.
-- Only one brochure per workshop may be active at a time (partial unique index)
-- to avoid ambiguous AI summarization inputs.
CREATE TABLE workshop_brochures (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    workshop_id      UUID         NOT NULL,
    storage_key      TEXT         NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    byte_size        BIGINT       NOT NULL CHECK (byte_size >= 0),
    checksum_sha256  CHAR(64)     NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    uploaded_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID         NOT NULL,
    updated_by       UUID         NOT NULL,

    CONSTRAINT pk_workshop_brochures PRIMARY KEY (id),
    CONSTRAINT fk_workshop_brochures_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_workshop_brochures_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_workshop_brochures_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: look up brochures for a given workshop
CREATE INDEX idx_workshop_brochures_workshop_id ON workshop_brochures (workshop_id);
-- Partial unique index: at most one active brochure per workshop at any time
CREATE UNIQUE INDEX idx_workshop_brochures_active
    ON workshop_brochures (workshop_id)
    WHERE is_active = TRUE;

CREATE TRIGGER trg_workshop_brochures_updated_at
    BEFORE UPDATE ON workshop_brochures
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: idempotency_keys
-- =============================================================================
-- Stores client-supplied idempotency keys and cached responses to guarantee
-- exactly-once processing for registration and payment flows.
-- States: PENDING (in-flight lock) → COMPLETED (serve cached) / FAILED (retry ok).
-- expires_at is managed by the TTL cleanup job (every 6 hours).
-- CRITICAL: All reads and writes on this table must route to the PRIMARY replica.
CREATE TABLE idempotency_keys (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    idempotency_key  VARCHAR(64)  NOT NULL,
    request_method   VARCHAR(10)  NOT NULL,
    request_path     VARCHAR(255) NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    response_code    INTEGER      NULL,
    response_body    JSONB        NULL,
    response_headers JSONB        NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID         NULL,
    updated_by       UUID         NULL,

    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_idempotency_keys_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_idempotency_keys_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: sweeper queries for stale PENDING or FAILED keys
CREATE INDEX idx_idempotency_keys_status ON idempotency_keys (status);
-- Index: TTL cleanup job
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

CREATE TRIGGER trg_idempotency_keys_updated_at
    BEFORE UPDATE ON idempotency_keys
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: registrations
-- =============================================================================
-- Core registration lifecycle table. States: PENDING → CONFIRMED / FAILED / CANCELED.
-- CRITICAL: qr_payload, qr_signature, and qr_issued_at are intentionally NULL at
-- insert time. They must ONLY be populated atomically inside the PENDING → CONFIRMED
-- transition transaction. Application code enforces this invariant.
-- CRITICAL: All reads/writes must route to the PRIMARY replica.
CREATE TABLE registrations (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    workshop_id           UUID           NOT NULL,
    student_user_id       UUID           NOT NULL,
    status                VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELED')),
    price_at_registration NUMERIC(10, 2) NOT NULL DEFAULT 0,
    currency              CHAR(3)        NOT NULL DEFAULT 'USD',
    idempotency_key_id    UUID           NOT NULL,
    qr_payload            TEXT           NULL,
    qr_signature          TEXT           NULL,
    qr_issued_at          TIMESTAMPTZ    NULL,
    expires_at            TIMESTAMPTZ    NULL,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by            UUID           NOT NULL,
    updated_by            UUID           NOT NULL,

    CONSTRAINT pk_registrations PRIMARY KEY (id),
    CONSTRAINT fk_registrations_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_registrations_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_registrations_idempotency FOREIGN KEY (idempotency_key_id) REFERENCES idempotency_keys (id),
    CONSTRAINT fk_registrations_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_registrations_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: workshop-scoped registration lookups (admin, analytics)
CREATE INDEX idx_registrations_workshop_id ON registrations (workshop_id);
-- Index: student registration history
CREATE INDEX idx_registrations_student_user_id ON registrations (student_user_id);
-- Partial unique index: prevents a student from holding two active registrations
-- for the same workshop simultaneously (last-resort DB guard for overbooking)
CREATE UNIQUE INDEX idx_registrations_active_per_student
    ON registrations (workshop_id, student_user_id)
    WHERE status IN ('PENDING', 'CONFIRMED');
-- Composite partial index: Dual-Case Reconciliation Sweeper query
-- (WHERE status = 'PENDING' AND expires_at < NOW()), includes workshop_id for
-- efficient per-workshop capacity refund operations in the same sweep pass
CREATE INDEX idx_registrations_sweeper
    ON registrations (expires_at, workshop_id)
    WHERE status = 'PENDING';

CREATE TRIGGER trg_registrations_updated_at
    BEFORE UPDATE ON registrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: payments
-- =============================================================================
-- Payment records for paid workshop registrations.
-- States: PENDING → COMPLETED / FAILED / REFUNDED.
-- provider_payment_id is UNIQUE to guarantee idempotent webhook processing:
-- if the gateway retries a webhook, the handler finds a terminal-state record
-- and returns 200 OK without reprocessing.
-- CRITICAL: Status transitions must be coordinated with registrations in the
-- same transaction to maintain consistency.
CREATE TABLE payments (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    registration_id      UUID           NOT NULL,
    provider             VARCHAR(50)    NOT NULL,
    provider_payment_id  VARCHAR(100)   NOT NULL,
    status               VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    amount               NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    currency             CHAR(3)        NOT NULL,
    completed_at         TIMESTAMPTZ    NULL,
    failed_at            TIMESTAMPTZ    NULL,
    failure_code         VARCHAR(100)   NULL,
    failure_reason       TEXT           NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID           NULL,
    updated_by           UUID           NULL,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_registration_id UNIQUE (registration_id),
    CONSTRAINT uq_payments_provider_payment_id UNIQUE (provider_payment_id),
    CONSTRAINT fk_payments_registration FOREIGN KEY (registration_id) REFERENCES registrations (id),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_payments_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: reconciliation sweeper and status-based admin queries
CREATE INDEX idx_payments_status ON payments (status);

CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: attendances
-- =============================================================================
-- Records QR check-ins performed by STAFF, both real-time and offline-synced.
-- sync_id is a device-generated UUIDv4 used as the idempotency key for offline
-- sync batches; (workshop_id, sync_id) uniqueness resolves ALREADY_USED responses.
-- No hard deletes: fraudulent or accidental check-ins are marked INVALIDATED.
CREATE TABLE attendances (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    workshop_id           UUID         NOT NULL,
    registration_id       UUID         NOT NULL,
    checked_in_by_user_id UUID         NOT NULL,
    sync_id               UUID         NOT NULL,
    device_id             VARCHAR(100) NULL,
    check_in_method       VARCHAR(20)  NOT NULL CHECK (check_in_method IN ('OFFLINE_SYNC', 'REALTIME_API')),
    status                VARCHAR(20)  NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'INVALIDATED')),
    checked_in_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            UUID         NOT NULL,
    updated_by            UUID         NOT NULL,

    CONSTRAINT pk_attendances PRIMARY KEY (id),
    CONSTRAINT uq_attendances_workshop_registration UNIQUE (workshop_id, registration_id),
    CONSTRAINT uq_attendances_workshop_sync_id UNIQUE (workshop_id, sync_id),
    CONSTRAINT fk_attendances_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_attendances_registration FOREIGN KEY (registration_id) REFERENCES registrations (id),
    CONSTRAINT fk_attendances_checked_in_by FOREIGN KEY (checked_in_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_attendances_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_attendances_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: filter out invalidated records for attendance reporting
CREATE INDEX idx_attendances_status ON attendances (status);
-- Index: time-based attendance reports
CREATE INDEX idx_attendances_checked_in_at ON attendances (checked_in_at);

CREATE TRIGGER trg_attendances_updated_at
    BEFORE UPDATE ON attendances
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: ai_summary_jobs
-- =============================================================================
-- Tracks async AI summarization jobs triggered by ORGANIZER brochure uploads.
-- States: PROCESSING → COMPLETED / FAILED.
-- Workers drive all state transitions; the web client is read-only on this table.
-- next_retry_at drives broker-backed backoff retry scheduling.
CREATE TABLE ai_summary_jobs (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    workshop_id    UUID         NOT NULL,
    brochure_id    UUID         NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    result_text    TEXT         NULL,
    error_message  TEXT         NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMPTZ  NULL,
    completed_at   TIMESTAMPTZ  NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID         NOT NULL,
    updated_by     UUID         NOT NULL,

    CONSTRAINT pk_ai_summary_jobs PRIMARY KEY (id),
    CONSTRAINT fk_ai_summary_jobs_workshop FOREIGN KEY (workshop_id) REFERENCES workshops (id),
    CONSTRAINT fk_ai_summary_jobs_brochure FOREIGN KEY (brochure_id) REFERENCES workshop_brochures (id),
    CONSTRAINT fk_ai_summary_jobs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_ai_summary_jobs_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: worker polling for PROCESSING jobs and monitoring dashboards
CREATE INDEX idx_ai_summary_jobs_status ON ai_summary_jobs (status);
-- Index: retry scheduler (WHERE next_retry_at <= now() AND status = 'PROCESSING')
CREATE INDEX idx_ai_summary_jobs_next_retry_at ON ai_summary_jobs (next_retry_at);
-- Composite index: admin job history view per workshop, ordered by creation time
CREATE INDEX idx_ai_summary_jobs_workshop_created ON ai_summary_jobs (workshop_id, created_at);

CREATE TRIGGER trg_ai_summary_jobs_updated_at
    BEFORE UPDATE ON ai_summary_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: in_app_notifications
-- =============================================================================
-- User-facing notifications rendered in the web and mobile UI notification center.
-- is_read / read_at power the unread badge counter.
-- event_key identifies the domain event that generated the notification.
CREATE TABLE in_app_notifications (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    event_key  VARCHAR(100) NOT NULL,
    payload    JSONB        NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMPTZ  NULL,
    action_url VARCHAR(255) NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID         NULL,
    updated_by UUID         NULL,

    CONSTRAINT pk_in_app_notifications PRIMARY KEY (id),
    CONSTRAINT fk_in_app_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_in_app_notifications_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_in_app_notifications_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Composite index: primary read path for loading a user's unread notifications
-- (heavily optimised for the unread badge counter query: WHERE user_id = ? AND is_read = false)
CREATE INDEX idx_in_app_notifications_user_is_read
    ON in_app_notifications (user_id, is_read);

CREATE TRIGGER trg_in_app_notifications_updated_at
    BEFORE UPDATE ON in_app_notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: notification_delivery_logs
-- =============================================================================
-- System-level audit log for external notification delivery (email, push, SMS).
-- Populated by background workers; never shown in the user-facing UI.
-- Used by DevOps for debugging failed deliveries and retry analysis.
CREATE TABLE notification_delivery_logs (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id              UUID         NULL,
    channel              VARCHAR(20)  NOT NULL CHECK (channel IN ('EMAIL', 'PUSH', 'SMS')),
    recipient_address    VARCHAR(255) NOT NULL,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    provider_message_id  VARCHAR(200) NULL,
    error_reason         TEXT         NULL,
    sent_at              TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID         NULL,
    updated_by           UUID         NULL,

    CONSTRAINT pk_notification_delivery_logs PRIMARY KEY (id),
    CONSTRAINT fk_notification_delivery_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notification_delivery_logs_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_notification_delivery_logs_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- Index: worker retry queries (WHERE status = 'FAILED' AND channel = ?)
CREATE INDEX idx_notification_delivery_logs_status_channel ON notification_delivery_logs (status, channel);
-- Index: admin audit history per user
CREATE INDEX idx_notification_delivery_logs_user_id ON notification_delivery_logs (user_id);

CREATE TRIGGER trg_notification_delivery_logs_updated_at
    BEFORE UPDATE ON notification_delivery_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE: event_publication
-- =============================================================================
-- Transactional outbox table managed entirely by Spring Modulith.
-- WARNING: Do NOT rename columns, add constraints, or change types.
--          Spring Modulith validates this schema at startup via its internal
--          JDBC registry; any deviation causes application startup failure.
CREATE TABLE event_publication (
    id                     UUID         NOT NULL,
    listener_id            TEXT         NOT NULL,
    event_type             TEXT         NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completion_date        TIMESTAMPTZ  NULL,
    status                 TEXT         NULL,
    completion_attempts    INTEGER      NULL DEFAULT 0 CHECK (completion_attempts >= 0),
    last_resubmission_date TIMESTAMPTZ  NULL,

    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

-- Composite index: resubmission sweep and backlog ordering
CREATE INDEX idx_event_publication_status_date
    ON event_publication (status, publication_date);
