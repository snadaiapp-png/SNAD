-- ============================================================
-- SNAD Platform — Create crm_workload_assignments
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-06 (REM-2-GAP-MATRIX.md)
--
-- Creates the workload assignments table referenced by:
--   JdbcWorkloadRepository.java
--   OwnershipJdbcSupport.workloadAssignmentMapper()
--   WorkloadController.java (/api/v1/crm/workload)
--
-- actual_hours and end_date are nullable: set when work
-- completes. service_id has no FK because no services table
-- exists in the schema.
-- ============================================================

CREATE TABLE crm_workload_assignments (
    id               UUID NOT NULL,
    tenant_id        UUID NOT NULL,
    staff_id         UUID NOT NULL,
    service_id       UUID,
    job_id           UUID,
    estimated_hours  INT NOT NULL,
    actual_hours     INT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    start_date       DATE NOT NULL,
    end_date         DATE,
    created_by       UUID NOT NULL,
    updated_by       UUID NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_workload_assignments PRIMARY KEY (id),
    CONSTRAINT uk_crm_workload_assignments_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_crm_workload_assignments_tenant_staff
    ON crm_workload_assignments (tenant_id, staff_id);

CREATE INDEX idx_crm_workload_assignments_tenant_service
    ON crm_workload_assignments (tenant_id, service_id);

CREATE INDEX idx_crm_workload_assignments_tenant_staff_status
    ON crm_workload_assignments (tenant_id, staff_id, status);
