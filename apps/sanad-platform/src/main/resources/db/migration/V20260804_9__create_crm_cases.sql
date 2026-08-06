-- ============================================================
-- SNAD Platform — Create CRM Cases table
-- ------------------------------------------------------------
-- MOD-001: Case/Ticket Management
--
-- Creates the crm_cases table for customer support case management.
-- Cases track customer issues through a lifecycle:
--   OPEN → IN_PROGRESS → RESOLVED → CLOSED
-- A CLOSED case may be reopened back to IN_PROGRESS.
--
-- Also seeds CRM.CASE.READ and CRM.CASE.WRITE capabilities and
-- grants them to the ADMIN role for every existing tenant.
--
-- Portable SQL — works on PostgreSQL 16 and H2 (PostgreSQL mode).
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_cases (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    subject VARCHAR(240) NOT NULL,
    description TEXT,

    -- Case classification
    case_type VARCHAR(40),

    -- Lifecycle: OPEN → IN_PROGRESS → RESOLVED → CLOSED
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority INTEGER NOT NULL DEFAULT 50,

    -- Customer link
    customer_id UUID,

    -- Assignment & ownership
    assignee_user_id UUID,
    owner_user_id UUID,

    -- Optional link to any CRM entity
    related_id UUID,

    -- Scheduling
    due_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,

    -- Audit (required by constitution §3.2)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_crm_cases PRIMARY KEY (id),
    CONSTRAINT uk_crm_cases_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_cases_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_cases_type CHECK (
        case_type IS NULL OR case_type IN ('BUG','FEATURE','QUESTION','SUPPORT')
    ),
    CONSTRAINT ck_crm_cases_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    CONSTRAINT ck_crm_cases_priority CHECK (priority BETWEEN 0 AND 100)
);

-- Indexes (tenant-scoped, purpose-suffixed)
CREATE INDEX IF NOT EXISTS idx_crm_cases_assignee_status
    ON crm_cases (tenant_id, assignee_user_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_crm_cases_customer
    ON crm_cases (tenant_id, customer_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_cases_status_priority
    ON crm_cases (tenant_id, status, priority DESC, updated_at DESC);

-- ============================================================
-- Seed CRM.CASE.READ and CRM.CASE.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.CASE.READ',  'Read CRM Cases',  'View tenant CRM cases'),
    ('CRM.CASE.WRITE', 'Write CRM Cases', 'Create and update tenant CRM cases')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

-- Grant CRM.CASE.* to ADMIN role in every tenant
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
    ON capability.code IN ('CRM.CASE.READ', 'CRM.CASE.WRITE')
    AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
