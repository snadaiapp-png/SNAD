-- ============================================================
-- SNAD-SEC-RLS-FULL-CLOSURE-2026-10-28 — Phase 3 remediation
-- Tenant-owned business data: fail-closed RLS restoration
-- ============================================================
-- EVIDENCE CHAIN (all verified on branch security/rls-forensic-remediation):
--   * docs/security/RLS_FORENSIC_REMEDIATION_2026-10-26.md — production snapshot:
--     49 public tables without RLS; 81 NULL-allow policy candidates (CRM legacy).
--   * Full 213-migration chain replay on isolated PostgreSQL 17.6 (this task,
--     exit 0, flyway validate 213/213 exit 0): the CRM-018 dynamic RLS generator
--     is ABSENT from the chain (historical ENABLE→DISABLE failure, see
--     docs/crm/remediation/ROOT-CAUSE-R1.md and the runbook
--     docs/runbooks/CRM-018-RLS-DISABLE-rollback.sql). Chain end-state therefore
--     leaves 90+2 tenant-owned tables (crm_*, commerce_*, erp_*, bp_*, website_*,
--     subscription tenant rows, identity/RBAC) with NO row-level isolation —
--     they rely on grants + application-level tenancy only.
--   * Classification matrix: tests/security/rls_tenant_owned_classification.json
--     (RLS-06 evidence; PLATFORM-REFERENCE tables have no tenant_id and keep the
--     V20261020_1 default-deny grant contract; credential/pre-auth tables are
--     deliberately deferred to their G1 characterization contract — see T2 note).
--
-- CONTRACT (matches repo precedent V20261011_3 users / V20261021_1 CRM / V20260905_5 HR):
--   ENABLE + FORCE ROW LEVEL SECURITY (runtime role sanad owns these tables).
--   One fail-closed policy per table TO sanad:
--     USING / WITH CHECK ( tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '') )
--   Missing, empty or unauthorized tenant context ⇒ NULL comparison ⇒ zero rows,
--   writes denied. No NULL-allow branch, no BYPASSRLS, no owner bypass exception.
--   Cross-tenant/background work MUST use the trusted scoped path
--   (security.rls.TenantRlsTransactionContext) — there is no cross-tenant bypass here.
--
-- T2 DEFERRAL (explicit, bounded): refresh_tokens, password_reset_tokens,
--   login_tenant_index, provisioning_jobs(+steps), subscription_commands are
--   credential/pre-auth/background-contract tables. Per
--   docs/security/g1-users-auth-rls-compatibility-execution.md they require
--   characterization tests of their pre-auth paths BEFORE RLS is applied.
--   They are NOT touched by this migration. This is not a silent exception.
--
-- FUTURE-MIGRATION CONTRACT: because this migration FORCEs RLS while the
--   Flyway owner is the runtime role, any LATER migration that seeds these
--   tables must set app.tenant_id (SET LOCAL) inside its own transaction,
--   exactly as V20260918_4/V20260923_3 seed paths do.
--
-- IDEMPOTENCE: PostgreSQL has no CREATE POLICY IF NOT EXISTS; the
--   DROP POLICY IF EXISTS + CREATE sequence follows the documented repo
--   precedent (V20260905_5 §IDEMPOTENCE). Flyway never re-runs a versioned
--   migration; the guards below re-assert the end-state if ever replayed.
-- ============================================================

