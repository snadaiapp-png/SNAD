# CRM-007-DATA-002: Customer (Account) Model Validation

> **Task:** TASK 2 — ENTITY MODEL VALIDATION (Customer)
> **Date:** 2026-07-28
> **Status:** PASS

---

## Table: `crm_accounts`

### Column Inventory

| Column | Type | Nullable | Default | Status |
|---|---|---|---|---|
| `id` | UUID | NO | — | PASS |
| `tenant_id` | UUID | NO | — | PASS |
| `version` | BIGINT | NO | 0 | PASS |
| `display_name` | VARCHAR(240) | NO | — | PASS |
| `normalized_name` | VARCHAR(240) | NO | — | PASS |
| `account_type` | VARCHAR(40) | NO | — | PASS |
| `lifecycle_status` | VARCHAR(32) | NO | 'ACTIVE' | PASS |
| `parent_account_id` | UUID | YES | — | PASS |
| `owner_user_id` | UUID | YES | — | PASS |
| `primary_currency_code` | VARCHAR(3) | YES | — | PASS |
| `preferred_locale` | VARCHAR(35) | YES | — | PASS |
| `time_zone` | VARCHAR(64) | YES | — | PASS |
| `source` | VARCHAR(80) | YES | — | PASS |
| `created_by` | UUID | NO | — | PASS |
| `updated_by` | UUID | NO | — | PASS |
| `created_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |
| `archived_at` | TIMESTAMP WITH TIME ZONE | YES | — | PASS |

---

## Customer Identity

| Field | Purpose | Status |
|---|---|---|
| `id` | Primary identifier (UUID) | PASS |
| `tenant_id` | Tenant isolation | PASS |
| `display_name` | Human-readable name | PASS |
| `normalized_name` | Search-optimized name | PASS |
| `account_type` | BUSINESS, PERSON, PARTNER, PROSPECT, OTHER | PASS |

---

## Contact Information

| Field | Purpose | Status |
|---|---|---|
| `preferred_locale` | Language preference (ar-SA, en-US) | PASS |
| `time_zone` | Timezone (Asia/Riyadh) | PASS |
| `primary_currency_code` | Currency (SAR) | PASS |

---

## Customer Lifecycle

| Field | Purpose | Status |
|---|---|---|
| `lifecycle_status` | ACTIVE, INACTIVE, ARCHIVED | PASS |
| `archived_at` | Archive timestamp | PASS |
| `version` | Optimistic concurrency | PASS |

---

## Relationships

| Relationship | Target Table | FK Constraint | Status |
|---|---|---|---|
| Parent Account | `crm_accounts` (self) | `fk_crm_accounts_parent_same_tenant` | PASS |
| Contacts | `crm_contacts` | `fk_crm_contacts_account_same_tenant` | PASS |
| Opportunities | `crm_opportunities` | `fk_crm_opportunities_account_same_tenant` | PASS |
| Leads (converted) | `crm_leads` | `fk_crm_leads_converted_account_same_tenant` | PASS |
| Activities | `crm_activities` | Application-level (related_type = 'ACCOUNT') | PASS |
| Timeline Events | `crm_timeline_events` | Application-level (subject_type = 'ACCOUNT') | PASS |

---

## Audit Fields

| Field | Purpose | Status |
|---|---|---|
| `created_by` | Creation user | PASS |
| `updated_by` | Last update user | PASS |
| `created_at` | Creation timestamp | PASS |
| `updated_at` | Last update timestamp | PASS |

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Customer identity | PASS |
| Contact information | PASS |
| Customer lifecycle | PASS |
| Relationships validated | PASS |
| Audit fields present | PASS |

---

**Result:** PASS
