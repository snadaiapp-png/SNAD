-- ============================================================
-- SNAD Platform — Create crm_service_assignments
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-07 (REM-2-GAP-MATRIX.md)
--
-- Creates the service assignments table referenced by:
--   JdbcServiceAssignmentRepository.java
--   OwnershipJdbcSupport.serviceAssignmentMapper()
--   ServiceAssignmentController.java (/api/v1/crm/service-assignments)
--
-- UNIQUE(tenant_id, team_id, service_id) prevents duplicate
-- assignments. service_id has no FK because no services table
-- exists in the schema.
-- ============================================================

CREATE TABLE crm_service_assignments (
    id          UUID NOT NULL,
    tenant_id   UUID NOT NULL,
    team_id     UUID NOT NULL,
    service_id  UUID NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by  UUID NOT NULL,
    updated_by  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_service_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_service_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_service_assignments_tenant_team_service UNIQUE (tenant_id, team_id, service_id)
    -- NOTE: team_id references crm_sales_teams (created by V20260722_1, PostgreSQL-only).
    -- FK omitted from shared migration to maintain H2+PostgreSQL portability.
);

CREATE INDEX idx_crm_service_assignments_tenant_team
    ON crm_service_assignments (tenant_id, team_id);

CREATE INDEX idx_crm_service_assignments_tenant_service
    ON crm_service_assignments (tenant_id, service_id);
