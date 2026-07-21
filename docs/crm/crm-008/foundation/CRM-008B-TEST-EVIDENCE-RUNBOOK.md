# CRM-008B — Test and Evidence Runbook

> **Status:** PREPARED / EXECUTION NOT AUTHORIZED  
> **Acceptance baseline:** 21 criteria  
> **Implementation merge gate:** 17 criteria  
> **Formal closure gate:** all 21 criteria

---

## 1. Purpose

Define the exact test layers, evidence ownership, entry/exit conditions and failure rules for CRM-008B. This runbook prevents repository regression tests from being misrepresented as CRM-008 implementation evidence.

---

## 2. Gate Classification

### Implementation Merge Gate — 17 criteria

```text
AC-01 through AC-11
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
```

### Formal Implementation Closure — 21 criteria

Adds:

```text
AC-12 Localization
AC-13 Accessibility
AC-14 Performance
AC-15 Production Proof
```

A green repository CI run is necessary but insufficient. Each criterion requires its own named evidence.

---

## 3. Test Layers

| Layer | Environment | Purpose | Blocking gate |
|---|---|---|---|
| T0 | Static/repository | Architecture, OpenAPI, migration naming, documentation distinction | Merge |
| T1 | Unit | Domain states, rule evaluation, RBAC decision logic | Merge |
| T2 | PostgreSQL 16 Testcontainers | Migration, repositories, tenant isolation, transactions | Merge |
| T3 | Concurrency | Queue claim, assignment conflict, round-robin counter | Merge |
| T4 | API integration | 38 operations, RFC 7807, ETag, idempotency, audit | Merge |
| T5 | Web integration | Core ownership workflows against controlled backend | Formal closure |
| T6 | Playwright visual/accessibility | Arabic/English, RTL/LTR, keyboard and axe | Formal closure |
| T7 | Performance | Bounded tenant queries and p95 targets | Formal closure |
| T8 | Production authenticated smoke | Exact-SHA end-to-end proof | Formal closure / go-live |

---

## 4. Acceptance Traceability

| Criterion | Primary package | Evidence owner | Mandatory evidence |
|---|---|---|---|
| AC-01 Tenant Isolation | WP-03..WP-09 | QA + Security | Two-tenant Testcontainers/API report |
| AC-02 Single Primary Owner | WP-07 | Backend QA | Active-assignment count and transaction report |
| AC-03 History Immutability | WP-07 | DB + Security | Privilege inventory and mutation rejection |
| AC-04 Concurrent Queue Claim | WP-04 | QA | One success, one 409, final owner |
| AC-05 Atomic Transfer | WP-08 | QA | Full rollback and no orphan history |
| AC-06 Separation of Duties | WP-08/WP-09 | Security QA | Self-approval rejection and audit row |
| AC-07 Rule Explainability | WP-06/WP-07 | Product QA | Rule trace, assignment and history correlation |
| AC-08 Workload Safety | WP-04/WP-06 | QA | Eligibility/capacity skip trace |
| AC-09 Territory Integrity | WP-05 | QA | Cycle rejection and closure-table proof |
| AC-10 Audit Completeness | WP-03..WP-09 | Security QA | Action-to-audit reconciliation |
| AC-11 Workflow Integration | WP-08 | Architecture QA | Port interaction and fail-closed stub evidence |
| AC-12 Localization | WP-10 | Web QA | Arabic/English screenshots and i18n report |
| AC-13 Accessibility | WP-10 | Accessibility QA | Keyboard and axe report |
| AC-14 Performance | WP-04/WP-07/WP-10 | Performance QA | p95 results and query plans |
| AC-15 Production Proof | WP-11 | Release QA | Exact-SHA authenticated production evidence |
| AC-DB-01 DB Single Active | WP-01/WP-07 | DB QA | Raw JDBC unique violation and count=1 |
| AC-DB-02 Partial-State Failure | WP-01 | DB QA | Failed migration and unchanged partial state |
| AC-DB-03 Postconditions | WP-01 | DB QA | Structured schema verification report |
| AC-CONC-01 Assignment Conflict | WP-07 | QA | One success, one conflict, one history row |
| AC-RR-01 Round-Robin Counter | WP-06 | QA | Atomic counter and deterministic ring distribution |
| AC-TEST-01 Evidence Distinction | WP-11 | QA Owner | Automated/document review output |

---

## 5. Mandatory Scenario Matrix

### Tenant isolation

Run every applicable resource scenario for tenant A and tenant B:

- Team.
- Team membership.
- Queue.
- Queue membership and item.
- Territory and hierarchy.
- Territory assignment.
- Assignment rule and version.
- Assignment.
- Transfer request and step.
- Ownership history.

For each resource:

- Same-tenant read succeeds with capability.
- Same-tenant write succeeds with capability.
- Missing capability fails.
- Cross-tenant read and write fail without data leakage.
- Request-provided tenant identity is ignored or rejected.
- Database same-tenant relation prevents direct cross-tenant linkage.

### RBAC

Minimum roles:

- ADMIN.
- SALES_MANAGER.
- SALES_REPRESENTATIVE.
- User with read-only capability.
- User without CRM ownership capabilities.
- Internal workflow principal for `CRM.TRANSFER.EXECUTE`.

Minimum negative scenarios:

