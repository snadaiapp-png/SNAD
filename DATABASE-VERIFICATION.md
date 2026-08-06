# DATABASE VERIFICATION

**Audit Date:** 2026-08-03
**HEAD SHA:** `1356b902e11da10384cad00e537369c672ee6752`

---

## Migration Files

| # | File | Path | Lines | Purpose |
|---|------|------|-------|---------|
| 1 | `V20260716_1__create_crm_tasks.sql` | `db/migration/` | 102 | Create crm_tasks |
| 2 | `V20260716_2__create_crm_notes.sql` | `db/migration/` | 90 | Create crm_notes |
| 3 | `V20260717_6__create_crm_g1_extension_tables.sql` | `db/migration/` | 250 | Create 6 G1 extension tables |
| 4 | `V20260718_1__reconcile_crm_g1_after_baseline_gap.sql` | `db/vendor/postgresql/` | 442 | Reconciliation + postcondition verification |

**Total CRM-related SQL files:** 32

---

## Table Inventory — 8 Tables (PASS)

| # | Table | Source File | Line | Primary Key |
|---|-------|------------|------|-------------|
| 1 | `crm_tasks` | `V20260716_1` | 19 | `(tenant_id, id)` |
| 2 | `crm_notes` | `V20260716_2` | 18 | `(tenant_id, id)` |
| 3 | `crm_assignments` | `V20260717_6` | 19 | `(tenant_id, id)` |
| 4 | `crm_transfers` | `V20260717_6` | 55 | `(tenant_id, id)` |
| 5 | `crm_audit_logs` | `V20260717_6` | 107 | `(tenant_id, id)` |
| 6 | `crm_reports` | `V20260717_6` | 143 | `(tenant_id, id)` |
| 7 | `crm_phone_numbers` | `V20260717_6` | 180 | `(tenant_id, id)` |
| 8 | `crm_contact_lookup_index` | `V20260717_6` | 218 | `(tenant_id, id)` |

---

## tenant_id Column Verification — 8/8 (PASS)

Every table has `tenant_id UUID NOT NULL`:

| Table | File | Line |
|-------|------|------|
| crm_tasks | V20260716_1 | 21 |
| crm_notes | V20260716_2 | 20 |
| crm_assignments | V20260717_6 | 21 |
| crm_transfers | V20260717_6 | 57 |
| crm_audit_logs | V20260717_6 | 109 |
| crm_reports | V20260717_6 | 145 |
| crm_phone_numbers | V20260717_6 | 182 |
| crm_contact_lookup_index | V20260717_6 | 220 |

---

## Tenant FK Constraints — 8 Total (PASS)

| # | Constraint | Table | File | Line | SQL |
|---|-----------|-------|------|------|-----|
| 1 | `fk_crm_tasks_tenant` | crm_tasks | V20260716_1 | 55 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 2 | `fk_crm_notes_tenant` | crm_notes | V20260716_2 | 44 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 3 | `fk_crm_assignments_tenant` | crm_assignments | V20260717_6 | 40 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 4 | `fk_crm_transfers_tenant` | crm_transfers | V20260717_6 | 81 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 5 | `fk_crm_audit_logs_tenant` | crm_audit_logs | V20260717_6 | 126 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 6 | `fk_crm_reports_tenant` | crm_reports | V20260717_6 | 166 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 7 | `fk_crm_phone_numbers_tenant` | crm_phone_numbers | V20260717_6 | 202 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |
| 8 | `fk_crm_contact_lookup_tenant` | crm_contact_lookup_index | V20260717_6 | 236 | `FOREIGN KEY (tenant_id) REFERENCES tenants (id)` |

---

## Same-Tenant Composite FKs — 2 Total (PASS)

| # | Constraint | Child Table | Parent Table | SQL |
|---|-----------|-------------|--------------|-----|
| 1 | `fk_crm_phone_numbers_contact_same_tenant` | crm_phone_numbers | crm_contacts | `FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id)` |
| 2 | `fk_crm_contact_lookup_contact_same_tenant` | crm_contact_lookup_index | crm_contacts | `FOREIGN KEY (tenant_id, contact_id) REFERENCES crm_contacts (tenant_id, id)` |

