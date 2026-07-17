# CRM-G1 Production Migration Evidence

> **Evidence status:** `PENDING_PRODUCTION_EXECUTION`
>
> Do not change this record to `COMPLETE` until every required field is populated
> from an actual controlled target-database execution and independently reviewed.

## 1. Change identity

| Field | Evidence |
|---|---|
| Exact deployed commit SHA | `PENDING` |
| Release tag or deployment ID | `PENDING` |
| Change ticket / approval reference | `PENDING` |
| Target environment identifier | `PENDING` |
| Migration operator | `PENDING` |
| Database owner approver | `PENDING` |
| Application owner approver | `PENDING` |
| Started at (UTC) | `PENDING` |
| Completed at (UTC) | `PENDING` |

## 2. Backup and rollback readiness

| Control | Evidence |
|---|---|
| Verified backup / restore-point reference | `PENDING` |
| Backup verification timestamp | `PENDING` |
| Restore assurance or test reference | `PENDING` |
| Rollback decision owner | `PENDING` |
| Previous approved application SHA | `PENDING` |

## 3. Source and CI evidence

| Check | Expected | Observed |
|---|---|---|
| `CRM G1 Schema Isolation` | PASS on exact release source | `PENDING` |
| Immutable artifact name | contains exact source SHA | `PENDING` |
| Artifact digest | recorded | `PENDING` |
| Migration tests | PASS | `PENDING` |
| Behavioral cross-tenant PostgreSQL test | PASS | `PENDING` |
| `CRM Authenticated Acceptance` | PASS | `PENDING` |

## 4. Flyway evidence

| Check | Expected | Observed |
|---|---|---|
| Pre-migration `flyway:validate` | PASS | `PENDING` |
| Target migration version | `20260717.6` | `PENDING` |
| Description | `create crm g1 extension tables` | `PENDING` |
| Type | `SQL` | `PENDING` |
| Successful history rows | exactly `1` | `PENDING` |
| Post-migration `flyway:validate` | PASS | `PENDING` |

- Redacted Flyway artifact or log reference: `PENDING`
- Integrity hash: `PENDING`

## 5. Schema inventory

The target database must contain all eight tables:

- [ ] `crm_tasks`
- [ ] `crm_assignments`
- [ ] `crm_transfers`
- [ ] `crm_notes`
- [ ] `crm_audit_logs`
- [ ] `crm_reports`
- [ ] `crm_phone_numbers`
- [ ] `crm_contact_lookup_index`

Observed table inventory artifact: `PENDING`

## 6. Index inventory

Exactly 26 explicit `idx_crm_%` indexes must exist and every one must begin with
`tenant_id`.

| Table | Expected | Observed |
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

- Observed index inventory artifact: `PENDING`
- Non-tenant-leading explicit indexes: expected `0`; observed `PENDING`

## 7. Tenant-isolation evidence

| Check | Expected | Observed |
|---|---|---|
| `tenant_id` coverage | 8 of 8 tables | `PENDING` |
| Tenant-root FK coverage | 8 of 8 tables | `PENDING` |
| Phone-to-contact same-tenant FK | present | `PENDING` |
| Lookup-to-contact same-tenant FK | present | `PENDING` |
| Cross-tenant lookup write | rejected by PostgreSQL | `PENDING` |
| Same-tenant lookup write | accepted | `PENDING` |
| Tenant B API access to Tenant A | controlled `4xx` | `PENDING` |
| Tenant B detail pages | no Tenant A content | `PENDING` |
| Tenant B lists/search/overview | no Tenant A content | `PENDING` |

- Catalog verification output: `PENDING`
- Behavioral PostgreSQL test report: `PENDING`
- Runtime two-tenant artifact or workflow run: `PENDING`

## 8. Health and regression evidence

| Check | Expected | Observed |
|---|---|---|
| Application health | healthy | `PENDING` |
| Authentication smoke | PASS | `PENDING` |
| CRM account/contact read paths | PASS | `PENDING` |
| CRM tenant-isolation suite | PASS | `PENDING` |
| Error-rate regression | none attributable to migration | `PENDING` |

## 9. Exceptions, incidents, and rollback

- Exception, incident, or rollback reference: `NONE / PENDING`
- Description: `PENDING`
- Final database state: `PENDING`
- Final application state: `PENDING`

## 10. Closure decision

```text
EXACT_SHA_SOURCE_CI: PENDING
POSTGRESQL_MIGRATION_TESTS: PENDING
POSTGRESQL_BEHAVIORAL_ISOLATION: PENDING
PRODUCTION_FLYWAY: PENDING
PRODUCTION_SCHEMA_VERIFICATION: PENDING
RUNTIME_TENANT_ISOLATION: PENDING
DATABASE_OWNER_APPROVAL: PENDING
APPLICATION_OWNER_APPROVAL: PENDING
CRM-G1: OPEN / NOT_READY_FOR_CLOSURE
```

Final decision and rationale: `PENDING`