- Representative bulk assignment.
- Representative rule activation.
- Queue claimant queue administration.
- Team reader membership mutation.
- Requester self-approval.
- Human invocation of internal transfer execution.

### Concurrency

- Two claimants, one queue item.
- Two assignments, one record.
- N assignments, N records, one round-robin rule.
- Concurrent rule-version activation.
- Transfer execution replay with same idempotency key.
- Bulk reassignment overlap.

No concurrency test may pass by accepting duplicate writes, HTTP 500, unbounded retries or timeout inflation.

---

## 6. API Contract Verification

All 38 operations must verify:

- Tenant scope from authenticated context only.
- Declared capability and deny-by-default behavior.
- Request validation.
- RFC 7807 error envelope.
- Request ID and correlation ID propagation.
- ETag behavior where optimistic concurrency applies.
- Idempotency-Key behavior for retry-safe POST operations.
- Bounded pagination and maximum page size.
- Audit emission for successful and failed writes.
- Ownership history emission only for ownership-changing operations.

Backward compatibility verification must cover existing CRM v1/v2 operations that read owner fast-path columns.

---

## 7. Performance Baseline

Required dataset:

- 100 tenants.
- 100,000 assignments.
- At least 1,000 assignments in the measured tenant.
- Representative queue, history, rule and transfer data.

Required targets:

```text
GET /api/v2/crm/my-work: p95 < 200 ms
GET /api/v2/crm/queues/{id}/items: p95 < 150 ms
GET /api/v2/crm/ownership-history: p95 < 100 ms
```

Query-plan evidence must show tenant-leading index usage and no unbounded sequential scan for the measured path.

---

## 8. Frontend and Accessibility Evidence

Required routes/screens:

- My Work.
- Teams.
- Queues.
- Territories.
- Assignment Rules.
- Transfers.
- Record ownership panel/history.

For every screen:

- Arabic and English.
- RTL and LTR.
- Empty, loading, success, validation, 403, 409 and backend-failure states.
- Keyboard navigation.
- Accessible labels and names.
- No critical axe violations.
- Capability-aware action visibility.

The client must not be considered an authorization boundary; API tests remain authoritative.

---

## 9. Production Proof — AC-15

Entry conditions:

- Protected implementation merge completed.
- All first 20 criteria passed in controlled environments.
- Exact Vercel deployment SHA equals exact `main` SHA.
- Exact Render immutable backend image equals exact `main` SHA.
- Production Flyway rows and schema postconditions are verified read-only.
- Two least-privilege acceptance identities exist in distinct tenants.
- Approved production change and backup/PITR references are recorded.

Production scenario:

1. Tenant A authenticates.
2. Tenant A creates a team and queue.
3. Tenant A creates or routes an ownable record.
4. Tenant A claims or assigns the record.
5. Tenant A requests a transfer.
6. A distinct authorized manager approves.
7. Ownership history and audit show one correlated chain.
8. Tenant B attempts access and is denied without leakage.
9. Health remains UP.
10. Runtime logs contain no unexplained 5xx.

Required evidence:

- Workflow run and job IDs.
- Exact frontend/backend/repository SHA.
- Deployment IDs and backend image digest.
- Redacted request/result summary.
- Redacted audit and ownership-history exports.
- Flyway and schema verification.
- Runtime error inspection window.
- Immutable artifact digest.

---

## 10. Failure Rules

A criterion is `FAIL` when any of the following occurs:

- Unexpected HTTP 5xx.
- Tenant data leakage.
- Capability bypass.
- Duplicate ACTIVE assignment.
- Lost round-robin update.
- Partial transfer commit.
- Mutable ownership history.
- Migration silent repair.
- Missing or mismatched evidence SHA.
- Test skip, acceptance weakening or timeout increase used to produce green status.
- Required test did not run.

Flaky classification requires repeated controlled evidence. A single rerun success does not erase a proven defect.

---

## 11. Evidence Directory Contract

Each test execution package should contain:

```text
run-context
commit-and-deployment-identity
migration-and-schema-report
unit-and-integration-reports
concurrency-report
rbac-and-tenant-isolation-report
openapi-contract-report
playwright-report
accessibility-report
performance-report
production-smoke-report
runtime-error-report
SHA256SUMS
```

The manifest must cover every file. Secrets and raw credentials are prohibited.

---

## 12. Sign-Off Matrix

| Gate | Product | QA | Security | Architecture | Release |
|---|---|---|---|---|---|
| Start authorization | Required | Readiness confirmation | Boundary confirmation | Required | Not required |
| Implementation merge | Product acceptance | 17 AC approval | RBAC/tenant/migration approval | Contract approval | Merge readiness |
| Formal closure | All 21 AC approval | Full evidence approval | Production security approval | Final consistency | Production evidence approval |
| Commercial go-live | Required | Required | Required | Consulted | Required |

REM-P0-006 remains an independent requirement for commercial go-live.

---

## 13. Runbook Decision

```text
TEST_LAYERS: DEFINED
AC_TRACEABILITY: COMPLETE
NEGATIVE_SCENARIOS: DEFINED
CONCURRENCY_MATRIX: DEFINED
PRODUCTION_PROOF: DEFINED
EVIDENCE_CONTRACT: DEFINED
FAILURE_RULES: DEFINED
TEST_EXECUTION: NOT_STARTED
```
