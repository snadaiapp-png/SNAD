# CRM Current Baseline

> **Authoritative branch:** `main`  
> **CRM-008R authorized base:** `d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4`  
> **Reconciled:** 2026-07-26  
> **Document status:** AUTHORITATIVE FOR CRM-007 / CRM-008R / CRM-009 / CRM-010 SCOPE

This document is the current source of truth for the as-built CRM state relevant
to CRM-007 and CRM-008. Historical design documents, issue bodies and pull
request descriptions remain evidence of their time and are not current status
declarations when this baseline supersedes them.

Repository merge, production evidence and commercial authorization are separate
controlled decisions. Source merge or CI success alone does not authorize
commercial production use.

## 1. Current stage status

```text
CRM_PRODUCT_BUILD: IMPLEMENTED_AND_CONNECTED
CRM_BUILD_READINESS: CLOSED
PLATFORM_CORE_DEPENDENCY: SATISFIED

CRM_G1: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_007: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_007_FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
CRM_007_FINAL_EVIDENCE: docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md

CRM_008A_DESIGN: CLOSED_APPROVED
CRM_008B_IMPLEMENTATION: MERGED
CRM_008B_ORIGINAL_HEAD_SHA: cf20094dc5998b6b42d20dcbcb16743b07058852
CRM_008B_ORIGINAL_MERGE_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
CRM_008R_CORRECTIVE_REMEDIATION: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_008R_ISSUE: #725
CRM_008R_PR: #726 / MERGED
CRM_008R_MERGE_AUTHORIZED: YES
CRM_008R_DEPLOYMENT_AUTHORIZED: YES

CRM_008_TEAM_MANAGEMENT: CLOSED_WITH_PRODUCTION_EVIDENCE
CRM_008_CLOSURE_DATE: 2026-07-29
CRM_008_FINAL_STATUS: CERTIFIED
CRM_008_EVIDENCE: docs/crm/crm-008/CRM-008-FINAL-CLOSURE-CERTIFICATE.md

CRM_009_WORKFLOW_AI_GATEWAY: CLOSED_WITH_FULL_EVIDENCE
CRM_009_CLOSURE_DATE: 2026-07-29
CRM_009_FINAL_STATUS: CERTIFIED
CRM_009_PR: #704 / MERGED
CRM_009_EVIDENCE: docs/crm/crm-009/CRM-009-FINAL-CLOSURE-CERTIFICATE.md
CRM_009_PRODUCTION_STATUS: PASS
CRM_009_REMEDIATION_COMPLETE: YES
CRM_009_HIGH_FINDINGS: 0

CRM_010_CUSTOMER_360_INTELLIGENCE: STARTED
CRM_010_INITIATION_DATE: 2026-07-29
CRM_010_PRE_EXECUTION_GATE: PASSED
CRM_010_FINAL_STATUS: READY_FOR_AGENT_EXECUTION
CRM_010_EVIDENCE: docs/crm/crm-010/CRM-010-EXECUTION-PLAN.md
CRM_010_ARTIFACTS: 15 documents (6 initiation + 9 pre-execution)

CRM_011_AND_LATER_STAGES: PRESERVED_AND_GOVERNED_SEPARATELY
COMMERCIAL_GO_LIVE: NOT_INFERRED_FROM_THIS_DOCUMENT

# CRM-029: Issue #189 traceability
ISSUE_189: CI-PLATFORM-01 — Restore GitHub Actions execution
ISSUE_189_STATUS: OPEN
ISSUE_189_WORKFLOW_REFERENCE: .github/workflows/crm-deployment-readiness.yml
ISSUE_189_GOVERNANCE: docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md (CRM-029)
```

## 2. Architectural baseline

CRM remains inside the approved modular platform architecture:

