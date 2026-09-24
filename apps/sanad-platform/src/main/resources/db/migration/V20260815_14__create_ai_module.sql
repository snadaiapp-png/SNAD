-- ============================================================
-- V20260815_14: AI Module — Agent Registry + Inference Log
--
-- Creates the AI module schema:
--   - ai_agents          (agent registry: name, provider, status, config)
--   - ai_inference_log   (immutable record of every AI invocation)
--
-- Design principles (same as V20260815_1/3/5/10):
--   * Tenant-scoped (RLS-enabled, tenant_id NOT NULL on every row)
--   * State machines via CHECK constraints
--   * TIMESTAMPTZ timestamps
--   * UUID primary keys
--   * Idempotent (IF NOT EXISTS / WHERE NOT EXISTS)
--   * Optimistic locking via version field
--   * No flyway_schema_history manipulation
--   * Advisory-only: ai_inference_log.advisory = TRUE by default
-- ============================================================

-- ============================================================
-- STEP 1: ai_agents — agent registry
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_agents (
    id              UUID            NOT NULL,
    tenant_id       UUID            NOT NULL,
    code            VARCHAR(100)    NOT NULL,
    name            VARCHAR(300)    NOT NULL,
    description     TEXT,
    provider        VARCHAR(50)     NOT NULL DEFAULT 'DETERMINISTIC',
    model_name      VARCHAR(200),
    system_prompt   TEXT,
    configuration   JSONB,
    status          VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    max_tokens      INTEGER,
    temperature     DOUBLE PRECISION,
    created_by      UUID            NOT NULL,
    version_lock    BIGINT          NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_agents PRIMARY KEY (id),
    CONSTRAINT uk_ai_agents_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_ai_agents_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ai_agent_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_ai_agent_provider CHECK (provider IN ('DETERMINISTIC', 'OPENAI', 'ANTHROPIC', 'AZURE_OPENAI', 'CUSTOM')),
    CONSTRAINT fk_ai_agent_created_by FOREIGN KEY (tenant_id, created_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agents_tenant_status ON ai_agents(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_ai_agents_tenant_provider ON ai_agents(tenant_id, provider);

-- ============================================================
-- STEP 2: ai_inference_log — immutable execution record
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_inference_log (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    agent_id            UUID            NOT NULL,
    invoked_by          UUID            NOT NULL,
    input_summary       TEXT,
    input_hash          VARCHAR(64),
    output_summary      TEXT,
    output_hash         VARCHAR(64),
    advisory            BOOLEAN         NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    error_message       TEXT,
    tokens_input        INTEGER,
    tokens_output       INTEGER,
    latency_ms          BIGINT,
    cost_cents          INTEGER         NOT NULL DEFAULT 0,
    correlation_id      UUID,
    business_entity_type VARCHAR(100),
    business_entity_id  UUID,
    workflow_instance_id UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_inference_log PRIMARY KEY (id),
    CONSTRAINT ck_ai_inf_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'TIMEOUT', 'CANCELLED')),
    CONSTRAINT fk_ai_inf_agent FOREIGN KEY (tenant_id, agent_id)
        REFERENCES ai_agents(tenant_id, id),
    CONSTRAINT fk_ai_inf_invoked_by FOREIGN KEY (tenant_id, invoked_by)
        REFERENCES users(tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_ai_inf_tenant_created ON ai_inference_log(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_inf_agent ON ai_inference_log(agent_id);
CREATE INDEX IF NOT EXISTS idx_ai_inf_invoked_by ON ai_inference_log(tenant_id, invoked_by);
CREATE INDEX IF NOT EXISTS idx_ai_inf_business_entity ON ai_inference_log(tenant_id, business_entity_type, business_entity_id);

-- ============================================================
-- STEP 3: Enable RLS on all AI tables
-- ============================================================
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'ai_agents',
        'ai_inference_log'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format($f$
            DROP POLICY IF EXISTS tenant_isolation ON %I;
            CREATE POLICY tenant_isolation ON %I
                USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $f$, tbl, tbl);
    END LOOP;
END $$;
