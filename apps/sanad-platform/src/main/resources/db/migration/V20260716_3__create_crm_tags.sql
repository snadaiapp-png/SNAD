-- ============================================================
-- SNAD Platform — Create CRM Tags + Tag Assignments tables
-- ------------------------------------------------------------
-- Branch: feature/crm-tags
-- Gate:   CRM Phase 3 — Tags/Labels (third item per docs/next-execution-plan.md)
--
-- Creates two tables:
--   1. crm_tags — tenant-scoped tag definitions (name + optional color)
--   2. crm_tag_assignments — many-to-many link between tags and any CRM entity
--
-- Tags are reusable across entity types. Each tenant manages its own
-- tag catalog. Assignments are idempotent (same tag+subject = no-op).
--
-- Also seeds CRM.TAG.READ and CRM.TAG.WRITE capabilities and grants
-- them to the ADMIN role for every existing tenant.
--
-- Portable SQL — works on PostgreSQL 16 and H2 (PostgreSQL mode).
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_tags (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(80) NOT NULL,
    color VARCHAR(20),

    -- Audit (required by constitution §3.2)
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tags PRIMARY KEY (id),
    CONSTRAINT uk_crm_tags_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_tags_tenant_name UNIQUE (tenant_id, LOWER(name)),
    CONSTRAINT fk_crm_tags_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_tags_name_not_empty CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_crm_tags_tenant_name
    ON crm_tags (tenant_id, name);

CREATE TABLE IF NOT EXISTS crm_tag_assignments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,

    tag_id UUID NOT NULL,
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,

    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tag_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_tag_assignments UNIQUE (tenant_id, tag_id, subject_type, subject_id),
    CONSTRAINT fk_crm_tag_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_crm_tag_assignments_tag FOREIGN KEY (tenant_id, tag_id) REFERENCES crm_tags (tenant_id, id),
    CONSTRAINT ck_crm_tag_assignments_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK','NOTE')
    )
);

CREATE INDEX IF NOT EXISTS idx_crm_tag_assignments_subject
    ON crm_tag_assignments (tenant_id, subject_type, subject_id);

CREATE INDEX IF NOT EXISTS idx_crm_tag_assignments_tag
    ON crm_tag_assignments (tenant_id, tag_id);

-- ============================================================
-- Seed CRM.TAG.READ and CRM.TAG.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TAG.READ',  'Read CRM Tags',  'View tenant CRM tags and assignments'),
    ('CRM.TAG.WRITE', 'Write CRM Tags', 'Create tags and assign/unassign them')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

-- Grant CRM.TAG.* to ADMIN role in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
    ON capability.code IN ('CRM.TAG.READ', 'CRM.TAG.WRITE')
    AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
