# CRM-007-DATA-004: Job (Activity) Model Validation

> **Task:** TASK 2 — ENTITY MODEL VALIDATION (Job)
> **Date:** 2026-07-28
> **Status:** PASS

---

## Table: `crm_activities`

### Column Inventory

| Column | Type | Nullable | Default | Status |
|---|---|---|---|---|
| `id` | UUID | NO | — | PASS |
| `tenant_id` | UUID | NO | — | PASS |
| `version` | BIGINT | NO | 0 | PASS |
| `activity_type` | VARCHAR(20) | NO | — | PASS |
| `subject` | VARCHAR(240) | NO | — | PASS |
| `description` | TEXT | YES | — | PASS |
| `status` | VARCHAR(20) | NO | 'OPEN' | PASS |
| `priority` | INTEGER | NO | 50 | PASS |
| `due_at` | TIMESTAMP WITH TIME ZONE | YES | — | PASS |
| `completed_at` | TIMESTAMP WITH TIME ZONE | YES | — | PASS |
| `result` | VARCHAR(40) | YES | — | PASS |
| `related_type` | VARCHAR(30) | YES | — | PASS |
| `related_id` | UUID | YES | — | PASS |
| `owner_user_id` | UUID | YES | — | PASS |
| `created_by` | UUID | NO | — | PASS |
| `updated_by` | UUID | NO | — | PASS |
| `created_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |
| `updated_at` | TIMESTAMP WITH TIME ZONE | NO | — | PASS |

---

## Scheduling Data

| Field | Purpose | Status |
|---|---|---|
| `due_at` | Scheduled date/time | PASS |
| `priority` | Priority level (0-100) | PASS |

---

## Activity Types

| Type | Description | Status |
|---|---|---|
| `TASK` | Standard task | PASS |
| `CALL` | Phone call | PASS |
| `MEETING` | Meeting | PASS |
| `EMAIL` | Email | PASS |
| `NOTE` | Note | PASS |
| `MESSAGE` | Message | PASS |
| `OTHER` | Other | PASS |

---

## Status Lifecycle

| Status | Description | Status |
|---|---|---|
| `OPEN` | Initial state | PASS |
| `IN_PROGRESS` | In progress | PASS |
| `COMPLETED` | Completed | PASS |
| `CANCELLED` | Cancelled | PASS |
| `ARCHIVED` | Archived | PASS |

---

## Customer Relationship

| Field | Purpose | Status |
|---|---|---|
| `related_type` | Entity type (ACCOUNT, CONTACT, LEAD, OPPORTUNITY) | PASS |
| `related_id` | Entity UUID | PASS |

---

## Team Assignment

| Field | Purpose | Status |
|---|---|---|
| `owner_user_id` | Assigned user | PASS |

---

## Service Lifecycle

| Field | Purpose | Status |
|---|---|---|
| `status` | Current status | PASS |
| `completed_at` | Completion timestamp | PASS |
| `result` | Completion result | PASS |

---

## Relationships

| Relationship | Target Table | FK Constraint | Status |
|---|---|---|---|
| Related Entity | Application-level | `related_type` + `related_id` | PASS |
| Owner | `users` | Application-level | PASS |

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Scheduling data | PASS |
| Customer relationship | PASS |
| Team assignment | PASS |
| Service lifecycle | PASS |
| Payment relationship | N/A (ERP scope) |

---

**Result:** PASS
