-- ============================================================
-- SNAD Platform — Create CRM Tasks table
-- ------------------------------------------------------------
-- Branch: feature/crm-tasks
-- Gate:   CRM Phase 3 — Tasks (first item per docs/next-execution-plan.md)
--
-- Creates the crm_tasks table for standalone task management
-- (separate from crm_activities which covers TASK/CALL/MEETING/etc
-- as timeline events). Tasks here are first-class work items with
-- assignee, due date, priority, status lifecycle, and optional
-- linkage to any CRM entity (account/contact/lead/opportunity).
--
-- Also seeds CRM.TASK.READ and CRM.TASK.WRITE capabilities and
-- grants them to the ADMIN role for every existing tenant.
--
-- Portable SQL — works on PostgreSQL 16 and H2 (PostgreSQL mode).
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_tasks (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    title VARCHAR(240) NOT NULL,
    description TEXT,

    -- Optional link to any CRM entity
    related_type VARCHAR(40),
    related_id UUID,

    -- Assignment & ownership
    assignee_user_id UUID,
    owner_user_id UUID,

    -- Lifecycle: OPEN -> IN_PROGRESS -> COMPLETED | CANCELLED
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 50,

    -- Scheduling
    start_at TIMESTAMP WITH TIME ZONE,
    due_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    -- Result / completion notes
    result TEXT,

    -- Audit (required by constitution §3.2)
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_tasks PRIMARY KEY (id),
    CONSTRAINT uk_crm_tasks_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_tasks_related_type CHECK (
        related_type IS NULL OR related_type IN ('ACCOUNT','CONTACT','LEAD','OPPORTUNITY','ACTIVITY')
    ),
    CONSTRAINT ck_crm_tasks_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_crm_tasks_priority CHECK (priority BETWEEN 0 AND 100)
);

-- Indexes (tenant-scoped, purpose-suffixed)
CREATE INDEX IF NOT EXISTS idx_crm_tasks_assignee_status
    ON crm_tasks (tenant_id, assignee_user_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_crm_tasks_related
    ON crm_tasks (tenant_id, related_type, related_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_tasks_status_due
    ON crm_tasks (tenant_id, status, due_at, updated_at DESC);

-- ============================================================
-- Seed CRM.TASK.READ and CRM.TASK.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.TASK.READ',  'Read CRM Tasks',  'View tenant CRM tasks'),
    ('CRM.TASK.WRITE', 'Write CRM Tasks', 'Create and update tenant CRM tasks')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

-- Grant CRM.TASK.* to ADMIN role in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
    ON capability.code IN ('CRM.TASK.READ', 'CRM.TASK.WRITE')
    AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
