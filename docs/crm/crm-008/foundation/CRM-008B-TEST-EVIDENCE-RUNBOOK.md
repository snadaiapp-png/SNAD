# CRM-008B — Test and Evidence Runbook

> **Acceptance baseline:** 21 criteria  
> **Implementation merge gate:** 17 criteria  
> **Formal closure gate:** all 21 criteria

## 1. Purpose

Define the mandatory test layers, evidence owners, entry/exit conditions and failure rules for CRM-008B. Repository regression success is necessary but is not CRM-008B completion evidence by itself.

## 2. Gate Classification

### Implementation merge — 17 criteria

```text
AC-01 through AC-11
AC-DB-01
AC-DB-02
AC-DB-03
AC-CONC-01
AC-RR-01
AC-TEST-01
```

### Formal closure — 21 criteria

Adds:

```text
AC-12 Localization
AC-13 Accessibility
AC-14 Performance
AC-15 Production Proof
```

Each criterion requires named evidence tied to one exact SHA.

## 3. Test Layers

| Layer | Environment | Purpose | Gate |
|---|---|---|---|
| T0 | Static/repository | Architecture, OpenAPI, migration naming and evidence distinction | Merge |
| T1 | Unit | Domain states, rule evaluation and RBAC logic | Merge |
| T2 | PostgreSQL 16 Testcontainers | Migrations, repositories, tenants and transactions | Merge |
| T3 | Concurrency | Queue claim, assignment conflict and round-robin | Merge |
| T4 | API integration | 38 operations, RFC 7807, ETag, idempotency and audit | Merge |
| T5 | Web integration | Controlled ownership workflows | Closure |
| T6 | Playwright/accessibility | Arabic/English, RTL/LTR, keyboard and axe | Closure |
| T7 | Performance | Tenant-bounded query plans and p95 targets | Closure |
| T8 | Production authenticated smoke | Exact-SHA end-to-end proof | Closure |

## 4. Acceptance Traceability

| Criterion | Scope | Evidence |
|---|---|---|
| AC-01 Tenant Isolation | All aggregates/APIs | Two-tenant DB and API report |
| AC-02 Single Primary Owner | Assignments | Active count and transaction proof |
| AC-03 History Immutability | Ownership ledger | Privilege and mutation rejection |
| AC-04 Concurrent Queue Claim | Queues | One success, one conflict, final owner |
| AC-05 Atomic Transfer | Transfers | Full rollback and no orphan history |
| AC-06 Separation of Duties | Transfers/RBAC | Self-approval rejection and audit |
| AC-07 Rule Explainability | Rules/assignments | Correlated evaluation trace |
| AC-08 Workload Safety | Rules/queues | Eligibility and capacity skip trace |
| AC-09 Territory Integrity | Territories | Cycle rejection and closure proof |
| AC-10 Audit Completeness | All writes | Action-to-audit reconciliation |
| AC-11 Workflow Integration | Transfers | Port interaction and fail-closed stub |
| AC-12 Localization | Web | Arabic/English screenshots and i18n report |
| AC-13 Accessibility | Web | Keyboard and axe report |
| AC-14 Performance | Lists/history | p95 and query-plan evidence |
| AC-15 Production Proof | Release | Exact-SHA authenticated evidence |
| AC-DB-01 DB Single Active | Assignments | Raw DB conflict and count=1 |
| AC-DB-02 Partial-State Failure | Migrations | Failed migration and unchanged state |
| AC-DB-03 Postconditions | Migrations | Structured schema inventory |
| AC-CONC-01 Assignment Conflict | Assignments | One success, one conflict, one history row |
| AC-RR-01 Round-Robin Counter | Rules | Atomic counter and deterministic distribution |
| AC-TEST-01 Evidence Distinction | QA | Automated/document review output |

## 5. Mandatory Scenario Matrix

For teams, memberships, queues, queue items, territories, rules, assignments, transfers and ownership history:

