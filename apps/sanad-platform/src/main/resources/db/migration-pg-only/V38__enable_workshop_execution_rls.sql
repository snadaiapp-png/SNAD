-- SANAD Stage 06 — PostgreSQL tenant isolation for Workshop Execution Platform.

ALTER TABLE workshops ENABLE ROW LEVEL SECURITY;
ALTER TABLE workshops FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workshops ON workshops
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE workshop_work_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE workshop_work_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workshop_items ON workshop_work_items
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE workshop_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE workshop_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workshop_assignments ON workshop_assignments
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE workshop_dependencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE workshop_dependencies FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workshop_dependencies ON workshop_dependencies
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE workshop_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE workshop_activities FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_workshop_activities ON workshop_activities
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    workshops,
    workshop_work_items,
    workshop_assignments,
    workshop_dependencies,
    workshop_activities
TO sanad_runtime_app;
