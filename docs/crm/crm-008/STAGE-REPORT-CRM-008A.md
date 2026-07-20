# CRM-008A — Final Design Closure Record

> **Stage:** CRM-008A — Discovery, Architecture and Design Baseline  
> **Repository:** `snadaiapp-png/SNAD`  
> **Design delivery:** PR #591 — merge commit `32304c8bbae8a95aeccdad4dceb42dd053d2e39b`  
> **Final correction and closure:** PR #593 — merge commit `79df3956334d8e405122774899461ee225177f72`  
> **Security remediation:** PR #594 — merge commit `aaa134fe2c4512de16a75c2fc47f211e47e29555`  
> **CRM-008A Design Closure:** `COMPLETED`  
> **CRM-008A Design Baseline:** `APPROVED`  
> **Owner Review Documentation:** `APPROVED`  
> **Internal Consistency:** `PASS`  
> **CRM-008 Formal Implementation Closure:** `NOT_STARTED`  
> **CRM-008B Implementation:** `NOT_AUTHORIZED`  
> **Commercial Go-Live:** `NOT_AUTHORIZED`

---

## 1. Final Closure Decision

```text
FINAL_CLOSURE: COMPLETED
CRM_008A_REPOSITORY_DELIVERY: MERGED
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_008A_DESIGN_BASELINE: APPROVED
OWNER_REVIEW_DOCUMENTATION: APPROVED
INTERNAL_CONSISTENCY: PASS
DOCUMENTATION_SCOPE: VERIFIED
PRODUCT_SIGN_OFF: RECORDED
QA_SIGN_OFF: RECORDED
SECURITY_SIGN_OFF: RECORDED
SECURITY_FINDING: RESOLVED
OPEN_REVIEW_THREADS: 0

CRM_008_FORMAL_IMPLEMENTATION_CLOSURE: NOT_STARTED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
CRM_008_COMMERCIAL_GO_LIVE: NOT_AUTHORIZED

CRM_007_PRODUCTION_CLOSURE: REQUIRED_BEFORE_CRM_008B
ISSUE_563: MUST_BE_CLOSED_BEFORE_CRM_008B
PR_567: MUST_BE_FORMALLY_RESOLVED
REM_P0_006: INDEPENDENT_AND_UNAFFECTED
```

This decision closes the discovery, architecture, and documentation stage only. It does not claim that CRM-008 has been implemented, tested in production, or authorized for commercial use.

---

## 2. Closed Scope

CRM-008A establishes the governed design baseline for:

- Sales teams and memberships.
- Work queues and queue memberships.
- Territories, hierarchy, coverage, and overlap resolution.
- Assignment rules and versioning.
- Manual and automated assignment.
- Round-robin and least-loaded assignment strategies.
- Ownership transfers and approval boundaries.
- Immutable ownership history.
- Tenant isolation and RBAC.
- Migration, concurrency, acceptance, and evidence requirements.

### 2.1 Authoritative documents

| Document | Path | Closure status |
|---|---|---|
| Discovery Report | `docs/crm/crm-008/00-discovery-report.md` | Approved baseline |
| Domain Model | `docs/crm/crm-008/domain/01-domain-model.md` | Approved baseline |
| OpenAPI Contract Draft | `docs/crm/crm-008/contracts/01-openapi-draft.md` | Existing approved design baseline |
| RBAC Matrix | `docs/crm/crm-008/rbac/01-rbac-matrix.md` | Existing approved design baseline |
| Migration Plan | `docs/crm/crm-008/migrations/01-migration-plan.md` | Approved plan; not executed |
| Acceptance Plan | `docs/crm/crm-008/tests/01-acceptance-plan.md` | Approved plan; 21 criteria |
| Stage Report | `docs/crm/crm-008/STAGE-REPORT-CRM-008A.md` | Final closure record |

### 2.2 Explicitly outside this closure

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

The four Java marker interfaces delivered in the original design baseline add no runtime behavior and do not constitute CRM-008 implementation.

---

## 3. Final Design Metrics

```text
TOTAL_PLANNED_MIGRATIONS: 9
TOTAL_NEW_TABLES: 14
TOTAL_NEW_INDEXES: 40
TOTAL_TENANT_FK_CONSTRAINTS: 21
TOTAL_NEW_CAPABILITIES: 17
TOTAL_NEW_ROLES: 2
TOTAL_ACCEPTANCE_CRITERIA: 21
IMPLEMENTATION_MERGE_CRITERIA: 17
FORMAL_IMPLEMENTATION_CLOSURE_CRITERIA: 21
ADDED_CRITERIA: 6
ROUND_ROBIN_TEST: AC-RR-01
```

