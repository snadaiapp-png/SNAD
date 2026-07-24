-- ============================================================
-- SNAD Platform — CRM-009 — V20260724.3
-- Service callback replay protection
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
-- ============================================================

DO $precondition$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'crm_integration_callback_replay'
    ) THEN
        RAISE EXCEPTION 'V20260724.3 precondition failed: crm_integration_callback_replay already exists';
    END IF;
END
$precondition$;

CREATE TABLE crm_integration_callback_replay (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    service_name    VARCHAR(120) NOT NULL,
    jti             VARCHAR(200) NOT NULL,
    nonce           VARCHAR(200) NOT NULL,
    correlation_id  VARCHAR(160) NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_crm_integration_callback_replay PRIMARY KEY (id),
    CONSTRAINT fk_crm_callback_replay_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT crm_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT crm_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT crm_callback_replay_nonce_uq UNIQUE (service_name, nonce)
);

CREATE INDEX crm_callback_replay_expiry_idx
    ON crm_integration_callback_replay (expires_at);

CREATE INDEX crm_callback_replay_tenant_received_idx
    ON crm_integration_callback_replay (tenant_id, received_at DESC);

DO $postcondition$
DECLARE
    table_count INTEGER;
    unique_count INTEGER;
    index_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_callback_replay';
    IF table_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.3 postcondition failed: callback replay table missing';
    END IF;

    SELECT COUNT(*) INTO unique_count
      FROM information_schema.table_constraints
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_callback_replay'
       AND constraint_name IN ('crm_callback_replay_jti_uq', 'crm_callback_replay_nonce_uq')
       AND constraint_type = 'UNIQUE';
    IF unique_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.3 postcondition failed: replay unique constraints missing';
    END IF;

    SELECT COUNT(*) INTO index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN ('crm_callback_replay_expiry_idx', 'crm_callback_replay_tenant_received_idx');
    IF index_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.3 postcondition failed: replay indexes missing';
    END IF;
END
$postcondition$;
