# Known Issues

## Critical

### 1. SANAD_CONTROL_PLANE_TENANT_ID Not Set in Render
- **Impact**: Production release workflow fails at "Verify required Render runtime configuration"
- **Root Cause**: The environment variable was never configured in Render Dashboard
- **Resolution**: Set SANAD_CONTROL_PLANE_TENANT_ID in Render to a valid tenant UUID from production database
- **Workaround**: None — this is a hard blocker for production release

### 2. No db/migration-pg-only on Main
- **Impact**: PostgreSQL Row-Level Security (RLS) policies are not in production
- **Root Cause**: Main's prod profile uses `classpath:db/migration` only (no pg-only)
- **Resolution**: The fix/flyway-forward-migrations-20260703 branch has forward-only RLS migrations but was reverted
- **Risk**: Medium — tenant isolation relies on application-level RLS, not database-level

## Medium

### 3. CRM Tables Missing RLS Policies
- **Impact**: CRM tables (crm_accounts, crm_contacts, etc.) have tenant_id but no RLS policies
- **Root Cause**: V20260702_1 creates tables but no RLS; pg-only directory not on main
- **Resolution**: Add RLS policies for CRM tables in a new forward-only migration
- **Risk**: Medium — tenant isolation enforced at application level via TenantContext

### 4. Testcontainers Tests Require Docker
- **Impact**: 2 tests fail locally without Docker
- **Root Cause**: FlywayV15ProductionUpgradeTest and CrmPostgresMigrationTest use @Testcontainers
- **Resolution**: These pass in CI (GitHub Actions provides Docker). Not a real blocker.
- **Workaround**: Set RUN_TESTCONTAINERS_TESTS=true in CI only

### 5. V15 Java Migration PostgreSQL UUID Handling
- **Impact**: V15 migration uses `setObject(idx, uuid, Types.OTHER)` for PostgreSQL compatibility
- **Root Cause**: Original Java V15 used `setString()` which fails on PostgreSQL UUID columns
- **Resolution**: Already fixed in main (PR #205)
- **Status**: RESOLVED

## Low

### 6. No db/migration-pg-only Directory on Main
- **Impact**: Audit/idempotency/RLS infrastructure migrations not in production
- **Root Cause**: These were developed on infra/* branches but never merged to main
- **Resolution**: Need a clean PR adding forward-only V20260703_x migrations
- **Risk**: Low for current operations, medium for audit compliance

### 7. CRM Custom Fields Schema-Only
- **Impact**: crm_custom_field_definitions table exists but no API or UI
- **Resolution**: Implement custom fields API and UI in CRM completion phase
- **Risk**: None — feature gap, not a bug

### 8. CRM Import Backend-Only
- **Impact**: crm_import_jobs table exists but import UI not implemented
- **Resolution**: Implement import UI in CRM completion phase
- **Risk**: None — feature gap

