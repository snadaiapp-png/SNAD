-- ============================================================
-- SNAD Platform — Create crm_capacity_plans
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-05 (REM-2-GAP-MATRIX.md)
--
-- Creates the capacity plans table referenced by:
--   JdbcCapacityRepository.java
--   OwnershipJdbcSupport.capacityPlanMapper()
--   CapacityController.java (/api/v1/crm/capacity)
--
-- allocated_capacity defaults to 0 at creation.
-- status defaults to 'DRAFT'.
-- ============================================================

CREATE TABLE crm_capacity_plans (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL,
    team_id             UUID NOT NULL,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    max_capacity        INT NOT NULL,
    allocated_capacity  INT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by          UUID NOT NULL,
    updated_by          UUID NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_capacity_plans PRIMARY KEY (id),
    CONSTRAINT uk_crm_capacity_plans_tenant_id UNIQUE (tenant_id, id)
    -- NOTE: team_id references crm_sales_teams (created by V20260722_1, PostgreSQL-only).
    -- FK omitted from shared migration to maintain H2+PostgreSQL portability.
);

CREATE INDEX idx_crm_capacity_plans_tenant_team
    ON crm_capacity_plans (tenant_id, team_id);

CREATE INDEX idx_crm_capacity_plans_tenant_team_status
    ON crm_capacity_plans (tenant_id, team_id, status);
