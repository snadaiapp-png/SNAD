# MISSION 5 — Migration Dependency Graph

**Generated:** 2026-08-07  
**Status:** READ-ONLY ANALYSIS  
**Scope:** Complete Flyway migration lineage with dependency chain visualization

---

## 1. Overview

This document maps the complete Flyway migration lineage for SNAD Platform, identifying all migrations, their relationships, and the baseline gap that caused the V20260716_3 (crm_tags) issue.

---

## 2. Migration Timeline

### 2.1 Pre-Baseline Migrations (V20260713_1 — V20260717_5)

These 28 migrations were **skipped by Flyway baseline 20260717.6**:

```
V20260713_1 — crm_idempotency_records (original)
V20260715_1 — crm_core_tables (accounts, contacts, leads, opportunities, pipelines, activities)
V20260716_1 — crm_tasks + CRM.TASK.READ/WRITE capabilities
V20260716_2 — crm_notes + CRM.NOTE.READ/WRITE capabilities
V20260716_3 — crm_tags + crm_tag_assignments + CRM.TAG.READ/WRITE capabilities  ← MISSING IN PRODUCTION
V20260716_4 — enterprise account columns + 5 enterprise tables
V20260717_1 — contact relationship model (5 tables)
V20260717_2 — (unknown — skipped)
V20260717_3 — (unknown — skipped)
V20260717_4 — (unknown — skipped)
V20260717_5 — (unknown — skipped)
```

### 2.2 Baseline Version (V20260717_6)

```
V20260717_6 — crm_g1_extension_tables (6 tables: assignments, transfers, audit_logs, reports, phone_numbers, contact_lookup_index)
  └── BASELINE VERSION (Flyway marks as "already applied" on all databases)
  └── Creates: crm_assignments, crm_transfers, crm_audit_logs, crm_reports, crm_phone_numbers, crm_contact_lookup_index
  └── Does NOT create: crm_tasks (V20260716_1), crm_notes (V20260716_2), crm_tags (V20260716_3)
```

### 2.3 Post-Baseline Migrations (V20260718_1+)

```
V20260718_1 — RECONCILIATION: crm_g1_after_baseline_gap
  ├── Creates 8 tables (crm_tasks, crm_notes, crm_assignments, crm_transfers, crm_audit_logs, crm_reports, crm_phone_numbers, crm_contact_lookup_index)
  ├── Creates 4 capabilities (CRM.TASK.READ/WRITE, CRM.NOTE.READ/WRITE)
  ├── Grant capabilities to ADMIN role
  └── Pattern: IF NOT EXISTS + precondition checks

V20260721_1 — RECONCILIATION: crm_contact_relationship_model_after_baseline_gap
  ├── Adds 5 columns to crm_contacts
  ├── Creates crm_contact_relationship_roles, crm_contact_account_relationships, crm_contact_relationship_history, crm_contact_ownership_history
  └── Backfills relationship data

V20260721_2 — RECONCILIATION: crm_idempotency_records_after_baseline_gap
  └── Creates crm_idempotency_records with indexes

V20260807_1 — grant_crm_capabilities_to_non_admin_roles
  └── Grants CRM capabilities to VIEWER, MEMBER, MANAGER, ORG_ADMIN, SALES_MANAGER, SALES_REPRESENTATIVE

V20260807_2 — seed_default_pipeline_and_accounts
  └── Seeds default Sales Pipeline with 5 stages + 2 sample accounts per ACTIVE tenant

V20260807_3 — add_case_insensitive_tag_unique_index  ← BLOCKER ON FRESH DB
  └── Creates index ON crm_tags (which does NOT exist in fresh DB)
  └── Will fail with: ERROR: relation "crm_tags" does not exist

V20260807_4 — add_activity_result_column_and_related_type_check
  └── Adds result column + CHECK constraint to crm_activities
```

---

## 3. Dependency Chain Visualization

```
PRE-BASELINE (skipped by baseline 20260717.6)
│
├── V20260713_1 (crm_idempotency_records)
│   └── Reconciled by: V20260721_2 ✅
│
├── V20260715_1 (crm_core_tables)
│   └── Reconciled by: (implicitly in V20260717_6 baseline) ✅
│
├── V20260716_1 (crm_tasks + capabilities)
│   └── Reconciled by: V20260718_1 ✅
│
├── V20260716_2 (crm_notes + capabilities)
│   └── Reconciled by: V20260718_1 ✅
│
├── V20260716_3 (crm_tags + crm_tag_assignments + capabilities)
│   └── Reconciled by: ❌ NONE — MANUALLY APPLIED TO PRODUCTION ONLY
│   └── Fresh DB impact: ❌ BLOCKS V20260807_3
│
├── V20260716_4 (enterprise account columns + 5 tables)
│   └── Reconciled by: ❌ NONE — but no downstream migration depends on it
│
└── V20260717_1 (contact relationship model)
    └── Reconciled by: V20260721_1 ✅

BASELINE
│
└── V20260717_6 (crm_g1_extension_tables — 6 tables)
    └── Applied to all databases ✅

POST-BASELINE
│
├── V20260718_1 (RECONCILIATION: crm_g1)
│   └── Depends on: V20260717_6 (baseline) ✅
│   └── Safe for clean installs (IF NOT EXISTS) ✅
│
├── V20260721_1 (RECONCILIATION: contact relationships)
│   └── Depends on: crm_contacts, crm_accounts tables ✅
│   └── Safe for clean installs (ADD COLUMN IF NOT EXISTS) ✅
│
├── V20260721_2 (RECONCILIATION: idempotency records)
│   └── Depends on: none (self-contained) ✅
│   └── Safe for clean installs (CREATE TABLE IF NOT EXISTS) ✅
│
├── V20260807_1 (grant capabilities)
│   └── Depends on: access_capabilities, roles tables ✅
│   └── Safe for clean installs (WHERE NOT EXISTS) ✅
│
├── V20260807_2 (seed pipeline)
│   └── Depends on: crm_pipelines, crm_accounts tables ✅
│   └── Safe for clean installs (WHERE NOT EXISTS) ✅
│
├── V20260807_3 (tag unique index)  ← BLOCKER
│   └── Depends on: crm_tags table ❌ (does NOT exist in fresh DB)
│   └── Will fail on clean install ❌
│
└── V20260807_4 (activity result column)
    └── Depends on: crm_activities table ✅
    └── Safe for clean installs (ADD COLUMN IF NOT EXISTS) ✅
```

