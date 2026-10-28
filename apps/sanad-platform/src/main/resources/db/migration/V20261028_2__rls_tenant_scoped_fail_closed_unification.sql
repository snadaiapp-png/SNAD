-- Forward-only RLS unification: every tenant-scoped table in the chain must be
-- protected by the canonical fail-closed tenant_isolation contract.
--
-- Task: SNAD-SEC-RLS-FULL-CLOSURE-2026-10-28
-- Basis: docs/security/RLS_PERMISSION_MATRIX_2026-10-28.md (live catalog matrix)
--
-- WHAT THIS MIGRATION DOES
--   1. Group A (96 tenant-scoped tables with a tenant_id column):
--      ENABLE + FORCE ROW LEVEL SECURITY, and a single fail-closed tenant_isolation
--      policy (missing/empty/wrong tenant context denies all rows and writes).
--   2. login_tenant_index: ENABLE RLS + the same fail-closed policy WITHOUT FORCE
--      (documented exception: the table is maintained exclusively by the SECURITY
--      DEFINER trigger function sync_login_tenant_index(); FORCE would break
--      definer maintenance in environments where the migration runner owns the
--      tables; non-owner reads remain tenant-scoped; pre-auth reads flow through
--      the SECURITY DEFINER resolver V20261011_4).
--   3. partners: ENABLE + FORCE RLS + an explicit bounded partner/platform policy
--      (partner GUC match OR platform tenant match; no NULL-allow anywhere).
--   4. Re-asserts service_role EXECUTE on sync_login_tenant_index() (production
--      parity; the grant was observed in production and documented in
--      candidate_function_hardening.sql; the chain lost it when V20261020_1
--      revoked PUBLIC EXECUTE).
--
-- WHY "DROP POLICY IF EXISTS tenant_isolation" IS USED (documented justification,
-- per the no-silent-drift-masking rule):
--   Production carries out-of-band manually-applied fail-open tenant_isolation
--   policies on many of these tables (see matrix §4). The chain does not create
--   them. A forward-only unification must REPLACE that drifted policy with the
--   canonical fail-closed contract in every environment; IF EXISTS makes the
--   replacement idempotent across chain-fresh and production-drifted states.
--   The drift is not hidden: the complete replaced-policy inventory is published
--   in the permission matrix document. No applied migration is edited.
--
-- PLATFORM / BACKGROUND-JOB CONTRACT (unchanged from V20261021_1):
--   Background and cross-tenant work must establish a trusted tenant scope
--   explicitly. There is no NULL-context bypass in this migration.

