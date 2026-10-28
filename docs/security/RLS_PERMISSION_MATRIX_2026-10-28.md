# RLS Permission Matrix — Full Catalog Forensic Classification

**Task:** `SNAD-SEC-RLS-FULL-CLOSURE-2026-10-28`  
**Basis:** live catalog extraction, not historical reports. Two evidence sources:

1. **Chain state** — isolated disposable PostgreSQL 17.6 fixture (`sanad`), full 213-migration Flyway chain applied from repository `main` @ `71e4371ffdbd91f7bf26f4c445ccc72ce4f362be`. Raw JSON: `evidence/rls-closure/forensic-matrix-sanad.json` (execution artifact).
2. **Production state** — read-only Supabase Management API catalog scan of project `tkbrvupemreqabwzdpyq` (metadata only; zero user rows read; zero writes). Raw JSON: `evidence/rls-closure/prod-rescan-*.json`.

## 1. State comparison — chain vs production vs 2026-10-26 audit

| Metric | Chain fixture @ 71e4371f | Production (this scan) | 2026-10-26 audit |
|---|---:|---:|---:|
| public tables | 294 | 319 | 319 |
| tables without RLS | 121 | 49 | 49 |
| RLS enabled, not forced | 60 | 156 | 165 |
| RLS enabled + FORCE | 113 | 114 | — |
| policies total | 187 | 283 | 283 |
| classic NULL-allow tenant policies (fail-open) | 12 | 0 classic / 72 NULL-branch variants | 81 candidates |
| non-extension functions without pinned search_path | 0 | 0 | 16 |
| non-extension functions callable by anon | 0 | 0 | exposed (sync fn) |

**Key reconciliation findings:**

1. The historical 81 fail-open candidates **do not exist in the current chain** (0 classic NULL-allow policies). They were eliminated by the post-audit chain evolution (`V20261011_3`, `V20261020_1`, `V20261020_2`, `V20261021_1`, `V20261028_1`).
2. **Production still carries 72 fail-open NULL-allow `tenant_isolation` policies on 66 tables** — applied out-of-band (manual RLS outside Flyway history). The chain does not create these policies at all.
3. **Chain drift (the reverse direction):** the chain leaves 121 tables without RLS, of which production has RLS on 72 (out-of-band). A fresh environment built from the chain would be *unprotected* on those tables. This is the central gap this remediation closes.
4. Production owns all tables as `postgres` (superuser). Fixture tables are owned by `sanad` (migration runner). Therefore FORCE ROW LEVEL SECURITY is required in the chain so enforcement holds in every environment.
5. Function hardening is uniform: 41/41 non-extension functions pinned and denied to anon/authenticated in BOTH chain and production; `sync_login_tenant_index` retains `service_role` EXECUTE in production only (fixture chain lost that grant — re-asserted by this remediation).
6. Production-only out-of-band objects beyond the chain: `service_callback_replay` (RLS-less) plus 24 RLS-protected out-of-band tables. These require an owner-led reconciliation and are out of this migration's forward-only scope; listed in §5.

## 2. Group A — tenant-scoped tables (tenant_id present): fail-closed tenant isolation

96 tables. Contract (canonical, identical to `V20261021_1` / `V20261011_3`):

```sql
ALTER TABLE public.<t> ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.<t> FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.<t>;  -- replaces out-of-band fail-open/manual drift; documented justification
CREATE POLICY tenant_isolation ON public.<t> FOR ALL
  USING (NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
         AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))
  WITH CHECK (NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
              AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''));
```

Missing context, empty context, wrong tenant → zero rows / rejected writes (fail-closed). Cross-tenant work must set an explicitly authorized context (per `TenantRlsConnectionHandler` + the V20261021_1 header contract).

