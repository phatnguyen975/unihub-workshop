CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) NOT NULL,
  student_id VARCHAR(50),
  full_name VARCHAR(200) NOT NULL,
  phone VARCHAR(30),
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL CHECK (role IN ('STUDENT','ORGANIZER','STAFF')),
  status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' CHECK (status IN ('UNVERIFIED','ACTIVE','LOCKED')),
  organization_id UUID,
  is_temporary_password BOOLEAN NOT NULL DEFAULT false,
  failed_login_attempts INT NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
  lockout_until TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_users_student_id UNIQUE (student_id)
);

CREATE TABLE organizations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(200) NOT NULL,
  description TEXT,
  address VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  organizer_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users
  ADD CONSTRAINT fk_users_organization
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT;

ALTER TABLE organizations
  ADD CONSTRAINT fk_organizations_organizer
  FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE RESTRICT;

CREATE TABLE workshops (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organizer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  location VARCHAR(255) NOT NULL,
  venue VARCHAR(255),
  room VARCHAR(120),
  room_layout_key VARCHAR(512),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','CANCELED','COMPLETED')),
  version INT NOT NULL DEFAULT 0 CHECK (version >= 0),
  capacity_total INT NOT NULL DEFAULT 0 CHECK (capacity_total >= 0),
  capacity_remaining INT NOT NULL DEFAULT 0 CHECK (capacity_remaining >= 0 AND capacity_remaining <= capacity_total),
  price_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (price_amount >= 0),
  currency CHAR(3) CHECK ((price_amount = 0 AND currency IS NULL) OR (price_amount > 0 AND currency IS NOT NULL)),
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL CHECK (end_time > start_time),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workshop_speakers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workshop_id UUID NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
  speaker_name VARCHAR(200) NOT NULL,
  speaker_bio TEXT,
  sort_order INT NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workshop_assignments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workshop_id UUID NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
  staff_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_workshop_assignments UNIQUE (workshop_id, staff_id)
);

CREATE TABLE registrations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  workshop_id UUID NOT NULL REFERENCES workshops(id) ON DELETE RESTRICT,
  idempotency_key VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','CONFIRMED','CANCELED')),
  ticket_type VARCHAR(20) NOT NULL DEFAULT 'FREE' CHECK (ticket_type IN ('FREE','PAID')),
  qr_code_payload TEXT,
  qr_issued_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_registrations_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  registration_id UUID NOT NULL REFERENCES registrations(id) ON DELETE CASCADE,
  provider VARCHAR(50) NOT NULL,
  provider_ref VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
  amount NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (amount >= 0),
  currency CHAR(3) NOT NULL,
  failure_reason TEXT,
  provider_payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_payments_provider_ref UNIQUE (provider_ref)
);

CREATE TABLE attendances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  registration_id UUID NOT NULL REFERENCES registrations(id) ON DELETE RESTRICT,
  checked_in_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  scanned_at TIMESTAMPTZ,
  checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source VARCHAR(20) NOT NULL DEFAULT 'ONLINE' CHECK (source IN ('ONLINE','OFFLINE','SYNC')),
  offline_mode_flag BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_attendances_registration UNIQUE (registration_id)
);

CREATE TABLE brochures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workshop_id UUID NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
  storage_key VARCHAR(512) NOT NULL,
  file_name VARCHAR(255),
  mime_type VARCHAR(100),
  size_bytes BIGINT CHECK (size_bytes >= 0),
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' CHECK (status IN ('UPLOADED','DELETED')),
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_brochures_storage_key UNIQUE (storage_key)
);

CREATE TABLE ai_summaries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workshop_id UUID NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
  summary_text TEXT,
  error_message TEXT,
  retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  last_attempt_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_summaries_workshop UNIQUE (workshop_id)
);

CREATE TABLE event_publication (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  completion_date TIMESTAMPTZ,
  completion_attempts INT NOT NULL DEFAULT 0 CHECK (completion_attempts >= 0),
  event_type VARCHAR(255) NOT NULL,
  listener_id VARCHAR(255) NOT NULL,
  publication_date TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_resubmission_date TIMESTAMPTZ,
  serialized_event TEXT NOT NULL,
  status VARCHAR(255)
);

