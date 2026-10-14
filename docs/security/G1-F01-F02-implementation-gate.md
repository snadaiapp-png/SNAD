# G1 F-01/F-02 implementation gate — 2026-10-14

Status: IMPLEMENTATION NOT COMPLETE — DO NOT MERGE — DO NOT DEPLOY

## Scope
Resolve the unauthenticated tenantless login path (F-01) and missing tenant GUC pass-through (F-02) before any production RLS migration for `public.users`.

## Evidence-based baseline
- `AuthService.login` calls `UserRepository.findAllByEmail/findAllByUsername` when request tenantId is absent.
- `TenantRlsConnectionHandler.ensureTenantContext` returns without applying a tenant GUC when autocommit is enabled or when no valid tenant_id exists.
- The existing Python RED contract is source-level only; it is not proof of runtime isolation.
- The current live RLS certificate must not be treated as passing when the live isolation job is skipped.

## Required implementation (sequence matters)
1. Characterize all callers of `public.users` during login, registration bootstrap, session-version validation, refresh/revoke, reset/recovery, owner/control-plane access, and background processing.
2. Implement a dedicated pre-auth identity resolver with an explicit, reviewed least-privilege database contract. It must not give the general `sanad` role RLS bypass. Tenantless login must preserve documented unique-vs-ambiguous behavior without exposing cross-tenant rows to JPA.
3. Ensure the resolver is invoked through a dedicated database path whose privilege boundary is enforceable (for example, a narrowly scoped SECURITY DEFINER function owned by a NOLOGIN role, with EXECUTE granted only to the intended runtime role); verify search_path, object qualification, input normalization, return columns, grants, and owner privileges.
4. Change tenant connection handling so ordinary tenant data access cannot silently proceed without a valid tenant scope. Do not globally fail closed until pre-auth, registration bootstrap, session checks, and explicitly authorized control-plane/background paths have been classified and migrated; otherwise a blanket change may break authentication or encourage unsafe bypasses.
5. Bind tenant scope transaction-locally on the same physical datasource/transaction used by JPA. Test connection-pool reuse, rollback/commit reset, nested transactions, and tenant switching attempts.
6. Add PostgreSQL Direct tests using known fixture rows in Tenant A and Tenant B. Verify SELECT, INSERT, UPDATE (including tenant_id mutation), DELETE, absent/invalid GUC, incorrect request tenant hint, ambiguous identifiers, registration + initial login, refresh/revoke, reset/recovery, and session-version lookup.
7. Verify effective runtime role, role memberships, SUPERUSER/BYPASSRLS, table ownership, ENABLE/FORCE flags, and actual policy expressions. Run the live certificate on the exact candidate SHA with approved non-production credentials.
8. Require all relevant CI, independent review, and sanitized evidence before changing G1 state.

## Safety invariants
- No production DDL or privilege changes in this work item.
- No weakening assertions, disabling RLS tests, or granting general bypass privileges.
- No secrets, raw database URLs, tenant identifiers, user records, or credentials in logs/artifacts.
- Keep G1=HOLD, P0 SECURITY HOLD=ACTIVE, PAYMENTS=DISABLED, NO MERGE, NO DEPLOY until evidence passes.

## Acceptance
This work is not complete until source changes and PostgreSQL Direct runtime tests pass together. A static source contract, a skipped live job, or a successful CRM suite alone does not satisfy acceptance.