DO $snad$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'billing_invoices',
        'bp_analytics_snapshots',
        'bp_inventory_balances',
        'bp_inventory_movements',
        'bp_ledger_entries',
        'bp_payment_events',
        'bp_process_runs',
        'bp_process_steps',
        'bp_workflow_approvals',
        'commerce_cart_items',
        'commerce_carts',
        'commerce_collection_products',
        'commerce_collections',
        'commerce_order_items',
        'commerce_order_status_history',
        'commerce_orders',
        'commerce_prices',
        'commerce_product_variants',
        'commerce_products',
        'commerce_store_domains',
        'commerce_stores',
        'crm_account_addresses',
        'crm_account_identifiers',
        'crm_account_merge_history',
        'crm_account_relationships',
        'crm_account_status_history',
        'crm_accounts',
        'crm_activities',
        'crm_assignments',
        'crm_audit_logs',
        'crm_communication_method_history',
        'crm_communication_methods',
        'crm_communication_policies',
        'crm_contact_account_relationships',
        'crm_contact_lookup_index',
        'crm_contact_ownership_history',
        'crm_contact_relationship_history',
        'crm_contact_relationship_roles',
        'crm_custom_field_definitions',
        'crm_custom_field_values',
        'crm_idempotency_records',
        'crm_import_errors',
        'crm_import_files',
        'crm_import_jobs',
        'crm_leads',
        'crm_notes',
        'crm_opportunities',
        'crm_opportunity_stage_history',
        'crm_party_address_history',
        'crm_party_addresses',
        'crm_phone_numbers',
        'crm_pipeline_stages',
        'crm_pipelines',
        'crm_reports',
        'crm_tag_assignments',
        'crm_tags',
        'crm_tasks',
        'crm_timeline_events',
        'crm_transfers',
        'erp_goods_receipt_items',
        'erp_goods_receipts',
        'erp_inventory_adjustments',
        'erp_inventory_balances',
        'erp_inventory_movements',
        'erp_inventory_reservations',
        'erp_inventory_transfer_items',
        'erp_inventory_transfers',
        'erp_items',
        'erp_purchase_order_items',
        'erp_purchase_orders',
        'erp_purchase_requisition_items',
        'erp_purchase_requisitions',
        'erp_suppliers',
        'erp_warehouses',
        'governance_configurations',
        'organization_memberships',
        'organizations',
        'password_reset_tokens',
        'provisioning_jobs',
        'refresh_tokens',
        'role_capabilities',
        'roles',
        'subscription_change_events',
        'subscription_commands',
        'subscription_items',
        'tenant_domains',
        'tenant_quota',
        'tenant_subscriptions',
        'user_role_assignments',
        'website_domains',
        'website_navigation',
        'website_navigation_items',
        'website_pages',
        'website_publications',
        'website_theme_settings',
        'websites'
    ]
    LOOP
        IF to_regclass(format('public.%I', tbl)) IS NULL THEN
            RAISE EXCEPTION 'RLS unification: required table is missing: %', tbl;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = tbl
              AND column_name = 'tenant_id'
        ) THEN
            RAISE EXCEPTION 'RLS unification: tenant_id column missing from %', tbl;
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

-- login_tenant_index: ENABLE + fail-closed policy, deliberately WITHOUT FORCE.
DO $snad$
BEGIN
    IF to_regclass('public.login_tenant_index') IS NULL THEN
        RAISE EXCEPTION 'RLS unification: required table is missing: login_tenant_index';
    END IF;
    ALTER TABLE public.login_tenant_index ENABLE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS tenant_isolation ON public.login_tenant_index;
    CREATE POLICY tenant_isolation ON public.login_tenant_index FOR ALL
        USING (
            NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
        )
        WITH CHECK (
            NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
            AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
        );
END
$snad$;

-- partners: bounded partner/platform contract; both branches are explicit.
DO $snad$
BEGIN
    IF to_regclass('public.partners') IS NULL THEN
        RAISE EXCEPTION 'RLS unification: required table is missing: partners';
    END IF;
    ALTER TABLE public.partners ENABLE ROW LEVEL SECURITY;
    ALTER TABLE public.partners FORCE ROW LEVEL SECURITY;
    DROP POLICY IF EXISTS partner_access ON public.partners;
    CREATE POLICY partner_access ON public.partners FOR ALL
        USING (
            (
                NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
                AND id::text = current_setting('app.partner_id', true)
            )
            OR (
                NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
                AND current_setting('app.tenant_id', true)
                    = '00000000-0000-0000-0000-000000000001'
            )
        )
        WITH CHECK (
            (
                NULLIF(current_setting('app.partner_id', true), '') IS NOT NULL
                AND id::text = current_setting('app.partner_id', true)
            )
            OR (
                NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
                AND current_setting('app.tenant_id', true)
                    = '00000000-0000-0000-0000-000000000001'
            )
        );
END
$snad$;

-- Production parity: preserve the documented service_role EXECUTE grant on the
-- SECURITY DEFINER trigger function (V20261020_1 revoked PUBLIC; the explicit
-- service_role grant observed in production must survive in chain-built envs).
DO $snad$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role')
       AND to_regprocedure('public.sync_login_tenant_index()') IS NOT NULL THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.sync_login_tenant_index() TO service_role';
    END IF;
END
$snad$;

