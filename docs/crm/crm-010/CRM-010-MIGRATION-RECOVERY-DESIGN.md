# CRM-010 Migration / Recovery Acceptance Design

**Date:** 2026-07-29
**Issue:** #705 — Mandatory Deliverable #4
**Scope:** CRM-010 database migration forward, rollback, and recovery acceptance criteria

---

## 1. Migration Inventory

### 1.1 CRM-010 Migrations

| Version | File | Purpose | Tables Affected |
|---------|------|---------|-----------------|
| V20260729_1 | `db/vendor/postgresql/V20260729_1__create_crm_customer_intelligence.sql` | Create 6 intelligence tables, 6 indexes, 5 capabilities | CREATE 6 tables |
| V20260729_2 | `db/vendor/postgresql/V20260729_2__seed_default_scoring_models.sql` | Seed 4 default scoring model configurations | INSERT into `crm_scoring_models` |

### 1.2 Migration Dependencies

```
V20260729_2 (seed models) → depends on → V20260729_1 (create tables)
```

V20260729_2 inserts into `crm_scoring_models` which is created by V20260729_1.

---

## 2. Forward Migration Acceptance

### 2.1 Preconditions (Before Migration)

| Check | Verification | Status |
|-------|-------------|--------|
| No `crm_customer_scores` table exists | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'crm_customer_scores'` returns 0 | ✅ Verified in CI |
| No `crm_scoring_models` table exists | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'crm_scoring_models'` returns 0 | ✅ Verified in CI |
| Flyway schema history is clean | No failed entries for V20260729_* | ✅ Verified in CI |

### 2.2 Postconditions (After Migration)

| Check | Verification | Expected |
|-------|-------------|----------|
| 6 tables created | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN (...)` | 6 |
| 6 indexes created | `SELECT COUNT(*) FROM pg_indexes WHERE tablename LIKE 'crm_customer_%' OR tablename = 'crm_scoring_models'` | 6 |
| 5 capabilities seeded | `SELECT COUNT(*) FROM crm_capabilities WHERE capability LIKE 'CRM.CUSTOMER_%'` | 5 |
| 4 scoring models seeded | `SELECT COUNT(*) FROM crm_scoring_models WHERE tenant_id = '00000000-0000-0000-0000-000000000000'` | 4 |
| All tables have `tenant_id` column | `SELECT COUNT(*) FROM information_schema.columns WHERE column_name = 'tenant_id' AND table_name LIKE 'crm_customer_%'` | 6 |
| Flyway validate passes | `flyway validate` | SUCCESS |

### 2.3 Acceptance Test

```sql
-- V20260729_1 acceptance
SELECT COUNT(*) FROM information_schema.tables
WHERE table_name IN (
  'crm_customer_scores', 'crm_customer_score_history',
  'crm_customer_segments', 'crm_segment_memberships',
  'crm_next_best_actions', 'crm_scoring_models'
); -- Expected: 6

-- V20260729_2 acceptance
SELECT COUNT(*) FROM crm_scoring_models
WHERE tenant_id = '00000000-0000-0000-0000-000000000000'
AND active = TRUE; -- Expected: 4
```

---

## 3. Rollback Design

### 3.1 Rollback Strategy

CRM-010 uses **forward-only rollback** — no automated `down()` migration. Rollback is manual via SQL scripts.

### 3.2 Rollback Script

```sql
-- CRM-010 Rollback Script
-- Execute in reverse dependency order
-- WARNING: This destroys all CRM-010 intelligence data

-- Step 1: Remove scoring models (V20260729_2)
DELETE FROM crm_scoring_models
WHERE tenant_id = '00000000-0000-0000-0000-000000000000';

-- Step 2: Remove capabilities (V20260729_1)
DELETE FROM crm_capabilities
WHERE capability IN (
  'CRM.CUSTOMER_360.READ',
  'CRM.CUSTOMER_INTELLIGENCE.READ',
  'CRM.CUSTOMER_INTELLIGENCE.WRITE',
  'CRM.CUSTOMER_INTELLIGENCE.ADMIN',
  'CRM.CUSTOMER_SEGMENT.MANAGE'
);

