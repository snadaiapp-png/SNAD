-- H2 compatibility migration for CRM-009 V20260724.2
CREATE TABLE IF NOT EXISTS crm_integration_command_artifacts (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    artifact_type VARCHAR(80) NOT NULL,
    artifact_id UUID NOT NULL,
    execution_status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT crm_command_artifacts_status_ck CHECK (
        execution_status IN ('CREATED', 'REVERSED')
    ),
    CONSTRAINT crm_command_artifacts_decision_action_uq
        UNIQUE (tenant_id, decision_id, action_code),
    CONSTRAINT crm_command_artifacts_tenant_uq UNIQUE (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_tenant_decision_idx
    ON crm_integration_command_artifacts (tenant_id, decision_id);
CREATE INDEX IF NOT EXISTS crm_integration_command_artifacts_artifact_idx
    ON crm_integration_command_artifacts (tenant_id, artifact_type, artifact_id);

CREATE TABLE IF NOT EXISTS service_callback_replay (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_name VARCHAR(120) NOT NULL,
    jti VARCHAR(200) NOT NULL,
    nonce VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT service_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT service_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT service_callback_replay_nonce_uq UNIQUE (service_name, nonce)
);
CREATE INDEX IF NOT EXISTS service_callback_replay_expiry_idx
    ON service_callback_replay (expires_at);
CREATE INDEX IF NOT EXISTS service_callback_replay_tenant_received_idx
    ON service_callback_replay (tenant_id, received_at DESC);
SELECT 1;