-- Final verification guard: every Group A table must be RLS+FORCE with a
-- fail-closed tenant_isolation policy; any violation aborts the migration.
DO $snad$
DECLARE
    tbl text;
    missing int := 0;
    bad int := 0;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'billing_invoices',
        'bp_analytics_snapshots',
        'bp_inventory_balances',
        'bp_inventory_movements',
        'bp_ledger_entries',
        'bp_payment_events',
        'bp_process_runs',
        'bp_process_steps',
        'bp_workflow_approvals',
        'commerce_cart_items',
        'commerce_carts',
        'commerce_collection_products',
        'commerce_collections',
        'commerce_order_items',
        'commerce_order_status_history',
        'commerce_orders',
        'commerce_prices',
        'commerce_product_variants',
        'commerce_products',
        'commerce_store_domains',
        'commerce_stores',
        'crm_account_addresses',
        'crm_account_identifiers',
        'crm_account_merge_history',
        'crm_account_relationships',
        'crm_account_status_history',
        'crm_accounts',
        'crm_activities',
        'crm_assignments',
        'crm_audit_logs',
        'crm_communication_method_history',
        'crm_communication_methods',
        'crm_communication_policies',
        'crm_contact_account_relationships',
        'crm_contact_lookup_index',
        'crm_contact_ownership_history',
        'crm_contact_relationship_history',
        'crm_contact_relationship_roles',
        'crm_custom_field_definitions',
        'crm_custom_field_values',
        'crm_idempotency_records',
        'crm_import_errors',
        'crm_import_files',
        'crm_import_jobs',
        'crm_leads',
        'crm_notes',
        'crm_opportunities',
        'crm_opportunity_stage_history',
        'crm_party_address_history',
        'crm_party_addresses',
        'crm_phone_numbers',
        'crm_pipeline_stages',
        'crm_pipelines',
        'crm_reports',
        'crm_tag_assignments',
        'crm_tags',
        'crm_tasks',
        'crm_timeline_events',
        'crm_transfers',
        'erp_goods_receipt_items',
        'erp_goods_receipts',
        'erp_inventory_adjustments',
        'erp_inventory_balances',
        'erp_inventory_movements',
        'erp_inventory_reservations',
        'erp_inventory_transfer_items',
        'erp_inventory_transfers',
        'erp_items',
        'erp_purchase_order_items',
        'erp_purchase_orders',
        'erp_purchase_requisition_items',
        'erp_purchase_requisitions',
        'erp_suppliers',
        'erp_warehouses',
        'governance_configurations',
        'organization_memberships',
        'organizations',
        'password_reset_tokens',
        'provisioning_jobs',
        'refresh_tokens',
        'role_capabilities',
        'roles',
        'subscription_change_events',
        'subscription_commands',
        'subscription_items',
        'tenant_domains',
        'tenant_quota',
        'tenant_subscriptions',
        'user_role_assignments',
        'website_domains',
        'website_navigation',
        'website_navigation_items',
        'website_pages',
        'website_publications',
        'website_theme_settings',
        'websites'
    ]
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname = tbl
              AND c.relrowsecurity AND c.relforcerowsecurity
        ) THEN
            RAISE NOTICE 'RLS unification verification: % lacks RLS+FORCE', tbl;
            missing := missing + 1;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_policy pol
            JOIN pg_class c ON c.oid = pol.polrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname = tbl
              AND pol.polname = 'tenant_isolation'
              AND pg_get_expr(pol.polqual, pol.polrelid) LIKE '%NULLIF(current_setting(%app.tenant_id%%'
              AND pg_get_expr(pol.polqual, pol.polrelid) NOT LIKE '%IS NULL OR%'
              AND pg_get_expr(pol.polwithcheck, pol.polrelid) LIKE '%NULLIF(current_setting(%app.tenant_id%%'
        ) THEN
            RAISE NOTICE 'RLS unification verification: % lacks fail-closed tenant_isolation policy', tbl;
            bad := bad + 1;
        END IF;
    END LOOP;

    IF missing > 0 OR bad > 0 THEN
        RAISE EXCEPTION 'RLS unification verification failed: % tables without RLS+FORCE, % without fail-closed policy', missing, bad;
    END IF;
    RAISE NOTICE 'RLS unification verified: 96 tables RLS+FORCE fail-closed, login_tenant_index policy-only, partners bounded policy';
END
$snad$;
