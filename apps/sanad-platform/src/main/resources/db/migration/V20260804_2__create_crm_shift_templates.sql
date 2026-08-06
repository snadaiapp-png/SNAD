-- ============================================================
-- SNAD Platform — Create crm_shift_templates
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-01 (REM-2-GAP-MATRIX.md)
--
-- Creates the shift template table referenced by:
--   JdbcShiftTemplateRepository.java
--   OwnershipJdbcSupport.shiftTemplateMapper()
--   ShiftTemplateController.java (/api/v1/crm/shift-templates)
--
-- Columns derived from repository INSERT/SELECT/UPDATE SQL.
-- ============================================================

CREATE TABLE crm_shift_templates (
    id            UUID NOT NULL,
    tenant_id     UUID NOT NULL,
    name          VARCHAR(200) NOT NULL,
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    days_of_week  VARCHAR(50) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by    UUID NOT NULL,
    updated_by    UUID NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_shift_templates PRIMARY KEY (id),
    CONSTRAINT uk_crm_shift_templates_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_shift_templates_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_crm_shift_templates_tenant_status
    ON crm_shift_templates (tenant_id, status);
