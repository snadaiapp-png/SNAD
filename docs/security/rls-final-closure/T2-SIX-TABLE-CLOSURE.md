# T2 SIX-TABLE CLOSURE — D6 Batch 2 (Issue #1462)

**Branch:** `fix/d6-t2-batch2-rls-closure` · **Base:** main@`5e854ef4426d7ff500675be69f15ff3358db917a` · **Generated:** 2026-10-31 (Asia/Riyadh session)
**Environment:** isolated PostgreSQL 17.11 (user-space), full 245+1 Flyway chain applied as restricted role `sanad` (NOSUPERUSER, NOBYPASSRLS — verified). CI uses PostgreSQL 16; version difference documented in POSTGRESQL-DIRECT-EVIDENCE.md.

## Per-table disposition (mandate §5.10)

| # | Table | Disposition | Implementation |
|---|---|---|---|
| 1 | `refresh_tokens` | **COVERED BY D6 BATCH 1 — PR #1458 (open, not merged).** Not duplicated here to avoid conflicting RLS definitions. RED on this branch until #1458 merges (reproduced cross-tenant READ of tenant A row as B: `count=1` under B GUC). | PR #1458: `V20261028_2` + `AuthTokenTenantScopeResolver` + `AuthService` pre-auth discovery |
| 2 | `password_reset_tokens` | Same as #1 | PR #1458 |
| 3 | `login_tenant_index` | **ACL closure — no tenant RLS by documented design** (pre-auth discovery contract, V20261011_4). `V20261031_1`: REVOKE ALL FROM PUBLIC, explicit minimal grants to `sanad` (SELECT/INSERT/DELETE — trigger+resolver needs), UPDATE revoked, contract COMMENT. **Residual (documented, follow-up recommended):** `identifier` stored in plaintext; hashing to the `auth_token_tenant_index` pattern is the recommended next slice (needs Java + trigger + backfill — deliberately not bundled into this P0 security batch). | `V20261031_1` §2 |
| 4 | `provisioning_jobs` | **ENABLE + FORCE RLS, fail-closed TO PUBLIC policy.** Access paths verified first: `ProvisioningJobRunner` is request-scoped (`LifecycleController.provision/retryJob`) → JWT-bound tenant GUC. No background poller exists. | `V20261031_1` §1 |
| 5 | `provisioning_job_steps` | **ENABLE + FORCE RLS with subquery policy through the parent job** (table has no `tenant_id` column — verified; tenant flows via `job_id`). Subquery evaluates under the same GUC, so `provisioning_jobs`' own FORCE RLS applies inside it. WITH CHECK blocks cross-tenant step insertion (42501 reproduced in evidence). | `V20261031_1` §1 |
| 6 | `subscription_commands` | **ENABLE + FORCE RLS, fail-closed TO PUBLIC policy.** Writers: user lifecycle commands (JWT context) + background `TrialExpirationService` — the latter was **silently broken already by #1460** (its cross-tenant scan of FORCE-RLS `tenant_subscriptions` returned zero rows) and is repaired in this change set using the established dunning pattern (`BillingStateService#runDunningCycleOnce`): enumerate tenants from the non-RLS platform directory, per-tenant transaction-local GUC via `TenantRlsTransactionContext`. Control-plane note (§5 warning honored): no uniform policy was forced onto executive read paths — they already operate under applied tenant contexts (pattern verified in `BillingStateService`, `AuthController`, workflow workers); CI PostgreSQL Direct + acceptance suites are the gate for any missed path. | `V20261031_1` §1 + `TrialExpirationService` |

## Additional verified-gap fixes in this migration (Article 6 audit output)

Five CRM tables shipped with the **legacy permissive-when-unset policy shape and no FORCE** (owner bypass + everything-visible-when-GUC-unset): `crm_next_best_actions`, `crm_assignment_rules`, `crm_queue_memberships`, `crm_transfer_steps`, `crm_sales_teams`. All five now: FORCE ROW LEVEL SECURITY + fail-closed `NULLIF(current_setting(...))` policy (V20261021_1 language). Catalog self-verification inside the migration aborts Flyway if ENABLE/FORCE/policy-shape drifts.

## Evidence (this directory)

- `t2_red_probe.json` — pre-fix RED: cross-tenant reads succeeded on `provisioning_jobs`, `provisioning_job_steps`, `subscription_commands`; catalog RED for all six T2 tables (rls=false, force=false, 0 policies).
- `pgdirect_t2_green.json` — post-fix matrix: 42 checks; catalog ENABLE+FORCE PASS ×8; role NOBYPASSRLS PASS; A_READ_A / B_READ_A=0 / B_UPDATE_DELETE_A=0-rows / A_INSERT_B_CTX=42501 / NO_TENANT_CONTEXT=0 / INVALID_TENANT_CONTEXT=0 across tables (rows: `refresh_tokens`/`password_reset_tokens` entries are RED-EXPECTED — batch 1 scope, PR #1458).
- `migration_apply.json` — full chain apply log (245 + V20261031_1, role `sanad`).
- Definitive table-level matrix (psql transcript, real rows): A_ctx sees own rows (1/1/1/1/1/2), B_ctx sees 0/0/0/0/0/own-only, NO_ctx sees 0 everywhere except by-design pre-auth indexes.

## Known suite-coverage note (honest limitation)

8 CRM tables (`crm_assignment_rule_counters`, `crm_assignment_rules`, `crm_integration_command_artifacts`, `crm_integration_command_executions`, `crm_integration_outbox`, `crm_queue_memberships`, `crm_sales_teams`, `crm_transfer_steps`) are flagged **INCONCLUSIVE** by `tests/security/rls_postgres_direct_suite.py` because its synthetic B-tenant rows hit **tenant-congruent unique keys** (`UNIQUE (tenant_id, …)` — verified constraint definitions) — a suite generator artifact, not a leak: cross-tenant reads were blocked in targeted probes. Recommendation: teach the suite generator tenant-scoped unique values (follow-up, no schema change).
