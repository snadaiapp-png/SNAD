# HRM-G0 — FINAL ENGINEERING CLOSURE CERTIFICATE

```
CURRENT AUTHORITATIVE STATE
---------------------------
Issued by: HRM-G0 Final Reconciliation & G1 Transition Directive
Reconciliation branch: fix/hrm-g0-reconciliation
Reconciliation PR: recorded on merge (see G0_CLOSURE_MERGE_SHA below)
```

## 1. Baseline identity

```text
CURRENT_MAIN_BASE = 73580379f68704e0327c1421fed412a7c6cc3c3b
G0_MERGE_SHA = 748e2c6076c94b7a29e2a5e8e4f4a817b0f2fc2b
G0_ANCESTRY = 748e2c60 IS AN ANCESTOR OF CURRENT_MAIN_BASE (verified by git merge-base --is-ancestor)
RECONCILIATION_CANDIDATE_SHA = recorded in section 9 after the candidate commit exists
```

Post-G0 commits on main were inventoried and classified: `73580379` (PR #987,
workflow fork/join fail-safe) and `57f395aa` (PR #986, workflow retry backoff
clamp). Both touch `apps/sanad-platform/.../workflow/**` and test sources only.

```text
POST_G0_HR_RELEVANT_COMMITS = NONE
UNEXPECTED_HR_DRIFT = NONE
```

## 2. Reconciliation verdict

```text
HRM_G0_IMPLEMENTATION = COMPLETE
HRM_G0_PR_914 = MERGED
POSTGRESQL_DIRECT = PASS
FULL_BACKEND = PASS
FULL_WEB = PASS
FLYWAY = PASS
RLS = PASS
TENANT_ISOLATION = PASS
SECURITY = PASS
AUDIT = PASS
IDEMPOTENCY = PASS
API_CONTRACT = PASS
CUTOVER_REHEARSAL = PASS

SOURCE_DEFECTS_FOUND = 1 (T-G0-DEF-1 — remediated in this reconciliation, see section 6)
SOURCE_DEFECTS_OPEN = 0

LEGAL_REVIEW = BLOCKED_OR_PENDING_HUMAN
SA_PACK = DRAFT
PRODUCTION_AUTHORIZATION = NO
PRODUCTION_MUTATIONS = 0
PRODUCTION_DEPLOYED = NO
```

## 3. Environment evidence (PostgreSQL rule compliance)

```text
POSTGRESQL_HOST_NATIVE = YES (local disposable cluster, PostgreSQL 16.4 binaries, host-native initdb/pg_ctl)
PSQL = psql (PostgreSQL) 16.4
PG_ISREADY = 127.0.0.1:5437 — accepting connections
DOCKER_USED = NO
TESTCONTAINERS_USED = NO
CONTAINERIZED_POSTGRESQL = NO
GITHUB_POSTGRES_SERVICE_CONTAINER = REMOVED from CI in this reconciliation
DISPOSABLE_DATABASE = fresh initdb cluster created for verification; zero production contact
```

Application runtime role contract, asserted directly against the rehearsal
database and enforced identically in CI provisioning:

```text
sanad                     rolsuper=false rolcreatedb=false rolcreaterole=false rolbypassrls=false rolcanlogin=true
crm_contact_rls_test_user rolsuper=false rolcreatedb=false rolcreaterole=false rolbypassrls=false rolcanlogin=true
```

## 4. Backend verification (exact reconciliation candidate)

Full Maven suite, executed in ordered verification chunks on one fresh
disposable host-native PostgreSQL instance, against the reconciliation
candidate SHA:

```text
TEST_COUNT = 3107 (aggregate across 20 chunk executions, including the dedicated pg-acceptance profile run)
FAILURES = 0
ERRORS = 0
SKIPPED = 0 (the 6 profile-gated CommerceOrderPostgresConcurrencyTest cases were
            executed by the dedicated pg-acceptance run: 6 tests, 0 failures,
            0 errors, 0 skipped — their default-profile skip is a documented
            RC-4 design, not an unexplained skip)
TEST_CLASSES_COVERED = 398/398
HRM_FOCUSED_SUITE = 531+ tests across 43 HR test classes — ALL GREEN
```

