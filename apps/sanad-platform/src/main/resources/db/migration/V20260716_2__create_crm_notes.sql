-- ============================================================
-- SNAD Platform — Create CRM Notes table
-- ------------------------------------------------------------
-- Branch: feature/crm-notes
-- Gate:   CRM Phase 3 — Notes (second item per docs/next-execution-plan.md)
--
-- Creates crm_notes for attaching plain-text notes to any CRM entity
-- (account/contact/lead/opportunity/activity/task). Notes are immutable
-- append-only records — edit/delete is intentionally NOT supported in V1
-- to preserve an audit trail (use Tasks for actionable items).
--
-- Also seeds CRM.NOTE.READ and CRM.NOTE.WRITE capabilities and grants
-- them to the ADMIN role for every existing tenant.
--
-- Portable SQL — works on PostgreSQL 16 and H2 (PostgreSQL mode).
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_notes (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    -- The entity this note is attached to (required)
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,

    -- Note body (required, capped at 10k for V1)
    body TEXT NOT NULL,

    -- Optional author override (defaults to authenticated user via service)
    author_user_id UUID,

    -- Soft-delete support (notes are append-only but may be hidden)
    archived BOOLEAN NOT NULL DEFAULT FALSE,

    -- Audit (required by constitution §3.2)
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_notes PRIMARY KEY (id),
    CONSTRAINT uk_crm_notes_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_notes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_notes_subject_type CHECK (
        subject_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY','TASK')
    ),
    CONSTRAINT ck_crm_notes_body_not_empty CHECK (LENGTH(TRIM(body)) > 0)
);

-- Indexes (tenant-scoped, purpose-suffixed)
CREATE INDEX IF NOT EXISTS idx_crm_notes_subject
    ON crm_notes (tenant_id, subject_type, subject_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_notes_author
    ON crm_notes (tenant_id, author_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_notes_active
    ON crm_notes (tenant_id, archived, created_at DESC);

-- ============================================================
-- Seed CRM.NOTE.READ and CRM.NOTE.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.NOTE.READ',  'Read CRM Notes',  'View tenant CRM notes'),
    ('CRM.NOTE.WRITE', 'Write CRM Notes', 'Create and archive tenant CRM notes')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

-- Grant CRM.NOTE.* to ADMIN role in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
    ON capability.code IN ('CRM.NOTE.READ', 'CRM.NOTE.WRITE')
    AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
