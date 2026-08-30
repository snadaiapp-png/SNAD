-- ============================================================
-- V20260830_1: Workflow Y2 identity bridge and capabilities
--
-- Task 2 scope in this commit:
--   * Enforce optional one-to-one Employee <-> User linkage per tenant.
--
-- Capability expansion is appended by Wave 0 / Task 3 so this Flyway
-- version remains the single additive identity/capability migration.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;