---

## Explicit Indexes — 26 Total (PASS)

All 26 indexes have `tenant_id` as the leading column:

| # | Index Name | Table | Line |
|---|-----------|-------|------|
| 1 | `idx_crm_tasks_assignee_status` | crm_tasks | 64 |
| 2 | `idx_crm_tasks_related` | crm_tasks | 67 |
| 3 | `idx_crm_tasks_status_due` | crm_tasks | 70 |
| 4 | `idx_crm_notes_subject` | crm_notes | 52 |
| 5 | `idx_crm_notes_author` | crm_notes | 55 |
| 6 | `idx_crm_notes_active` | crm_notes | 58 |
| 7 | `idx_crm_assignments_subject_active` | crm_assignments | 48 |
| 8 | `idx_crm_assignments_user_active` | crm_assignments | 50 |
| 9 | `idx_crm_assignments_role_status` | crm_assignments | 52 |
| 10 | `idx_crm_transfers_subject_status` | crm_transfers | 100 |
| 11 | `idx_crm_transfers_recipient_status` | crm_transfers | 102 |
| 12 | `idx_crm_transfers_requested_at` | crm_transfers | 104 |
| 13 | `idx_crm_audit_logs_entity_time` | crm_audit_logs | 134 |
| 14 | `idx_crm_audit_logs_actor_time` | crm_audit_logs | 136 |
| 15 | `idx_crm_audit_logs_correlation` | crm_audit_logs | 138 |
| 16 | `idx_crm_audit_logs_action_time` | crm_audit_logs | 140 |
| 17 | `idx_crm_reports_owner_status` | crm_reports | 173 |
| 18 | `idx_crm_reports_type_status` | crm_reports | 175 |
| 19 | `idx_crm_reports_last_run` | crm_reports | 177 |
| 20 | `idx_crm_phone_numbers_contact` | crm_phone_numbers | 209 |
| 21 | `idx_crm_phone_numbers_e164` | crm_phone_numbers | 211 |
| 22 | `idx_crm_phone_numbers_primary` | crm_phone_numbers | 213 |
| 23 | `idx_crm_phone_numbers_verified` | crm_phone_numbers | 215 |
| 24 | `idx_crm_contact_lookup_phone` | crm_contact_lookup_index | 244 |
| 25 | `idx_crm_contact_lookup_email` | crm_contact_lookup_index | 246 |
| 26 | `idx_crm_contact_lookup_name` | crm_contact_lookup_index | 248 |

**Index prefix check:** 0 indexes without `tenant_id` as leading column.

---

## CHECK Constraints — 23 Total

