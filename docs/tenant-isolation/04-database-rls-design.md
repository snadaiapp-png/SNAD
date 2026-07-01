# Stage 04 — Database RLS Design

## 1. Overview

PostgreSQL Row-Level Security (RLS) is enabled on all 8 TENANT_OWNED tables to enforce tenant isolation at the database level. This is defense-in-depth: even if the application layer fails to scope a query, the database refuses to return cross-tenant rows.

## 2. Migration

**File:** `apps/sanad-platform/src/main/resources/db/migration-pg-only/V17__enable_tenant_rls.sql`

This migration is in `db/migration-pg-only/` (not `db/migration/`) because H2 (local profile) does not support RLS. The `application-prod.yml` profile loads both directories; `application-local.yml` loads only `db/migration`.

## 3. RLS Policy

Each tenant-owned table has:

```sql
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <table> FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_<table> ON <table>
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

### USING clause
Applied to SELECT, UPDATE, DELETE. Only rows where `tenant_id` matches the current session setting are visible.

### WITH CHECK clause
Applied to INSERT, UPDATE. Rows that don't match the current session setting are rejected.

### FORCE ROW LEVEL SECURITY
Forces RLS even for the table owner. The runtime DB role is NOT a superuser and does NOT have BYPASSRLS.

## 4. Session Variable

The application sets `app.current_tenant_id` inside each transaction via `TenantRlsBinder`:

```java
SET LOCAL app.current_tenant_id = '<uuid>';
```

- `SET LOCAL` scopes the setting to the current transaction
- The setting is automatically cleared when the transaction commits or rolls back
- No risk of leaking to a pooled connection

## 5. Fail-Closed Behavior

If `app.current_tenant_id` is NOT set (missing TenantContext):
- `current_setting('app.current_tenant_id', true)` returns NULL (the `true` parameter = missing_ok)
- `tenant_id = NULL` evaluates to FALSE for all rows
- RLS returns 0 rows — the database fails CLOSED

## 6. Protected Tables

1. organizations
2. organization_memberships
3. users
4. roles
5. role_capabilities
6. user_role_assignments
7. refresh_tokens
8. password_reset_tokens

## 7. Unprotected Tables (Global Exceptions)

1. `tenants` — root table (no tenant_id column); see `04-global-table-exceptions.json`
2. `access_capabilities` — global reference catalog; see `04-global-table-exceptions.json`

## 8. Runtime DB Role

The runtime DB role used by the Spring application:
- Is NOT a superuser
- Does NOT have `BYPASSRLS`
- Is NOT the table owner (table owner is the migration role)

This ensures RLS policies are enforced for all application queries.

## 9. CI Testing

The `tenant-isolation` CI job runs against PostgreSQL 16 with RLS enabled:
- `TenantCrudIsolationIntegrationTest` — cross-tenant CRUD denied
- `TenantBindingSecurityIntegrationTest` — tenantId mismatch rejected
- `OrganizationTenantIsolationTest` — organization cross-tenant denied
- `TenantAwarePaginationIntegrationTest` — pagination remains tenant-scoped
- `TenantContextLifecycleTest` — context lifecycle rules

## 10. H2 Local Profile

H2 (local profile) does not support RLS. The local profile relies on application-layer tenant scoping (all repository methods take tenantId). The CI jobs run against PostgreSQL 16 to verify RLS enforcement.