| # | Table | Class | Production today | Remediation |
|---:|---|---|---|---|
| 1 | `billing_invoices` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 2 | `bp_analytics_snapshots` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 3 | `bp_inventory_balances` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 4 | `bp_inventory_movements` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 5 | `bp_ledger_entries` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 6 | `bp_payment_events` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 7 | `bp_process_runs` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 8 | `bp_process_steps` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 9 | `bp_workflow_approvals` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 10 | `commerce_cart_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 11 | `commerce_carts` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 12 | `commerce_collection_products` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 13 | `commerce_collections` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 14 | `commerce_order_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 15 | `commerce_order_status_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 16 | `commerce_orders` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 17 | `commerce_prices` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 18 | `commerce_product_variants` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 19 | `commerce_products` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 20 | `commerce_store_domains` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 21 | `commerce_stores` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 22 | `crm_account_addresses` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 23 | `crm_account_identifiers` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 24 | `crm_account_merge_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 25 | `crm_account_relationships` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 26 | `crm_account_status_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 27 | `crm_accounts` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 28 | `crm_activities` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 29 | `crm_assignments` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 30 | `crm_audit_logs` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 31 | `crm_communication_method_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 32 | `crm_communication_methods` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 33 | `crm_communication_policies` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 34 | `crm_contact_account_relationships` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 35 | `crm_contact_lookup_index` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 36 | `crm_contact_ownership_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 37 | `crm_contact_relationship_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 38 | `crm_contact_relationship_roles` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 39 | `crm_custom_field_definitions` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 40 | `crm_custom_field_values` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 41 | `crm_idempotency_records` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 42 | `crm_import_errors` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 43 | `crm_import_files` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 44 | `crm_import_jobs` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 45 | `crm_leads` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 46 | `crm_notes` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 47 | `crm_opportunities` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 48 | `crm_opportunity_stage_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 49 | `crm_party_address_history` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 50 | `crm_party_addresses` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 51 | `crm_phone_numbers` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 52 | `crm_pipeline_stages` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 53 | `crm_pipelines` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 54 | `crm_reports` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 55 | `crm_tag_assignments` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 56 | `crm_tags` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 57 | `crm_tasks` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 58 | `crm_timeline_events` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 59 | `crm_transfers` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 60 | `erp_goods_receipt_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 61 | `erp_goods_receipts` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 62 | `erp_inventory_adjustments` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 63 | `erp_inventory_balances` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 64 | `erp_inventory_movements` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 65 | `erp_inventory_reservations` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 66 | `erp_inventory_transfer_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 67 | `erp_inventory_transfers` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 68 | `erp_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 69 | `erp_purchase_order_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 70 | `erp_purchase_orders` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 71 | `erp_purchase_requisition_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 72 | `erp_purchase_requisitions` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 73 | `erp_suppliers` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 74 | `erp_warehouses` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 75 | `governance_configurations` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 76 | `organization_memberships` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 77 | `organizations` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 78 | `password_reset_tokens` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 79 | `provisioning_jobs` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 80 | `refresh_tokens` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 81 | `role_capabilities` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 82 | `roles` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 83 | `subscription_change_events` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 84 | `subscription_commands` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 85 | `subscription_items` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 86 | `tenant_domains` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 87 | `tenant_quota` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 88 | `tenant_subscriptions` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 89 | `user_role_assignments` | tenant-data | no RLS (grants-only) | ENABLE+FORCE + fail-closed tenant_isolation |
| 90 | `website_domains` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 91 | `website_navigation` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 92 | `website_navigation_items` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 93 | `website_pages` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 94 | `website_publications` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 95 | `website_theme_settings` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |
| 96 | `websites` | tenant-data | manual RLS + fail-open policy (out-of-band) | ENABLE+FORCE + fail-closed tenant_isolation |

## 3. Group B — platform / reference / credential tables without tenant dimension