HRM-focused gates verified green in the suite run, among others:
`HrRlsFailClosedIntegrationTest`, `HrTenantContextRegressionTest`,
`HrScopedAuthorizationScopeMatrixIntegrationTest`,
`HrHistoricalAuthorizationIntegrationTest`,
`HrSensitiveReadAuditIntegrationTest`, `HrAuditOutboxAtomicityIntegrationTest`
(18 tests), `HrOutboxDeliveryIntegrationTest`, `HrIdempotencyIntegrationTest`
(12 tests), `HrIamPolicyConsumerIntegrationTest` (10 tests),
`HrModuleBoundaryArchitectureTest`, `HrOpenApiContractTest`,
`HrCutoverStateIntegrationTest`, `HrCanonicalBackfill*IntegrationTest`,
`HrCountryPolicyResolverTest`, `HrComplianceEngineTest`,
`HrCountryPackLifecycleIntegrationTest`, `HrContractCompensationBoundaryTest`,
`HrEmploymentLifecycleIntegrationTest` (31 tests), plus the new
`HrEmploymentCanonicalWriteIntegrityIntegrationTest` (4 tests).

## 5. Web verification

```text
WEB_LINT = PASS (0 errors; 45 pre-existing warnings, unchanged)
WEB_TYPECHECK = PASS (tsc --noEmit, clean)
WEB_UNIT_TESTS = PASS (840 tests, 0 failures — including the new G0 closure-state regression suite)
WEB_PRODUCTION_BUILD = PASS (next build, success)
```

## 6. Source defect ledger

### T-G0-DEF-1 — Placeholder identity + hardcoded classification on the canonical employment write path (FOUND AND REMEDIATED)

* Discovery: Phase 1 reconciliation inventory (read-only audit at the
  candidate base).
* Defect: `JdbcEmploymentRepository.saveEmployment` persisted
  `first_name='Test'`, `last_name='Employee'`, `display_name='Test Employee'`
  and hardcoded `employment_type='FULL_TIME'` for every employment created
  through the canonical path, discarding the real Person identity and the
  real worker classification. Read-side blast radius was contained: the web
  Employee Directory and the v1 compatibility projection read names from the
  canonical `hr_people` table, but the v1 projection reads `employment_type`
  from the legacy column, so non-FULL_TIME classifications were mis-projected.
* Remediation (this reconciliation): names are projected from the canonical
  `hr_people` row inside the same tenant transaction (missing person ⇒
  fail-closed write rejection); `employment_type` now persists the real
  `workerClassificationCode` (out-of-vocabulary codes are rejected by the
  legacy CHECK constraint instead of being silently masked).
* Regression lock: `HrEmploymentCanonicalWriteIntegrityIntegrationTest` —
  4 PostgreSQL Direct tests, all green on the rehearsal database.
* Status: `T-G0-DEF-1 = REMEDIATED_IN_RECONCILIATION_PR`

## 7. Flyway and migration rehearsal (fresh disposable database)

```text
FLYWAY_CHAIN = PASS (complete migration chain applied from an empty database)
FLYWAY_VALIDATE = validate-on-migrate enabled in CI provisioning; no validation failures
FLYWAY_TOTAL_MIGRATIONS = 170 rows in flyway_schema_history
FAILED_MIGRATIONS = 0
FLYWAY_TERMINAL_VERSION = 20260905.18 (V20260905_18__reconcile_y2_identity_with_g0_cutover.sql;
  final row is repeatable R__finalize_hr_backfill_closure.sql)
DUPLICATE_ACTIVE_VERSIONS = 0
HR_SCHEMA_EXISTS = PASS (33 HR/business tables; all key tables present)
NO_DB_ROLLBACK = enforced (no destructive migration; no schema-history modification)
PREVIOUS_BINARY_ROLLBACK = application-level compatibility preserved by expand-safe
  migrations; no schema rollback required or attempted
PRODUCTION_TOUCHED = NO
```

