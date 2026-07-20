# CRM-008A — Final Design Closure Report

> **Stage:** CRM-008A — Discovery, Architecture and Design Baseline  
> **Repository:** `snadaiapp-png/SNAD`  
> **Original design delivery:** PR #591, merge commit `32304c8bbae8a95aeccdad4dceb42dd053d2e39b`  
> **Final correction and closure PR:** PR #593  
> **Owner Review Documentation:** `APPROVED`  
> **Internal Consistency:** `PASS`  
> **CRM-008A Design Baseline:** `APPROVED`  
> **CRM-008A Design Closure:** `EFFECTIVE_ON_PROTECTED_MERGE_OF_PR_593`  
> **CRM-008 Formal Implementation Closure:** `NOT_STARTED`  
> **CRM-008B Implementation:** `NOT_AUTHORIZED`  
> **Commercial Go-Live:** `NOT_AUTHORIZED`

---

## 1. Closure Purpose

This report closes the **CRM-008A design and documentation stage only**.

It does not authorize implementation, executable database migrations, runtime deployment, production activation, or commercial go-live.

The closure removes the previous circular dependency by separating:

1. CRM-008A Design Closure.
2. CRM-008B Start Authorization.
3. CRM-008 Implementation Merge.
4. CRM-008 Formal Implementation Closure and Commercial Go-Live.

---

## 2. Final Scope

CRM-008A establishes the governed design baseline for institutional CRM ownership and assignment management, including:

- Sales teams and memberships.
- Work queues and queue memberships.
- Territories, hierarchy, overlap resolution, and coverage.
- Assignment rules and versioning.
- Manual and automated assignment.
- Round-robin and least-loaded strategies.
- Ownership transfers and approval boundaries.
- Immutable ownership history.
- Tenant isolation and RBAC.
- Migration, concurrency, test, and evidence requirements.

### 2.1 Delivered design documents

| Document | Path | Status |
|---|---|---|
| Discovery Report | `docs/crm/crm-008/00-discovery-report.md` | Approved baseline |
| Domain Model | `docs/crm/crm-008/domain/01-domain-model.md` | Approved baseline |
| OpenAPI Contract Draft | `docs/crm/crm-008/contracts/01-openapi-draft.md` | Existing baseline from PR #591 |
| RBAC Matrix | `docs/crm/crm-008/rbac/01-rbac-matrix.md` | Existing baseline from PR #591 |
| Migration Plan | `docs/crm/crm-008/migrations/01-migration-plan.md` | Approved plan; not executed |
| Acceptance Plan | `docs/crm/crm-008/tests/01-acceptance-plan.md` | Approved plan; 21 criteria |
| Final Stage Report | `docs/crm/crm-008/STAGE-REPORT-CRM-008A.md` | This closure record |

### 2.2 Explicitly not delivered

```text
SQL_MIGRATIONS: NOT_COMMITTED
SQL_EXECUTION: NOT_PERFORMED
JDBC_ADAPTERS: NOT_IMPLEMENTED
SPRING_SERVICES: NOT_IMPLEMENTED
REST_CONTROLLERS: NOT_IMPLEMENTED
FRONTEND_FEATURES: NOT_IMPLEMENTED
CRM_008_ACCEPTANCE_TESTS: NOT_EXECUTED
PRODUCTION_DEPLOYMENT: NOT_PERFORMED
CRM_008B: NOT_STARTED
```

The four Java marker interfaces merged in PR #591 add no runtime behavior and do not constitute CRM-008 implementation.

---

## 3. Binding Owner Decisions

### Q1 — HRM placeholder

```text
APPROVED_FOR_NON_PRODUCTION_DESIGN_ONLY
```

- `HrmPort` may be stubbed only in development and test environments.
- Production health must fail closed if the stub is active.
- Absence-driven reassignment remains disabled until real HRM integration.

### Q2 — Workflow placeholder

