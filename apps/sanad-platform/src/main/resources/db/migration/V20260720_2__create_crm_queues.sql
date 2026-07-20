-- ============================================================================
-- SANAD CRM-008 — V20260720_2 — Create CRM Queues
-- ============================================================================
-- Forward-only. Idempotent. Tenant-scoped. Composite FKs. Tenant-leading indexes.
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Queues
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_queues (
    id                          UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    code                        VARCHAR(64) NOT NULL,
    display_name                VARCHAR(200) NOT NULL,
    record_type                 VARCHAR(20) NOT NULL,
    description                 VARCHAR(1000),
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    max_items_per_user          INTEGER NOT NULL DEFAULT 10,
    sla_minutes                 INTEGER,
    escalation_target_queue_id  UUID,
    default_owner_id            UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT fk_queues_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_queues_status CHECK (status IN ('ACTIVE','DRAINING','ARCHIVED')),
    CONSTRAINT ck_queues_record_type CHECK (record_type IN (
        'LEAD','OPPORTUNITY','TASK','ACTIVITY','ACCOUNT'
    )),
    CONSTRAINT ck_queues_capacity CHECK (max_items_per_user >= 1 AND max_items_per_user <= 1000),
    CONSTRAINT ck_queues_no_self_escalation CHECK (escalation_target_queue_id IS NULL
        OR escalation_target_queue_id <> id),
    -- Inline composite UNIQUE so crm_queue_memberships can FK to (tenant_id, queue_id)
    CONSTRAINT pk_queues PRIMARY KEY (id),
    CONSTRAINT uk_queues_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_queues_tenant_code
    ON crm_queues (tenant_id, code);

CREATE INDEX IF NOT EXISTS idx_queues_tenant_status_type
    ON crm_queues (tenant_id, status, record_type);

-- ----------------------------------------------------------------------------
-- 2. Queue Memberships
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_queue_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    queue_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    added_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at      TIMESTAMPTZ,
    removed_reason  VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT fk_queue_memberships_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_memberships_queues FOREIGN KEY (tenant_id, queue_id)
        REFERENCES crm_queues(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_queue_memberships_status CHECK (status IN ('ACTIVE','REMOVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_memberships_active
    ON crm_queue_memberships (tenant_id, queue_id, user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_queue_memberships_user_active
    ON crm_queue_memberships (tenant_id, user_id, status)
    WHERE status = 'ACTIVE';

COMMIT;

-- Validation:
-- SELECT count(*) FROM information_schema.tables WHERE table_name IN ('crm_queues','crm_queue_memberships');
-- Expected: 2