- Backend domain: `apps/sanad-platform/src/main/java/com/sanad/platform/crm`.
- Operational UI: `apps/web/app/crm`.
- Authenticated API boundary: `/api/v1/crm/*` and `/api/v2/crm/*`.
- Tenant identity source: authenticated context only.
- Authorization: capability-based and deny-by-default.
- Persistence: PostgreSQL with forward-only Flyway migrations.
- Concurrency: ETag/If-Match plus atomic database compare-and-set or row locking.
- Audit: central platform AuditPort and governed authorization evidence.
- Timeline: central CRM TimelineEventPort.
- Workflow and AI: central platform integrations; no parallel CRM runtime.

Compatibility fields such as `crm_contacts.account_id`, primary email and phone
projections and legacy address projections remain transitional. Their removal
requires a separate deprecation gate after all callers and generated clients
have migrated.

## 3. CRM-007 production baseline

CRM-007 delivers canonical owner-scoped addresses and communication methods for
ACCOUNT and PERSON records, including tenant-safe CRUD, lifecycle, primary and
preferred selection, verification, privacy masking, search, import, export,
Audit, Timeline, compatibility projections, bilingual UI, OpenAPI generation
and PostgreSQL upgrade evidence.

```text
FINAL_RELEASE_SHA: 4cedf631a3e61f39039615d93cd03c3111213eb9
VERCEL_DEPLOYMENT: dpl_FtG7Pj4MUBNjEFjahPopscqKn7b9
RENDER_DEPLOYMENT: dep-d9gartok1i2s7388lprg
RENDER_IMAGE_DIGEST: sha256:810e69e1c05668ebd9540b71554e13190c837d38004aa3a37dacbde7521cb2cd
CRM_G1_RUN: 29917230857 / SUCCESS
CRM_007_RUN: 29917314330 / SUCCESS
UNEXPECTED_CRM_HTTP_5XX: 0
```

The authoritative execution record is `docs/crm/EXEC-PROMPT-CRM-007.md`; the
immutable production record is
`docs/crm/evidence/CRM-007-FINAL-PRODUCTION-CLOSURE.md`.

## 4. CRM-008 implementation baseline

CRM-008A established the approved design for teams, memberships, queues,
territories, assignment rules, ownership assignment, immutable history, formal
transfers, tenant isolation, RBAC, audit and API governance.

CRM-008B implemented that foundation through PR #691:

```text
IMPLEMENTATION_HEAD_SHA: cf20094dc5998b6b42d20dcbcb16743b07058852
IMPLEMENTATION_MERGE_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
FEATURE_HEAD_WORKFLOWS: 23/23 SUCCESS
```

The as-built schema created 13 ownership tables and upgraded the pre-existing
`crm_assignments` table. The design-stage wording "14 new tables" is historical;
the authoritative implementation statement is `13 created + 1 upgraded`.

Deferred boundaries remain fail-closed:

- Multi-step approval requires the real Workflow Engine path.
- HRM absence reassignment remains disabled until real integration is authorized.
- Shared contributor ownership remains separately deferred.
- Commercial go-live is not implied by the implementation merge.

## 5. CRM-008R corrective scope

Post-merge review identified two hardening requirements:

1. Some ownership If-Match paths validated a pre-read value without retaining a
   database concurrency authority through the final mutation.
2. Teams, queues, transfers and assignment rules loaded unbounded tenant lists
   and truncated them in Java.

CRM-008R corrects those defects without rewriting historical evidence:

```text
ISSUE: #725
PR: #726 / MERGED
AUTHORIZED_BASE_SHA: d8c0e8dc330a054cc071c0c9ca2c8b59cf52dae4
ATOMIC_IF_MATCH: IMPLEMENTED_AND_ACCEPTED
DATABASE_CURSOR_PAGINATION: IMPLEMENTED_AND_ACCEPTED
FINAL_CLOSURE_EVIDENCE: PRODUCTION_VERIFIED
MERGE_AUTHORIZED: YES
DEPLOYMENT_AUTHORIZED: YES
```

The stage remains open until one unchanged exact head passes all gates, merges
with expected-head protection, deploys from the exact merge SHA and passes
production concurrency, pagination and tenant-isolation smoke tests.

