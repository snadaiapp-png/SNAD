-- H2 compatibility stub for CRM-009 V20260724.2
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
SELECT 1;
