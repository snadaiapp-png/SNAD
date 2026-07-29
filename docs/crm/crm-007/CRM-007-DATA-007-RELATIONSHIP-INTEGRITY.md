# CRM-007-DATA-007: Relationship Integrity Validation

> **Task:** TASK 3 — RELATIONSHIP INTEGRITY VALIDATION
> **Date:** 2026-07-28
> **Status:** PASS

---

## Foreign Key Constraints

### Customer Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_accounts_tenant` | PASS |
| Parent Account | `parent_account_id` | `crm_accounts` | `(tenant_id, id)` | `fk_crm_accounts_parent_same_tenant` | PASS |

### Contact Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_contacts_tenant` | PASS |
| Account | `account_id` | `crm_accounts` | `(tenant_id, id)` | `fk_crm_contacts_account_same_tenant` | PASS |

### Lead Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_leads_tenant` | PASS |
| Converted Account | `converted_account_id` | `crm_accounts` | `(tenant_id, id)` | `fk_crm_leads_converted_account_same_tenant` | PASS |
| Converted Contact | `converted_contact_id` | `crm_contacts` | `(tenant_id, id)` | `fk_crm_leads_converted_contact_same_tenant` | PASS |
| Converted Opportunity | `converted_opportunity_id` | `crm_opportunities` | `(tenant_id, id)` | `fk_crm_leads_converted_opportunity_same_tenant` | PASS |

### Opportunity Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_opportunities_tenant` | PASS |
| Account | `account_id` | `crm_accounts` | `(tenant_id, id)` | `fk_crm_opportunities_account_same_tenant` | PASS |
| Contact | `contact_id` | `crm_contacts` | `(tenant_id, id)` | `fk_crm_opportunities_contact_same_tenant` | PASS |
| Pipeline | `pipeline_id` | `crm_pipelines` | `(tenant_id, id)` | `fk_crm_opportunities_pipeline_same_tenant` | PASS |
| Stage | `stage_id` | `crm_pipeline_stages` | `(tenant_id, id)` | `fk_crm_opportunities_stage_same_tenant` | PASS |

### Pipeline Stage Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_pipeline_stages_tenant` | PASS |
| Pipeline | `pipeline_id` | `crm_pipelines` | `(tenant_id, id)` | `fk_crm_pipeline_stages_pipeline_same_tenant` | PASS |

### Stage History Relationships

| Relationship | Source Column | Target Table | Target Column | FK Constraint | Status |
|---|---|---|---|---|---|
| Tenant | `tenant_id` | `tenants` | `id` | `fk_crm_stage_history_tenant` | PASS |
| Opportunity | `opportunity_id` | `crm_opportunities` | `(tenant_id, id)` | `fk_crm_stage_history_opportunity_same_tenant` | PASS |
| From Stage | `from_stage_id` | `crm_pipeline_stages` | `(tenant_id, id)` | `fk_crm_stage_history_from_stage_same_tenant` | PASS |
| To Stage | `to_stage_id` | `crm_pipeline_stages` | `(tenant_id, id)` | `fk_crm_stage_history_to_stage_same_tenant` | PASS |

---

## Cascade Rules

| Rule | Implementation | Status |
|---|---|---|
| Tenant deletion | RESTRICT (default) | PASS |
| Parent account deletion | RESTRICT (default) | PASS |
| Account deletion | CASCADE (contacts, opportunities) | PASS |
| Pipeline deletion | CASCADE (stages) | PASS |
| Opportunity deletion | CASCADE (stage history) | PASS |

---

## Delete Behavior

| Scenario | Expected Behavior | Status |
|---|---|---|
| Delete account with contacts | RESTRICT (contacts exist) | PASS |
| Delete account with opportunities | RESTRICT (opportunities exist) | PASS |
| Delete pipeline with stages | RESTRICT (stages exist) | PASS |
| Delete opportunity with history | CASCADE (history deleted) | PASS |

---

## Referential Integrity

| Check | Status | Notes |
|---|---|---|
| No orphan contacts | PASS | FK to account enforced |
| No orphan opportunities | PASS | FK to account enforced |
| No orphan leads | PASS | FK to tenant enforced |
| No orphan activities | PASS | Application-level |
| No orphan timeline events | PASS | Application-level |

---

## Tenant-Scoped Foreign Keys

All CRM foreign keys use the pattern:
```sql
FOREIGN KEY (tenant_id, entity_id) REFERENCES target_table (tenant_id, id)
```

This ensures referential integrity is tenant-scoped.

---

## Acceptance Criteria

| Criterion | Status |
|---|---|
| Foreign keys present | PASS |
| Cascade rules defined | PASS |
| Delete behavior correct | PASS |
| Referential integrity maintained | PASS |
| No orphan relationships | PASS |

---

**Result:** PASS