## 6. Exact migration inventory

The governance drift check validates full migration filenames, not only Flyway
version numbers. The following inventory is authoritative for CRM files under
`apps/sanad-platform/src/main/resources/db/migration/`:

| Version | Exact file | Classification |
|---|---|---|
| `20260702.1` | `V20260702_1__create_unified_crm_core.sql` | MERGED |
| `20260702.2` | `V20260702_2__reconcile_admin_role_and_capabilities.sql` | MERGED |
| `20260702.3` | `V20260702_3__complete_crm_imports_custom_fields.sql` | MERGED |
| `20260706.1` | `V20260706_1__create_tenant_quota.sql` | MERGED |
| `20260711.1` | `V20260711_1__create_subscription_change_events.sql` | MERGED |
| `20260713.1` | `V20260713_1__create_crm_idempotency_records.sql` | MERGED |
| `20260713.2` | `V20260713_2__add_pipeline_version_column.sql` | MERGED |
| `20260716.1` | `V20260716_1__create_crm_tasks.sql` | MERGED |
| `20260716.2` | `V20260716_2__create_crm_notes.sql` | MERGED |
| `20260716.3` | `V20260716_3__create_crm_tags.sql` | MERGED |
| `20260716.4` | `V20260716_4__crm_enterprise_account_customer_master.sql` | MERGED |
| `20260717.1` | `V20260717_1__crm_contact_relationship_model.sql` | MERGED |
| `20260717.2` | `V20260717_2__crm_contact_relationship_capabilities.sql` | MERGED |
| `20260717.3` | `V20260717_3__crm_timeline_tenant_lifecycle.sql` | MERGED |
| `20260717.6` | `V20260717_6__create_crm_g1_extension_tables.sql` | MERGED / PRODUCTION RECONCILED |
| `20260717.100` | `V20260717_100__crm_addresses_communication_methods.sql` | MERGED / PRODUCTION VERIFIED |
| `20260717.101` | `V20260717_101__crm_addresses_communication_capabilities.sql` | MERGED / PRODUCTION VERIFIED |
| `20260718.1` | `V20260718_1__reconcile_crm_g1_after_baseline_gap.sql` | MERGED / PRODUCTION VERIFIED |

The CRM-008 PostgreSQL-native migration set is loaded from
`apps/sanad-platform/src/main/resources/db/vendor/postgresql/`:

- `V20260722_1__create_crm_sales_teams.sql`
- `V20260722_2__create_crm_queues.sql`
- `V20260722_3__create_crm_territories.sql`
- `V20260722_4__create_crm_assignment_rules.sql`
- `V20260722_5__upgrade_crm_assignments_and_create_ownership_history.sql`
- `V20260722_6__create_crm_transfer_requests.sql`
- `V20260722_7__add_owner_team_queue_columns.sql`
- `V20260722_8__seed_crm_ownership_capabilities.sql`
- `V20260722_9__create_crm_assignment_rule_counters.sql`

Later CRM stages may add vendor-specific migrations governed by their own stage
records. CRM-008R authorizes no new migration by default. Flyway repair, manual
history editing, destructive rollback and ad-hoc production SQL are prohibited.

## 8. CRM-009 implementation baseline

CRM-009 implements Workflow Engine & AI Gateway Integration through PR #704:

```text
PR: #704 / MERGED
IMPLEMENTATION_FILES: 32
TEST_CLASSES: 23
TEST_METHODS: 81
QUALITY_SCORE: 9.40/10
CLOSURE_DATE: 2026-07-29
FINAL_STATUS: CONDITIONAL_CERTIFIED
```

The CRM-009 PostgreSQL-native migration set is loaded from
`apps/sanad-platform/src/main/resources/db/vendor/postgresql/`:

- `V20260723_1__create_crm_integration_requests.sql`
- `V20260724_1__create_crm_command_executions_ledger.sql`
- `V20260724_2__create_crm_command_artifacts.sql`