| Table | Class | Decision | Rationale |
|---|---|---|---|
| `access_capabilities` | platform-catalog | No RLS (documented exception) | Capability catalog; read-only reference; default-deny. |
| `application_iam_contracts` | platform-catalog | No RLS (documented exception) | IAM contract registry; default-deny. |
| `applications` | platform-catalog | No RLS (documented exception) | Application registry; default-deny. |
| `country_currencies` | reference-data | No RLS (documented exception) | Global reference (ISO currencies); no tenant dimension. |
| `flyway_schema_history` | migration-metadata | No RLS (documented exception) | Flyway bookkeeping; owner-only access; zero PUBLIC/anon/authenticated grants verified; RLS adds no tenant dimension. |
| `hr_compliance_rules` | reference-data | No RLS (documented exception) | Country-pack compliance rules; global reference. |
| `hr_country_packs` | reference-data | No RLS (documented exception) | Country packs; global reference. |
| `login_tenant_index` | credential-index | ENABLE RLS + tenant policy WITHOUT FORCE | Login tenant index maintained exclusively by SECURITY DEFINER trigger function sync_login_tenant_index; FORCE would break definer maintenance where the migration runner owns tables (CI/fixture); ENABLE RLS + fail-closed tenant policy WITHOUT FORCE: definer/owner maintenance preserved, non-owner reads tenant-scoped, pre-auth reads flow through the SECURITY DEFINER resolver (V20261011_4). |
| `module_capabilities` | platform-catalog | No RLS (documented exception) | Capability catalog; read-only reference; default-deny. |
| `modules` | platform-catalog | No RLS (documented exception) | Module catalog; read-only reference; default-deny to Data API roles. |
| `partners` | partner-contract | ENABLE+FORCE RLS + bounded partner/platform policy | Explicit branches only: partner GUC match OR platform tenant match; no NULL-allow. |
| `plan_module_entitlements` | billing-catalog | No RLS (documented exception) | Plan entitlement catalog; default-deny. |
| `plan_versions` | billing-catalog | No RLS (documented exception) | Plan version catalog; default-deny. |
| `platform_audit_logs` | platform-audit | No RLS (documented exception) | Platform control-plane audit (actor_tenant_id/target_tenant_id); append-only; default-deny; owner+backend only. |
| `platform_countries` | reference-data | No RLS (documented exception) | Global reference (countries); no tenant dimension. |
| `prices` | billing-catalog | No RLS (documented exception) | Price catalog; default-deny. |
| `product_entitlements` | billing-catalog | No RLS (documented exception) | Product entitlement catalog; default-deny. |
| `products` | billing-catalog | No RLS (documented exception) | Product catalog; default-deny. |
| `protected_system_roles` | platform-registry | No RLS (documented exception) | Protected role definitions; admin-only; default-deny. |
| `provisioning_job_steps` | provisioning-ops-child | No RLS (documented exception) | Child of tenant-scoped provisioning_jobs (job_id join); no tenant column; default-deny to Data API roles; parent isolation governs the workflow; backend-only access. |
| `saas_plan_entitlements` | billing-catalog | No RLS (documented exception) | Plan entitlement catalog; default-deny. |
| `saas_plans` | billing-catalog | No RLS (documented exception) | Plan catalog; read via governed Executive APIs; default-deny to Data API roles. |
| `system_services` | platform-catalog | No RLS (documented exception) | Service registry; default-deny. |
| `tenants` | platform-registry | No RLS (documented exception) | Root tenant registry — the isolation boundary object itself; pre-auth resolution via SECURITY DEFINER resolver (V20261011_4); zero public grants. |
| `usage_metrics` | platform-catalog | No RLS (documented exception) | Metric definitions; default-deny. |

**Grants control for Group B (verified):** zero `SELECT/INSERT/UPDATE/DELETE` grants to `PUBLIC`, `anon`, `authenticated` on all of these tables in the chain state AND in production (default-deny data plane). Access is limited to table owner (`postgres` in production, `sanad` in fixture/CI) and explicitly granted backend roles.

## 4. Fail-open policy inventory — production (to be superseded when the chain deploys)

72 production policies match the NULL-allow pattern; 66 tables. The remediation migration replaces the policy named `tenant_isolation` on every chain-covered table with the fail-closed contract. Production tables NOT covered by the chain are listed in §5 and require owner reconciliation.

