# RLS Policy Phase — Source Review and Execution Gates
Date: 2026-10-26
Task: SNAD-SEC-RLS-EXEC-2026-10-26
Branch: security/rls-forensic-remediation
Status: SOURCE REVIEW COMPLETE / POLICY MIGRATION NOT AUTHORIZED OR EXECUTED
Production: unchanged; NO MERGE / NO DEPLOY

## Purpose

This document records the source-level findings required before implementing the remaining RLS policy work. It is deliberately not a policy migration and does not claim runtime verification. The 2026-10-26 catalog audit identified 81 candidate tenant-context NULL-allow policies and 49 public tables without RLS. The right remediation cannot be inferred from a text pattern alone.

## Evidence reviewed

1. `docs/security/RLS_FORENSIC_REMEDIATION_2026-10-26.md` — catalog snapshot and acceptance matrix.
2. `apps/sanad-platform/src/main/resources/db/migration/V20260730_1__enable_crm_row_level_security.sql` — CRM policy generator.
3. `docs/crm/crm-018/CRM-018-RLS-DESIGN.md` — documented legacy contract.
4. `apps/sanad-platform/src/main/java/com/sanad/platform/security/rls/TenantRlsConnectionHandler.java` — tenant GUC propagation.
5. `apps/sanad-platform/src/main/resources/db/migration/V20261020_1__supabase_data_api_default_deny.sql` — default-deny grants and function hardening.
6. `docs/crm/remediation/ROOT-CAUSE-R1.md` and `docs/runbooks/CRM-018-RLS-DISABLE-rollback.sql` — historical migration-order failure and the documented correction.

This was a GitHub source review. No PostgreSQL command or runtime test was executed in this review.

## Confirmed source-level risk

The CRM migration creates `tenant_isolation` policies with this logical form:

```sql
current_setting('app.tenant_id', true) IS NULL
OR tenant_id::text = current_setting('app.tenant_id', true)
```

The CRM design document explicitly describes this as “permissive-when-unset”: when the GUC is absent, the predicate permits all rows. The connection handler also returns without setting the GUC when the authenticated security context has no valid tenant UUID. Consequently, the source code establishes a fail-open path whenever a database operation runs under a role subject to these policies without tenant context. This is a source-level conclusion; the current production runtime behavior has not been re-tested.

The design documents state that the permissive branch was intentionally retained for background jobs and backward compatibility. Replacing it globally with fail-closed predicates without first identifying and separating those jobs could break valid operations. Conversely, keeping the permissive branch means RLS does not provide isolation for those operations. This is a security-contract conflict that must be resolved explicitly, not hidden by preserving the old behavior.

## Important migration-history evidence

The repository's historical root-cause record documents that a prior forward migration sequence ran an ENABLE-RLS migration and then a DISABLE-RLS migration in the same full Flyway pass, causing isolation tests to see both tenants' rows. The disable script is now present as a manual runbook at `docs/runbooks/CRM-018-RLS-DISABLE-rollback.sql`, and the former Flyway path returns 404 on the current remediation branch. The historical report says local Testcontainers verification was not available at that time. Therefore the historical “fixed” status is not a substitute for a fresh run of the current full migration chain and tenant-isolation integration tests.

## Required policy-phase decisions and implementation constraints

### A. Establish separate access contracts

Before changing policy predicates, enumerate each workload and role:
- tenant-scoped user request;
- privileged service/backend request;
- cross-tenant background job;
- platform-control-plane operation;
- immutable shared/reference-data read;
- migration/maintenance task.

Do not use a missing GUC as an implicit authorization signal. Any cross-tenant operation must use an explicit, narrow, auditable role or a reviewed privileged function, not the ordinary tenant application role. Confirm actual role ownership, grants, `BYPASSRLS`, SECURITY DEFINER paths, views, triggers and RPC before choosing the mechanism.

### B. Resolve tenant-context provenance