RLS rehearsal (direct probes on the rehearsal database, application role):

```text
OWN_TENANT_READ = PASS
CROSS_TENANT_READ = DENIED (0 rows)
CROSS_TENANT_WRITE = DENIED (42501: new row violates row-level security policy)
NO_TENANT_CONTEXT = FAIL_CLOSED (read: 0 rows; write: 42501)
APPLICATION_ROLE_SUPERUSER = FALSE
APPLICATION_ROLE_BYPASSRLS = FALSE
HR_TENANT_SCOPED_TABLES_FORCE_RLS = 31/31
GLOBAL_REFERENCE_TABLES_WITHOUT_RLS = hr_country_packs, hr_compliance_rules —
  BY DESIGN: no tenant_id column; shared reference data (6 GCC pack shells,
  0 rules), asserted DRAFT by HrCountryPackLifecycleIntegrationTest
```

## 8. Governance drift remediated by this reconciliation

```text
DOCUMENTATION_DRIFT = FIXED (docs/hrm/g0/evidence/03, 04, 05, 06 now carry a
  CURRENT AUTHORITATIVE STATE block; historical pre-merge statements preserved verbatim)
EXECUTION_DASHBOARD_DRIFT = FIXED (apps/web/app/hr/hr-execution-data.ts no longer
  reports G0 as NOT_STARTED; G0 group/tasks reconciled to DONE; certification
  bound to this certificate; regression suite hr-g0-closure-state.regression.test.ts
  binds dashboard state to this file and fails closed if the certificate disappears)
CI_GOVERNANCE_DEFECTS = FIXED (PostgreSQL service containers removed from ci.yml and
  post-merge-verification.yml; post-merge verification redesigned into parallel jobs
  A–F with a fail-closed aggregation gate; evidence validator strengthened with
  postgresql_host_native / provision_db / db_role_contract / hrm_focused_tests as
  critical checks; hrm-human-preview.yml annotated as historical harness)
```

## 9. Reconciliation candidate commit

```text
RECONCILIATION_CANDIDATE_SHA = (recorded post-commit in the PR description; the
  candidate is the head of fix/hrm-g0-reconciliation carrying this file)
G0_CLOSURE_MERGE_SHA = recorded after the protected merge lands on main (Phase 9)
```

## 10. Claim discipline

This certificate deliberately does NOT contain, and must never be quoted as,
the following claims, because no independent gate has proven them:

* `PRODUCTION_READY`
* `PRODUCTION_CERTIFIED`
* `SAUDI_LEGAL_COMPLIANT`

Legal review of the Saudi Country Pack is an independent human gate
(`LEGAL_REVIEW = BLOCKED_OR_PENDING_HUMAN`); the pack remains `DRAFT`
(`SA_PACK = DRAFT`). Production remains untouched
(`PRODUCTION_AUTHORIZATION = NO`, `PRODUCTION_MUTATIONS = 0`).

## 11. Follow-up defect register (non-blocking, out of reconciliation scope)

1. **HRM-CERT-PERSISTENCE** — HR execution certifications other than G0 are held
   in a process-local Map (`HrExecutionProvider`) and do not survive restart;
   durable certification persistence requires a DB-backed store. The G0
   documented certification is authoritative and restart-safe (this
   certificate); the volatility of runtime submissions is documented in the
   provider and cannot fabricate or downgrade G0 state.
2. **HRM-PREVIEW-HARNESS** — `hrm-human-preview.yml` remains pinned to PR #914
   as a historical record; a generalized HR preview harness is required when
   HRM-G1 reaches its first human preview.
3. **CI-PROVISION-DEDUP** — the least-privilege provisioning heredoc is
   duplicated across CI jobs; extract a reusable composite action (behavioral
   parity required, no gate weakening).
