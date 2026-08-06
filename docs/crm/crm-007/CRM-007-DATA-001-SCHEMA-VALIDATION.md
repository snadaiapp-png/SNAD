# CRM-007-DATA-001: Schema Baseline Validation

> **Task:** TASK 1 — SCHEMA BASELINE VALIDATION
> **Date:** 2026-07-28
> **Status:** PASS

---

## Schema Overview

### Migration File

| Field | Value |
|---|---|
| File | `V20260702_1__create_unified_crm_core.sql` |
| Database | PostgreSQL |
| Status | MERGED / PRODUCTION VERIFIED |

---

## Tables Created

| Table | Primary Key | Status |
|---|---|---|
| `crm_accounts` | UUID | PASS |
| `crm_contacts` | UUID | PASS |
| `crm_pipelines` | UUID | PASS |
| `crm_pipeline_stages` | UUID | PASS |
| `crm_leads` | UUID | PASS |
| `crm_opportunities` | UUID | PASS |
| `crm_opportunity_stage_history` | UUID | PASS |
| `crm_activities` | UUID | PASS |
| `crm_timeline_events` | UUID | PASS |
| `crm_import_jobs` | UUID | PASS |
| `crm_custom_field_definitions` | UUID | PASS |

**Total:** 11 tables in core migration

---

## Naming Conventions

| Pattern | Status | Notes |
|---|---|---|
| Table prefix `crm_` | PASS | Consistent across all tables |
| Column naming | PASS | snake_case |
| Constraint naming | PASS | Standard PostgreSQL naming |
| Index naming | PASS | `idx_` prefix |

---

## Primary Keys

| Table | PK Column | Type | Status |
|---|---|---|---|
| `crm_accounts` | `id` | UUID | PASS |
| `crm_contacts` | `id` | UUID | PASS |
| `crm_pipelines` | `id` | UUID | PASS |
| `crm_pipeline_stages` | `id` | UUID | PASS |
| `crm_leads` | `id` | UUID | PASS |
| `crm_opportunities` | `id` | UUID | PASS |
| `crm_opportunity_stage_history` | `id` | UUID | PASS |
| `crm_activities` | `id` | UUID | PASS |
| `crm_timeline_events` | `id` | UUID | PASS |
| `crm_import_jobs` | `id` | UUID | PASS |
| `crm_custom_field_definitions` | `id` | UUID | PASS |

---

## Unique Constraints

| Table | Constraint | Columns | Status |
|---|---|---|---|
| `crm_accounts` | `uk_crm_accounts_tenant_id` | (tenant_id, id) | PASS |
| `crm_contacts` | `uk_crm_contacts_tenant_id` | (tenant_id, id) | PASS |
| `crm_pipelines` | `uk_crm_pipelines_tenant_id` | (tenant_id, id) | PASS |
| `crm_pipelines` | `uk_crm_pipelines_tenant_name` | (tenant_id, name) | PASS |
| `crm_pipeline_stages` | `uk_crm_pipeline_stages_tenant_id` | (tenant_id, id) | PASS |
| `crm_pipeline_stages` | `uk_crm_pipeline_stages_sequence` | (tenant_id, pipeline_id, sequence) | PASS |
| `crm_pipeline_stages` | `uk_crm_pipeline_stages_name` | (tenant_id, pipeline_id, name) | PASS |
| `crm_leads` | `uk_crm_leads_tenant_id` | (tenant_id, id) | PASS |
| `crm_opportunities` | `uk_crm_opportunities_tenant_id` | (tenant_id, id) | PASS |
| `crm_activities` | `uk_crm_activities_tenant_id` | (tenant_id, id) | PASS |
| `crm_custom_field_definitions` | `uk_crm_custom_fields_tenant_entity_key` | (tenant_id, entity_type, field_key) | PASS |

---

## Required Fields

| Table | Required Fields | Status |
|---|---|---|
| `crm_accounts` | id, tenant_id, display_name, normalized_name, account_type, created_by, updated_by, created_at, updated_at | PASS |
| `crm_contacts` | id, tenant_id, given_name, display_name, normalized_name, consent_summary, created_by, updated_by, created_at, updated_at | PASS |
| `crm_leads` | id, tenant_id, display_name, normalized_name, status, created_by, updated_by, created_at, updated_at | PASS |
| `crm_opportunities` | id, tenant_id, pipeline_id, stage_id, name, currency_code, created_by, updated_by, created_at, updated_at | PASS |
| `crm_activities` | id, tenant_id, activity_type, subject, status, priority, created_by, updated_by, created_at, updated_at | PASS |

---

## Check Constraints

