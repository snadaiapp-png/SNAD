-- ============================================================================
-- SANAD CRM-008B — V20260722_2 — Create CRM Queues
-- ============================================================================
BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

CREATE TABLE crm_queues (
    id                          UUID NOT NULL,
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
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,
    CONSTRAINT pk_queues PRIMARY KEY (id),
    CONSTRAINT uk_queues_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_queues_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_queues_status CHECK (status IN ('ACTIVE','DRAINING','ARCHIVED')),
    CONSTRAINT ck_queues_record_type CHECK (record_type IN (
        'LEAD','OPPORTUNITY','TASK','ACTIVITY','ACCOUNT'
    )),
    CONSTRAINT ck_queues_capacity CHECK (max_items_per_user >= 1 AND max_items_per_user <= 1000),
    CONSTRAINT ck_queues_no_self_escalation CHECK (escalation_target_queue_id IS NULL
        OR escalation_target_queue_id <> id)
);

CREATE UNIQUE INDEX uk_queues_tenant_code
    ON crm_queues (tenant_id, code);

CREATE INDEX idx_queues_tenant_status_type
    ON crm_queues (tenant_id, status, record_type);

CREATE TABLE crm_queue_memberships (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    queue_id        UUID NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    added_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    removed_at      TIMESTAMP WITH TIME ZONE,
    removed_reason  VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT pk_queue_memberships PRIMARY KEY (id),
    CONSTRAINT fk_queue_memberships_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_queue_memberships_queues FOREIGN KEY (tenant_id, queue_id)
        REFERENCES crm_queues(tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_queue_memberships_status CHECK (status IN ('ACTIVE','REMOVED'))
);

-- Partial unique index moved to vendor-specific migration

CREATE INDEX idx_queue_memberships_user_active
    ON crm_queue_memberships (tenant_id, user_id, status);

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
