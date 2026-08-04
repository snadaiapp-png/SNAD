-- ============================================================
-- SNAD Platform — Create crm_shift_assignments
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-02 (REM-2-GAP-MATRIX.md)
--
-- Creates the shift assignment table referenced by:
--   JdbcShiftAssignmentRepository.java
--   OwnershipJdbcSupport.shiftAssignmentMapper()
--   ShiftAssignmentController.java (/api/v1/crm/shift-assignments)
--
-- Depends on: V20260804_2 (crm_shift_templates for FK)
-- ============================================================

CREATE TABLE crm_shift_assignments (
    id                 UUID NOT NULL,
    tenant_id          UUID NOT NULL,
    team_id            UUID NOT NULL,
    staff_id           UUID NOT NULL,
    shift_template_id  UUID NOT NULL,
    start_date         DATE NOT NULL,
    end_date           DATE NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by         UUID NOT NULL,
    updated_by         UUID NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_shift_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_shift_assignments_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_crm_shift_assignments_template FOREIGN KEY (tenant_id, shift_template_id)
        REFERENCES crm_shift_templates (tenant_id, id)
);

CREATE INDEX idx_crm_shift_assignments_tenant_team
    ON crm_shift_assignments (tenant_id, team_id);

CREATE INDEX idx_crm_shift_assignments_tenant_staff
    ON crm_shift_assignments (tenant_id, staff_id);

CREATE INDEX idx_crm_shift_assignments_tenant_staff_dates
    ON crm_shift_assignments (tenant_id, staff_id, start_date, end_date);