**Remediation completed** (2026-07-29):
1. Audit trail integrated via AuditPort injection into CrmWorkflowUseCases and CrmIntegrationUseCases
2. Timeline events integrated via TimelineEventPort injection into CrmWorkflowUseCases and CrmIntegrationUseCases

## 9. API and security baseline

- Tenant authority is never accepted from request body or query parameters.
- Unauthenticated requests return 401; missing capabilities return 403.
- Cross-tenant access is concealed or rejected fail-closed.
- Idempotent writes reject missing idempotency keys.
- Version-governed writes require If-Match; stale requests return 412.
- CRM-008R holds the target PostgreSQL row lock through the mutation transaction.
- Collection cursors are opaque, tenant-bound and filter-bound.
- Target lists use bounded PostgreSQL keyset queries with `pageSize + 1`.

## 10. Evidence and closure control

CRM-007 remains closed; shared-infrastructure hardening does not reopen its
historical release evidence. CRM-008R remains open until:

- PostgreSQL race tests prove exactly one winner for a shared ETag;
- cursor first/middle/final, tampering and cross-tenant tests pass;
- CI, PostgreSQL, security, contract, authenticated acceptance, Web CI,
  Playwright, performance, backup and readiness gates pass on one SHA;
- PR #726 merges with expected-head protection;
- the exact merge SHA deploys and production smoke has zero unexplained 5xx;
- `docs/crm/crm-008/evidence/CRM-008B-FINAL-CLOSURE.md` is finalized;
- Issue #597 and PR #691 receive current-status blocks while preserving history.

## 11. CRM-031: Production GO Decision Record

CRM-031 records the formal production GO decision:

```text
TICKET: CRM-031
STATUS: COMPLETE
FEATURE_COMMIT: e81f78d6
MERGE_COMMIT: 2e2064d08328cf1487069d18c287b944b9da9860
PR: #838 / MERGED
PRODUCTION_GO_RECORD: docs/release/CRM-PRODUCTION-GO.md
DRIFT_CHECK_SECTION: 16
CLOSURE_DATE: 2026-07-31
FINAL_STATUS: CERTIFIED
```

### Evidence References

| Evidence | Location | Status |
|----------|----------|--------|
| Production SHA | `evidence/release-sha.json` | `beb6e18c` |
| Smoke evidence | `evidence/fullstack-remediation-010/REMEDIATION-EVIDENCE.md` | PASS |
| Flyway assertion | `CrmFlywayHistoryAssertionTest.java` | 5/5 PASS |
| Branch protection | `evidence/branch-protection-crm.json` | Configured |
| External approver | `docs/governance/SINGLE-EXTERNAL-APPROVER-AUTHORITY.md` | EXISTS |

### GO Decision Status

The production GO record is currently `NO-GO (DRAFT)`. The actual GO/NO-GO
decision requires explicit signatures from:
1. Project owner (account: `snadaiapp-png`)
2. Single external approver per `SINGLE-EXTERNAL-APPROVER-AUTHORITY.md`

### Issue #189 Traceability

```text
ISSUE_189: CI-PLATFORM-01 — Restore GitHub Actions execution
ISSUE_189_STATUS: OPEN
ISSUE_189_WORKFLOW_REFERENCE: .github/workflows/crm-deployment-readiness.yml
ISSUE_189_GOVERNANCE: docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md (CRM-029)
```

## 12. CRM-032: Penetration Test Closure

CRM-032 completes penetration testing for the CRM surface. HIGH-01 and
HIGH-02 were remediated by engineering changes (CRM-032 Engineering
Remediation, Security-by-Design) on 2026-07-31. No Risk Acceptance was used.