DO $snad$
DECLARE
    tbl text;
    protected_tables text[] := ARRAY[
      -- CRM core + extensions (CRM-018 contract lost from chain; restored fail-closed)
      'crm_account_addresses','crm_account_identifiers','crm_account_merge_history',
      'crm_account_relationships','crm_account_status_history','crm_accounts',
      'crm_activities','crm_assignments','crm_audit_logs','crm_communication_method_history',
      'crm_communication_methods','crm_communication_policies','crm_contact_account_relationships',
      'crm_contact_lookup_index','crm_contact_ownership_history','crm_contact_relationship_history',
      'crm_contact_relationship_roles','crm_custom_field_definitions','crm_custom_field_values',
      'crm_idempotency_records','crm_import_errors','crm_import_files','crm_import_jobs',
      'crm_leads','crm_notes','crm_opportunities','crm_opportunity_stage_history',
      'crm_party_address_history','crm_party_addresses','crm_phone_numbers',
      'crm_pipeline_stages','crm_pipelines','crm_reports','crm_tag_assignments','crm_tags',
      'crm_tasks','crm_timeline_events','crm_transfers',
      -- Commerce
      'commerce_cart_items','commerce_carts','commerce_collection_products','commerce_collections',
      'commerce_order_items','commerce_order_status_history','commerce_orders','commerce_prices',
      'commerce_product_variants','commerce_products','commerce_store_domains','commerce_stores',
      -- ERP
      'erp_goods_receipt_items','erp_goods_receipts','erp_inventory_adjustments',
      'erp_inventory_balances','erp_inventory_movements','erp_inventory_reservations',
      'erp_inventory_transfer_items','erp_inventory_transfers','erp_items',
      'erp_purchase_order_items','erp_purchase_orders','erp_purchase_requisition_items',
      'erp_purchase_requisitions','erp_suppliers','erp_warehouses',
      -- Business-process engine runtime
      'bp_analytics_snapshots','bp_inventory_balances','bp_inventory_movements',
      'bp_ledger_entries','bp_payment_events','bp_process_runs','bp_process_steps',
      'bp_workflow_approvals',
      -- Websites
      'website_domains','website_navigation','website_navigation_items','website_pages',
      'website_publications','website_theme_settings','websites',
      -- Subscription/billing tenant rows
      'billing_invoices','subscription_change_events','subscription_items',
      'tenant_domains','tenant_subscriptions',
      -- Identity / RBAC tenant-scoped
      'organization_memberships','organizations','role_capabilities','roles',
      'user_role_assignments',
      -- Governance
      'governance_configurations'
    ];
    -- KNOWN SCHEMA DRIFT (documented, not masked): tenant_quota.tenant_id is
    -- VARCHAR(64) while the platform contract is UUID. Fixing the column type
    -- is a dedicated change outside this security migration's scope. The
    -- fail-closed text predicate below is type-correct for it, so the table is
    -- still protected under its own type-checked guard rather than left open.
    quota_table text := 'tenant_quota';
    -- G2c scope: tables that already carry a strict single tenant policy but
    -- were left ENABLE-only — while owned by the runtime role sanad this makes
    -- the policy a dead letter (owners bypass RLS without FORCE).
    -- Runtime-proven defect (PostgreSQL Direct suite, this branch): as owner,
    -- platform_files accepted a cross-tenant UPDATE with tenant_id swap.
    -- Contract: keep each table's existing strict policy unchanged; only bind
    -- the owner to it. Background jobs must use the trusted scoped path.
    force_only_tables text[] := ARRAY[
      'ai_agents','ai_inference_log','analytics_dashboards','analytics_data_sources',
      'analytics_reports','decision_actions','decision_participants','escalations',
      'executive_alerts','executive_decisions','executive_health_snapshots',
      'executive_insights','finance_accounts','finance_invoice_lines','finance_invoices',
      'finance_journal_entries','finance_journal_lines','finance_payments','issues',
      'key_results','kpi_definitions','kpi_measurements','kpi_targets',
      'management_audit_trail','platform_files','risks','strategic_initiatives',
      'strategic_objectives','workflow_analytics_process_facts','workflow_analytics_step_facts',
      'workflow_approval_requests','workflow_attachments','workflow_branch_tokens',
      'workflow_business_calendars','workflow_calendar_holidays','workflow_customer_feedback',
      'workflow_definitions','workflow_delegations','workflow_event_inbox',
      'workflow_event_outbox','workflow_execution_attempts','workflow_external_actions',
      'workflow_external_participants','workflow_incidents','workflow_instances',
      'workflow_journey','workflow_notification_intents','workflow_notification_policies',
      'workflow_portal_access_log','workflow_portal_otp_challenges',
      'workflow_responsibility_segments','workflow_step_instances','workflow_step_transitions',
      'workflow_steps','workflow_timers','workflow_transition_audit',
      'workflow_user_notifications','workflow_webhook_endpoints','workflow_work_item_candidates',
      'workflow_work_items'
    ];
    t text;
    missing text[] := ARRAY[]::text[];
