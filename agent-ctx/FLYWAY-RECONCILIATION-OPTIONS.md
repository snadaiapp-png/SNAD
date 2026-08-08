# MISSION 5 — Flyway Reconciliation Options

**Generated:** 2026-08-07  
**Status:** READ-ONLY ANALYSIS  
**Scope:** Evaluation of 5 remediation options for V20260716_3 baseline gap

---

## 1. Problem Statement

V20260716_3 (crm_tags + crm_tag_assignments) was skipped by Flyway baseline 20260717.6. Unlike V20260716_1 and V20260716_2 (which were reconciled by V20260718_1), V20260716_3 has **no reconciliation migration**. This causes:

- **Production:** Tables exist (manually applied), but no Flyway history entry
- **Fresh Database:** V20260807_3 fails with `relation "crm_tags" does not exist`
- **Reproducibility:** Cannot provision a fresh database from scratch

---

## 2. Options Overview

| Option | Description | Production Safety | Fresh DB Safety | Flyway Compatible |
|--------|-------------|-------------------|-----------------|-------------------|
| **A** | Create reconciliation migration | ✅ SAFE | ✅ FIXES | ✅ YES |
| **B** | Add `IF EXISTS` to V20260807_3 | ⚠️ RISKY | ✅ FIXES | ❌ NO |
| **C** | Reset Flyway baseline | ❌ DANGEROUS | ✅ FIXES | ⚠️ PARTIAL |
| **D** | Manual DB provisioning script | ✅ SAFE | ⚠️ PARTIAL | ❌ NO |
| **E** | Delete V20260807_3 migration | ⚠️ RISKY | ✅ FIXES | ❌ NO |

---

## 3. Detailed Option Analysis

### Option A: Create Reconciliation Migration

**Description:** Create a new migration file (e.g., `V20260718_2__reconcile_crm_tags_after_baseline_gap.sql`) that creates `crm_tags` and `crm_tag_assignments` with idempotent DDL, following the established pattern of V20260718_1.

**Implementation:**
```sql
-- New file: apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260718_2__reconcile_crm_tags_after_baseline_gap.sql
-- Creates crm_tags + crm_tag_assignments if not exists
-- Seeds CRM.TAG.READ/WRITE capabilities
-- Grants capabilities to ADMIN role
-- Postcondition verification
```

**Production Safety:** ✅ SAFE
- Tables already exist in production → `IF NOT EXISTS` is a no-op
- Capabilities already seeded → `WHERE NOT EXISTS` is a no-op
- No data modification, no schema changes
- Flyway records the migration as applied, but no SQL actually runs

**Fresh DB Safety:** ✅ FIXES
- Creates tables that V20260807_3 depends on
- New database gets all required objects
- V20260807_3 succeeds

**Flyway Compatibility:** ✅ YES
- Follows established reconciliation pattern
- Version number falls between V20260717_6 (baseline) and V20260807_1 (next post-baseline)
- Out-of-order migration allowed (`FLYWAY_OUT_OF_ORDER=true`)
- Consistent with V20260718_1, V20260721_1, V20260721_2 pattern

**Risks:**
- Requires deploying a new migration file
- Flyway history will show one additional migration

**Verdict:** ⭐ RECOMMENDED

---

### Option B: Add `IF EXISTS` Guard to V20260807_3

**Description:** Modify V20260807_3 to check if `crm_tags` exists before creating the index.

**Implementation:**
```sql
-- Modify V20260807_3 to:
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'crm_tags') THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_crm_tags_tenant_name_ci
            ON crm_tags (tenant_id, LOWER(name));
    END IF;
END
$$;
```

**Production Safety:** ⚠️ RISKY
- Requires modifying an existing migration file
- Flyway may reject modified migrations (checksum mismatch)
- Existing production Flyway history has checksum for original V20260807_3

**Fresh DB Safety:** ✅ FIXES
- Index creation skipped if table doesn't exist
- V20260807_3 succeeds

**Flyway Compatibility:** ❌ NO
- Flyway validates checksums of applied migrations
- Changing V20260807_3 causes checksum mismatch on production
- Would require `flyway repair` or manual history manipulation

**Risks:**
- Checksum mismatch on production
- May require `flyway repair` (dangerous in production)
- Breaks Flyway's integrity guarantees

**Verdict:** ❌ NOT RECOMMENDED

---

### Option C: Reset Flyway Baseline

**Description:** Set baseline to V20260716_3 (or lower) and re-run all migrations.

**Implementation:**
```bash
# Set FLYWAY_BASELINE_VERSION=20260716.2 or lower
# Or use flyway baseline command
```

**Production Safety:** ❌ DANGEROUS
- Re-running migrations on production could cause data loss
- Some migrations are not idempotent (INSERT statements)
- Would need to drop and recreate tables
- High risk of breaking production data

**Fresh DB Safety:** ✅ FIXES
- All migrations applied from scratch
- Fresh database gets everything

**Flyway Compatibility:** ⚠️ PARTIAL
- Technically possible but requires manual intervention
- Flyway history would need to be cleared or reset
- Out-of-order migrations may cause conflicts

**Risks:**
- Data loss on production
- Migration conflicts
- High operational risk
- Requires extensive testing

**Verdict:** ❌ NOT RECOMMENDED

---

### Option D: Manual DB Provisioning Script

**Description:** Create a shell script that provisions a fresh database by running SQL files directly, bypassing Flyway.

**Implementation:**
```bash
#!/bin/bash
# provision-db.sh
psql -f migrations/V20260715_1__crm_core.sql
psql -f migrations/V20260716_1__crm_tasks.sql
psql -f migrations/V20260716_2__crm_notes.sql
psql -f migrations/V20260716_3__crm_tags.sql
# ... etc
```