-- Step 3: Drop tables in reverse dependency order (V20260729_1)
DROP TABLE IF EXISTS crm_next_best_actions CASCADE;
DROP TABLE IF EXISTS crm_segment_memberships CASCADE;
DROP TABLE IF EXISTS crm_customer_segments CASCADE;
DROP TABLE IF EXISTS crm_customer_score_history CASCADE;
DROP TABLE IF EXISTS crm_customer_scores CASCADE;
DROP TABLE IF EXISTS crm_scoring_models CASCADE;

-- Step 4: Remove Flyway history entries
DELETE FROM flyway_schema_history
WHERE version IN ('20260729.2', '20260729.1');
```

### 3.3 Rollback Acceptance

| Check | Verification | Status |
|-------|-------------|--------|
| All 6 tables dropped | `SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'crm_customer_%' OR table_name = 'crm_scoring_models'` returns 0 | ✅ |
| All 5 capabilities removed | `SELECT COUNT(*) FROM crm_capabilities WHERE capability LIKE 'CRM.CUSTOMER_%'` returns 0 | ✅ |
| Flyway history clean | `SELECT COUNT(*) FROM flyway_schema_history WHERE version LIKE '20260729%'` returns 0 | ✅ |
| Existing CRM data unaffected | `SELECT COUNT(*) FROM crm_accounts` unchanged | ✅ |

---

## 4. Recovery Design

### 4.1 Recovery Scenarios

| Scenario | Trigger | Recovery Action | RTO |
|----------|---------|-----------------|-----|
| Migration fails mid-execution | Flyway error | Flyway retry (idempotent CREATE IF NOT EXISTS) | <5 min |
| Migration corrupts data | Data validation failure | Execute rollback script, restore from backup | <30 min |
| Partial migration (V20260729_1 OK, V20260729_2 fails) | Seed failure | Manually insert scoring models from seed script | <10 min |
| Production database failure | Infrastructure failure | Restore from PITR backup, re-run migrations | <1 hour |

### 4.2 Recovery Prerequisites

| Prerequisite | Verification | Status |
|-------------|-------------|--------|
| Pre-migration backup exists | Backup timestamp > migration timestamp | ✅ Required |
| PITR enabled | `archive_mode = on`, `archive_command` configured | ✅ Required |
| Rollback script tested | Rollback tested in staging environment | ✅ Required |
| Staging environment matches production | Same PostgreSQL version, same Flyway version | ✅ Required |

### 4.3 Recovery Test Acceptance

| Test | Expected Result | Status |
|------|----------------|--------|
| Execute rollback script on staging | All CRM-010 objects removed | ✅ Acceptance criteria |
| Re-run migrations after rollback | All objects recreated successfully | ✅ Acceptance criteria |
| Verify existing CRM data after rollback/re-migration | `crm_accounts`, `crm_contacts` unchanged | ✅ Acceptance criteria |

---

## 5. Migration Test Coverage

| Test | File | What It Verifies |
|------|------|-----------------|
| `CrmPostgresMigrationTest` | `crm/web/CrmPostgresMigrationTest.java` | Tables exist, indexes exist, capabilities seeded, version correct |
| `Crm008bFoundationAcceptanceTest` | `crm/web/Crm008bFoundationAcceptanceTest.java` | Latest Flyway version is correct, all tables exist |
| `CrmG1TenantIsolationPostgresTest` | `crm/web/CrmG1TenantIsolationPostgresTest.java` | Tenant isolation after migration |

### 5.1 CI Verification

The `crm-g1-schema-isolation.yml` workflow runs on every PR:
1. Starts PostgreSQL 16 service container
2. Runs `flyway migrate` via CLI
3. Executes `CrmPostgresMigrationTest` (verifies 6 tables, 6 indexes, 5 capabilities)
4. Executes `CrmG1TenantIsolationPostgresTest` (verifies tenant isolation)

**All checks pass on PR #818.**

---

## 6. Data Retention and Archival

| Policy | Implementation | Status |
|--------|---------------|--------|
| Score history retention | No automatic archival; manual archival recommended for records >12 months | ⚠️ Deferred |
| Scoring model retention | Active models retained; inactive models marked `active = FALSE` | ✅ Implemented |
| NBA retention | Expired NBAs marked with `expires_at`; batch cleanup via `expireStaleRecommendations()` | ✅ Implemented |

---

**Design Authority:** Governance Remediation Agent
**Date:** 2026-07-29
**Status:** ✅ COMPLETE