Trace the authenticated tenant from JWT validation/security context through transaction boundaries and JDBC connection reuse. Verify that:
- tenant identity is authorized against membership, not merely copied from a client-supplied field;
- `SET LOCAL app.tenant_id` is applied within the transaction before protected SQL;
- a missing, malformed or unauthenticated context cannot reach a permissive policy through the normal user role;
- switching tenants within one transaction is rejected;
- transaction commit/rollback and pool reuse cannot leak prior context;
- explicitly cross-tenant jobs have a separate authorization contract.

### C. Review every candidate policy individually

For each of the 81 audit candidates, capture table, policy name, command, roles, full USING expression, full WITH CHECK expression, table owner, grants, tenant key, joins/FKs and caller paths. Classify the predicate as:
- confirmed fail-open for tenant users;
- intentional and bounded privileged path;
- shared/reference-data policy;
- false positive / not applicable;
- unresolved.

Do not mass-replace expressions based only on `IS NULL` matching. Preserve valid platform/partner branches only when their authorization conditions are independently verified. Test both row visibility and write rejection.

### D. Classify the 49 tables without RLS

Assign each table a documented class: tenant-owned, platform-only, shared reference, credential/session secret, billing/financial, audit/operations, or migration metadata. Record owner, grants for SELECT/INSERT/UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER, views, functions, and expected workload. Add RLS and policies only where appropriate to the proven access contract. For platform-only and migration metadata tables, verify least-privilege grants and indirect paths even if a row policy is not the right control.

### E. Re-evaluate FORCE RLS per table

Do not enable FORCE indiscriminately. Identify table-owner operations, migrations, security-definer functions and maintenance jobs. If FORCE is needed, test all affected operations with non-owner application roles and privileged paths. Any exception must have a named owner and rationale.

## Required real PostgreSQL acceptance tests

Run against the current full Flyway chain in a disposable local PostgreSQL environment. These tests are NOT RUN by this source review:

1. Fresh database migration from empty state; Flyway validate; confirm RLS is still enabled after the full chain.
2. Tenant A can read/write only A rows; Tenant B can read/write only B rows.
3. A cannot read/update/delete B rows, insert a row for B, or update A's tenant key to B.
4. Missing, empty, malformed, unauthorized and stale tenant context is denied for ordinary tenant roles.
5. A cross-tenant background workload succeeds only through its explicitly authorized path; it must not use the ordinary tenant role's NULL-allow behavior.
6. Context is transaction-local and cannot leak across commit, rollback or pooled connection reuse.
7. Test all policy commands and both USING and WITH CHECK semantics.
8. Test indirect paths: views, SECURITY DEFINER functions, triggers, RPC, joins and aggregate/count queries.
9. Test roles with actual production-like grants; do not run all assertions as table owner or superuser.
10. Re-run the full relevant Maven/PostgreSQL Direct suite and preserve exact command, exit code and logs.

Negative controls must prove the test suite fails if a permissive NULL branch or cross-tenant grant is reintroduced. Do not label mocks or static checks as database isolation tests.

## Stop conditions

Do not create or apply a policy migration until the role/workload matrix and test environment are ready. If any required integration test cannot run, mark it BLOCKED or NOT RUN and leave the acceptance gate open. Do not query or mutate production to compensate for missing local resources. No paid branch, hosted database, CI run with uncertain charges, merge, or deployment is authorized by this document.

## Current disposition

- Function hardening migration `V20261026_4`: previously reported as applied and tested in disposable local PostgreSQL by the prior execution log; not re-run in this source review.
- 81 tenant-policy candidates: OPEN; no changes made here.
- 49 tables without RLS: OPEN; no table-by-table classification completed here.
- Full Maven/PostgreSQL Direct suite: NOT RUN in the recorded execution environment.
- Production catalog rescan: BLOCKED in the prior log by Management API HTTP 401; no fresh production evidence.
- Overall task: PARTIALLY COMPLETED. Production remains unchanged. NO MERGE / NO DEPLOY.
