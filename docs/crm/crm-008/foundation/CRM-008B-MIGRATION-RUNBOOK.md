# CRM-008B — Migration Execution Runbook

> **Database:** PostgreSQL 16  
> **Policy:** forward-only, exact-state, fail-closed  
> **Reserved range:** `V20260722_1` through `V20260722_9`  
> **Production execution:** not authorized by this document

## 1. Purpose

Define how CRM-008B migrations are designed, validated, reviewed, merged and eventually promoted without masking schema drift, editing Flyway history or using manual Production SQL.

## 2. Planned Sequence

| Order | Version | Purpose | Type |
|---:|---|---|---|
| 1 | `V20260722_1` | Sales teams and memberships | CREATE |
| 2 | `V20260722_2` | Queues and queue memberships | CREATE |
| 3 | `V20260722_3` | Territories, closure and assignments | CREATE |
| 4 | `V20260722_4` | Assignment rules and versions | CREATE |
| 5 | `V20260722_5` | Assignments and ownership history | CREATE |
| 6 | `V20260722_6` | Transfer requests and steps | CREATE |
| 7 | `V20260722_7` | Owner team/queue columns | ALTER |
| 8 | `V20260722_8` | Ownership capabilities and roles | SEED |
| 9 | `V20260722_9` | Assignment rule counters | CREATE |

The range must be rechecked immediately before implementation branch creation and again before merge.

## 3. Preconditions

### Governance

- CRM-007 is closed with merged evidence.
- Issue #563 and #571 are closed.
- Issue #597 records exact Project Owner authorization.
- The implementation branch descends from the authorized SHA.
- REM-P0-006 remains independent.

### Repository and Flyway

- `V20260722_1` through `_9` are unused.
- No failed Flyway history rows exist in controlled environments.
- Production and CI migration locations are identical.
- No repair or history edit is required.

### Database baseline

- Required tenant, identity and CRM base tables have exact expected definitions.
- Existing ownable CRM tables have the exact predecessor state required by migration 7.
- No target CRM-008 object exists partially or unexpectedly.

Any failed precondition stops implementation or promotion. Reconciliation requires a separate forward-only migration and explicit review.

## 4. Migration Rules

### CREATE migrations

Applies to 1, 2, 3, 4, 5, 6 and 9.

Required:

- All target tables, indexes and constraints are absent.
- Any partial target state aborts before DDL.
- DDL and postconditions run transactionally where PostgreSQL permits.
- Postconditions verify definitions, not names only.

Forbidden:

- `IF NOT EXISTS` used to conceal drift.
- Creating only missing fragments of a partial schema.
- Continuing when an unexpected target object exists.

### ALTER migration

Applies to 7.

- Exact predecessor columns and constraints must exist.
- New columns and indexes must be absent.
- Existing data must satisfy backfill and constraint assumptions.
- Postconditions verify type, nullability, indexes and compatibility.

### SEED migration

Applies to 8.

- Stable reserved identifiers are required.
- Existing matching records must exactly match approved definitions.
- Conflicting identifiers, codes, grants or statuses abort.
- `CRM.TRANSFER.EXECUTE` remains internal-only.

## 5. Required Postconditions

```text
NEW_TABLES: 14
NEW_EXPLICIT_INDEXES: 40
TENANT_OR_COMPOSITE_FOREIGN_KEYS: 21
NEW_CAPABILITIES: 17
NEW_ROLES: 2
FAILED_FLYWAY_ROWS: 0
FRACTIONAL_MIGRATION_VERSIONS: 0
UNEXPECTED_OBJECTS: 0
```

Additional invariants:

- Every tenant-owned table has non-null tenant identity.
- Every cross-resource relation is same-tenant at database and application layers.
- Every business index is tenant-leading.
- Exactly one ACTIVE assignment is database-enforced.
- Round-robin state is unique per tenant and rule.
- Ownership history cannot be updated or deleted by the application role.
- Existing owner fast-path reads remain backward compatible.

## 6. Validation Stages

### M1 — Clean PostgreSQL 16

Apply full repository history to an empty database. Require deterministic order, Flyway validate success and exact inventory.

### M2 — Supported pre-CRM-008 baseline

Apply only the new range to the exact predecessor schema. Require no data loss and existing CRM regressions to pass.

### M3 — Partial and unexpected states

Simulate partial tables, wrong constraints, occupied versions and conflicting seeds. Every case must fail closed without silent repair.

### M4 — Concurrency

Prove:

- one ACTIVE assignment under concurrent writes;
- one successful queue claim;
- atomic round-robin counters;
- deterministic rule-version activation.

### M5 — Controlled pre-production

Apply the exact release artifact to an approved production-equivalent environment. Require application startup with schema validation, health and authenticated smoke.

### M6 — Production

Entry requires all first 20 acceptance criteria, protected merge, backup/PITR reference and exact frontend/backend/repository identity.

Execution rules:

- Standard application/Flyway startup path only.
- No manual SQL.
- No Flyway repair or history edit.
- Stop on first migration, health or acceptance regression.
- HTTP 500 is never an expected outcome.

## 7. Evidence Package

- Exact release SHA and backend image digest.
- Flyway version, description, type and success rows.
- Preconditions and postconditions.
- Schema, constraint and index inventory.
- PostgreSQL 16 clean/baseline/partial/concurrency reports.
- Application startup and health evidence.
- Backup/PITR reference for Production.
- Redacted runtime and audit evidence.
- SHA-256 manifest for all files.

Secrets, credentials, database URLs and private tenant identifiers are prohibited.

## 8. Failure Handling

- Before merge: correct the draft and rerun the complete migration history.
- Before Production: do not deploy; preserve failure evidence.
- During transactional migration: rely on rollback and record exact state.
- After successful application: never edit the migration; use a new forward-only correction.
- After runtime regression: stop promotion and use application rollback only when schema compatibility is proven.

## 9. Approval Checklist

- [ ] Database Engineering review.
- [ ] Backend transaction review.
- [ ] Security tenant-isolation and privilege review.
- [ ] QA migration-test review.
- [ ] Release recovery review.
- [ ] Exact-head required checks.
- [ ] Project Owner scope approval recorded in Issue #597.

## 10. Decision

```text
MIGRATION_SEQUENCE: DEFINED
RESERVED_RANGE: V20260722_1..V20260722_9
PRECONDITIONS: DEFINED
POSTCONDITIONS: DEFINED
FAIL_CLOSED_POLICY: DEFINED
VALIDATION_STAGES: DEFINED
PRODUCTION_EXECUTION: NOT_AUTHORIZED_UNTIL_RELEASE_GATE
```