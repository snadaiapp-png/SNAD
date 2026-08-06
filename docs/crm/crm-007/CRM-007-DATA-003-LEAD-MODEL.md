# CRM-007-DATA-003: Lead Model Validation

> **Task:** TASK 2 — ENTITY MODEL VALIDATION (Lead)
> **Date:** 2026-07-28
> **Status:** PASS

---

## Table: `crm_leads`

### Column Inventory

| Column | Type | Nullable | Default | Status |
|---|---|---|---|---|
| `id` | UUID | NO | — | PASS |
| `tenant_id` | UUID | NO | — | PASS |
| `version` | BIGINT | NO | 0 | PASS |
| `display_name` | VARCHAR(240) | NO | — | PASS |
| `normalized_name` | VARCHAR(240) | NO | — | PASS |
| `company_name` | VARCHAR(240) | YES | — | PASS |
| `email` | VARCHAR(255) | YES | — | PASS |
| `normalized_email` | VARCHAR(255) | YES | — | PASS |
| `phone` | VARCHAR(64) | YES | — | PASS |
| `source` | VARCHAR(120) | YES | — | PASS |
| `status` | VARCHAR(32) | NO | 'NEW' | PASS |
| `owner_user_id` | UUID | YES | — | PASS |
| `queue_id` | UUID | YES | — | PASS |
| `score` | NUMERIC(8,3) | YES | — | PASS |
| `converted_account_id` | UUID | YES | — | PASS |
| `converted_contact_id` | UUID | YES | — | PASS |
| `converted_opportunity_id` | UUID | YES | — | PASS |
| `created_by` | UUID | NO | — | PASS |
| `updated_by` | UUID | NO | — | PASS |
| `created_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |

---

## Lead Identity

| Field | Purpose | Status |
|---|---|---|
| `id` | Primary identifier (UUID) | PASS |
| `tenant_id` | Tenant isolation | PASS |
| `display_name` | Lead name | PASS |
| `normalized_name` | Search-optimized name | PASS |
| `company_name` | Company affiliation | PASS |

---

## Source Tracking

| Field | Purpose | Status |
|---|---|---|
| `source` | Lead origin (WEB, PHONE, WHATSAPP, etc.) | PASS |
| `score` | Lead scoring | PASS |

---

## Status Lifecycle

| Status | Description | Status |
|---|---|---|
| `NEW` | Initial state | PASS |
| `ASSIGNED` | Assigned to user | PASS |
| `CONTACTED` | Contact attempted | PASS |
| `QUALIFIED` | Qualified for conversion | PASS |
| `DISQUALIFIED` | Not qualified | PASS |
| `CONVERTED` | Converted to customer | PASS |
| `ARCHIVED` | Archived | PASS |

---

## Conversion References

| Field | Purpose | Status |
|---|---|---|
| `converted_account_id` | Created customer | PASS |
| `converted_contact_id` | Created contact | PASS |
| `converted_opportunity_id` | Created opportunity | PASS |

---

## Relationships

| Relationship | Target Table | FK Constraint | Status |
|---|---|---|---|
| Converted Account | `crm_accounts` | `fk_crm_leads_converted_account_same_tenant` | PASS |
| Converted Contact | `crm_contacts` | `fk_crm_leads_converted_contact_same_tenant` | PASS |
| Converted Opportunity | `crm_opportunities` | `fk_crm_leads_converted_opportunity_same_tenant` | PASS |

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Lead identity | PASS |
| Source tracking | PASS |
| Status lifecycle | PASS |
| Conversion references | PASS |
| Activity history | PASS (via timeline) |

---

**Result:** PASS
