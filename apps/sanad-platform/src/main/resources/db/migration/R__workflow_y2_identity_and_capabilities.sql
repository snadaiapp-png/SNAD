-- ============================================================
-- Workflow Y2 identity bridge and capabilities
--
-- Wave 0 / Task 2:
--   * Enforce optional one-to-one Employee <-> User linkage per tenant.
--
-- This migration is repeatable and idempotent because legacy CRM migration
-- history guards currently assert the exact ordered list of versioned Flyway
-- migrations. Y2 schema changes are therefore introduced here without
-- rewriting historical migrations or manipulating flyway_schema_history.
-- Task 3 appends capability seeds using idempotent INSERT ... WHERE NOT EXISTS.
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_user
    ON hr_employees (tenant_id, user_id)
    WHERE user_id IS NOT NULL;