CREATE TABLE idempotency_keys (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key VARCHAR(255) NOT NULL,
  request_hash TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED','FAILED')),
  response_status_code INT,
  response_body JSONB,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_idempotency_keys_key UNIQUE (idempotency_key)
);

CREATE TABLE student_import_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_file VARCHAR(512) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' CHECK (status IN ('PROCESSING','COMPLETED','FAILED')),
  total_rows INT NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
  quarantined_rows INT NOT NULL DEFAULT 0 CHECK (quarantined_rows >= 0),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE student_import_errors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  batch_id UUID NOT NULL REFERENCES student_import_batches(id) ON DELETE CASCADE,
  row_number INT NOT NULL CHECK (row_number > 0),
  error_reason TEXT NOT NULL,
  raw_row TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(100) NOT NULL,
  subject_template VARCHAR(255) NOT NULL,
  body_template TEXT NOT NULL,
  version INT NOT NULL DEFAULT 1 CHECK (version >= 1),
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE in_app_notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  event_type VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  payload JSONB,
  status VARCHAR(20) NOT NULL DEFAULT 'UNREAD' CHECK (status IN ('UNREAD','READ')),
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  registration_id UUID REFERENCES registrations(id) ON DELETE RESTRICT,
  batch_id UUID REFERENCES student_import_batches(id) ON DELETE SET NULL,
  template_id UUID REFERENCES notification_templates(id) ON DELETE SET NULL,
  event_type VARCHAR(100) NOT NULL,
  channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL' CHECK (channel IN ('EMAIL','SMS','PUSH','IN_APP')),
  status VARCHAR(30) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','SENT','DELIVERED','FAILED','PERMANENT_FAILURE')),
  recipient_address VARCHAR(255) NOT NULL,
  provider_message_id VARCHAR(100),
  error_message TEXT,
  payload JSONB,
  status_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  jti UUID NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by_jti UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_refresh_tokens_jti UNIQUE (jti)
);

CREATE TABLE password_reset_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE INDEX idx_users_full_name ON users (full_name);
CREATE INDEX idx_users_phone ON users (phone);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_lockout_until ON users (lockout_until);
CREATE INDEX idx_users_last_login_at ON users (last_login_at);
CREATE INDEX idx_users_created_at ON users (created_at);
CREATE INDEX idx_users_updated_at ON users (updated_at);

CREATE INDEX idx_organizations_name ON organizations (name);
CREATE INDEX idx_organizations_status ON organizations (status);
CREATE INDEX idx_organizations_organizer_id ON organizations (organizer_id);
CREATE INDEX idx_organizations_created_at ON organizations (created_at);
CREATE INDEX idx_organizations_updated_at ON organizations (updated_at);

CREATE INDEX idx_workshops_organizer_id ON workshops (organizer_id);
CREATE INDEX idx_workshops_title ON workshops (title);
CREATE INDEX idx_workshops_location ON workshops (location);
CREATE INDEX idx_workshops_venue ON workshops (venue);
CREATE INDEX idx_workshops_room ON workshops (room);
CREATE INDEX idx_workshops_room_layout_key ON workshops (room_layout_key);
CREATE INDEX idx_workshops_status ON workshops (status);
CREATE INDEX idx_workshops_currency ON workshops (currency);
CREATE INDEX idx_workshops_start_time ON workshops (start_time);
CREATE INDEX idx_workshops_end_time ON workshops (end_time);
CREATE INDEX idx_workshops_created_at ON workshops (created_at);
CREATE INDEX idx_workshops_updated_at ON workshops (updated_at);
CREATE INDEX idx_workshops_fts ON workshops USING GIN (to_tsvector('simple', title || ' ' || coalesce(description, '')));

CREATE INDEX idx_workshop_speakers_workshop_id ON workshop_speakers (workshop_id);
CREATE INDEX idx_workshop_speakers_name ON workshop_speakers (speaker_name);
CREATE INDEX idx_workshop_speakers_sort_order ON workshop_speakers (sort_order);
CREATE INDEX idx_workshop_speakers_created_at ON workshop_speakers (created_at);
CREATE INDEX idx_workshop_speakers_updated_at ON workshop_speakers (updated_at);