The six additional criteria are:

```text
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
```

`AC-CONC-01` governs concurrent assignment attempts for the same record. `AC-RR-01` governs concurrent evaluations for different records that share the same assignment rule.

---

## 4. Binding Owner Decisions

### Q1 — HRM boundary

```text
APPROVED_FOR_NON_PRODUCTION_DESIGN_ONLY
```

The HRM stub is permitted only in development and test. Production must fail closed if the stub is active. Absence-driven reassignment remains disabled until real HRM integration.

### Q2 — Workflow boundary

```text
SINGLE_APPROVER_TEMPORARILY_ALLOWED
MULTI_STEP_APPROVAL_BLOCKED
```

The central Workflow Engine remains the authoritative integration target. Multi-step approval must fail closed until that integration exists.

### Q3 — Shared ownership

```text
DEFERRED_TO_CRM_008C
```

CRM-008B may provide one primary owner and team or queue operational responsibility. Contributor-based authorization is outside CRM-008B.

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

---

## 5. Core Invariants

### 5.1 Tenant isolation

- Every tenant-owned table has `tenant_id UUID NOT NULL`.
- Same-tenant composite foreign keys are required where applicable.
- Every tenant query and index is tenant-leading.
- Application authorization remains tenant-scoped.

### 5.2 Single active assignment

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

Required implementation evidence includes the PostgreSQL partial unique index, transactional supersede-and-insert behavior, AC-DB-01, and AC-CONC-01.

The historical index failure remains:

```text
ROOT_CAUSE: UNDETERMINED
CLASSIFICATION: MIGRATION_DESIGN_OR_SCHEMA_STATE_DEFECT
```

CRM-008B must reproduce and root-cause it before implementation merge.

### 5.3 Ownership history

- Append-only semantics.
- No application UPDATE or DELETE.
- Database-role revocation for UPDATE and DELETE.
- Verification evidence required before formal implementation closure.

---

## 6. Migration Governance

```text
MIGRATION_VERSIONING: SEQUENTIAL_INTEGER
FRACTIONAL_VERSIONS: PROHIBITED_BY_SANAD_POLICY
MIGRATION_POLICY: FORWARD_ONLY
IF_NOT_EXISTS_AS_ACTIVE_PATTERN: PROHIBITED
CREATE_TARGET_STATE: OBJECTS_MUST_BE_ABSENT
ALTER_TARGET_STATE: EXACT_PREDECESSOR_REQUIRED
SEED_POLICY: CONTROLLED_IDEMPOTENCY_AND_FAIL_CLOSED
NO_FLYWAY_REPAIR
NO_MANUAL_FLYWAY_HISTORY_EDIT
NO_AD_HOC_ROLLBACK_MIGRATION
```

Historical record:

```text
V20260720_1 through V20260720_8:
Temporarily committed during CRM-008A and removed before PR #591 merged.

V20260720_9:
Introduced later as a design-only planned migration during Owner Review R2.
Never committed as executable SQL.
```

A failed-before-commit operation uses transaction rollback only. A successfully applied migration is corrected through a new forward-only corrective or approved reconciliation migration.

---

## 7. Non-Circular Governance Gates

### Gate A — CRM-008A Design Closure

```text
STATUS: COMPLETED
```

Completion evidence:

- Owner Review Documentation approved.
- Internal consistency passed.
- Product, QA, and Security role sign-offs recorded on final PR #593 HEAD `e9a86b67386993c3c26628ea26bcd567802f6133`.
- Documentation-only scope verified: five Markdown files.
- No open review threads.
- All final-head workflows succeeded.
- Security finding resolved through PR #594.
- PR #593 merged using expected-head protection.

Implementation acceptance criteria are not prerequisites for Gate A.

### Gate B — CRM-008B Start Authorization

```text
STATUS: BLOCKED
```

Required before authorization:

```text
CRM_008A_DESIGN_CLOSURE: COMPLETED
CRM_007_PRODUCTION_CLOSURE: COMPLETED
ISSUE_563: CLOSED_WITH_EVIDENCE
PR_567: FORMALLY_RESOLVED
IMPLEMENTATION_BACKLOG: ASSIGNED
PROJECT_OWNER_AUTHORIZATION: EXPLICITLY_ISSUED
```

