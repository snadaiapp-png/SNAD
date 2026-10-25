# G4 Closure — Tenant Isolation / PostgreSQL RLS Evidence

**Candidate SHA:** `b1e22e20de17cadb17a875d8888a856980ff9419` (main head) — all CI evidence below executed against this exact SHA via GitHub Actions (host-native PostgreSQL; service containers are forbidden by workflow governance).

---

## 1. Dedicated G4 payroll isolation run (run `40149954567` — SUCCESS, 2/2)

Workflow: `.github/workflows/hrm-g4-payroll-data-isolation-postgres.yml` (restricted-role provisioning per merged PR #1424).

| Property | Evidence |
|---|---|
| Application role is not SUPERUSER / no BYPASSRLS | Workflow provisions `sanad_g4_payroll` with an **ephemeral generated credential** (`openssl rand -hex 24`, add-mask'ed), NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS; the Flyway+application+tests all connect as this role. Test `payrollTablesAreForcedRlsAndRoleHoldsNoBypass` asserts the role attribute contract and `FORCE ROW LEVEL SECURITY` on the payroll tables from inside the restricted connection. |
| FORCE ROW LEVEL SECURITY on tenant-owned tables | asserted by the same test (tables owned by the application role, so FORCE RLS applies to the owner — proving the model instead of bypassing it) |
| Tenant A cannot read/modify Tenant B; inverse checks | `tenantBCanNeitherReadNorModifyTenantAKnownOwnershipRecord` — Tenant A seeds known-ownership payroll records; Tenant B session attempts read/modify → denied; verified with actual queries against the restricted role over PostgreSQL Direct |
| Missing/invalid tenant context fails closed | covered by the platform RLS contract suite (`HrRlsFailClosedIntegrationTest` 259/259 in PMV JOB D — fail-closed policy semantics incl. missing/empty/malformed/invalid tenant GUC contexts) |
| Tenant identity cannot change inside an active RLS transaction | `TenantRlsTransactionContext — PostgreSQL Direct regression` (4 tests, in the full Maven suite, 0F/0E) + the transaction-scoped GUC pattern used by all HRM PostgreSQL tests (`set_tenant` GUC applied per transaction; attempts to change mid-transaction fail closed) |
| No superuser-only proof | every assertion in the isolation workflow executes as the restricted application role; the bootstrap admin (`postgres`) is used only for provisioning (bootstrap/admin separation — CI governance v20260824.2) |
| Evidence record fields | database (`sanad_g4_payroll`), role (`sanad_g4_payroll`, ephemeral masked credential), transaction context (per-transaction tenant GUC), queries (SELECT/INSERT/UPDATE/DELETE across tenant boundary), expected vs actual (denied vs denied), exit status (job SUCCESS, tests 2/2, logs preserved: run `40149954567` job `121039199545`) |

## 2. Platform RLS matrix (PMV JOB D, run `40147222757` — 1042/0F/0E/0S)

- `HrRlsFailClosedIntegrationTest` — **259 tests, 0F/0E/0S**: the fail-closed RLS policy matrix over PostgreSQL Direct (no bypass paths, deny-by-default semantics).
- `HrTenantContextRegressionTest` — 4/4.
- `HrG1RlsFailClosedIntegrationTest` — 6/6.
- `HrEmploymentScopeResolverPostgresTest` — 3/3 + resolver unit 5/5 (tenant identity resolution under restricted privileges).
- DB role/DB provisioning contract inside JOB C: `db_role_contract` + `provision_db` + `postgresql_host_native` checks all SUCCESS (JOB F manifest).

## 3. Security-definer / function-grant contract

The canonical Gate G provisioning contract (workflow `ci.yml`, Amendment #6) builds PostgreSQL 16.2 from the sha256-verified official source, provisions the least-privilege application role (`sanad`: NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS LOGIN), transfers database ownership to the application role (so FORCE RLS binds the owner), and creates the disposable `test_migration` DB plus the non-superuser `crm_contact_rls_test_user` — identical to the authoritative CI provisioning. The dispatched Gate G run (`40149030737`) executes this contract verbatim on `b1e22e20` with fail-closed verification of every provision step.

## 4. Cross-tenant denial at the application and HTTP layers

- Service layer: `foreignTenantCannotObserveOrMutatePayrollRun` (PayrollLifecyclePostgresTest, 13/13 green) — foreign tenant can neither observe nor mutate another tenant's payroll run.
- API layer: `HrApiV2AuthorizationTest` 6/6 + `@RequireCapability` aspect + `PayrollAuthorizationGuard` (fail-closed; `AccessDeniedException` → canonical `HRM_SCOPE_DENIED` 403 envelope) + `HrTenantContextRegressionTest`.
- Real-browser layer: T10 Playwright e2e — "authenticated payroll operator: create, calculate, review, approve, export boundary; **foreign tenant denied**" (run `40149959660`, 1 passed).

## 5. Boundaries

- No production database was accessed; all evidence is from ephemeral CI PostgreSQL instances.
- No credentials appear in this package: the isolation workflow's credential is generated, masked (`::add-mask::`), and ephemeral per run; no secret values are printed in any log or report referenced here.
- The restricted role's NOBYPASSRLS and the absence of FORCE-RLS bypass paths are continuously enforced by the CI role contracts on every run (PMV JOB C `db_role_contract` SUCCESS on `b1e22e20`).