CREATE INDEX idx_workshop_assignments_staff_id ON workshop_assignments (staff_id);
CREATE INDEX idx_workshop_assignments_assigned_at ON workshop_assignments (assigned_at);
CREATE INDEX idx_workshop_assignments_created_at ON workshop_assignments (created_at);
CREATE INDEX idx_workshop_assignments_updated_at ON workshop_assignments (updated_at);

CREATE INDEX idx_registrations_user_id ON registrations (user_id);
CREATE INDEX idx_registrations_workshop_id ON registrations (workshop_id);
CREATE INDEX idx_registrations_status ON registrations (status);
CREATE INDEX idx_registrations_ticket_type ON registrations (ticket_type);
CREATE INDEX idx_registrations_qr_issued_at ON registrations (qr_issued_at);
CREATE INDEX idx_registrations_created_at ON registrations (created_at);
CREATE INDEX idx_registrations_updated_at ON registrations (updated_at);
CREATE UNIQUE INDEX idx_registrations_active_unique ON registrations (user_id, workshop_id)
  WHERE status IN ('PENDING','CONFIRMED');

CREATE INDEX idx_payments_registration_id ON payments (registration_id);
CREATE INDEX idx_payments_provider ON payments (provider);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_currency ON payments (currency);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_payments_updated_at ON payments (updated_at);
CREATE INDEX idx_payments_provider_payload ON payments USING GIN (provider_payload);

CREATE INDEX idx_attendances_checked_in_by ON attendances (checked_in_by_user_id);
CREATE INDEX idx_attendances_scanned_at ON attendances (scanned_at);
CREATE INDEX idx_attendances_checked_in_at ON attendances (checked_in_at);
CREATE INDEX idx_attendances_source ON attendances (source);
CREATE INDEX idx_attendances_created_at ON attendances (created_at);
CREATE INDEX idx_attendances_updated_at ON attendances (updated_at);

CREATE INDEX idx_brochures_workshop_id ON brochures (workshop_id);
CREATE INDEX idx_brochures_status ON brochures (status);
CREATE INDEX idx_brochures_uploaded_at ON brochures (uploaded_at);
CREATE INDEX idx_brochures_created_at ON brochures (created_at);
CREATE INDEX idx_brochures_updated_at ON brochures (updated_at);

CREATE INDEX idx_ai_summaries_status ON ai_summaries (status);
CREATE INDEX idx_ai_summaries_last_attempt_at ON ai_summaries (last_attempt_at);
CREATE INDEX idx_ai_summaries_created_at ON ai_summaries (created_at);
CREATE INDEX idx_ai_summaries_updated_at ON ai_summaries (updated_at);

CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
CREATE INDEX idx_event_publication_event_type ON event_publication (event_type);
CREATE INDEX idx_event_publication_listener_id ON event_publication (listener_id);
CREATE INDEX idx_event_publication_publication_date ON event_publication (publication_date);
CREATE INDEX idx_event_publication_pending ON event_publication (publication_date)
  WHERE completion_date IS NULL;

CREATE INDEX idx_idempotency_keys_status ON idempotency_keys (status);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
CREATE INDEX idx_idempotency_keys_updated_at ON idempotency_keys (updated_at);
CREATE INDEX idx_idempotency_keys_response_body ON idempotency_keys USING GIN (response_body);

CREATE INDEX idx_student_import_batches_source_file ON student_import_batches (source_file);
CREATE INDEX idx_student_import_batches_status ON student_import_batches (status);
CREATE INDEX idx_student_import_batches_started_at ON student_import_batches (started_at);
CREATE INDEX idx_student_import_batches_completed_at ON student_import_batches (completed_at);
CREATE INDEX idx_student_import_batches_created_at ON student_import_batches (created_at);
CREATE INDEX idx_student_import_batches_updated_at ON student_import_batches (updated_at);

