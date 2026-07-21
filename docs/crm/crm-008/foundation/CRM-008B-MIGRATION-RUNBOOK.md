# CRM-008B — Migration Execution Runbook

> **Status:** PREPARED / NOT AUTHORIZED FOR EXECUTION  
> **Applies after:** CRM-007 closure and explicit CRM-008B Gate B authorization  
> **Database:** PostgreSQL 16  
> **Policy:** Forward-only, fail-closed, exact-state validation

---

## 1. Purpose

Define the controlled sequence for implementing, validating, reviewing and eventually applying the nine CRM-008B migrations without masking schema drift or altering Flyway history.

This runbook is procedural documentation. It contains no executable SQL and authorizes no production change.

---

## 2. Planned Sequence

| Order | Planned migration | Purpose | Change type |
|---:|---|---|---|
| 1 | Sales teams and memberships | Two tenant-owned tables and related constraints/indexes | CREATE |
| 2 | Queues and queue memberships | Two tenant-owned tables and related constraints/indexes | CREATE |
| 3 | Territories, closure and assignments | Hierarchy, closure table and assignment table | CREATE |
| 4 | Assignment rules and versions | Versioned rule definitions | CREATE |
| 5 | Assignments and ownership history | Single-active assignment and immutable ledger | CREATE |
| 6 | Transfer requests and steps | Transfer state and approval steps | CREATE |
| 7 | Owner team/queue columns | Extend existing CRM ownable records | ALTER |
| 8 | Capabilities and roles | Seed 17 capabilities and 2 roles | SEED |
| 9 | Assignment rule counters | Per-tenant/per-rule atomic round-robin state | CREATE |

The final filenames and version numbers must use the next available sequential integer versions observed on the authorized base SHA. Historical design labels `V20260720_1` through `_9` are references, not authorization to reuse a version that has become occupied.

---

## 3. Preconditions Before Any Migration Commit

### Governance

- CRM-007 Production Closure is complete.
- Issue #563 is closed with immutable evidence.
- PR #567 is closed or formally superseded.
- Issue #597 contains explicit Project Owner authorization.
- The exact authorized `main` SHA is recorded.
- The implementation branch was created after authorization.

### Repository and Flyway

- Determine the highest migration version on the exact authorized SHA.
- Verify the nine intended versions are unused.
- Verify production and CI Flyway locations are consistent.
- Verify no failed Flyway history entries exist in controlled environments.
- Verify no repair or history edit is required.

### Database Baseline

- Required base tables exist with exact expected definitions.
- Required tenant and identity constraints exist.
- Existing CRM ownable tables have the exact predecessor columns needed by the ALTER migration.
- No planned CRM-008 target object exists partially or unexpectedly.

Any failed precondition stops the sequence. A separate approved reconciliation migration is required; silent repair is prohibited.

---

## 4. Migration-Type Rules

### CREATE migrations

Applicable to planned migrations 1, 2, 3, 4, 5, 6 and 9.

Required behavior:

- All target tables, constraints and indexes must be absent.
- Any partial presence aborts the migration.
- DDL and postcondition checks execute in one transaction where PostgreSQL permits.
- Postconditions verify complete definitions, not name existence only.

Forbidden behavior:

- Continuing because a target object already exists.
- `IF NOT EXISTS` used to conceal unexpected state.
- Creating only missing fragments of a partially present target.

### ALTER migration

Applicable to planned migration 7.

Required behavior:

- Exact predecessor tables and columns must exist.
- New owner-team/owner-queue columns must be absent before execution.
- Existing data must satisfy all backfill and constraint assumptions.
- Postconditions verify type, nullability, indexes and compatibility behavior.

### SEED migration

Applicable to planned migration 8.

Required behavior:

- Missing capability/role records may be inserted only with reserved stable identifiers.
- Existing matching records must have the exact approved code and definition.
- Conflicting code, identifier, role grant or status aborts execution.
- `CRM.TRANSFER.EXECUTE` remains internal-only.

---

## 5. Required Postconditions

After the complete sequence, evidence must prove:

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

Additional invariant checks:

