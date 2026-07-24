-- H2 compatibility stub for CRM-009 V20260723.1
CREATE TABLE IF NOT EXISTS crm_integration_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    integration_type VARCHAR(80) NOT NULL,
    contract_name VARCHAR(120) NOT NULL,
    contract_version VARCHAR(40) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    causation_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    source_entity_type VARCHAR(80) NOT NULL,
    source_entity_id UUID NOT NULL,
    source_entity_version BIGINT NOT NULL CHECK (source_entity_version >= 0),
    required_capability VARCHAR(160) NOT NULL,
    data_classification VARCHAR(80) NOT NULL,
    payload JSON NOT NULL DEFAULT '{}',
    result_payload JSON,
    status VARCHAR(40) NOT NULL,
    external_reference UUID,
    error_code VARCHAR(120),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_integration_expiry_ck CHECK (expires_at > requested_at),
    CONSTRAINT crm_integration_tenant_idempotency_uq UNIQUE (tenant_id, integration_type, idempotency_key)
);
CREATE INDEX IF NOT EXISTS crm_integration_tenant_status_idx ON crm_integration_requests (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_correlation_idx ON crm_integration_requests (tenant_id, correlation_id);
CREATE INDEX IF NOT EXISTS crm_integration_tenant_entity_idx ON crm_integration_requests (tenant_id, source_entity_type, source_entity_id, created_at);

CREATE TABLE IF NOT EXISTS crm_integration_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    integration_request_id UUID NOT NULL,
    integration_type VARCHAR(80) NOT NULL,
    dispatch_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(200),
    claim_expires_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    idempotency_key VARCHAR(200) NOT NULL,
    payload JSON NOT NULL DEFAULT '{}',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_integration_outbox_uq UNIQUE (tenant_id, integration_request_id)
);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_claimable_idx ON crm_integration_outbox (tenant_id, dispatch_status, next_attempt_at);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_retry_idx ON crm_integration_outbox (next_attempt_at, dispatch_status);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_tenant_status_idx ON crm_integration_outbox (tenant_id, dispatch_status, created_at);

CREATE TABLE IF NOT EXISTS crm_integration_decisions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    integration_request_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint VARCHAR(500) NOT NULL,
    expected_entity_version BIGINT NOT NULL CHECK (expected_entity_version >= 0),
    correlation_id VARCHAR(160) NOT NULL,
    decision_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    command_reference VARCHAR(500),
    error_code VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_integration_decision_uq UNIQUE (tenant_id, integration_request_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS crm_integration_decisions_tenant_request_idx ON crm_integration_decisions (tenant_id, integration_request_id, created_at);
SELECT 1;
