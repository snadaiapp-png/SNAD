-- H2 compatibility migration for CRM-009 V20260724.3
CREATE TABLE IF NOT EXISTS crm_integration_callback_replay (
    id UUID NOT NULL DEFAULT RANDOM_UUID() PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_name VARCHAR(120) NOT NULL,
    jti VARCHAR(200) NOT NULL,
    nonce VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT crm_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT crm_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT crm_callback_replay_nonce_uq UNIQUE (service_name, nonce)
);
CREATE INDEX IF NOT EXISTS crm_callback_replay_expiry_idx
    ON crm_integration_callback_replay (expires_at);
CREATE INDEX IF NOT EXISTS crm_callback_replay_tenant_received_idx
    ON crm_integration_callback_replay (tenant_id, received_at DESC);
SELECT 1;