```text
TICKET: CRM-032
STATUS: GOVERNANCE COMPLETE
REMEDIATION: ENGINEERING (Security-by-Design), 2026-07-31
FEATURE_COMMIT: 1022b563
MERGE_COMMIT: 9455511727335244d7fb9dd8c4242a495785790a
PR: #839 / MERGED
PENTEST_REPORT: docs/audit/CRM-PENTEST-REPORT.md
RISK_ACCEPTANCE_REGISTER: docs/security/OWNER-RISK-ACCEPTANCE-REGISTER.md
DRIFT_CHECK_SECTION: 17
CLOSURE_DATE: 2026-07-31
FINAL_STATUS: GOVERNANCE COMPLETE
BLOCKER: RESOLVED — HIGH-01, HIGH-02 remediated (0 HIGH, 0 CRITICAL)
```

### Remediation Evidence

| Finding | Remediation | Evidence |
|---------|-------------|----------|
| HIGH-01: Test Encryption Key Hardcoded as Default | Startup guard + shared crypto validator + config hardening (test key default removed) | `ProductionSecurityGuard.java`, `CrmEncryptionKeyValidator.java`, `application-local.yml`, `ProductionSecurityGuardTest` 8/8, `CrmEncryptionKeyValidatorTest` 8/8 |
| HIGH-02: No Startup Guard for Production-Critical Security Features | `EnvironmentPostProcessor` validating encryption key, RLS, actuator exposure in prod | `ProductionSecurityGuard.java`, `META-INF/spring.factories`, `ProductionSecurityGuardTest` 8/8 |

### Security Findings

| Severity | Count | Status |
|----------|-------|--------|
| CRITICAL | 0 | ✅ None |
| HIGH | 0 | ✅ All remediated |
| MEDIUM | 7 | 📋 Documented (2 remediated, 5 tracked) |
| LOW | 4 | 📋 Informational |

### High Finding Status

| Finding | Risk ID | Status | Resolution |
|---------|---------|--------|------------|
| HIGH-01: Test Encryption Key | RISK-CRM-032-001 | ✅ REMEDIATED | Engineering — risk acceptance SUPERSEDED |
| HIGH-02: No Startup Guard | RISK-CRM-032-002 | ✅ REMEDIATED | Engineering — risk acceptance SUPERSEDED |

### Positive Security Controls Verified

| Control | Status |
|---------|--------|
| SQL Injection Prevention | ✅ PASS |
| XSS Prevention | ✅ PASS |
| Multi-Tenant Isolation | ✅ PASS |
| RBAC Enforcement | ✅ PASS |
| CORS Configuration | ✅ PASS |
| Error Handling | ✅ PASS |
| Bootstrap Security | ✅ PASS |
| Refresh Token Security | ✅ PASS |
| Session Versioning | ✅ PASS |
| File Upload Security | ✅ PASS |
| XXE Prevention | ✅ PASS |
| Rate Limiting | ✅ PASS |

---

## 13. CRM-022: Governance Remediation Closure

CRM-022 (CRM-specific CI job) reached governance closure on 2026-08-01 after
a repository-wide Governance Drift Check PASS. All governance drift
violations originating from CRM-022 were remediated; no risk acceptance was
used and the drift rule was not modified.

```text
TICKET: CRM-022
STATUS: GOVERNANCE COMPLETE
REPOSITORY_DRIFT: PASS
DRIFT_SCRIPT: scripts/crm/governance-drift-check.sh
REMEDIATION_CERT: docs/crm/crm-022/CRM-022-REMEDIATION-CERTIFICATION.md
CLOSURE_RECORD: docs/crm/crm-022/CRM-022-GOVERNANCE-CLOSURE.md
EVIDENCE_DOC: docs/crm/crm-022/CRM-022-GOVERNANCE-EVIDENCE.md
REMEDIATION_COMMIT: 34a3bb47cd87154c69346169202c20b043fcf57b
CLOSURE_DATE: 2026-08-01
FINAL_STATUS: GOVERNANCE COMPLETE
```

## 14. CRM-033: Performance Baseline — Infrastructure Blocker Removal

