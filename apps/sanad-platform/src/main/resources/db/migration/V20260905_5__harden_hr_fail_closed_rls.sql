-- ============================================================
-- HRM-G0 / WS2 / Task 5 — MINIMAL GREEN
-- Harden legacy HR RLS from fail-open to fail-closed.
-- ============================================================
--
-- SCOPE: hr_employees, hr_departments, hr_positions ONLY.
--        The 12 canonical tables from Tasks 1-4 already have
--        FORCE RLS + fail-closed WITH CHECK policies and are
--        not touched here.
--
-- PRE-STATE (from V20260819_1 + V20260830_1):
--   ENABLE ROW LEVEL SECURITY  ✓
--   FORCE  ROW LEVEL SECURITY ✗  ← RED (owner bypasses RLS)
--   POLICY tenant_isolation:
--     USING ( current_setting('app.tenant_id', true) IS NULL
--             OR tenant_id::text = current_setting('app.tenant_id', true) )
--     ← FAIL OPEN (NULL ctx allows all rows)
--
-- POST-STATE (this migration):
--   ENABLE ROW LEVEL SECURITY  ✓
--   FORCE  ROW LEVEL SECURITY  ✓
--   POLICY tenant_isolation:
--     USING ( tenant_id::text = current_setting('app.tenant_id', true) )
--     WITH CHECK ( tenant_id::text = current_setting('app.tenant_id', true) )
--     ← FAIL CLOSED (NULL ctx hides all rows + denies writes)
--
-- WHY FORCE RLS:
--   Runtime role `sanad` is the TABLE OWNER of these legacy tables.
--   Table owners normally bypass RLS unless FORCE is set.
--   The runtime role is verified non-superuser + non-BYPASSRLS by
--   HrRlsFailClosedIntegrationTest.runtimeRole_isNotSuperuser and
--   runtimeRole_doesNotHaveBypassrls, and table ownership is verified
--   by catalog probes — FORCE RLS is therefore mandatory.
--
-- IDEMPOTENCE:
--   PostgreSQL has no CREATE POLICY IF NOT EXISTS.
--   We use DROP POLICY IF EXISTS before CREATE so the migration
--   can be re-applied if it ever needs to be (Flyway will not
--   normally re-run a versioned migration).
-- ============================================================

-- ============================================================
-- hr_employees
-- ============================================================

ALTER TABLE hr_employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employees FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON hr_employees;

CREATE POLICY tenant_isolation
ON hr_employees
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);

-- ============================================================
-- hr_departments
-- ============================================================

ALTER TABLE hr_departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_departments FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON hr_departments;

CREATE POLICY tenant_isolation
ON hr_departments
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);

-- ============================================================
-- hr_positions
-- ============================================================

ALTER TABLE hr_positions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_positions FORCE  ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON hr_positions;

CREATE POLICY tenant_isolation
ON hr_positions
FOR ALL
USING (
    tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    tenant_id::text = current_setting('app.tenant_id', true)
);