| # | Table | Constraint | Purpose |
|---|-------|-----------|---------|
| 1 | crm_tasks | `ck_crm_tasks_related_type` | related_type IN valid values |
| 2 | crm_tasks | `ck_crm_tasks_status` | status IN OPEN/IN_PROGRESS/COMPLETED/CANCELLED |
| 3 | crm_tasks | `ck_crm_tasks_priority` | priority BETWEEN 0 AND 100 |
| 4 | crm_notes | `ck_crm_notes_subject_type` | subject_type IN 6 entity types |
| 5 | crm_notes | `ck_crm_notes_body_not_empty` | LENGTH(TRIM(body)) > 0 |
| 6 | crm_assignments | `ck_crm_assignments_subject_type` | subject_type IN 6 entity types |
| 7 | crm_assignments | `ck_crm_assignments_status` | status IN ACTIVE/ENDED/CANCELLED |
| 8 | crm_assignments | `ck_crm_assignments_dates` | ends_at IS NULL OR ends_at >= starts_at |
| 9 | crm_transfers | `ck_crm_transfers_subject_type` | subject_type IN 6 entity types |
| 10 | crm_transfers | `ck_crm_transfers_type` | transfer_type IN OWNERSHIP/ASSIGNMENT/QUEUE |
| 11 | crm_transfers | `ck_crm_transfers_status` | status IN 5 values |
| 12 | crm_transfers | `ck_crm_transfers_distinct_users` | from_user_id <> to_user_id |
| 13 | crm_transfers | `ck_crm_transfers_decision_time` | decided_at >= requested_at |
| 14 | crm_transfers | `ck_crm_transfers_completion_time` | completed_at >= requested_at |
| 15 | crm_audit_logs | `ck_crm_audit_logs_entity_type` | entity_type IN 14 entity types |
| 16 | crm_audit_logs | `ck_crm_audit_logs_action_not_empty` | LENGTH(TRIM(action_code)) > 0 |
| 17 | crm_reports | `ck_crm_reports_visibility` | visibility IN PRIVATE/TEAM/TENANT |
| 18 | crm_reports | `ck_crm_reports_status` | status IN DRAFT/ACTIVE/ARCHIVED |
| 19 | crm_reports | `ck_crm_reports_name_not_empty` | LENGTH(TRIM(name)) > 0 |
| 20 | crm_reports | `ck_crm_reports_definition_not_empty` | LENGTH(TRIM(definition_json)) > 0 |
| 21 | crm_phone_numbers | `ck_crm_phone_numbers_type` | phone_type IN MOBILE/WORK/HOME/FAX/OTHER |
| 22 | crm_phone_numbers | `ck_crm_phone_numbers_e164` | e164 LIKE '+%' AND LENGTH >= 4 |
| 23 | crm_contact_lookup_index | `ck_crm_contact_lookup_identifier` | At least one of phone/email/name non-null |

---

## UNIQUE Constraints — 8 Total

| # | Table | Constraint | Columns |
|---|-------|-----------|---------|
| 1 | crm_tasks | `uk_crm_tasks_tenant_id` | (tenant_id, id) |
| 2 | crm_notes | `uk_crm_notes_tenant_id` | (tenant_id, id) |
| 3 | crm_assignments | `uk_crm_assignments_tenant_id` | (tenant_id, id) |
| 4 | crm_transfers | `uk_crm_transfers_tenant_id` | (tenant_id, id) |
| 5 | crm_audit_logs | `uk_crm_audit_logs_tenant_id` | (tenant_id, id) |
| 6 | crm_reports | `uk_crm_reports_tenant_id` | (tenant_id, id) |
| 7 | crm_phone_numbers | `uk_crm_phone_numbers_tenant_id` | (tenant_id, id) |
| 8 | crm_contact_lookup_index | `uk_crm_contact_lookup_tenant_contact` | (tenant_id, contact_id) |

---

## Reconciliation Migration

**File:** `V20260718_1__reconcile_crm_g1_after_baseline_gap.sql` (442 lines)

Contains:
- Precondition check: verifies 0 or 8 tables exist (refuses partial state)
- All 8 CREATE TABLE IF NOT EXISTS statements
- All 26 CREATE INDEX IF NOT EXISTS statements
- 4 capability INSERTs (CRM.TASK.READ, CRM.TASK.WRITE, CRM.NOTE.READ, CRM.NOTE.WRITE)
- Postcondition block verifying: 8 tables, 8 tenant_id columns, 8 tenant FKs, 26 indexes, 0 non-tenant-first indexes, 2 same-tenant contact FKs, 4 capabilities

---

## DATABASE VERIFICATION SUMMARY

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| Total tables | 8 | 8 | ✅ PASS |
| tenant_id UUID NOT NULL | 8/8 | 8/8 | ✅ PASS |
| Tenant FKs → tenants(id) | 8 | 8 | ✅ PASS |
| Same-tenant composite FKs | 2 | 2 | ✅ PASS |
| Explicit indexes | 26 | 26 | ✅ PASS |
| Indexes leading with tenant_id | 26 | 26 | ✅ PASS |
| CHECK constraints | — | 23 | ✅ VERIFIED |
| UNIQUE constraints | — | 8 | ✅ VERIFIED |
| Reconciliation migration | — | Present | ✅ VERIFIED |

**RESULT: G1 DATABASE VERIFIED. All 8 tables, 26 indexes, 8 tenant FKs, 2 same-tenant FKs confirmed in migration SQL.**