---

## 4. Critical Dependency Chain

```
V20260716_3 (skipped by baseline)
    │
    ▼
crm_tags table (does NOT exist in fresh DB)
    │
    ▼
V20260807_3 (creates index ON crm_tags)
    │
    ▼
❌ FAILS: relation "crm_tags" does not exist
```

---

## 5. Reconciliation Migration Pattern

All existing reconciliation migrations follow the same pattern:

### Pattern A: Precondition Check (V20260718_1)
```sql
DO $precondition$
DECLARE
    table_count INTEGER;
    baseline_gap_present BOOLEAN;
BEGIN
    -- Check if tables exist
    SELECT COUNT(*) INTO table_count FROM information_schema.tables WHERE ...;
    
    IF table_count NOT IN (0, expected_count) THEN
        RAISE EXCEPTION 'Partial state detected: found % of % tables', table_count, expected_count;
    END IF;
    
    IF table_count = 0 THEN
        -- Verify baseline gap exists
        SELECT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '...' AND type = 'BASELINE') INTO baseline_gap_present;
        IF NOT baseline_gap_present THEN
            RAISE EXCEPTION 'Tables absent but baseline gap not found';
        END IF;
    END IF;
END
$precondition$;
```

### Pattern B: Idempotent DDL (V20260721_1, V20260721_2)
```sql
CREATE TABLE IF NOT EXISTS crm_... (...);
ALTER TABLE crm_... ADD COLUMN IF NOT EXISTS ...;
```

### Pattern C: Postcondition Verification (V20260718_1)
```sql
DO $postcondition$
BEGIN
    -- Verify tables, columns, indexes, capabilities exist
    IF table_count <> expected THEN
        RAISE EXCEPTION 'Reconciliation failed: expected % tables, found %', expected, actual;
    END IF;
END
$postcondition$;
```

---

## 6. Missing Reconciliation Analysis

### V20260716_3 — crm_tags + crm_tag_assignments

**Status:** Skipped by baseline, NO reconciliation migration exists.

**Impact:**
- Production: Manually applied SQL (tables exist, 0 rows)
- Fresh DB: Tables do NOT exist → V20260807_3 fails

**Objects missing:**
1. `crm_tags` table (2 columns, 4 constraints, 1 index)
2. `crm_tag_assignments` table (2 columns, 4 constraints, 2 indexes)
3. `CRM.TAG.READ` capability
4. `CRM.TAG.WRITE` capability
5. Capability grant to ADMIN role

**Reconciliation needed:** Yes — create V20260718_2 (or similar) following established pattern.

### V20260716_4 — Enterprise Account Columns + 5 Tables

**Status:** Skipped by baseline, NO reconciliation migration exists.

**Impact:**
- Production: Tables/columns exist (created by earlier manual intervention or Hibernate)
- Fresh DB: Tables/columns do NOT exist → application may fail at runtime

**Objects missing:**
1. Enterprise columns on crm_accounts (5 columns)
2. crm_account_addresses table
3. crm_account_identifiers table
4. crm_account_relationships table
5. crm_account_status_history table
6. crm_account_merge_history table

**Reconciliation needed:** Yes — but no downstream migration depends on these, so lower priority.

---

## 7. Summary

| Migration | Status | Reconciled | Fresh DB Impact |
|-----------|--------|------------|-----------------|
| V20260713_1 | Skipped | ✅ V20260721_2 | None |
| V20260715_1 | Skipped | ✅ (baseline) | None |
| V20260716_1 | Skipped | ✅ V20260718_1 | None |
| V20260716_2 | Skipped | ✅ V20260718_1 | None |
| **V20260716_3** | **Skipped** | **❌ NONE** | **❌ BLOCKS V20260807_3** |
| V20260716_4 | Skipped | ❌ NONE | Runtime risk |
| V20260717_1 | Skipped | ✅ V20260721_1 | None |
| V20260717_6 | Baseline | N/A | N/A |
| V20260718_1 | Post-baseline | N/A | None (IF NOT EXISTS) |
| V20260721_1 | Post-baseline | N/A | None (IF NOT EXISTS) |
| V20260721_2 | Post-baseline | N/A | None (IF NOT EXISTS) |
| V20260807_1 | Post-baseline | N/A | None (WHERE NOT EXISTS) |
| V20260807_2 | Post-baseline | N/A | None (WHERE NOT EXISTS) |
| **V20260807_3** | **Post-baseline** | **N/A** | **❌ FAILS (depends on crm_tags)** |
| V20260807_4 | Post-baseline | N/A | None (IF NOT EXISTS) |

---

**Verdict:** MIGRATION LINEAGE REQUIRES REMEDIATION — V20260716_3 has no reconciliation migration, causing fresh database provisioning failure at V20260807_3.
