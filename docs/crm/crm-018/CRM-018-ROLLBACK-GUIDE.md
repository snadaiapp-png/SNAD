# CRM-018 — Rollback Guide

| Field | Value |
|-------|-------|
| Work Item | EXEC-PROMPT-CRM-018 |
| Date | 2026-07-29 |

## 1. Rollback Options

There are two ways to roll back CRM-018 RLS, depending on the situation:

| Option | When to Use | Reversibility |
|--------|-------------|---------------|
| A: Disable proxy only | Quick mitigation; keep RLS policies for later | Re-enable proxy |
| B: Run rollback migration | Complete removal of RLS | Re-run enable migration |

## 2. Option A — Disable Proxy (Soft Rollback)

### When to Use
- RLS proxy is suspected of causing performance issues
- Need to temporarily bypass RLS without DB changes
- Quick rollback during incident response

### Steps

1. Set the environment variable or property:
   ```bash
   export SNAD_RLS_ENABLED=false
   ```
   Or in `application.yml`:
   ```yaml
   snad:
     rls:
       enabled: false
   ```

2. Restart the application.

### Effect
- `TenantRlsDataSourcePostProcessor` is not registered (conditional bean)
- Connections are not proxied
- `SET LOCAL app.tenant_id` is never executed
- RLS policies remain in the database but are always permissive (no context set)
- **Application-layer filtering (`WHERE tenant_id = :t`) remains fully active**

### To Re-enable
```bash
export SNAD_RLS_ENABLED=true  # or remove the property (default is true)
```
Restart the application.

## 3. Option B — Run Rollback Migration (Full Rollback)

### When to Use
- Complete removal of RLS policies from the database
- RLS is not needed and should be cleaned up
- Before a major schema refactor

### Steps

The rollback migration `V20260730_2__disable_crm_row_level_security.sql`
is already in the migration chain. It will run automatically on the next
Flyway migration if applied.

#### Method 1: Flyway Repair + Migrate (if V20260730_1 already applied)

Since `V20260730_2` is the next version after `V20260730_1`, simply
running Flyway migrate will apply it:

```bash
# Flyway will apply V20260730_2 on next startup
# No manual action needed if migrations run sequentially
```

#### Method 2: Manual SQL Execution (Emergency)

```sql
-- Run directly against the database
DO $$
DECLARE
    tbl record;
BEGIN
    FOR tbl IN
        SELECT c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
            ON t.table_name = c.table_name
            AND t.table_schema = c.table_schema
        WHERE c.table_schema = 'public'
          AND c.column_name = 'tenant_id'
          AND c.table_name LIKE 'crm_%'
          AND t.table_type = 'BASE TABLE'
        ORDER BY c.table_name
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl.table_name);
        EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', tbl.table_name);
        RAISE NOTICE 'RLS disabled on %', tbl.table_name;
    END LOOP;
END
$$;
```

### Effect
- All `tenant_isolation` policies dropped
- RLS disabled on all CRM tables
- Database returns to pre-CRM-018 state
- Application-layer filtering remains fully active

### To Re-enable
Run `V20260730_1__enable_crm_row_level_security.sql` via Flyway:
```bash
flyway migrate  # will re-apply if version is managed
```

## 4. Rollback Verification

After either rollback option, verify RLS is inactive:

```sql
-- Should return 0
SELECT COUNT(*) FROM pg_tables
WHERE tablename LIKE 'crm_%'
  AND rowsecurity = true;

-- Should return 0
SELECT COUNT(*) FROM pg_policies
WHERE tablename LIKE 'crm_%'
  AND policyname = 'tenant_isolation';
```

## 5. Safety Guarantees During Rollback

| Guarantee | Status |
|-----------|--------|
| No data loss | ✅ Rollback only changes policies/metadata |
| No schema changes | ✅ No columns/indexes/constraints affected |
| Application continues working | ✅ App-layer filtering always active |
| No downtime required | ✅ Policies can be dropped while app runs |
| Reversible | ✅ Re-run enable migration to restore |

## 6. Rollback Decision Matrix

| Situation | Recommended Option |
|-----------|-------------------|
| Suspected performance issue | Option A (disable proxy) |
| RLS policy conflict | Option B (rollback migration) |
| Emergency data access needed | Option A + use owner role |
| Complete CRM-018 removal | Option B + remove Java classes |
| Temporary for debugging | Option A |