**Production Safety:** ✅ SAFE
- Script only runs on fresh databases
- No changes to production

**Fresh DB Safety:** ⚠️ PARTIAL
- Creates objects but bypasses Flyway
- Flyway history is empty
- Subsequent Flyway migrations may conflict
- Not a long-term solution

**Flyway Compatibility:** ❌ NO
- Flyway expects to manage all schema changes
- Empty Flyway history after manual provisioning
- Future migrations may fail or apply duplicates

**Risks:**
- Flyway history inconsistency
- Future migration failures
- Maintenance burden
- Not a standard pattern

**Verdict:** ❌ NOT RECOMMENDED

---

### Option E: Delete V20260807_3 Migration

**Description:** Remove V20260807_3 from the migration directory so it never runs.

**Implementation:**
```bash
rm apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260807_3__add_case_insensitive_tag_unique_index.sql
```

**Production Safety:** ⚠️ RISKY
- Index already exists on production
- Removing migration doesn't affect existing production
- But Flyway history still has V20260807_3 as applied
- Checksum mismatch if file is removed

**Fresh DB Safety:** ✅ FIXES
- V20260807_3 never runs, no failure
- But crm_tags table still doesn't exist
- Application may fail at runtime when accessing tags

**Flyway Compatibility:** ❌ NO
- Flyway history references V20260807_3
- Removing file causes checksum mismatch
- Requires `flyway repair` or manual history manipulation

**Risks:**
- Runtime failures (tags feature broken)
- Checksum mismatch
- Incomplete fix

**Verdict:** ❌ NOT RECOMMENDED

---

## 4. Comparison Matrix

| Criteria | Option A | Option B | Option C | Option D | Option E |
|----------|----------|----------|----------|----------|----------|
| Production Safety | ✅ | ⚠️ | ❌ | ✅ | ⚠️ |
| Fresh DB Safety | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| Flyway Compatible | ✅ | ❌ | ⚠️ | ❌ | ❌ |
| Idempotent | ✅ | ✅ | ❌ | N/A | N/A |
| Follows Pattern | ✅ | ❌ | ❌ | ❌ | ❌ |
| Low Risk | ✅ | ❌ | ❌ | ❌ | ❌ |
| Long-term Solution | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Overall** | **⭐** | **❌** | **❌** | **❌** | **❌** |

---

## 5. Option A — Detailed Design

### 5.1 Migration File

```
File: apps/sanad-platform/src/main/resources/db/vendor/postgresql/V20260718_2__reconcile_crm_tags_after_baseline_gap.sql
```

### 5.2 Structure (following V20260718_1 pattern)

```
1. Header comment (purpose, baseline gap reference)
2. Precondition check block
   - Verify crm_tags does NOT exist OR already complete
   - Verify baseline gap exists (version 20260717.6 BASELINE)
3. CREATE TABLE crm_tags (IF NOT EXISTS)
4. CREATE TABLE crm_tag_assignments (IF NOT EXISTS)
5. Seed CRM.TAG.READ and CRM.TAG.WRITE capabilities
6. Grant capabilities to ADMIN role
7. Postcondition verification block
   - Verify 2 tables exist
   - Verify tenant_id columns
   - Verify tenant foreign keys
   - Verify indexes
   - Verify capabilities
```

### 5.3 Key SQL

```sql
-- Precondition
DO $precondition$
DECLARE
    tag_table_count INTEGER;
    baseline_gap_present BOOLEAN;
BEGIN
    SELECT COUNT(*) INTO tag_table_count
      FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name IN ('crm_tags', 'crm_tag_assignments');
    
    IF tag_table_count NOT IN (0, 2) THEN
        RAISE EXCEPTION 'CRM-Tags reconciliation refuses partial state: found % of 2 tables', tag_table_count;
    END IF;
    
    IF tag_table_count = 0 THEN
        SELECT EXISTS (
            SELECT 1 FROM flyway_schema_history
             WHERE version = '20260717.6' AND type = 'BASELINE' AND success = TRUE
        ) INTO baseline_gap_present;
        
        IF NOT baseline_gap_present THEN
            RAISE EXCEPTION 'CRM-Tags tables absent, but verified baseline gap not present';
        END IF;
    END IF;
END
$precondition$;

-- Tables (IF NOT EXISTS)
CREATE TABLE IF NOT EXISTS crm_tags (...);
CREATE TABLE IF NOT EXISTS crm_tag_assignments (...);

-- Capabilities
INSERT INTO access_capabilities (...) SELECT ... WHERE NOT EXISTS (...);
INSERT INTO role_capabilities (...) SELECT ... WHERE NOT EXISTS (...);

-- Postcondition
DO $postcondition$
BEGIN
    -- Verify 2 tables, tenant columns, foreign keys, indexes, capabilities
END
$postcondition$;
```

### 5.4 Version Number

- `V20260718_2` — Falls between V20260718_1 and V20260721_1
- Consistent with existing reconciliation migrations
- Out-of-order migration allowed (`FLYWAY_OUT_OF_ORDER=true`)

---

## 6. Recommendation

**Option A: Create Reconciliation Migration** is the ONLY option that:
- ✅ Is safe for production (no changes)
- ✅ Fixes fresh database provisioning
- ✅ Is compatible with Flyway
- ✅ Follows established patterns
- ✅ Is a long-term solution

All other options have critical flaws:
- Option B: Checksum mismatch
- Option C: Data loss risk
- Option D: Flyway inconsistency
- Option E: Incomplete fix

---

**Verdict:** MIGRATION LINEAGE REQUIRES REMEDIATION — Option A (reconciliation migration) is the recommended fix.