| Table | Constraint | Validation | Status |
|---|---|---|---|
| `crm_accounts` | `ck_crm_accounts_type` | account_type IN ('BUSINESS','PERSON','PARTNER','PROSPECT','OTHER') | PASS |
| `crm_accounts` | `ck_crm_accounts_status` | lifecycle_status IN ('ACTIVE','INACTIVE','ARCHIVED') | PASS |
| `crm_accounts` | `ck_crm_accounts_currency` | CHAR_LENGTH(primary_currency_code) = 3 | PASS |
| `crm_accounts` | `ck_crm_accounts_parent_not_self` | parent_account_id <> id | PASS |
| `crm_contacts` | `ck_crm_contacts_status` | lifecycle_status IN ('ACTIVE','INACTIVE','ARCHIVED') | PASS |
| `crm_contacts` | `ck_crm_contacts_consent` | consent_summary IN ('UNKNOWN','GRANTED','DENIED','WITHDRAWN') | PASS |
| `crm_pipeline_stages` | `ck_crm_pipeline_stage_sequence` | sequence >= 0 | PASS |
| `crm_pipeline_stages` | `ck_crm_pipeline_stage_probability` | probability BETWEEN 0 AND 100 | PASS |
| `crm_pipeline_stages` | `ck_crm_pipeline_stage_terminal` | terminal_state IN ('WON','LOST') | PASS |
| `crm_leads` | `ck_crm_leads_status` | status IN ('NEW','ASSIGNED','CONTACTED','QUALIFIED','DISQUALIFIED','CONVERTED','ARCHIVED') | PASS |
| `crm_opportunities` | `ck_crm_opportunities_currency` | CHAR_LENGTH(currency_code) = 3 | PASS |
| `crm_opportunities` | `ck_crm_opportunities_probability` | probability BETWEEN 0 AND 100 | PASS |
| `crm_opportunities` | `ck_crm_opportunities_status` | status IN ('OPEN','WON','LOST','CANCELLED','ARCHIVED') | PASS |
| `crm_activities` | `ck_crm_activities_type` | activity_type IN ('TASK','CALL','MEETING','EMAIL','NOTE','MESSAGE','OTHER') | PASS |
| `crm_activities` | `ck_crm_activities_status` | status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED','ARCHIVED') | PASS |
| `crm_activities` | `ck_crm_activities_priority` | priority BETWEEN 0 AND 100 | PASS |

---

## Indexes

| Table | Index | Columns | Status |
|---|---|---|---|
| `crm_accounts` | `idx_crm_accounts_tenant_status` | (tenant_id, lifecycle_status, updated_at DESC) | PASS |
| `crm_accounts` | `idx_crm_accounts_tenant_name` | (tenant_id, normalized_name, id) | PASS |
| `crm_accounts` | `idx_crm_accounts_tenant_owner` | (tenant_id, owner_user_id, lifecycle_status) | PASS |
| `crm_contacts` | `idx_crm_contacts_tenant_account` | (tenant_id, account_id, updated_at DESC) | PASS |
| `crm_contacts` | `idx_crm_contacts_name` | (tenant_id, normalized_name, id) | PASS |
| `crm_contacts` | `idx_crm_contacts_email` | (tenant_id, normalized_email) | PASS |
| `crm_contacts` | `idx_crm_contacts_owner` | (tenant_id, owner_user_id, lifecycle_status) | PASS |
| `crm_leads` | `idx_crm_leads_tenant_status_owner` | (tenant_id, status, owner_user_id, updated_at DESC) | PASS |
| `crm_leads` | `idx_crm_leads_email` | (tenant_id, normalized_email) | PASS |
| `crm_opportunities` | `idx_crm_opportunities_pipeline` | (tenant_id, pipeline_id, stage_id, status, updated_at DESC) | PASS |
| `crm_opportunities` | `idx_crm_opportunities_owner` | (tenant_id, owner_user_id, status, expected_close_date) | PASS |
| `crm_stage_history` | `idx_crm_stage_history_timeline` | (tenant_id, opportunity_id, changed_at DESC) | PASS |
| `crm_activities` | `idx_crm_activities_timeline` | (tenant_id, related_type, related_id, created_at DESC) | PASS |
| `crm_activities` | `idx_crm_activities_owner_due` | (tenant_id, owner_user_id, status, due_at) | PASS |
| `crm_timeline_events` | `idx_crm_timeline_subject` | (tenant_id, subject_type, subject_id, occurred_at DESC) | PASS |

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Schema validates successfully | PASS |
| No structural errors | PASS |
| Naming consistency | PASS |
| Primary keys present | PASS |
| Required fields defined | PASS |
| Check constraints present | PASS |
| Indexes present | PASS |

---

**Result:** PASS
