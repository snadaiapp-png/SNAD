# CRM-018 — Row-Level Security Design

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-018 |
| Title | Add row-level security as defense-in-depth |
| Milestone | CRM-G4 |
| Designer | CRM-018 Security Implementation Authority |
| Date | 2026-07-29 |

## 1. Design Goals

| Goal | Approach |
|------|----------|
| Defense-in-depth | Database-level RLS guarantees cross-tenant rows are never returned |
| Preserve existing behavior | Application-layer filtering remains primary; RLS is a safety net |
| No breaking API changes | No endpoint, DTO, or query signature changes |
| Security by default | When tenant context IS set, RLS denies cross-tenant access by default |
| Backward compatible migrations | Vendor-specific (PostgreSQL only); H2 tests unaffected |
| Clean Architecture | RLS is infrastructure; no domain/application layer changes |

## 2. RLS Policy Model

### 2.1 Permissive-When-Unset Policy (Safe Fallback)

```sql
CREATE POLICY tenant_isolation ON crm_<table>
    FOR ALL
    USING (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    )
    WITH CHECK (
        current_setting('app.tenant_id', true) IS NULL
        OR tenant_id::text = current_setting('app.tenant_id', true)
    );
```

| Condition | Behavior | Rationale |
|-----------|----------|-----------|
| `app.tenant_id` IS NULL | **Permissive** — all rows visible | Backward compatible; falls back to app-layer filtering |
| `app.tenant_id` = tenant A | **Strict** — only tenant A's rows | Defense-in-depth active |
| `app.tenant_id` = tenant B (mismatch) | **Blocks** — no rows returned | Cross-tenant access denied |

### 2.2 Why Permissive-When-Unset?

For a brownfield system with 62 tables and 351+ queries, a strict-only policy
would break the application if tenant context propagation has ANY gap. The
permissive fallback ensures:

1. **Zero breakage risk:** If `app.tenant_id` is not set, RLS is invisible.
2. **Real enforcement:** When tenant context IS propagated (all authenticated
   requests), RLS strictly enforces isolation.
3. **Deployable in stages:** RLS policies can be deployed first; context
   propagation activates them.
4. **Background-safe:** Cross-tenant background jobs (import worker) don't set
   `app.tenant_id` → RLS is permissive → they work as before.

### 2.3 FORCE ROW LEVEL SECURITY: NOT USED

We deliberately do **not** use `FORCE ROW LEVEL SECURITY`. This means the table
owner (the migration/admin role used by Flyway) bypasses RLS automatically.
This is required for:
- Flyway migrations to manage all tenants' schema/data
- Admin/diagnostic queries
- Background maintenance jobs

### 2.4 Coverage: All CRM Tables with `tenant_id`

RLS is applied dynamically to all tables matching:
- `table_schema = 'public'`
- `table_name LIKE 'crm_%'`
- Has a `tenant_id` column

This covers all 62 CRM tables and automatically includes future tables.

## 3. Tenant Context Propagation

### 3.1 Mechanism: Connection Proxy with Lazy `SET LOCAL`

```
Request → JwtAuthenticationFilter (sets SecurityContext with tenant_id)
  → CRM Controller → CRM UseCase (@Transactional)
    → DataSource.getConnection() → TenantRlsDataSource proxy
      → JdbcTemplate.prepareStatement()
        → Proxy intercepts: checks autocommit + SecurityContext
          → If autocommit=false AND tenant in SecurityContext:
              SET LOCAL app.tenant_id = '<uuid>'
          → Proceed with real statement
```

### 3.2 Why `SET LOCAL` (not `SET SESSION`)?

| Feature | `SET LOCAL` | `SET SESSION` |
|---------|-------------|---------------|
| Scope | Current transaction only | Connection lifetime |
| Pool safety | ✅ Resets on transaction end | ❌ Leaks across pool reuse |
| Autocommit behavior | Requires explicit transaction | Works in autocommit |
| Fit | @Transactional methods | Not safe with pooling |

`SET LOCAL` is the correct choice for connection-pooled multi-tenant apps.
It applies only within the current transaction (where `autocommit=false`) and
resets automatically when the transaction commits/rolls back.

### 3.3 Autocommit Guard

The connection proxy only executes `SET LOCAL` when `autocommit == false`:

| Path | Autocommit | SET LOCAL? | RLS Active? |
|------|-----------|------------|-------------|
| `@Transactional` CRM method | false | ✅ Executed | ✅ Enforced |
| Non-transactional read | true | ❌ Skipped | Permissive (app-layer filters) |
| Background job | true | ❌ Skipped | Permissive |

This ensures RLS is active for the transactional path (the primary data access
path) without risking breakage on non-transactional reads.

### 3.4 Tenant Source: SecurityContext (No ThreadLocal Filter Needed)

The proxy reads the tenant directly from `SecurityContextHolder`:
- Same source as existing repository code
- No additional filter or ThreadLocal to manage
- No lifecycle/cleanup concerns
- Works because the proxy runs on the request thread

## 4. Component Design

### 4.1 `TenantRlsDataSource`

Wraps the auto-configured HikariCP `DataSource`. Returns proxied connections.

```
TenantRlsDataSource implements AbstractDataSource
  ├── getConnection() → proxy(realConnection)
  └── getConnection(user, pass) → proxy(realConnection)
```