- Same-tenant read succeeds with capability.
- Same-tenant write succeeds with capability.
- Missing capability fails.
- Cross-tenant read/write fails without leakage.
- Request-provided tenant identity is rejected or ignored as authority.
- Database relations reject cross-tenant linkage.

Minimum principals:

- ADMIN.
- SALES_MANAGER.
- SALES_REPRESENTATIVE.
- Read-only ownership principal.
- Principal without ownership capabilities.
- Internal workflow principal for `CRM.TRANSFER.EXECUTE`.

Mandatory negative cases:

- Representative attempts bulk assignment.
- Representative activates rules.
- Queue claimant administers queues.
- Team reader changes membership.
- Requester self-approves transfer.
- Human principal invokes internal transfer execution.

Concurrency cases:

- Two claimants, one queue item.
- Two assignments, one record.
- N records under one round-robin rule.
- Concurrent rule-version activation.
- Transfer replay with the same idempotency key.
- Overlapping bulk reassignment.

No concurrency test may pass by accepting duplicate writes, HTTP 500, unbounded retries or timeout inflation.

## 6. API Contract Verification

All 38 operations must verify:

- Tenant scope from authenticated context only.
- Declared capability and deny-by-default behavior.
- Request validation and bounded pagination.
- RFC 7807 error envelope.
- Request/correlation identifiers.
- Strong optimistic-concurrency behavior where applicable.
- Idempotency for retry-safe POST operations.
- Audit for successful and failed writes.
- Ownership history only for ownership-changing operations.
- Backward compatibility for existing CRM owner fast-path reads.

## 7. Performance Baseline

Dataset:

- 100 tenants.
- 100,000 assignments.
- At least 1,000 assignments in the measured tenant.
- Representative queue, rule, history and transfer data.

Targets:

```text
GET /api/v2/crm/my-work: p95 < 200 ms
GET /api/v2/crm/queues/{id}/items: p95 < 150 ms
GET /api/v2/crm/ownership-history: p95 < 100 ms
```

Query-plan evidence must show tenant-leading index use and no unbounded sequential scan on measured paths.

## 8. Frontend Evidence

Required surfaces:

- My Work.
- Teams.
- Queues.
- Territories.
- Assignment Rules.
- Transfers.
- Record ownership panel/history.

For each surface:

- Arabic and English.
- RTL and LTR.
- Loading, empty, success, validation, 403, 409 and backend-failure states.
- Keyboard navigation and accessible names.
- No critical axe violations.
- Capability-aware controls without trusting the client as authorization.

## 9. Production Proof — AC-15

Entry:

- Protected implementation merge completed.
- First 20 criteria passed.
- Vercel and Render identities equal the exact release SHA.
- Render serves the immutable backend image for that SHA.
- Flyway and schema postconditions verified read-only.
- Two least-privilege identities exist in separate tenants.
- Approved Production change and backup/PITR references exist.

Scenario:

1. Tenant A authenticates.
2. Creates a team and queue.
3. Creates or routes an ownable CRM record.
4. Claims or assigns the record.
5. Requests a transfer.
6. A distinct manager approves.
7. Audit and ownership history show one correlated chain.
8. Tenant B access is denied without leakage.
9. Health remains UP.
10. No unexplained 5xx occurs.

Evidence:

- Workflow and job IDs.
- Exact repository/frontend/backend SHA.
- Deployment IDs and image digest.
- Redacted request/result summary.
- Redacted audit and history exports.
- Flyway/schema report.
- Runtime error window.
- Immutable artifact digest.

## 10. Failure Rules

A criterion fails on:

- Unexpected HTTP 5xx.
- Tenant leakage or capability bypass.
- Duplicate ACTIVE assignment.
- Lost round-robin update.
- Partial transfer commit.
- Mutable ownership history.
- Migration silent repair.
- Missing/mismatched evidence SHA.
- Skip, timeout increase, retry masking or expected-status weakening.
- Required test not executed.

A rerun success does not erase a proven defect without root-cause evidence.

## 11. Evidence Directory Contract

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

The manifest covers every file. Credentials and raw secrets are prohibited.

## 12. Decision

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