| Table | Policy | Cmd |
|---|---|---|
| `authorization_change_events` | `authorization_change_events_isolation` | * |
| `crm_account_addresses` | `tenant_isolation` | * |
| `crm_account_identifiers` | `tenant_isolation` | * |
| `crm_account_merge_history` | `tenant_isolation` | * |
| `crm_account_relationships` | `tenant_isolation` | * |
| `crm_account_status_history` | `tenant_isolation` | * |
| `crm_activities` | `tenant_isolation` | * |
| `crm_assignment_rule_counters` | `tenant_isolation` | * |
| `crm_assignment_rule_versions` | `tenant_isolation` | * |
| `crm_assignment_rules` | `tenant_isolation` | * |
| `crm_assignments` | `tenant_isolation` | * |
| `crm_audit_logs` | `tenant_isolation` | * |
| `crm_communication_method_history` | `tenant_isolation` | * |
| `crm_communication_methods` | `tenant_isolation` | * |
| `crm_communication_policies` | `tenant_isolation` | * |
| `crm_contact_account_relationships` | `tenant_isolation` | * |
| `crm_contact_lookup_index` | `tenant_isolation` | * |
| `crm_contact_ownership_history` | `tenant_isolation` | * |
| `crm_contact_relationship_history` | `tenant_isolation` | * |
| `crm_contact_relationship_roles` | `tenant_isolation` | * |
| `crm_custom_field_definitions` | `tenant_isolation` | * |
| `crm_custom_field_values` | `tenant_isolation` | * |
| `crm_customer_score_history` | `tenant_isolation` | * |
| `crm_customer_scores` | `tenant_isolation` | * |
| `crm_customer_segments` | `tenant_isolation` | * |
| `crm_idempotency_records` | `tenant_isolation` | * |
| `crm_import_errors` | `tenant_isolation` | * |
| `crm_import_files` | `tenant_isolation` | * |
| `crm_import_jobs` | `tenant_isolation` | * |
| `crm_integration_command_artifacts` | `tenant_isolation` | * |
| `crm_integration_command_executions` | `tenant_isolation` | * |
| `crm_integration_decisions` | `tenant_isolation` | * |
| `crm_integration_outbox` | `tenant_isolation` | * |
| `crm_integration_requests` | `tenant_isolation` | * |
| `crm_leads` | `tenant_isolation` | * |
| `crm_next_best_actions` | `tenant_isolation` | * |
| `crm_notes` | `tenant_isolation` | * |
| `crm_opportunities` | `tenant_isolation` | * |
| `crm_opportunity_stage_history` | `tenant_isolation` | * |
| `crm_ownership_history` | `tenant_isolation` | * |
| `crm_party_address_history` | `tenant_isolation` | * |
| `crm_party_addresses` | `tenant_isolation` | * |
| `crm_phone_numbers` | `tenant_isolation` | * |
| `crm_pipeline_stages` | `tenant_isolation` | * |
| `crm_pipelines` | `tenant_isolation` | * |
| `crm_queue_memberships` | `tenant_isolation` | * |
| `crm_queues` | `tenant_isolation` | * |
| `crm_reports` | `tenant_isolation` | * |
| `crm_sales_teams` | `tenant_isolation` | * |
| `crm_scoring_models` | `tenant_isolation` | * |
| `crm_segment_memberships` | `tenant_isolation` | * |
| `crm_tag_assignments` | `tenant_isolation` | * |
| `crm_tags` | `tenant_isolation` | * |
| `crm_tasks` | `tenant_isolation` | * |
| `crm_team_memberships` | `tenant_isolation` | * |
| `crm_territories` | `tenant_isolation` | * |
| `crm_territory_assignments` | `tenant_isolation` | * |
| `crm_territory_closure` | `tenant_isolation` | * |
| `crm_transfer_requests` | `tenant_isolation` | * |
| `crm_transfer_steps` | `tenant_isolation` | * |
| `crm_transfers` | `tenant_isolation` | * |
| `partner_delegation_capabilities` | `partner_delegation_capabilities_platform_all` | * |
| `partner_delegation_grants` | `partner_delegation_grants_platform_insert` | a |
| `partner_delegation_grants` | `partner_delegation_grants_platform_select` | r |
| `partner_delegation_grants` | `partner_delegation_grants_platform_update` | w |
| `partner_membership_capabilities` | `pmc_platform_insert` | a |
| `partner_membership_capabilities` | `pmc_platform_select` | r |
| `partner_membership_capabilities` | `pmc_platform_update` | w |
| `partner_memberships` | `partner_memberships_platform_select` | r |
| `partner_tenant_bindings` | `partner_tenant_bindings_platform_insert` | a |
| `partner_tenant_bindings` | `partner_tenant_bindings_platform_select` | r |
| `partner_tenant_bindings` | `partner_tenant_bindings_platform_update` | w |

## 5. Out-of-band production objects (owner reconciliation list, out of migration scope)

Production contains objects the chain does not define (created outside Flyway). This migration cannot own their lifecycle; they are enumerated for the owner:

- `public.service_callback_replay` — RLS-less production-only table (grant posture verified default-deny this scan).
- 24 additional RLS-protected production-only tables (created out-of-band; carry some of the §4 fail-open policies). Deploying the chain will NOT alter them; a dedicated reconciliation migration is required after this task if the owner wants them unified.

## 6. Role / function boundary verification (both evidence sources)

| Check | Chain | Production |
|---|---|---|
| Roles with BYPASSRLS | none (only `postgres` superuser) | none (only `postgres` superuser) |
| SECURITY DEFINER functions | 11 (10 HR migration helpers, no triggers; `sync_login_tenant_index` trigger) | 11 (same) |
| search_path pinned (non-extension) | 41/41 | 41/41 |
| anon/authenticated EXECUTE surface | 0 functions | 0 functions |
| `sync_login_tenant_index` anon/auth access | denied | denied |
| `sync_login_tenant_index` service_role EXECUTE | **missing (chain regression)** | granted |
| schema `public` CREATE to PUBLIC/anon/auth | none | none |

The `service_role` EXECUTE regression is re-asserted (guarded, role-existence-conditioned) by this remediation, matching production parity and the audited `candidate_function_hardening.sql` G3 guard.