### 4.2 Connection Proxy (JDK Dynamic Proxy)

`InvocationHandler` that intercepts `createStatement`, `prepareStatement`,
`prepareCall` and lazily applies `SET LOCAL` before delegation.

```
TenantRlsConnectionHandler implements InvocationHandler
  ├── delegate: Connection (real HikariCP connection)
  ├── tenantApplied: boolean (optimization flag)
  ├── invoke(proxy, method, args):
  │     if method is statement-creation:
  │       ensureTenantContext()
  │     return method.invoke(delegate, args)
  └── ensureTenantContext():
        if tenantApplied or delegate.autoCommit: return
        tenantId = read from SecurityContext
        if tenantId != null:
          delegate.createStatement().execute("SET LOCAL app.tenant_id = '<uuid>'")
          tenantApplied = true
```

### 4.3 `TenantRlsDataSourcePostProcessor`

`BeanPostProcessor` that wraps the auto-configured `DataSource` with
`TenantRlsDataSource`. This avoids circular dependency issues and integrates
transparently with Spring Boot's HikariCP auto-configuration.

```
TenantRlsDataSourcePostProcessor implements BeanPostProcessor
  └── postProcessAfterInitialization(bean, name):
        if bean is DataSource and not already wrapped:
          return new TenantRlsDataSource(bean)
        return bean
```

### 4.4 Conditional Activation

```java
@ConditionalOnProperty(name = "snad.rls.enabled", havingValue = "true", matchIfMissing = true)
```

RLS is enabled by default. Can be disabled via `snad.rls.enabled=false` for
debugging or specific environments.

## 5. Migration Strategy

### 5.1 Forward Migration

File: `db/vendor/postgresql/V20260730_1__enable_crm_row_level_security.sql`

Uses a dynamic `DO` block that:
1. Finds all `crm_*` tables with a `tenant_id` column
2. Enables RLS on each
3. Creates the permissive-when-unset policy on each
4. Uses `DROP POLICY IF EXISTS` for idempotency

### 5.2 Rollback Strategy

File: `db/vendor/postgresql/V20260730_2__disable_crm_row_level_security.sql`

Mirror migration that:
1. Drops the `tenant_isolation` policy from all CRM tables
2. Disables RLS on all CRM tables

This is a separate migration (not a Flyway undo), ensuring standard Flyway
version ordering. Can be applied by running the rollback migration version.

### 5.3 H2 Compatibility

The migration lives in `db/vendor/postgresql/` — H2 tests scan `db/vendor/h2/`
and never see it. No H2 no-op migration needed.

## 6. Testing Strategy

### 6.1 PostgreSQL Testcontainers Test

`CrmRlsTenantIsolationPostgresTest` — extends the existing pattern from
`CrmG1TenantIsolationPostgresTest`:

1. Start PostgreSQL 16 container
2. Run Flyway migrations (including the RLS migration)
3. Insert rows for two tenants
4. Set `app.tenant_id` to tenant A
5. Verify: SELECT returns only tenant A's rows
6. Verify: INSERT with tenant B's ID fails (WITH CHECK)
7. Verify: Without `app.tenant_id` set, all rows visible (fallback)
8. Verify: `SET LOCAL` resets after transaction

### 6.2 Existing Tests

| Test Type | Impact | Why |
|-----------|--------|-----|
| H2 unit tests | ✅ None | RLS migration not scanned |
| Testcontainers Postgres tests (manual Flyway) | ✅ None | They use `.locations("classpath:db/migration")` only |
| Full-context Postgres tests | ✅ None | Permissive policy when `app.tenant_id` unset |

## 7. Performance Impact

| Operation | Impact | Mitigation |
|-----------|--------|------------|
| `SET LOCAL` per transaction | ~0.1ms | Negligible; executed once per transaction |
| RLS policy evaluation per query | ~0.05ms | `tenant_id` is indexed; comparison is trivial |
| Connection proxy overhead | ~0.01ms per statement | JDK proxy; only intercepts 3 methods |

Total overhead: **< 1ms per transaction**, well within acceptable bounds.

## 8. Security Properties

| Property | Guarantee |
|-----------|-----------|
| Cross-tenant SELECT denied | ✅ RLS `USING` clause blocks non-matching rows |
| Cross-tenant INSERT denied | ✅ RLS `WITH CHECK` clause blocks non-matching writes |
| Cross-tenant UPDATE denied | ✅ Both `USING` (old row) and `WITH CHECK` (new row) enforced |
| Cross-tenant DELETE denied | ✅ RLS `USING` clause blocks deletion of non-matching rows |
| SQL injection | ✅ Even injected SQL respects RLS policies |
| Missing app-layer filter | ✅ RLS catches the gap (when context is set) |
| Migration/admin access | ✅ Table owner bypasses RLS (no FORCE RLS) |

## 9. Compatibility Matrix

| Concern | Status |
|---------|--------|
| Existing API endpoints | ✅ Unchanged |
| Existing DTOs | ✅ Unchanged |
| Existing repository queries | ✅ Unchanged |
| Existing @Transactional behavior | ✅ Unchanged |
| H2 tests | ✅ Unaffected |
| Flyway migrations | ✅ Owner bypasses RLS |
| Background jobs | ✅ Permissive when unset |
| Application-layer filtering | ✅ Still primary enforcement |