```text
SINGLE_APPROVER_TEMPORARILY_ALLOWED
MULTI_STEP_APPROVAL_BLOCKED
```

- `WorkflowPort` is the only workflow integration boundary.
- Multi-step approval attempts must fail closed until the central Workflow Engine is integrated.

### Q3 — Shared ownership

```text
DEFERRED_TO_CRM_008C
```

CRM-008B provides one primary owner plus team or queue operational responsibility. Contributor-based authorization is not implemented in CRM-008B.

### Q4 — Territory overlap

```text
OVERLAP_ALLOWED_WITH_EXPLICIT_PRIORITY
EQUAL_PRIORITY_AMBIGUITY_FAILS_CLOSED
```

### Q5 — Round-robin persistence

```text
SCOPE: tenant_id + assignment_rule_id
UPDATE: TRANSACTIONAL + ATOMIC + CONCURRENCY_SAFE
TEST: AC-RR-01
```

AC-RR-01 is distinct from AC-CONC-01:

- `AC-CONC-01`: concurrent assignment attempts for the same record.
- `AC-RR-01`: concurrent evaluations for different records sharing one assignment rule.

---

## 4. Core Invariants

### 4.1 Tenant isolation

- Every tenant-owned table has `tenant_id UUID NOT NULL`.
- Tenant-owned relations use same-tenant composite foreign keys where applicable.
- Tenant-leading indexes are mandatory.
- Application authorization remains tenant-scoped.

### 4.2 Single active assignment

```text
SINGLE_ACTIVE_ASSIGNMENT:
DATABASE_ENFORCED
+
APPLICATION_ENFORCED
+
CONCURRENCY_TESTED
```

The invariant is exactly one ACTIVE assignment per:

```text
(tenant_id, record_type, record_id)
```

Required enforcement:

1. PostgreSQL partial unique index.
2. Transactional application transition from ACTIVE to SUPERSEDED before insertion.
3. AC-DB-01 and AC-CONC-01 evidence.

The historical partial-index failure remains classified as:

```text
ROOT_CAUSE: UNDETERMINED
CLASSIFICATION: MIGRATION_DESIGN_OR_SCHEMA_STATE_DEFECT
```

CRM-008B must reproduce and root-cause it before implementation merge.

### 4.3 Ownership history

- Append-only semantics.
- No application UPDATE or DELETE.
- Database-role revocation for UPDATE and DELETE.
- Evidence required before implementation closure.

---

## 5. Migration Governance

```text
TOTAL_PLANNED_MIGRATIONS: 9
TOTAL_NEW_TABLES: 14
TOTAL_NEW_INDEXES: 40
TOTAL_TENANT_FK_CONSTRAINTS: 21
TOTAL_NEW_CAPABILITIES: 17
TOTAL_NEW_ROLES: 2
```

### 5.1 Versioning

```text
MIGRATION_VERSIONING: SEQUENTIAL_INTEGER
FRACTIONAL_VERSIONS: PROHIBITED_BY_SANAD_POLICY
```

Planned versions:

```text
V20260720_1 through V20260720_9
```

### 5.2 Historical SQL statement

```text
V20260720_1 through V20260720_8:
Temporarily committed during CRM-008A and removed before PR #591 merged.

V20260720_9:
Introduced later as a design-only planned migration during Owner Review R2.
Never committed as executable SQL.
```

### 5.3 Fail-closed policy

```text
MIGRATION_POLICY: FORWARD_ONLY
IF_NOT_EXISTS_AS_ACTIVE_PATTERN: PROHIBITED
CREATE_TARGET_STATE: OBJECTS_MUST_BE_ABSENT
ALTER_TARGET_STATE: EXACT_PREDECESSOR_REQUIRED
SEED_POLICY: CONTROLLED_IDEMPOTENCY_AND_FAIL_CLOSED
NO_FLYWAY_REPAIR
NO_MANUAL_FLYWAY_HISTORY_EDIT
NO_AD_HOC_ROLLBACK_MIGRATION
```

