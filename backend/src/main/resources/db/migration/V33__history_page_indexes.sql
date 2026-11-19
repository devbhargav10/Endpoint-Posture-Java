-- Support deterministic history pagination and fleet list ordering. Each replacement index
-- extends an existing lookup index with the UUID tie-breaker used by ORDER BY clauses.
CREATE INDEX IF NOT EXISTS idx_assessment_endpoint_created_id
    ON assessment(endpoint_id, created_at DESC, id DESC);
DROP INDEX IF EXISTS idx_assessment_endpoint_created;

CREATE INDEX IF NOT EXISTS idx_hw_health_endpoint_collected_id
    ON hardware_health(endpoint_id, collected_at DESC, id DESC);
DROP INDEX IF EXISTS idx_hw_health_endpoint_collected;

CREATE INDEX IF NOT EXISTS idx_diagnostic_endpoint_collected_id
    ON endpoint_diagnostic(endpoint_id, collected_at DESC, id DESC);
DROP INDEX IF EXISTS idx_diagnostic_endpoint_collected;

CREATE INDEX IF NOT EXISTS idx_sec_ind_endpoint_collected_id
    ON endpoint_security_indicator(endpoint_id, collected_at DESC, id DESC);
DROP INDEX IF EXISTS idx_sec_ind_endpoint_collected;

CREATE INDEX IF NOT EXISTS idx_session_log_endpoint_event_id
    ON endpoint_session_log(endpoint_id, event_at DESC, id DESC);
DROP INDEX IF EXISTS idx_session_log_endpoint_event;

CREATE INDEX IF NOT EXISTS idx_ise_audit_endpoint_occurred_id
    ON ise_action_audit(endpoint_id, occurred_at DESC, id DESC);
DROP INDEX IF EXISTS idx_ise_audit_endpoint_occurred;

CREATE INDEX IF NOT EXISTS idx_ise_audit_occurred_id
    ON ise_action_audit(occurred_at DESC, id DESC);
DROP INDEX IF EXISTS idx_ise_audit_occurred;

CREATE INDEX IF NOT EXISTS idx_ise_audit_enforcement_state
    ON ise_action_audit(endpoint_id, occurred_at DESC, id DESC)
    WHERE action_type IN ('RESTRICT', 'CLEAR_RESTRICTION');

CREATE INDEX IF NOT EXISTS idx_sec_ind_endpoint_collected_id_latest_ok
    ON endpoint_security_indicator(endpoint_id, collected_at DESC, id DESC)
    WHERE status = 'OK';

CREATE INDEX IF NOT EXISTS idx_warranty_record_serial_uploaded_id
    ON warranty_record(serial_number, uploaded_at DESC, id DESC);
DROP INDEX IF EXISTS idx_warranty_record_serial;

CREATE INDEX IF NOT EXISTS idx_endpoint_connected_lastseen_id
    ON endpoint(connected DESC, last_seen_at DESC, id ASC);
DROP INDEX IF EXISTS idx_endpoint_connected_lastseen;

CREATE INDEX IF NOT EXISTS idx_posture_job_created_id
    ON posture_job(created_at DESC, id DESC);
DROP INDEX IF EXISTS idx_posture_job_created;

CREATE INDEX IF NOT EXISTS idx_posture_job_endpoint_created_id
    ON posture_job(endpoint_id, created_at DESC, id DESC);