CREATE INDEX idx_student_import_errors_batch_id ON student_import_errors (batch_id);
CREATE INDEX idx_student_import_errors_row_number ON student_import_errors (row_number);
CREATE INDEX idx_student_import_errors_created_at ON student_import_errors (created_at);
CREATE INDEX idx_student_import_errors_updated_at ON student_import_errors (updated_at);

CREATE INDEX idx_notification_templates_event_type ON notification_templates (event_type);
CREATE INDEX idx_notification_templates_event_type_version ON notification_templates (event_type, version);
CREATE INDEX idx_notification_templates_is_active ON notification_templates (is_active);
CREATE INDEX idx_notification_templates_created_at ON notification_templates (created_at);
CREATE INDEX idx_notification_templates_updated_at ON notification_templates (updated_at);
CREATE UNIQUE INDEX idx_active_template ON notification_templates (event_type)
  WHERE is_active = true;

CREATE INDEX idx_in_app_notifications_user_id ON in_app_notifications (user_id);
CREATE INDEX idx_in_app_notifications_event_type ON in_app_notifications (event_type);
CREATE INDEX idx_in_app_notifications_status ON in_app_notifications (status);
CREATE INDEX idx_in_app_notifications_read_at ON in_app_notifications (read_at);
CREATE INDEX idx_in_app_notifications_created_at ON in_app_notifications (created_at);
CREATE INDEX idx_in_app_notifications_updated_at ON in_app_notifications (updated_at);
CREATE INDEX idx_in_app_notifications_payload ON in_app_notifications USING GIN (payload);

CREATE INDEX idx_notification_logs_registration_id ON notification_logs (registration_id);
CREATE INDEX idx_notification_logs_batch_id ON notification_logs (batch_id);
CREATE INDEX idx_notification_logs_template_id ON notification_logs (template_id);
CREATE INDEX idx_notification_logs_event_type ON notification_logs (event_type);
CREATE INDEX idx_notification_logs_channel ON notification_logs (channel);
CREATE INDEX idx_notification_logs_status ON notification_logs (status);
CREATE INDEX idx_notification_logs_recipient_address ON notification_logs (recipient_address);
CREATE INDEX idx_notification_logs_status_at ON notification_logs (status_at);
CREATE INDEX idx_notification_logs_created_at ON notification_logs (created_at);
CREATE INDEX idx_notification_logs_updated_at ON notification_logs (updated_at);
CREATE INDEX idx_notification_logs_payload ON notification_logs USING GIN (payload);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_issued_at ON refresh_tokens (issued_at);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens (revoked_at);
CREATE INDEX idx_refresh_tokens_created_at ON refresh_tokens (created_at);
CREATE INDEX idx_refresh_tokens_updated_at ON refresh_tokens (updated_at);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
CREATE INDEX idx_password_reset_tokens_used_at ON password_reset_tokens (used_at);
CREATE INDEX idx_password_reset_tokens_created_at ON password_reset_tokens (created_at);
CREATE INDEX idx_password_reset_tokens_updated_at ON password_reset_tokens (updated_at);

CREATE TRIGGER trg_set_updated_at_users
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_organizations
BEFORE UPDATE ON organizations
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_workshops
BEFORE UPDATE ON workshops
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_workshop_speakers
BEFORE UPDATE ON workshop_speakers
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_workshop_assignments
BEFORE UPDATE ON workshop_assignments
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_registrations
BEFORE UPDATE ON registrations
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_payments
BEFORE UPDATE ON payments
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_attendances
BEFORE UPDATE ON attendances
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_brochures
BEFORE UPDATE ON brochures
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_ai_summaries
BEFORE UPDATE ON ai_summaries
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_idempotency_keys
BEFORE UPDATE ON idempotency_keys
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_student_import_batches
BEFORE UPDATE ON student_import_batches
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_student_import_errors
BEFORE UPDATE ON student_import_errors
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_notification_templates
BEFORE UPDATE ON notification_templates
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_in_app_notifications
BEFORE UPDATE ON in_app_notifications
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_notification_logs
BEFORE UPDATE ON notification_logs
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_refresh_tokens
BEFORE UPDATE ON refresh_tokens
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_set_updated_at_password_reset_tokens
BEFORE UPDATE ON password_reset_tokens
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
