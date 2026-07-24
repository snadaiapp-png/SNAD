-- H2 compatibility stub for CRM-009 V20260723.1
-- Mirrors the PostgreSQL schema with H2-compatible syntax. Where PostgreSQL
-- enforces strict CHECK constraints, H2 mirrors them so that test runs surface
-- schema drift early. Partial (predicate-filtered) indexes are approximated
-- by full indexes because H2 does not support partial indexes.

CREATE TABLE IF NOT EXISTS crm_integration_requests (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
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
    requested_locale VARCHAR(20) NOT NULL,
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
    CONSTRAINT crm_integration_tenant_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT crm_integration_expiry_ck CHECK (expires_at > requested_at),
    CONSTRAINT crm_integration_status_ck CHECK (
        status IN ('PENDING','DISPATCHED','ACCEPTED','RUNNING','COMPLETED',
                   'RECOMMENDATION_AVAILABLE','CONFIRMED','EXECUTING','EXECUTED',
                   'EXECUTION_REJECTED','REJECTED','POLICY_DENIED','UNSAFE_OUTPUT',
                   'TIMED_OUT','UNAVAILABLE','CANCELLED','EXPIRED')
    ),
    CONSTRAINT crm_integration_type_ck CHECK (integration_type IN ('WORKFLOW','AI')),
    CONSTRAINT crm_integration_classification_ck CHECK (
        data_classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED')
    ),
    CONSTRAINT crm_integration_terminal_ck CHECK (
        (status NOT IN ('COMPLETED','EXECUTED','EXECUTION_REJECTED','REJECTED',
                        'POLICY_DENIED','UNSAFE_OUTPUT','TIMED_OUT',
                        'UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_non_terminal_ck CHECK (
        (status IN ('COMPLETED','EXECUTED','EXECUTION_REJECTED','REJECTED',
                    'POLICY_DENIED','UNSAFE_OUTPUT','TIMED_OUT',
                    'UNAVAILABLE','CANCELLED','EXPIRED'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_tenant_idempotency_uq UNIQUE (tenant_id, integration_type, idempotency_key)
);
CREATE INDEX IF NOT EXISTS crm_integration_tenant_status_idx ON crm_integration_requests (tenant_id, status, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_correlation_idx ON crm_integration_requests (tenant_id, correlation_id);
CREATE INDEX IF NOT EXISTS crm_integration_tenant_entity_idx ON crm_integration_requests (tenant_id, source_entity_type, source_entity_id, created_at);

CREATE TABLE IF NOT EXISTS crm_integration_outbox (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    integration_request_id UUID NOT NULL,
    integration_type VARCHAR(80) NOT NULL,
    event_type VARCHAR(40) NOT NULL DEFAULT 'AI_REQUEST_DISPATCH',
    dispatch_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(200),
    claim_token UUID,
    claim_expires_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    idempotency_key VARCHAR(200) NOT NULL,
    payload JSON NOT NULL DEFAULT '{}',
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_integration_outbox_request_fk FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_integration_outbox_status_ck CHECK (
        dispatch_status IN ('PENDING','CLAIMED','RETRY_WAIT','COMPLETED','DEAD_LETTER','CANCELLED')
    ),
    CONSTRAINT crm_integration_outbox_event_type_ck CHECK (
        event_type IN ('AI_REQUEST_DISPATCH','WORKFLOW_DISPATCH','CONFIRMED_COMMAND_EXECUTION')
    ),
    CONSTRAINT crm_integration_outbox_attempts_ck CHECK (
        attempt_count >= 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT crm_integration_outbox_claim_ck CHECK (
        (dispatch_status = 'CLAIMED' AND claimed_at IS NOT NULL AND claim_expires_at IS NOT NULL AND claim_token IS NOT NULL AND claimed_by IS NOT NULL)
        OR (dispatch_status <> 'CLAIMED' AND claimed_at IS NULL AND claim_expires_at IS NULL AND claim_token IS NULL AND claimed_by IS NULL)
    ),
    CONSTRAINT crm_integration_outbox_terminal_ck CHECK (
        (dispatch_status NOT IN ('COMPLETED','DEAD_LETTER','CANCELLED'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_outbox_non_terminal_ck CHECK (
        (dispatch_status IN ('COMPLETED','DEAD_LETTER','CANCELLED'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_outbox_uq UNIQUE (tenant_id, integration_request_id, event_type)
);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_claimable_idx ON crm_integration_outbox (tenant_id, dispatch_status, next_attempt_at);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_retry_idx ON crm_integration_outbox (next_attempt_at, dispatch_status);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_tenant_status_idx ON crm_integration_outbox (tenant_id, dispatch_status, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_outbox_expired_claim_idx ON crm_integration_outbox (claim_expires_at);

CREATE TABLE IF NOT EXISTS crm_integration_decisions (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
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
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT crm_integration_decisions_request_fk FOREIGN KEY (tenant_id, integration_request_id)
        REFERENCES crm_integration_requests (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT crm_integration_decision_ck CHECK (decision IN ('CONFIRM', 'REJECT')),
    CONSTRAINT crm_integration_decision_status_ck CHECK (
        decision_status IN ('PENDING','CONFIRMED','REJECTED','EXECUTING','EXECUTED','EXECUTION_REJECTED','CONFLICT')
    ),
    CONSTRAINT crm_integration_decision_terminal_ck CHECK (
        (decision_status NOT IN ('REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT'))
        OR (completed_at IS NOT NULL)
    ),
    CONSTRAINT crm_integration_decision_non_terminal_ck CHECK (
        (decision_status IN ('REJECTED','EXECUTED','EXECUTION_REJECTED','CONFLICT'))
        OR (completed_at IS NULL)
    ),
    CONSTRAINT crm_integration_decision_uq UNIQUE (tenant_id, integration_request_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS crm_integration_decisions_tenant_request_idx ON crm_integration_decisions (tenant_id, integration_request_id, created_at);
CREATE INDEX IF NOT EXISTS crm_integration_decisions_tenant_status_idx ON crm_integration_decisions (tenant_id, decision_status, created_at);
SELECT 1;
