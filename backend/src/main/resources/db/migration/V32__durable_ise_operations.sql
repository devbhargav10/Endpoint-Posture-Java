CREATE TABLE ise_action_operation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id uuid NOT NULL REFERENCES endpoint(id),
    mac_address varchar(32) NOT NULL,
    action_type varchar(40) NOT NULL,
    operation_key varchar(64) NOT NULL,
    operator varchar(255),
    policy varchar(255),
    posture_status varchar(40),
    posture_details text,
    state varchar(32) NOT NULL CHECK (state IN ('PENDING','IN_PROGRESS','SUCCEEDED','FAILED','RECONCILIATION_REQUIRED')),
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    result_detail text,
    resolved_by varchar(255),
    resolved_at timestamptz,
    resolution varchar(16) CHECK (resolution IN ('SUCCEEDED','FAILED')),
    resolution_detail text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);
CREATE INDEX ise_action_operation_recovery_idx ON ise_action_operation(state, updated_at)
    WHERE state IN ('PENDING','IN_PROGRESS','RECONCILIATION_REQUIRED');
CREATE INDEX ise_action_operation_endpoint_idx ON ise_action_operation(endpoint_id, created_at DESC);
CREATE UNIQUE INDEX ise_action_operation_active_key_uq ON ise_action_operation(operation_key)
    WHERE state IN ('PENDING','IN_PROGRESS');

ALTER TABLE ise_action_audit DROP CONSTRAINT ise_action_audit_action_type_check;
ALTER TABLE ise_action_audit ADD CONSTRAINT ise_action_audit_action_type_check
    CHECK (action_type IN ('SHARE_POSTURE','RESTRICT','CLEAR_RESTRICTION','OPERATION_RESOLVED'));