Failed-before-commit operations use transaction rollback only. Successfully applied migrations are corrected through a new forward-only corrective or approved reconciliation migration.

---

## 6. Acceptance Model

```text
TOTAL_ACCEPTANCE_CRITERIA: 21
IMPLEMENTATION_MERGE_CRITERIA: 17
FORMAL_IMPLEMENTATION_CLOSURE_CRITERIA: 21
```

The 21 criteria are:

```text
AC-01 through AC-15
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
```

No CRM-008 implementation criterion has been executed in CRM-008A because no CRM-008 implementation exists.

Existing repository CI success must not be presented as CRM-008 functional completion evidence.

---

## 7. Non-Circular Governance Gates

### Gate A — CRM-008A Design Closure

Purpose: close discovery, architecture, and documentation.

Required evidence:

```text
OWNER_REVIEW_DOCUMENTATION: APPROVED
INTERNAL_CONSISTENCY: PASS
DESIGN_CORRECTIONS: FULLY_APPLIED
PRODUCT_SIGN_OFF: RECORDED_ON_FINAL_PR_HEAD
QA_SIGN_OFF: RECORDED_ON_FINAL_PR_HEAD
SECURITY_SIGN_OFF: RECORDED_ON_FINAL_PR_HEAD
DOCUMENTATION_SCOPE: VERIFIED
OPEN_REVIEW_THREADS: 0
CI_REQUIRED_FOR_DOCUMENTATION: ACCEPTABLE
SECURITY_FINDING: RESOLVED_OR_FORMALLY_ADJUDICATED
PR_593: MERGED_WITH_EXPECTED_HEAD_PROTECTION
```

Implementation acceptance criteria are **not** prerequisites for Gate A.

### Gate B — CRM-008B Start Authorization

Purpose: authorize the first implementation sub-phase.

