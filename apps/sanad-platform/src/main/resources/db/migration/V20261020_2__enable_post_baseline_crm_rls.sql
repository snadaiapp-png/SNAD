-- ============================================================
-- CRM post-baseline RLS drift remediation
-- ============================================================
-- CRM-018 established row-level security for every crm_* table with tenant_id.
-- The tables below were created later (2026-08-04 / 2026-08-05), after the
-- dynamic CRM-018 migration had already run, so they never inherited RLS.
--
-- Preserve the current CRM compatibility contract:
--   * ENABLE RLS (not FORCE)
--   * permissive when app.tenant_id is unset (background-job compatibility)
--   * strict tenant filtering + WITH CHECK when app.tenant_id is set
--
-- A future dedicated hardening can migrate the whole CRM domain to fail-closed
-- semantics atomically; this migration only closes the post-baseline drift.
-- ============================================================

DO $snad$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'crm_capacity_plans',
        'crm_cases',
        'crm_email_logs',
        'crm_service_assignments',
        'crm_shift_assignments',
        'crm_shift_templates',
        'crm_staff_availability',
        'crm_staff_skills',
        'crm_workload_assignments'
    ]
    LOOP
        IF to_regclass(format('public.%I', tbl)) IS NULL THEN
            RAISE EXCEPTION 'Required CRM table is missing: %', tbl;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = tbl
              AND column_name = 'tenant_id'
        ) THEN
            RAISE EXCEPTION 'Required tenant_id column is missing from %', tbl;
        END IF;

        EXECUTE format(
            'ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',
            tbl
        );

        EXECUTE format(
            'DROP POLICY IF EXISTS tenant_isolation ON public.%I',
            tbl
        );

        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I FOR ALL
             USING (
                 current_setting(''app.tenant_id'', true) IS NULL
                 OR tenant_id::text = current_setting(''app.tenant_id'', true)
             )
             WITH CHECK (
                 current_setting(''app.tenant_id'', true) IS NULL
                 OR tenant_id::text = current_setting(''app.tenant_id'', true)
             )',
            tbl
        );
    END LOOP;
END
$snad$;
