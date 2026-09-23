-- ============================================================
-- V20260924_4 — HRM-G2: seed HR_LEAVE_APPROVAL workflow definition
-- ============================================================
-- Seeds the canonical LEAVE_APPROVAL workflow definition for all
-- existing tenants using the migration-safe RLS pattern (DO $$ with
-- set_config, same as V20260918_4/V20260923_3).
--
-- Graph: START → MANAGER_APPROVAL → HR_APPROVAL → END_APPROVED
--                     ↓                    ↓
--               END_REJECTED         END_REJECTED
--
-- The definition is PUBLISHED Y2 with deterministic family identity.
-- A clean tenant can resolve findActiveByCode(tenantId, 'HR_LEAVE_APPROVAL')
-- after this migration.
-- ============================================================

-- Seed the workflow definition using the canonical workflow_definitions table
-- (RLS-safe: DO $$ with set_config per tenant)
DO $$
DECLARE
    t RECORD;
    def_id UUID;
    family_id UUID;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, true);

        -- Check if HR_LEAVE_APPROVAL definition already exists
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM workflow_definitions
            WHERE tenant_id = t.id AND code = 'HR_LEAVE_APPROVAL' AND status = 'PUBLISHED'
        );

        -- Create deterministic IDs
        family_id := uuid_generate_v5('6ba7b810-9dad-11d1-80b4-00c04fd430c8'::uuid,
            'HR_LEAVE_APPROVAL:' || t.id::text);
        def_id := uuid_generate_v5(family_id, 'v1');

        -- Insert the definition (PUBLISHED Y2)
        INSERT INTO workflow_definitions (
            id, tenant_id, definition_family_id, code, name, description,
            module, version, status, trigger_type, created_by, version_lock,
            created_at, updated_at
        ) VALUES (
            def_id, t.id, family_id,
            'HR_LEAVE_APPROVAL',
            'Leave Approval',
            'Manager → HR two-step leave approval workflow',
            'HRM', 1, 'PUBLISHED', 'MANUAL',
            NULL, 1,
            NOW(), NOW()
        )
        ON CONFLICT DO NOTHING;

    END LOOP;
    PERFORM set_config('app.tenant_id', '', true);
END $$;

-- Note: The full Y2 graph definition (steps, transitions, approval policies)
-- would be created through the canonical WorkflowDefinition publication path
-- at application bootstrap time, not via raw SQL. This migration seeds the
-- definition header row so findActiveByCode() can resolve it. The application's
-- HrLeaveWorkflowAdapter calls graphExecutionService which resolves the full
-- graph from the published definition version.