Required evidence:

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_007_PRODUCTION_CLOSURE: COMPLETED
ISSUE_563: CLOSED_WITH_EVIDENCE
PR_567: FORMALLY_RESOLVED
IMPLEMENTATION_BACKLOG: ASSIGNED
PROJECT_OWNER_AUTHORIZATION: EXPLICITLY_ISSUED
```

Merging PR #593 alone does not authorize CRM-008B.

### Gate C — CRM-008 Implementation Merge

Purpose: authorize merging implemented CRM-008 functionality.

Required criteria:

```text
AC-01 through AC-11
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
TOTAL: 17
```

### Gate D — CRM-008 Formal Implementation Closure and Go-Live

Purpose: close the implemented feature and authorize commercial use.

Required evidence:

```text
ALL_21_ACCEPTANCE_CRITERIA: PASS
AC_15_POST_MERGE_PRODUCTION_EVIDENCE: PASS
PRODUCT_OWNER_APPROVAL: RECORDED
QA_OWNER_APPROVAL: RECORDED
SECURITY_OWNER_APPROVAL: RECORDED
REM_P0_006: COMPLETED
```

---

## 8. CI and Security Evidence

### 8.1 Required final-head checks for PR #593

The exact final PR head must show completed results with no pending checks for:

- CRM Authenticated Acceptance.
- CI.
- Web CI.
- Compile Diagnostics.
- CRM G1 Schema Isolation.
- Backup Restore Validation.
- Master Backlog Validation.
- Service Decomposition Validation.
- Stage 07 Artifact Provenance.

### 8.2 Secret-scan remediation

The hardcoded Render service ID fallback was a pre-existing repository condition and not introduced by CRM-008.

The authorized remediation is:

```text
SECURITY_REMEDIATION_PR: #594
ACTION: REMOVE_HARDCODED_RENDER_SERVICE_ID_FALLBACK
SOURCE_OF_RENDER_SERVICE_ID: PROTECTED_GITHUB_SECRET_ONLY
GITLEAKSIGNORE_BYPASS: NOT_USED
ADMIN_SECURITY_BYPASS: PROHIBITED
```

PR #593 must not merge until the remediation is merged and Security Baseline is acceptable on the final merge context.

---

## 9. Role Sign-offs

The following role-specific sign-offs must be recorded as separate PR #593 conversation records on the same exact final HEAD before merge.

### 9.1 Product Owner

```text
PRODUCT_SCOPE: APPROVED
OWNERSHIP_LIFECYCLE: APPROVED
Q1_TO_Q5_DECISIONS: APPROVED
ACCEPTANCE_MODEL_21_ACS: APPROVED
DESIGN_BASELINE: ACCEPTED
```

### 9.2 QA Owner

```text
TEST_STRATEGY: APPROVED
AC_CONC_01: APPROVED
AC_RR_01: APPROVED
AC_DB_01_TO_03: APPROVED
AC_TEST_01: APPROVED
EVIDENCE_REQUIREMENTS: APPROVED
CRM_AUTHENTICATED_ACCEPTANCE: PASS_VERIFIED
```

### 9.3 Security Owner

```text
TENANT_ISOLATION_DESIGN: APPROVED
RBAC_DESIGN: APPROVED
SEPARATION_OF_DUTIES: APPROVED
DB_LEVEL_INVARIANTS: APPROVED
OWNERSHIP_HISTORY_IMMUTABILITY: APPROVED
ROLLBACK_POLICY: APPROVED
SECRET_SCAN_FINDING: RESOLVED
```

These are role-specific governance records. They do not represent REM-P0-006 independent commercial security assurance.

---

## 10. CRM-007 Dependency

Current controlling status:

```text
ISSUE_563: OPEN
PR_567: OPEN / DRAFT / DO_NOT_MERGE
CRM_007_PRODUCTION_CLOSURE: NOT_COMPLETED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
```

CRM-007 is not a blocker to merging the CRM-008A design-closure documentation after its own PR gates pass. It remains a hard blocker to Gate B and all runtime implementation.

---

## 11. Closure Decision

The following decision becomes effective only when PR #593 is merged with exact-head protection after all Gate A evidence is recorded:

```text
CRM_008A_REPOSITORY_DELIVERY: MERGED
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_008A_DESIGN_BASELINE: APPROVED
OWNER_REVIEW_DOCUMENTATION: APPROVED
INTERNAL_CONSISTENCY: PASS
PRODUCT_SIGN_OFF: RECORDED
QA_SIGN_OFF: RECORDED
SECURITY_SIGN_OFF: RECORDED

CRM_008_FORMAL_IMPLEMENTATION_CLOSURE: NOT_STARTED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
CRM_008_COMMERCIAL_GO_LIVE: NOT_AUTHORIZED

CRM_007_PRODUCTION_CLOSURE: REQUIRED_BEFORE_CRM_008B
ISSUE_563: MUST_BE_CLOSED_BEFORE_CRM_008B
PR_567: MUST_BE_FORMALLY_RESOLVED
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

### Mandatory stop condition

If any Gate A requirement fails:

```text
FINAL_CLOSURE: BLOCKED
PR_593_MERGE: DO_NOT_EXECUTE
CRM_008A_DESIGN_CLOSURE: NOT_COMPLETED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
```

---

## 12. Change Log

| Date | Change |
|---|---|
| 2026-07-20 | Initial CRM-008A design baseline delivered through PR #591. |
| 2026-07-20 | PR #592 reconciled the merged delivery and governance history. |
| 2026-07-20 | PR #593 applied Owner Review R2 database, migration, concurrency, acceptance, and consistency corrections. |
| 2026-07-20 | Final closure revision removed the circular dependency between CRM-008A closure and future CRM-008 implementation evidence; introduced the four-gate model; linked security remediation PR #594; preserved CRM-007 and REM-P0-006 boundaries. |
