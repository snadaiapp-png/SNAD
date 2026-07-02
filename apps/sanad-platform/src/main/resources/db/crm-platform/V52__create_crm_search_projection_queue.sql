CREATE TABLE IF NOT EXISTS crm_platform.search_projection_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('UPSERT','DELETE','REBUILD')),
    document_version BIGINT NOT NULL DEFAULT 0,
    payload JSONB,
    routing_key VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','INDEXED','FAILED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    indexed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, entity_id, document_version, operation)
);

CREATE INDEX IF NOT EXISTS idx_crm_search_projection_dispatch
    ON crm_platform.search_projection_job (status, available_at, created_at);
CREATE INDEX IF NOT EXISTS idx_crm_search_projection_entity
    ON crm_platform.search_projection_job (tenant_id, entity_type, entity_id, document_version DESC);

CREATE TABLE IF NOT EXISTS crm_platform.customer_360_projection (
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    customer_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    normalized_name VARCHAR(512) NOT NULL,
    primary_email VARCHAR(512),
    primary_phone VARCHAR(100),
    owner_user_id UUID,
    lifecycle_status VARCHAR(80),
    open_opportunity_count INTEGER NOT NULL DEFAULT 0,
    open_opportunity_value NUMERIC(24,6) NOT NULL DEFAULT 0,
    last_interaction_at TIMESTAMPTZ,
    next_activity_at TIMESTAMPTZ,
    consent_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_versions JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, customer_id, customer_type)
);

CREATE INDEX IF NOT EXISTS idx_crm_customer_360_owner
    ON crm_platform.customer_360_projection (tenant_id, owner_user_id, lifecycle_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_customer_360_name
    ON crm_platform.customer_360_projection (tenant_id, normalized_name);