BEGIN
    -- G0: runtime role contract — the restricted role must exist and be restricted.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
                   AND NOT rolsuper AND NOT rolbypassrls) THEN
        RAISE EXCEPTION 'T1_RLS_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role first';
    END IF;

    -- G1: every listed table must exist and carry a UUID tenant_id column.
    FOREACH t IN ARRAY protected_tables LOOP
        IF to_regclass(format('public.%I', t)) IS NULL THEN
            missing := array_append(missing, t);
        ELSIF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema='public' AND table_name=t
              AND column_name='tenant_id' AND data_type='uuid'
        ) THEN
            RAISE EXCEPTION 'T1_TENANT_COLUMN_MISSING: % has no UUID tenant_id', t;
        END IF;
    END LOOP;
    IF array_length(missing, 1) > 0 THEN
        RAISE EXCEPTION 'T1_TABLES_MISSING: %', array_to_string(missing, ', ');
    END IF;

    -- G2: apply fail-closed RLS per table.
    FOREACH tbl IN ARRAY protected_tables LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON public.%I', tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I FOR ALL TO sanad
             USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))
             WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))',
            tbl);
    END LOOP;

    -- G2b: tenant_quota under its own type-checked guard (VARCHAR tenant_id).
    IF to_regclass(format('public.%I', quota_table)) IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema='public' AND table_name=quota_table
              AND column_name='tenant_id' AND data_type IN ('character varying','text')
        ) THEN
            RAISE EXCEPTION 'T1_QUOTA_TYPE_DRIFT: tenant_quota.tenant_id is neither UUID nor text — investigate';
        END IF;
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', quota_table);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', quota_table);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON public.%I', quota_table);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON public.%I FOR ALL TO sanad
             USING (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))
             WITH CHECK (tenant_id::text = NULLIF(current_setting(''app.tenant_id'', true), ''''))',
            quota_table);
    END IF;

    -- G2c: bind the table owner to its existing strict policy.
    FOREACH tbl IN ARRAY force_only_tables LOOP
        IF to_regclass(format('public.%I', tbl)) IS NULL THEN
            RAISE EXCEPTION 'T1_FORCE_TABLE_MISSING: %', tbl;
        END IF;
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', tbl);
    END LOOP;

    -- G3: post-apply verification — fail closed on any drift.
    -- Note: pg_policies stores normalized expressions with ::text casts
    -- (e.g. NULLIF(current_setting('app.tenant_id'::text, true), ''::text)),
    -- so the predicate check is normalization-tolerant but still rejects the
    -- fail-open marker (an IS NULL branch) outright.
    FOREACH tbl IN ARRAY protected_tables LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_class c
                       JOIN pg_namespace n ON n.oid=c.relnamespace
                       WHERE n.nspname='public' AND c.relname=tbl
                         AND c.relrowsecurity AND c.relforcerowsecurity) THEN
            RAISE EXCEPTION 'T1_VERIFY_RLS_STATE_FAILED: %', tbl;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
            WHERE schemaname='public' AND tablename=tbl AND policyname='tenant_isolation'
              AND qual LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
              AND with_check LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
              AND qual NOT ILIKE '%IS NULL%' AND with_check NOT ILIKE '%IS NULL%'
        ) THEN
            RAISE EXCEPTION 'T1_VERIFY_POLICY_CONTRACT_FAILED: %', tbl;
        END IF;
    END LOOP;

    IF to_regclass(format('public.%I', quota_table)) IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
            WHERE schemaname='public' AND tablename=quota_table AND policyname='tenant_isolation'
              AND qual LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
              AND with_check LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'
              AND qual NOT ILIKE '%IS NULL%' AND with_check NOT ILIKE '%IS NULL%'
        ) THEN
            RAISE EXCEPTION 'T1_VERIFY_POLICY_CONTRACT_FAILED: %', quota_table;
        END IF;
    END IF;
END
$snad$;
