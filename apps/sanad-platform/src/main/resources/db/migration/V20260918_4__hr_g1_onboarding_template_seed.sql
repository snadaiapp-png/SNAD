-- ============================================================
-- V20260908_3 — HRM-G1: seed one generic onboarding checklist template
--               (design §17; G0 V20260807_2 tenant-scoped seed pattern)
-- ============================================================
-- Tenant-scoped, versioned, idempotent (WHERE NOT EXISTS): for every ACTIVE
-- tenant that does not yet have the GENERIC-ONBOARDING template, insert the
-- version-1 generic template.
--
-- FORCE RLS is already live on this table (V20260908_2), so the seed sets
-- app.tenant_id per tenant via set_config(..., is_local => true) — the same
-- scoped path the application uses. No BYPASSRLS, no superuser: each INSERT
-- passes the policy's WITH CHECK by carrying the matching tenant context.
--
-- Tenants created later receive the template through the application
-- provisioning path (T9), not by re-running DDL.
--
-- Definition shape: ordered list of generic task titles; materialization
-- into hr_onboarding_checklists / hr_onboarding_tasks happens in the
-- application layer (T9) — snapshot semantics per §5.1.
-- ============================================================

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT id FROM tenants WHERE status = 'ACTIVE' LOOP
        PERFORM set_config('app.tenant_id', t.id::text, true);
        INSERT INTO hr_onboarding_checklist_templates
            (id, tenant_id, code, name, version, definition, is_active, created_at, updated_at)
        VALUES
            (gen_random_uuid(),
             t.id,
             'GENERIC-ONBOARDING',
             'Generic Onboarding Checklist',
             1,
             ('[{"seq":1,"title":"Complete personal file"},' ||
             ' {"seq":2,"title":"Sign employment contract"},' ||
             ' {"seq":3,"title":"Workstation and equipment ready"},' ||
             ' {"seq":4,"title":"Introduce team and policies"},' ||
             ' {"seq":5,"title":"Complete mandatory training"}]')::jsonb,
             TRUE,
             NOW(),
             NOW())
        ON CONFLICT (tenant_id, code) DO NOTHING;
    END LOOP;
    PERFORM set_config('app.tenant_id', '', true);
END $$;