CRM-033's infrastructure blocker — no automated path to a valid JWT in a
clean environment for performance testing — was removed via a permanent,
production-safe, profile-gated authentication strategy. The benchmark now
executes end-to-end automatically (k6 self-authenticates via the real
`/api/v1/auth/login` pipeline; no manual intervention, no H2 console, no
manual SQL).

```text
TICKET: CRM-033
STATUS: INFRASTRUCTURE BLOCKER REMOVED — PERMANENT AUTH STRATEGY VERIFIED
EXECUTION_GATE: docs/crm/crm-033/CRM-033-EXECUTION-GATE-AUTHORIZATION.md
BLOCKER_REPORT: docs/crm/crm-033/CRM-033-BLOCKER-REPORT.md
PERFORMANCE_REPORT: docs/crm/crm-033/CRM-033-PERFORMANCE-REPORT.md
CERTIFICATION: docs/crm/crm-033/CRM-033-FINAL-CERTIFICATION.md
EVIDENCE: evidence/crm-perf-baseline.json
BENCHMARK_RUNS: 2 × 10 min @ 50 RPS (2026-08-01)
THROUGHPUT: 49.02 RPS (29,642 requests, run 2)
ERROR_RATE: 0.0135% (< 1% target — PASS)
P95: 1,128.7 ms (run 2) — NOT MET (< 500 ms target) on local 2-core hardware
P99: 3,131.8 ms (run 2) — NOT MET (< 1000 ms target) on local 2-core hardware
AUTH_FAILURES: 0 — automatic login verified 2/2 runs
CI_GATE: .github/workflows/performance-baseline.yml (crm-033-authenticated-benchmark)
CRM_034_AUTHORIZATION: NOT_AUTHORIZED — withheld until CI gate certifies thresholds
FINAL_DECISION: ✅ CRM-033 COMPLETE (infrastructure deliverable)
```

### Authentication Strategy (permanent fix)

- `apps/sanad-platform/src/main/resources/application-perf-test.yml` — `perf-test`
  profile: H2 in-memory (`MODE=PostgreSQL`), deterministic JWT secret from
  environment (`${PERF_TEST_JWT_SECRET:${JWT_SECRET:}}`), INFO logging, no H2
  console, RLS/import-worker disabled, actuator metrics exposed.
- `apps/sanad-platform/src/main/java/com/sanad/platform/security/config/PerfTestBootstrapConfig.java`
  — `@Profile("perf-test")`; fails fast on blank secrets; seeds deterministic
  tenant/organization/admin user (`must_change_password=FALSE`) + role with all
  active capabilities + CRM reference data (account, contact, pipeline,
  stages, opportunity, CONVERTED lead) in one transaction.
- `performance/k6/crm-performance-baseline.js` — k6 `setup()` performs the
  login; 50 RPS constant-arrival-rate for 10 min across dashboard, accounts
  list, customer-360, and lead-conversion (idempotent replay path).
- `.github/workflows/performance-baseline.yml` — `crm-033-authenticated-benchmark`
  job: starts the app under `perf-test`, waits for health, runs k6 in Docker,
  publishes summary/artifacts, fails on threshold breaches (p95 < 500 ms,
  p99 < 1000 ms, error rate < 1%).

### Verification

- Build + tests: 136 test classes / 935 testcases — 0 failures, 0 errors,
  11 skipped (38 Docker/Testcontainers-dependent classes excluded, documented
  in the performance report).
- Benchmarks: run 1 (29,574 req, 49.2 RPS, p95 978.4 ms), run 2 authoritative
  (29,642 req, 49.02 RPS, p95 1,128.7 ms, p99 3,131.8 ms, 0.0135% errors).
- Latency targets NOT met on the local 2-core reference hardware (Pentium B960
  @ 2.2 GHz); the 4-vCPU CI gate is the authoritative threshold certification
  path. All metrics recorded verbatim from evidence; k6-native verdict `FAIL`
  is preserved in `evidence/crm-perf-baseline.json` (honesty requirement).
