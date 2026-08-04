-- ============================================================
-- SNAD Platform — Create crm_staff_availability
-- ------------------------------------------------------------
-- Branch: fix/rem-2-crm-008-schema-foundation
-- Epic:   REM-2 — CRM-008 Database Schema Remediation
-- Gap:    GAP-03 (REM-2-GAP-MATRIX.md)
--
-- Creates the staff availability table referenced by:
--   JdbcAvailabilityRepository.java
--   OwnershipJdbcSupport.staffAvailabilityMapper()
--   AvailabilityController.java (/api/v1/crm/availability)
--
-- start_time, end_time, and reason are nullable to support
-- partial-day availability and optional justification.
-- ============================================================

CREATE TABLE crm_staff_availability (
    id          UUID NOT NULL,
    tenant_id   UUID NOT NULL,
    staff_id    UUID NOT NULL,
    type        VARCHAR(20) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    start_time  TIME,
    end_time    TIME,
    reason      VARCHAR(500),
    created_by  UUID NOT NULL,
    updated_by  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT pk_crm_staff_availability PRIMARY KEY (id),
    CONSTRAINT uk_crm_staff_availability_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_crm_staff_availability_tenant_staff
    ON crm_staff_availability (tenant_id, staff_id);

CREATE INDEX idx_crm_staff_availability_tenant_staff_dates
    ON crm_staff_availability (tenant_id, staff_id, start_date);
