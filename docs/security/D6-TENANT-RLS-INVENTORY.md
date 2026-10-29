# D6 — Tenant RLS Inventory & Gap Decisions (SANAD-G1)

**Task:** SANAD-SEC-G1-D6-CLOSURE · **Base:** `main@71e4371f` (D1, PR #1457) · **Fix:** `V20261029_1__d6_identity_plane_fail_closed_tenant_rls.sql`
**Evidence:** live-catalog inventory extracted after a clean Flyway replay of all 241 migrations on PostgreSQL 17.4, running as the least-privilege `sanad` role (NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS), per the CI PostgreSQL Direct contract (`.github/workflows/ci.yml:179-226`).

## Live-catalog totals (at base)

| Metric | Count |
|---|---|
| Tables in `public` (r/p) | 319 |
| RLS enabled | 270 |
| RLS enabled **and** FORCED | 114 |
| Tables with `tenant_id` | 290 |
| `tenant_id` **without any RLS** (gap class L1) | 25 |
| RLS enabled but NOT forced (gap class L2) | 156 |

The full machine-readable inventory is retained in the execution evidence archive (`d6-inventory.json`, `d6-gap-detail.json`).

## L1 — tables that had `tenant_id` and NO RLS (the D6 gap)

### Fixed in this change (`V20261029_1`) — ENABLE + FORCE + fail-closed `NULLIF(current_setting('app.tenant_id', true), '')` policies `TO sanad` and `TO postgres` (V20261019_1 pattern)

| Table | Plane | Access-path evidence |
|---|---|---|
| `roles` | Identity/RBAC | All repository queries are `findByTenantId*` (`RoleRepository.java:13-27`); login bootstrap applies the tenant GUC explicitly (`AuthService.java:213,321`); provisioning applies the GUC (`RoleTemplateProvisioner.provision`) |
| `role_capabilities` | Identity/RBAC | Tenant-scoped queries (`RoleCapabilityRepository.java:16+`); capability evaluation runs post-auth under the connection-handler GUC |
| `user_role_assignments` | Identity/RBAC | `UserRoleGrantService.java:127` applies the GUC; grant admin is `@RequireCapability`-gated |
| `organizations` | Org structure | Tenant-scoped repository; login bootstrap resolves organizations after user authentication (tenant known) |
| `organization_memberships` | Org structure | `OrganizationMembershipRepository` is fully tenant-scoped; used by login bootstrap and `@RequireCapability` APIs |

**Regression proof:** `D6IdentityPlaneRlsPostgresTest` (A/B isolation for SELECT/INSERT/UPDATE/DELETE, WITH CHECK cross-tenant denial, missing-GUC fail-closed, FORCE+ownership contract, BYPASSRLS assertion, pooled-context reset) — plus the full identity-plane regression suite (569 tests) green after aligning the affected test fixtures and two production read paths to the sanctioned context pattern (`OrganizationService`, `OrganizationMembershipService`: `SUPPORTS`→`REQUIRED`, mirroring the documented `UserService` precedent for `public.users`).

### Documented exceptions (NOT protected by tenant RLS — justified + compensating controls)

| Table | Decision | Justification & compensating controls |
|---|---|---|
| `login_tenant_index` | EXCEPTED (by design) | Deliberately non-RLS pre-auth index created by `V20261011_4`; contains only tenant UUIDs per identifier (no tenant business data), maintained by a SECURITY DEFINER trigger; powers `g1_resolve_login_tenants` with `EXECUTE` revoked from `PUBLIC`. Pre-auth tenant resolution cannot require a tenant context by definition. |
| `refresh_tokens` | EXCEPTED (pre-auth hash plane) + follow-up | Core lookup is `findByTokenHashForUpdate(hash)` — pre-auth by 256-bit opaque token hash only; enabling fail-closed tenant RLS would break refresh. Compensating controls: SHA-256-at-rest hashes, rotation with replay detection (`AuthService.refresh`, lines 366-451), server-side `session_version` revocation, rate limiting. NO code path filters this table by client-supplied `tenant_id` (verified by repository inspection — the only tenant-scoped queries are server-side revocations executed with the token's own resolved tenant). Follow-up: introduce a SECURITY DEFINER `NOLOGIN` pre-auth resolver function (the `V20261011_4` pattern) so the by-hash lookup itself can live behind a controlled surface. |
| `password_reset_tokens` | EXCEPTED (pre-auth hash plane) + follow-up | Same contract: `findByTokenHashForUpdate(hash)` is pre-auth; single-use 32-byte tokens, 30-min TTL, IP recorded, generic anti-enumeration responses. The tenant-scoped `revokeAllActive` runs with the resolved user's tenant. Follow-up: same pre-auth definer-function pattern as `refresh_tokens`. |
| `tenant_domains` | EXCEPTED (pre-auth routing plane) + follow-up | Used by `HostRoutingService` for subdomain → tenant resolution BEFORE authentication (the login subdomain path and tenant site routing). Contains domain↔tenant mappings only. Follow-up: narrow read model or definer resolver; tenant-scoped writes are admin-gated. |
| `tenant_subscriptions`, `subscription_items`, `subscription_commands`, `subscription_change_events`, `billing_invoices`, `provisioning_jobs`, `tenant_quota` | EXCEPTED (control-plane/commercial plane) + follow-up | Legitimately read cross-tenant by the Executive Subscription Control Plane (`/executive/subscriptions*` surfaces, `@RequireCapability`-gated) and by background schedulers (`TrialExpirationService`, dunning cycle) that operate across tenants. Plain fail-closed tenant RLS would zero the executive read models and silently disable the trial-expiry/dunning workers. Compensating controls: executive APIs are capability-gated (`EXECUTIVE_*`), no client-supplied tenant filters on these read models, `BillingProviderModeGuard` keeps payments OFF. Follow-up: introduce a dedicated control-plane GUC plane (e.g. `app.control_plane` bound to the control-plane tenant in `JwtAuthenticationFilter`/`ControlPlaneAccessGuard`) and split tenant-plane policies from control-plane policies before enabling RLS here. |
| `bp_process_runs`, `bp_process_steps`, `bp_inventory_balances`, `bp_inventory_movements`, `bp_ledger_entries`, `bp_payment_events`, `bp_workflow_approvals`, `bp_analytics_snapshots` | EXCEPTED (business-process runtime) + follow-up | Written/read by the business-process engine's service-driven executions (`BusinessProcessService`); runtime steps execute under per-run tenant context but the engine's orchestration queries run cross-tenant by design (process scheduling and analytics snapshots). No direct client-supplied tenant filter path (service-internal only). Follow-up: apply tenant RLS together with the control-plane GUC plane once orchestration reads are scoped. |
| `service_callback_replay` | EXCEPTED (callback replay guard) + follow-up | Replay-protection store for inbound service callbacks (`CallbackReplayStore`), consulted before tenant identity can be established from the callback payload. Keyed by provider callback nonce; no tenant data at rest beyond the callback's tenant reference. Follow-up: evaluate a per-tenant policy variant once callback authentication is established upstream. |

**Resulting L1 posture:** 5/25 gap tables fixed and tested; 20/25 documented exceptions with explicit security justification and compensating controls; **zero undecided tenant-data tables remain.**

## L2 — RLS enabled but NOT forced (156 tables)

These tables have ENABLE + policies (fail-closed generation for CRM/HR/commerce/etc.) but not FORCE. Exposure depends on the ownership/runtime identity pairing:

- Local/CI: tables are owned by `sanad` and the runtime is `sanad` (owner == runtime) → without FORCE, policies do not bind the owner. CI compensates by testing sensitive tables through dedicated non-owner roles (`crm_contact_rls_test_user`) and by the FORCE waves below.
- Production (per `V20261019_1:3-14`): owner and runtime are `postgres` → unforced policies do not bind `postgres`.

The repo has already force-enabled the highest-risk tables (`public.users` V20261011_3+V20261019_1, 9 post-baseline CRM tables V20261021_1, role_template_bindings V20260820_6, and the 5 identity-plane tables in this change). **Follow-up (next D6 wave):** a catalog-driven forward-only migration that mirrors each tenant-GUC policy `TO postgres` and then `FORCE`s tenant-scoped tables whose policies are tenant-GUC based, following the certified V20261019_1/V20261021_1 sequence, with the production verification gates (`scripts/production/verify-flyway.sh`, pg-acceptance suite) as the promotion criteria. This is intentionally NOT bundled here: it is a wide-blast-radius change that requires its own branch, its own full PostgreSQL Direct run and its own production re-certification.

## Verification summary for this change

| Suite | Result |
|---|---|
| `D6IdentityPlaneRlsPostgresTest` (new) | 5/5 PASS |
| security + access + organization + user packages | 569/569 PASS |
| subscription.rbac + platformiam | 144 run, 0 failures (21 environment-skips unchanged) |
| AuthApi / TokenRevocation / AuthBootstrap / ApiRegression / Canonicalization / ProvisionerTenantAdmin | all green after fixture alignment |
| Clean-baseline comparison | 59/59 PASS on `main` without V20261029_1 — confirms every pre-fix failure was caused by the new RLS and every fixture change aligns with (not weakens) the security contract |