Gate A completion does not authorize Gate B.

### Gate C — CRM-008 Implementation Merge

```text
STATUS: NOT_STARTED
REQUIRED_CRITERIA: 17
```

Required criteria:

```text
AC-01 through AC-11
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
```

### Gate D — CRM-008 Formal Implementation Closure and Go-Live

```text
STATUS: NOT_STARTED
```

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

## 8. Final-Head CI Evidence

Final reviewed PR #593 HEAD:

```text
e9a86b67386993c3c26628ea26bcd567802f6133
```

| Workflow | Run ID | Result |
|---|---:|---|
| Compile Diagnostics | `29748323712` | SUCCESS |
| CI | `29748323556` | SUCCESS |
| Service Decomposition Validation | `29748323339` | SUCCESS |
| Master Backlog Validation | `29748323531` | SUCCESS |
| Stage 07 Artifact Provenance | `29748323455` | SUCCESS |
| CRM Authenticated Acceptance | `29748323274` | SUCCESS |
| Backup Restore Validation | `29748323561` | SUCCESS |
| Web CI | `29748323311` | SUCCESS |
| CRM G1 Schema Isolation | `29748323303` | SUCCESS |
| Security Baseline | `29748323469` | SUCCESS |

Existing repository test success is regression evidence for the final documentation HEAD. It is not evidence that CRM-008 implementation criteria have passed.

---

## 9. Security Closure

The pre-existing hardcoded Render service ID fallback was removed through PR #594.

```text
SECURITY_REMEDIATION_PR: 594
SECURITY_REMEDIATION_MERGE_SHA: aaa134fe2c4512de16a75c2fc47f211e47e29555
SOURCE_OF_RENDER_SERVICE_ID: PROTECTED_GITHUB_SECRET_ONLY
CURRENT_TREE_SECRET_SCAN: PASS
GITLEAKSIGNORE_BYPASS: NOT_USED
ADMIN_SECURITY_BYPASS: NOT_USED
SECURITY_FINDING: RESOLVED
```

This security design sign-off does not satisfy or replace `REM-P0-006`, which remains an independent commercial assurance gate.

---

## 10. Sign-off Evidence

All role-specific sign-offs were recorded on the exact final PR #593 HEAD.

| Role | PR #593 conversation record | Status |
|---|---:|---|
| Product Owner | `5023011361` | RECORDED |
| QA Owner | `5023093774` | RECORDED |
| Security Owner | `5023063064` | RECORDED |

These records approve the CRM-008A design baseline, test strategy, and design-security controls. They do not claim completion of future CRM-008 implementation tests.

---

## 11. CRM-007 Dependency

```text
ISSUE_563: OPEN
PR_567: OPEN / DRAFT / DO_NOT_MERGE
CRM_007_PRODUCTION_CLOSURE: NOT_COMPLETED
CRM_008B_IMPLEMENTATION: NOT_AUTHORIZED
```

CRM-007 did not block closing the documentation-only CRM-008A design stage after its own gates passed. It remains a hard blocker to CRM-008B and every executable CRM-008 change.

---

## 12. Final Authorization Boundary

```text
AUTHORIZED:
- CRM-008A design baseline as the authoritative implementation reference.
- Planning and backlog preparation that does not create executable CRM-008 changes.

NOT_AUTHORIZED:
- CRM-008B implementation.
- Flyway SQL creation or execution.
- Runtime adapters, services, or controllers.
- Frontend implementation.
- Production deployment.
- Commercial go-live.
```

No further CRM-008A design correction is required. Any future change to this baseline requires a new governed change request or ADR.

---

## 13. Change Log

| Date | Change |
|---|---|
| 2026-07-20 | Initial CRM-008A design baseline delivered through PR #591. |
| 2026-07-20 | PR #592 reconciled repository delivery and governance history. |
| 2026-07-20 | PR #593 applied final database, migration, concurrency, acceptance, consistency, and non-circular governance corrections. |
| 2026-07-20 | PR #594 removed the hardcoded Render service ID fallback; Security Baseline passed. |
| 2026-07-20 | Product, QA, and Security sign-offs recorded on final PR #593 HEAD. |
| 2026-07-20 | PR #593 merged with expected-head protection at merge SHA `79df3956334d8e405122774899461ee225177f72`; CRM-008A Design Closure became `COMPLETED`. |
