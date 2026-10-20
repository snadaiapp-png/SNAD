-- Forward-only hardening: post-baseline CRM tables must fail closed when tenant context is absent or empty.
-- Do not edit V20261020_2: it is already applied and its Flyway checksum is immutable.
-- Background work must establish a trusted tenant scope explicitly; there is no cross-tenant bypass here.

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

        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON public.%I', tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I FOR ALL
             USING (
                 NULLIF(current_setting(''app.tenant_id'', true), '''') IS NOT NULL
                 AND tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')
             )
             WITH CHECK (
                 NULLIF(current_setting(''app.tenant_id'', true), '''') IS NOT NULL
                 AND tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), '''')
             )',
            tbl
        );
    END LOOP;
END
$snad$;
