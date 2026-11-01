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

---

# Appendix — Execution & Acceptance Matrix Update (2026-10-26, SNAD-SEC-RLS-EXEC)

Execution session performed after this audit was written, on the same branch
(`security/rls-forensic-remediation`). Production was not touched: no DDL, no DML,
no connection to the production database was made during this session.

## What was executed

1. **Migration staged:** `apps/sanad-platform/src/main/resources/db/migration/V20261026_4__rls_forensic_function_search_path_and_acl_hardening.sql`
   — pins `search_path` for the 16 audited functions, revokes EXECUTE on
   `public.sync_login_tenant_index()` from `PUBLIC`/`anon`/`authenticated`
   (guarded: only for roles that exist), re-asserts the `service_role` grant,
   and enforces fail-closed guards: G0 (all 17 audited functions must exist),
   G1 (schema `public` must not grant CREATE to PUBLIC/anon/authenticated),
   G4 (post-apply verification: 17/17 pinned, function remains SECURITY DEFINER,
   trigger `users_sync_login_tenant_index` intact and enabled, client roles
   denied). Naming note: `V20261026_1..3` were never persisted on any branch and
   never applied to any database; this is the first migration of the RLS
   forensic task in the chain (numbering follows the remediation owner's
   instruction).
2. **Chain-state discovery:** `V20261020_1__supabase_data_api_default_deny.sql`
   (already in the chain) already revokes PUBLIC EXECUTE and pins
   `search_path` on all non-extension public functions at its run time. The
   production snapshot therefore predates `V20261020_1`: production has not
   received it. Migration `V20261026_4` re-asserts the remediation explicitly
   for the audited set with verification guards (order-independent,
   audit-traceable per finding ID).
3. **Disposable test environment (free, zero-cost, no Docker, no root):**
   portable PostgreSQL 17.6 on localhost:5432 (databases `sanad` and
   `sanad_parity`), Flyway CLI 11.7.2 (the version pinned by the repository's
   Maven build), roles `sanad` (LOGIN, NOSUPERUSER, NOBYPASSRLS) and
   `anon`/`authenticated`/`service_role` (NOLOGIN). The full 212-migration
   chain (V2 → V20261021.1 + repeatable) applied successfully in both
   databases — the repository's real DDL, not stubs.
4. **Scenario A (chain integrity, DB `sanad`):** chain applied (exit 0) →
   migration `V20261026_4` applied (exit 0; all guards passed) → `flyway
   validate` 213/213 (exit 0) → second `migrate` run reported "up to date, no
   migration necessary" (exit 0) → `flyway_schema_history` records
   `20261026.4 | success = t`.
5. **Scenario B (production-parity unification, DB `sanad_parity`):** the
   production divergence was reproduced exactly (16/16 audited functions
   unpinned; `sync_login_tenant_index()` `anon_execute = t`,
   `authenticated_execute = t`), then `V20261026_4` was applied on top —
   findings cleared (0 unpinned; `anon_execute = f`,
   `authenticated_execute = f`). This demonstrates the production → chain
   unification that the migration performs when deployment is authorized.
6. **Verification suite (13 checks, run against both databases):** 13/13 PASS
   on both. See the final execution report for the full command/exit-code
   evidence: `docs/security/RLS_EXECUTION_AND_VERIFICATION_REPORT_2026-10-26.md`.

## Acceptance matrix — updated status

| ID | Test | Expected result | Status after execution session |
|---|---|---|---|
| RLS-01 | No tenant context on a tenant-scoped SELECT | No tenant rows visible | NOT RUN — policy remediation is out of scope of `V20261026_4`; deferred per the policy-phase guidance above |
| RLS-02 | Tenant A reads its own rows | Only A rows visible | NOT RUN — same scope deferral |
| RLS-03 | Tenant A attempts to read/update/delete Tenant B rows | Zero rows / permission denied | NOT RUN — same scope deferral |
| RLS-04 | Missing, empty, malformed tenant context | Deny by default | NOT RUN — same scope deferral (81 candidate policies unchanged) |
| RLS-05 | UPDATE changes tenant_id to B | Rejected by WITH CHECK | NOT RUN — same scope deferral |
| RPC-01 | anon executes `sync_login_tenant_index()` | Permission denied | PASS (fixture, both scenarios) — `ERROR: permission denied for function sync_login_tenant_index` (exit 1) |
| RPC-02 | authenticated executes RPC | Permission denied | PASS (fixture, both scenarios) — same error (exit 1) |
| RPC-03 | Trigger INSERT/UPDATE/DELETE | Index rows remain correct | PASS (fixture, both scenarios) — INSERT → 2 index rows; UPDATE email → old gone/new present/username kept; DELETE → index empty; `service_role` EXECUTE retained; SECURITY DEFINER preserved |
| RLS-06 | All public tables classified and reviewed | No unexplained RLS-disabled table | NOT RUN — 49-table classification not performed this session |
| FN-01 | 16 functions have reviewed pinned search_path | Advisor finding cleared | PASS in fixture (both scenarios): 16/16 unpinned at prod-parity baseline → 0 after `V20261026_4`; 17/17 verified pinned post-apply. Production advisor re-check NOT PERFORMED (see PROD-RESCAN below) |
| REG-01 | Full Maven/PostgreSQL Direct suite | Green, no new failures | NOT RUN — Maven not available in the execution sandbox; trigger-level regressions (users sync, partners updated_at + authorization event, partner memberships audit, HR append-only guard probe) all PASSED |

Additional session items:

| ID | Item | Status |
|---|---|---|
| PROD-RESCAN | Read-only production catalog rescan via Management API | BLOCKED — Supabase access token rejected (HTTP 401 Invalid access token); production state is therefore evidenced from the original 2026-10-26 snapshot in this document |
| ACL-EDGE | anon SELECT on `login_tenant_index` (default-deny chain) | PASS (fixture) — `permission denied for table login_tenant_index` |
| TRG-PARTNER | `trg_partners_updated_at` + `trg_partners_authorization_change_event` + `trg_partner_memberships_audit` real-path | PASS (fixture) — PARTNER_SUSPENDED and PARTNER_MEMBERSHIP_SUSPENDED audit rows written; updated_at advanced |
| TRG-HR-GUARD | `hr_t7_offer_versions_append_only_guard` still immutable | PASS (fixture, function-level probe) — `HRM_OFFER_VERSION_IMMUTABLE` raised on UPDATE |

## Remaining before the policy phase and deployment

- Owner decisions per the policy-phase guidance in this document (RLS-01..05
  remain open by design — do not mass-edit 81 policies without the context
  tests described above).
- 49-table classification (RLS-06) and the full Maven/PostgreSQL Direct suite
  (REG-01) before any merge request.
- Production read-only rescan and Supabase Security Advisor re-check once a
  valid access token is provisioned (PROD-RESCAN).
- All five plaintext-exposed platform tokens must be rotated before any
  deployment authorization (governance rule: chat exposure = compromised).
