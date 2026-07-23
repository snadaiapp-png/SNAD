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
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_payload JSONB,
    status VARCHAR(40) NOT NULL,
    external_reference UUID,
    error_code VARCHAR(120),
    requested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT crm_integration_expiry_ck CHECK (expires_at > requested_at),
    CONSTRAINT crm_integration_tenant_idempotency_uq UNIQUE (tenant_id, integration_type, idempotency_key)
);

CREATE INDEX IF NOT EXISTS crm_integration_tenant_status_idx
    ON crm_integration_requests (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS crm_integration_correlation_idx
    ON crm_integration_requests (tenant_id, correlation_id);

INSERT INTO capabilities (id, code, name, description, module, created_at)
SELECT gen_random_uuid(), seed.code, seed.name, seed.description, 'CRM', now()
FROM (VALUES
    ('CRM.WORKFLOW.EXECUTE', 'CRM Workflow Execute', 'Dispatch and inspect governed CRM workflow requests'),
    ('CRM.AI.READ', 'CRM AI Read', 'Request governed advisory CRM AI outputs')
) AS seed(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM capabilities c WHERE c.code = seed.code);
