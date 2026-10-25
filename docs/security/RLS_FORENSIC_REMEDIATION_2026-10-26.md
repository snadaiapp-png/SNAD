# RLS Forensic Audit — 2026-10-26

## Scope and safety

- Target inspected read-only: Supabase project `snad-prod-fresh` (`tkbrvupemreqabwzdpyq`).
- Repository remediation branch: `security/rls-forensic-remediation`.
- No production DDL/DML was executed. No row contents were queried; this audit reads PostgreSQL catalogs and advisor metadata only.
- No Supabase branch/project was created and no paid resource was started.
- This report is evidence from a live catalog snapshot, not a certification that application paths are exploitable.

## Confirmed catalog findings

| Check | Observed | Security interpretation |
|---|---:|---|
| Tables in `public` | 319 | Schema-wide inventory baseline |
| Tables without RLS | 49 | Database row-level enforcement is absent for these tables; direct anon/authenticated SELECT was not granted on these 49 tables in the inspected snapshot |
| Tables with RLS enabled but not FORCE RLS | 165 | Table owners can bypass RLS unless ownership/role design prevents it; not automatically an exploit for non-owner application roles |
| Policies in `public` | 283 | Policy semantics require table-by-table validation |
| Policies with tenant context NULL-allow patterns | 81 | Fail-open candidate patterns; inspect exact predicates and application context before changing them |
| Functions with unset/mutable `search_path` | 16 | Supabase Security Advisor finding; pin a safe path after reviewing name resolution |
| `sync_login_tenant_index()` | SECURITY DEFINER, trigger function, EXECUTE inherited from PUBLIC | Advisor reports anon/authenticated callable via RPC. Revoke public client execution; preserve trigger behavior and verify it after change |
| Public SELECT on the 49 RLS-disabled tables | 0 | No direct public SELECT grant was found in this snapshot; do not claim confirmed public data exposure based only on missing RLS |

### 49 tables without RLS

`access_capabilities`, `application_iam_contracts`, `applications`, `billing_invoices`, `bp_analytics_snapshots`, `bp_inventory_balances`, `bp_inventory_movements`, `bp_ledger_entries`, `bp_payment_events`, `bp_process_runs`, `bp_process_steps`, `bp_workflow_approvals`, `country_currencies`, `flyway_schema_history`, `hr_compliance_rules`, `hr_country_packs`, `login_tenant_index`, `module_capabilities`, `modules`, `organization_memberships`, `organizations`, `partners`, `password_reset_tokens`, `plan_module_entitlements`, `plan_versions`, `platform_audit_logs`, `platform_countries`, `prices`, `product_entitlements`, `products`, `protected_system_roles`, `provisioning_job_steps`, `provisioning_jobs`, `refresh_tokens`, `role_capabilities`, `roles`, `saas_plan_entitlements`, `saas_plans`, `service_callback_replay`, `subscription_change_events`, `subscription_commands`, `subscription_items`, `system_services`, `tenant_domains`, `tenant_quota`, `tenant_subscriptions`, `tenants`, `usage_metrics`, `user_role_assignments`.

The inventory includes a mix of tenant data, credentials/session artifacts, billing, lookup/reference data, and internal migration/operations tables. Do not apply one generic policy to all 49: classify each table as tenant-owned, platform-only, immutable reference data, credential material, or migration/operations metadata, then select the correct policy and grants.

## Priority 0 — function execution boundary

Catalog inspection showed:

- `public.sync_login_tenant_index()` is `RETURNS trigger`, `SECURITY DEFINER`, with `search_path = pg_catalog, public`.
- ACL includes `EXECUTE` for `PUBLIC`; Supabase's advisor confirms anon and authenticated can reach the RPC endpoint.
- Existing explicit `service_role` EXECUTE grant was observed.

Candidate change for a reviewed migration (not applied):

```sql
REVOKE EXECUTE ON FUNCTION public.sync_login_tenant_index()
  FROM PUBLIC, anon, authenticated;
```

Keep the existing `service_role` grant unless code review proves it unnecessary. Trigger behavior must be regression-tested with INSERT, UPDATE (email/username changes), and DELETE against a disposable database. Verify the PostgREST RPC is no longer executable by anon/authenticated. Do not replace this trigger function with SECURITY INVOKER without proving its required table privileges.

## Priority 0 — tenant context must fail closed

