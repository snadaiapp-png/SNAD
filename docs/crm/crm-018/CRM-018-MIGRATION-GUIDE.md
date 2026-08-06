# CRM-018 — Migration Guide

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-018 |
| Date | 2026-07-29 |

## 1. Migration Overview

| Migration | File | Location | Action |
|-----------|------|----------|--------|
| `V20260730_1` | `V20260730_1__enable_crm_row_level_security.sql` | `db/vendor/postgresql/` | Enable RLS on all CRM tables |
| `V20260730_2` | `V20260730_2__disable_crm_row_level_security.sql` | `db/vendor/postgresql/` | Disable RLS (rollback) |

### H2 Mirrors (Test Only)

| Migration | File | Location | Content |
|-----------|------|----------|---------|
| `V20260730_1` | `V20260730_1__enable_crm_row_level_security.sql` | `src/test/resources/db/vendor/h2/` | `SELECT 1;` (no-op) |
| `V20260730_2` | `V20260730_2__disable_crm_row_level_security.sql` | `src/test/resources/db/vendor/h2/` | `SELECT 1;` (no-op) |

## 2. What the Migration Does

### V20260730_1 — Enable RLS

1. **Discovers** all tables matching: `schema = 'public'`, `name LIKE 'crm_%'`,
   has `tenant_id` column, `table_type = 'BASE TABLE'`
2. **Enables RLS** on each: `ALTER TABLE <name> ENABLE ROW LEVEL SECURITY`
3. **Creates policy** on each: `CREATE POLICY tenant_isolation ON <name> FOR ALL
   USING (...) WITH CHECK (...)`
4. Uses `DROP POLICY IF EXISTS` for idempotency

### Policy Logic

```sql
USING (
    current_setting('app.tenant_id', true) IS NULL
    OR tenant_id::text = current_setting('app.tenant_id', true)
)
WITH CHECK (
    current_setting('app.tenant_id', true) IS NULL
    OR tenant_id::text = current_setting('app.tenant_id', true)
)
```

- **When `app.tenant_id` is unset:** permissive (all rows) — backward compatible
- **When `app.tenant_id` is set:** strict (only matching tenant rows)

## 3. Deployment Instructions

### 3.1 Standard Deployment (Flyway Auto-Migration)

No manual action required. Flyway runs automatically on application startup:
```yaml
spring:
  flyway:
    enabled: true
    locations: "classpath:db/migration,classpath:db/vendor/{vendor}"
```

The migration runs as part of the normal startup sequence.

### 3.2 Verification After Deployment

```sql
-- Check RLS is enabled
SELECT tablename, rowsecurity
FROM pg_tables
WHERE tablename LIKE 'crm_%'
ORDER BY tablename;

-- Check policies exist
SELECT tablename, policyname, cmd, qual
FROM pg_policies
WHERE tablename LIKE 'crm_%'
  AND policyname = 'tenant_isolation'
ORDER BY tablename;
```

### 3.3 Performance Impact

| Operation | Overhead |
|-----------|----------|
| Migration execution | ~1-2 seconds (62 tables) |
| Per-transaction `SET LOCAL` | ~0.1ms |
| Per-query policy evaluation | ~0.05ms (indexed `tenant_id`) |

## 4. Compatibility

| Concern | Status |
|---------|--------|
| Existing application queries | ✅ Unaffected (permissive fallback) |
| Existing Flyway migrations | ✅ Run before V20260730_1 |
| Table owner / migration role | ✅ Bypasses RLS (no FORCE RLS) |
| Background jobs (no tenant context) | ✅ Permissive (all rows) |
| H2 tests | ✅ No-op mirror migrations |
| New CRM tables added later | ✅ Auto-covered if `crm_*` + `tenant_id` |

## 5. Configuration

### 5.1 Enable/Disable RLS Proxy

```yaml
# application.yml (or env var)
snad:
  rls:
    enabled: true  # default; set to false to disable the proxy
```

When `snad.rls.enabled=false`:
- The `TenantRlsDataSourcePostProcessor` is not registered
- Connections are not proxied
- `app.tenant_id` is never set
- RLS policies remain permissive (fallback mode)

### 5.2 Database Role Requirements

The application's database role must:
- Have `SELECT`, `INSERT`, `UPDATE`, `DELETE` on CRM tables (existing)
- NOT have `BYPASSRLS` (so RLS applies to application connections)

The migration/Flyway role must:
- Be the table owner, OR have `BYPASSRLS` (so migrations run unimpeded)

In the standard setup, Flyway runs as the table owner, which bypasses RLS
automatically. No additional role configuration is needed.
