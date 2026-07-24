-- ============================================================
-- SNAD Platform — CRM-009 — V20260724.2
-- Atomic command artifacts + service callback replay protection
-- Forward-only. Fail-closed. Tenant-scoped. PostgreSQL 16 native.
-- ============================================================

DO $precondition$
DECLARE
    command_artifact_count INTEGER;
    callback_replay_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO command_artifact_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_command_artifacts';
    IF command_artifact_count <> 0 THEN
        RAISE EXCEPTION 'V20260724.2 precondition failed: crm_integration_command_artifacts already exists';
    END IF;

    SELECT COUNT(*) INTO callback_replay_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'service_callback_replay';
    IF callback_replay_count <> 0 THEN
        RAISE EXCEPTION 'V20260724.2 precondition failed: service_callback_replay already exists';
    END IF;
END
$precondition$;

-- Exactly-once CRM artifact link. The adapter reserves and completes this row
-- in the same transaction as the activity/task creation.
CREATE TABLE crm_integration_command_artifacts (
    id                      UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    decision_id             UUID NOT NULL,
    action_code             VARCHAR(80) NOT NULL,
    artifact_type           VARCHAR(80) NOT NULL,
    artifact_id             UUID NOT NULL,
    execution_status        VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_crm_integration_command_artifacts PRIMARY KEY (id),
    CONSTRAINT crm_command_artifacts_decision_action_uq
        UNIQUE (tenant_id, decision_id, action_code),
    CONSTRAINT crm_command_artifacts_status_ck CHECK (
        execution_status IN ('CREATED', 'REVERSED')
    ),
    CONSTRAINT crm_command_artifacts_tenant_uq UNIQUE (tenant_id, id)
);

CREATE INDEX crm_integration_command_artifacts_tenant_decision_idx
    ON crm_integration_command_artifacts (tenant_id, decision_id);
CREATE INDEX crm_integration_command_artifacts_artifact_idx
    ON crm_integration_command_artifacts (tenant_id, artifact_type, artifact_id);

-- Service-level replay protection is intentionally not a CRM business table.
-- It protects all signed central-service callbacks and therefore uses the
-- service_ prefix while remaining tenant-scoped.
CREATE TABLE service_callback_replay (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    service_name    VARCHAR(120) NOT NULL,
    jti             VARCHAR(200) NOT NULL,
    nonce           VARCHAR(200) NOT NULL,
    correlation_id  VARCHAR(160) NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_service_callback_replay PRIMARY KEY (id),
    CONSTRAINT fk_service_callback_replay_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT service_callback_replay_expiry_ck CHECK (expires_at > received_at),
    CONSTRAINT service_callback_replay_jti_uq UNIQUE (service_name, jti),
    CONSTRAINT service_callback_replay_nonce_uq UNIQUE (service_name, nonce)
);

CREATE INDEX service_callback_replay_expiry_idx
    ON service_callback_replay (expires_at);
CREATE INDEX service_callback_replay_tenant_received_idx
    ON service_callback_replay (tenant_id, received_at DESC);

DO $postcondition$
DECLARE
    command_table_exists INTEGER;
    command_unique_count INTEGER;
    command_index_count INTEGER;
    replay_table_exists INTEGER;
    replay_unique_count INTEGER;
    replay_index_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO command_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'crm_integration_command_artifacts';
    IF command_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: command artifact table missing';
    END IF;

    SELECT COUNT(*) INTO command_unique_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_artifacts'
       AND indexname = 'crm_command_artifacts_decision_action_uq';
    IF command_unique_count <> 1 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: command artifact uniqueness missing';
    END IF;

    SELECT COUNT(*) INTO command_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND tablename = 'crm_integration_command_artifacts'
       AND indexname IN (
           'crm_integration_command_artifacts_tenant_decision_idx',
           'crm_integration_command_artifacts_artifact_idx'
       );
    IF command_index_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: expected 2 command artifact indexes';
    END IF;

    SELECT COUNT(*) INTO replay_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name = 'service_callback_replay';
    IF replay_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: callback replay table missing';
    END IF;

    SELECT COUNT(*) INTO replay_unique_count
      FROM information_schema.table_constraints
     WHERE table_schema = 'public'
       AND table_name = 'service_callback_replay'
       AND constraint_name IN (
           'service_callback_replay_jti_uq',
           'service_callback_replay_nonce_uq'
       )
       AND constraint_type = 'UNIQUE';
    IF replay_unique_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: callback replay uniqueness missing';
    END IF;

    SELECT COUNT(*) INTO replay_index_count
      FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname IN (
           'service_callback_replay_expiry_idx',
           'service_callback_replay_tenant_received_idx'
       );
    IF replay_index_count <> 2 THEN
        RAISE EXCEPTION 'V20260724.2 postcondition failed: callback replay indexes missing';
    END IF;
END
$postcondition$;