81 policies contain patterns involving `current_setting('app.tenant_id', true)` and NULL-allowing predicates. A representative predicate is:

```sql
current_setting('app.tenant_id', true) IS NULL
OR tenant_id::text = current_setting('app.tenant_id', true)
```

This means a missing tenant context can make the predicate true. It is a high-risk isolation defect candidate, but the correct remediation depends on how the application establishes tenant context and how platform/partner access is represented. Do not blindly replace all policies: `authorization_change_events` and partner/platform policies have distinct exceptional branches. First add tests proving that tenant context is transaction-local, cannot be supplied by an untrusted client without authorization, and is set for every tenant-scoped database operation. Then make missing/empty/invalid context deny by default and preserve explicit platform/partner paths only where verified.

## Priority 1 — missing RLS

No `anon`, `authenticated`, or `PUBLIC` SELECT privilege was found for the 49 RLS-disabled tables in the inspected snapshot. This reduces evidence of immediate direct reads but does not substitute for RLS. Review all privileges for SELECT/INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER, table ownership, views, SECURITY DEFINER functions, and indirect access paths before enabling RLS. Adding RLS without policies can break application paths; adding permissive generic policies can create exposure.

## Priority 1 — mutable function search_path

Security Advisor reported 16 functions without a pinned `search_path`:

- `hr_t7_offer_versions_append_only_guard()`
- `trg_partners_updated_at()`
- `trg_partners_authorization_change_event()`
- `trg_partner_memberships_updated_at()`
- `enforce_last_partner_admin()`
- `trg_partner_memberships_audit()`
- `enforce_partner_tenant_binding_lifecycle()`
- `trg_partner_tenant_bindings_audit()`
- `trg_partner_delegation_capabilities_updated_at()`
- `enforce_partner_delegation_grant_lifecycle()`
- `trg_partner_delegation_grants_audit()`
- `trg_partner_delegation_capabilities_audit()`
- `enforce_partner_membership_capability_lifecycle()`
- `enforce_final_partner_recovery_capability()`
- `enforce_partner_membership_recovery_survival()`
- `trg_partner_membership_capabilities_audit()`

All were observed as non-SECURITY-DEFINER in the current catalog snapshot. Review function bodies and object qualification before pinning `search_path`; run trigger and lifecycle regression tests afterward.

## Acceptance matrix

| ID | Test | Expected result | Current evidence/status |
|---|---|---|---|
| RLS-01 | No tenant context on a tenant-scoped SELECT | No tenant rows visible | Not executed; requires isolated PostgreSQL |
| RLS-02 | Tenant A reads its own rows | Only A rows visible | Not executed |
| RLS-03 | Tenant A attempts to read/update/delete Tenant B rows | Zero rows / permission denied | Not executed |
| RLS-04 | Missing, empty, malformed tenant context | Deny by default | Policy catalog shows 81 NULL-allow candidates; failing behavior not yet runtime-tested |
| RLS-05 | UPDATE changes tenant_id to B | Rejected by WITH CHECK | Not executed table-by-table |
| RPC-01 | anon executes `sync_login_tenant_index()` | Permission denied | Current ACL/advisor indicates exposed; remediation not applied |
| RPC-02 | authenticated executes RPC | Permission denied | Current ACL/advisor indicates exposed; remediation not applied |
| RPC-03 | Trigger INSERT/UPDATE/DELETE | Index rows remain correct | Not executed |
| RLS-06 | All public tables classified and reviewed | No unexplained RLS-disabled table | 49 tables currently RLS-disabled |
| FN-01 | 16 functions have reviewed pinned search_path | Advisor finding cleared | 16 findings currently present |
| REG-01 | Full Maven/PostgreSQL Direct suite | Green, no new failures | Not run in this session; no connected local runner and no CI run was triggered |

## Execution gates

1. Apply only catalog-derived, reviewed changes on this branch.
2. Use disposable PostgreSQL with the repository's host-native PostgreSQL CI contract; never use Docker fallback.
3. Test Tenant A/B, missing context, INSERT/UPDATE/DELETE, function ACLs, trigger behavior, and migrations.
4. Re-run security advisors and the full relevant Maven/PostgreSQL Direct tests.
5. Keep production unchanged, payments off, no merge, no deploy until explicit approval and all acceptance gates pass.
