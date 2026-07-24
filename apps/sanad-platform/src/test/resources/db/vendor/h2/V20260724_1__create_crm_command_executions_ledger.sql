-- H2 compatibility stub for CRM-009 V20260724.1
-- Mirrors the PostgreSQL schema with H2-compatible syntax.
CREATE TABLE IF NOT EXISTS crm_integration_command_executions (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    integration_request_id UUID NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    execution_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(200) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    command_reference VARCHAR(500),
    result_payload JSON,
    error_code VARCHAR(120),
    claim_token UUID,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_command_executions_request_fk FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_command_executions_status_ck CHECK (
        execution_status IN ('PENDING','EXECUTING','EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME')
    ),
    CONSTRAINT crm_command_executions_terminal_ck CHECK (
        (execution_status NOT IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_command_executions_non_terminal_ck CHECK (
        (execution_status IN ('EXECUTED','EXECUTION_REJECTED','UNKNOWN_OUTCOME'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_command_executions_decision_uq UNIQUE (tenant_id, decision_id),
    CONSTRAINT crm_command_executions_idempotency_uq UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS crm_integration_command_executions_tenant_status_idx
    ON crm_integration_command_executions (tenant_id, execution_status, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_command_executions_request_idx
    ON crm_integration_command_executions (tenant_id, integration_request_id, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_event_claim_idx
    ON crm_integration_outbox (event_type, dispatch_status, next_attempt_at, created_at);
SELECT 1;