- Every tenant-owned table has non-null tenant identity.
- Every tenant relation prevents cross-tenant linkage.
- Every business index is tenant-leading.
- Exactly one ACTIVE assignment is database-enforced.
- Round-robin state is unique per tenant and assignment rule.
- Ownership history application role cannot update or delete rows.
- Existing CRM owner fast-path reads remain compatible.

---

## 6. Validation Environments

### Stage M1 — Clean PostgreSQL 16

Purpose:

- Apply the complete repository migration history from an empty database.
- Prove deterministic order and schema creation.
- Run Flyway validation.

Exit:

- Complete migration success.
- Exact postcondition inventory.
- No unexpected objects.

### Stage M2 — Current Supported Baseline

Purpose:

- Start from the exact pre-CRM-008 supported schema.
- Apply only the new sequence.
- Verify compatibility with existing CRM data and owner columns.

Exit:

- No data loss.
- No failed history row.
- Existing CRM regression suite passes.

### Stage M3 — Partial and Unexpected States

Purpose:

- Simulate missing columns, wrong constraints, partial tables, occupied versions and conflicting seeds.
- Prove fail-closed behavior.

Exit:

- Each scenario aborts before silent repair.
- Partial state remains unchanged.
- Evidence clearly identifies the violated precondition.

### Stage M4 — Concurrency

Purpose:

- Prove single-active assignment and atomic round-robin counter behavior.

Exit:

- AC-DB-01, AC-CONC-01 and AC-RR-01 pass repeatedly.

### Stage M5 — Controlled Pre-Production

Purpose:

- Apply the exact release artifact to a production-equivalent database copy or approved staging environment.
- Validate runtime startup with schema validation enabled.

Exit:

- Health, readiness and authenticated ownership smoke pass.
- No unexplained 5xx.

### Stage M6 — Production

Entry requires:

- Protected merge completed.
- Approved backup/PITR reference.
- Exact frontend/backend/repository SHA identified.
- Product, QA, Security and Release approvals recorded.
- AC-01 through AC-14 complete.

Production execution rules:

- Use the standard application/Flyway startup path.
- No manual SQL from closure workflows.
- No Flyway repair or history edits.
- Stop on the first failed migration or health regression.
- Do not accept HTTP 500 as an expected result.

---

## 7. Evidence Package

The immutable migration evidence package must contain:

- Release SHA and backend image digest.
- Flyway version, description, type and success rows.
- Precondition report.
- Schema inventory report.
- Constraint and index verification report.
- Application startup and schema-validation evidence.
- Testcontainers reports for clean, baseline, partial-state and concurrency scenarios.
- Backup/PITR reference for production.
- Runtime health window.
- Redacted error and audit evidence.
- SHA-256 manifest for all evidence files.

No credential, token, database URL, password or private tenant identifier may appear in the evidence.

---

## 8. Failure Handling

### Before commit

- Correct the migration draft.
- Recreate the disposable test database.
- Rerun the complete migration history.

### Before production application

- Do not deploy the release.
- Correct through the same PR or a replacement PR.
- Preserve all failed test evidence.

### During transactional migration

- Rely on transaction rollback.
- Record the exact failure and schema observation.
- Do not repair Flyway history.

### After a migration is successfully applied

- Never edit the applied migration.
- Create a new forward-only corrective or reconciliation migration.
- Require the same precondition, postcondition, testing and approval gates.

### Runtime regression after successful migration

- Stop promotion.
- Preserve the database state and logs.
- Use the approved application rollback only when schema compatibility is proven.
- Use a forward corrective migration when schema correction is required.

---

## 9. Approval Checklist

- [ ] Database Engineering review.
- [ ] Backend transaction review.
- [ ] Security tenant-isolation and privilege review.
- [ ] QA migration-test review.
- [ ] Release rollback/forward-correction review.
- [ ] Project Owner scope approval.
- [ ] Exact-head CI success.

---

## 10. Runbook Decision

```text
MIGRATION_SEQUENCE: DEFINED
PRECONDITIONS: DEFINED
POSTCONDITIONS: DEFINED
FAIL_CLOSED_POLICY: DEFINED
VALIDATION_STAGES: DEFINED
EVIDENCE_PACKAGE: DEFINED
FAILURE_HANDLING: DEFINED
PRODUCTION_EXECUTION: NOT_AUTHORIZED
```
