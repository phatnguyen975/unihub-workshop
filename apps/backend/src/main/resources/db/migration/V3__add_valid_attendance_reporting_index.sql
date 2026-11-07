CREATE INDEX CONCURRENTLY idx_attendances_workshop_valid_checked_in_at_id
    ON attendances (workshop_id, checked_in_at DESC, id DESC)
    WHERE status = 'VALID';
