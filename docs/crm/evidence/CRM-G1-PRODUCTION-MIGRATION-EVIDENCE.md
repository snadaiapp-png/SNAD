# CRM-G1 Production Migration Evidence

> **Evidence status:** `PENDING_PRODUCTION_EXECUTION`
>
> This record must not be changed to `COMPLETE` until every required field is
> populated from an actual production execution and independently reviewed.

## 1. Change identity

| Field | Required value |
|---|---|
| Exact deployed commit SHA | `PENDING` |
| Branch or release tag | `PENDING` |
| Change ticket / release reference | `PENDING` |
| Production environment identifier | `PENDING` |
| Migration operator | `PENDING` |
| Database owner approver | `PENDING` |
| Application owner approver | `PENDING` |
| Execution started at (UTC) | `PENDING` |
| Execution completed at (UTC) | `PENDING` |

## 2. Backup and rollback readiness

| Control | Evidence |
|---|---|
| Verified backup / restore-point reference | `PENDING` |
| Backup verification timestamp | `PENDING` |
| Restore test or assurance reference | `PENDING` |
| Rollback decision owner | `PENDING` |
| Previous approved application SHA | `PENDING` |

## 3. Flyway evidence

| Check | Expected | Observed |
|---|---|---|
| Pre-migration `flyway:validate` | PASS | `PENDING` |
| Target migration | `20260717.6` | `PENDING` |
| Description | `complete crm g1 extension tables` | `PENDING` |
| Type | `SQL` | `PENDING` |
| Successful history rows | exactly `1` | `PENDING` |
| Post-migration `flyway:validate` | PASS | `PENDING` |

Attach or link the redacted Flyway output:

- Artifact / log reference: `PENDING`
- Integrity hash: `PENDING`

## 4. Schema inventory

The following tables must be present:

- [ ] `crm_tasks`
- [ ] `crm_assignments`
- [ ] `crm_transfers`
- [ ] `crm_notes`
- [ ] `crm_audit_logs`
- [ ] `crm_reports`
- [ ] `crm_phone_numbers`
- [ ] `crm_contact_lookup_index`

Observed table inventory artifact: `PENDING`

## 5. Index inventory

Exactly 26 explicit `idx_crm_%` indexes must be present across the eight extension
tables.

| Table | Expected explicit indexes | Observed |
|---|---:|---:|
| `crm_tasks` | 3 | `PENDING` |
| `crm_assignments` | 3 | `PENDING` |
| `crm_transfers` | 3 | `PENDING` |
| `crm_notes` | 3 | `PENDING` |
| `crm_audit_logs` | 4 | `PENDING` |
| `crm_reports` | 3 | `PENDING` |
| `crm_phone_numbers` | 4 | `PENDING` |
| `crm_contact_lookup_index` | 3 | `PENDING` |
| **Total** | **26** | `PENDING` |

Observed index inventory artifact: `PENDING`

## 6. Tenant-isolation evidence

| Check | Expected | Observed |
|---|---|---|
| Tenant FK coverage | 8 of 8 tables | `PENDING` |
| Contact lookup contact FK | same-tenant composite FK present | `PENDING` |
| Contact lookup account FK | same-tenant composite FK present | `PENDING` |
| PostgreSQL negative isolation test | PASS | `PENDING` |
| Tenant B API access to Tenant A | controlled `4xx` | `PENDING` |
| Tenant B detail pages | no Tenant A content | `PENDING` |
| Tenant B lists/search/overview | no Tenant A content | `PENDING` |

Runtime isolation artifact or workflow run: `PENDING`

## 7. Health and regression evidence

| Check | Expected | Observed |
|---|---|---|
| Application health | healthy | `PENDING` |
| Authentication smoke | PASS | `PENDING` |
| CRM account/contact read paths | PASS | `PENDING` |
| CRM tenant-isolation suite | PASS | `PENDING` |
| Error-rate regression | none attributable to migration | `PENDING` |

## 8. Exceptions and incidents

- Exception, incident, or rollback reference: `NONE / PENDING`
- Description: `PENDING`
- Final database state: `PENDING`
- Final application state: `PENDING`

## 9. Closure decision

```text
SOURCE_CI: PENDING
POSTGRESQL_MIGRATION_TESTS: PENDING
PRODUCTION_FLYWAY: PENDING
PRODUCTION_SCHEMA_VERIFICATION: PENDING
RUNTIME_TENANT_ISOLATION: PENDING
OWNER_APPROVAL: PENDING
CRM-G1: OPEN / NOT_READY
```

Final decision and rationale: `PENDING`
