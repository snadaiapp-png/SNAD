# R0C-12 Canonical Certification — Amendment #6 (GitHub Actions Facility)

PR #989 · branch `scp/r0c-12-access-check-jwt-correction`
Certified engineering head (FINAL_CANONICAL_SHA): `cb0bbedf4a8bfab44474aa1a25a22bf6cf2df0b1`
Certification facility: **GITHUB_ACTIONS** (hosted runners; conversation/sandbox lifecycle has zero effect)
Certification date (UTC): 2026-09-08

---

## 1. Executive verdict

| Gate | Verdict | Authoritative evidence |
|------|---------|------------------------|
| Gate G — Canonical full suite | **PASS** | CI run 34255622382, job "R0C-12 Canonical Gate G", artifact `r0c12-canonical-gate-g-cb0bbed-run-34255622382` |
| Gate H — Web / frontend | **PASS** | Web CI run 34255637383 (all steps success); PMV run 34255652534 JOB A + JOB E |
| Gate I — Security | **PASS** | PMV run 34255652534 JOB C/D/E; canonical frozen invariants; manifest result=PASS |
| PMV final aggregation | **PASS** | PMV run 34255652534 JOB F: `PMV_FINAL_GATE=PASS`, manifest `result=PASS`, `criticalFailures=[]`, `missingChecks=[]` |
| Main stability | **STABLE** | origin/main = `4b1b71990b33f58968f202f5ba61926844158f20`, ancestor of certified head |

`GATE_G_STATUS=PASS` · `FAILED_CONDITIONS=NONE` · `FINAL_INTEGRATED_GATE_G=PASS` · `CANONICAL_FACILITY=GITHUB_ACTIONS`

---

## 2. Gate G canonical execution contract (A6-4..A6-20)

Verdict file `A6-GATE-G-VERDICT.txt` (SHA-256 verified within artifact, 450/450 files OK):

```text
EXPECTED_SHA=cb0bbedf4a8bfab44474aa1a25a22bf6cf2df0b1
ACTUAL_SHA=cb0bbedf4a8bfab44474aa1a25a22bf6cf2df0b1
GITHUB_RUN_ID=34255622382
GITHUB_RUN_ATTEMPT=1
GITHUB_REF=refs/heads/scp/r0c-12-access-check-jwt-correction
EXECUTION_FACILITY=GITHUB_ACTIONS
PROCESS_PERSISTENCE_BINDING=PASS
JAVA_VERSION=21.0.12.1
MAVEN_VERSION=3.9.16
POSTGRES_VERSION=16.2
MAVEN_EXIT_CODE=0
TESTS=3469
FAILURES=0
ERRORS=0
SKIPPED=21
UNEXPLAINED_SKIPS=0
CRITICAL_SKIPS=0
XML_COUNT=431
SUREFIRE_RECONCILIATION=PASS
FROZEN_EVIDENCE_BINDING=PASS
PG_ACCEPTANCE_TESTS=21
PG_ACCEPTANCE_FAILURES=0
PG_ACCEPTANCE_ERRORS=0
PG_ACCEPTANCE_SKIPPED=0
CRITICAL_INVARIANTS=PASS
FAILED_CONDITIONS=NONE
GATE_G_STATUS=PASS
```

### 2.1 Environment (exact, fail-closed asserted on the runner)

- JDK: **Temurin `jdk-21.0.12.1+1`**, `java.version == 21.0.12.1` (hard assertion)
- Maven: **Apache Maven 3.9.16** (archive.apache.org, sha512-verified, `mvn -version` hard assertion)
- PostgreSQL: **16.2 built from official source release** (sha256-verified tarball) incl. contrib `btree_gist`; host-native process on 127.0.0.1:5432; **no Docker, no Testcontainers, no H2**
- Least-privilege contract (mechanically asserted `fffft`): role `sanad` = NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOBYPASSRLS/LOGIN; databases `sanad`, `test_migration` owned by `sanad`; `crm_contact_rls_test_user` under the same non-superuser RLS contract; fresh masked `CRM_CUSTOM_FIELD_ENCRYPTION_KEY` per run
- Cleanliness: APPLICATION_TABLE_COUNT=0, TEST_MIGRATION_TABLE_COUNT=0, STALE_SUREFIRE_XML=0

### 2.2 Canonical single-invocation suite

- Exactly ONE `mvn test -B -ntp -Dsurefire.useFile=false` (36+ min, no sharding, no retry)
- **3469 tests / 0 failures / 0 errors / 21 skipped** (431 TEST-*.xml)
- Console aggregate == XML element-sum (SUREFIRE_RECONCILIATION=PASS; reconciliation counts `<testcase>` elements — the testsuite `tests` attribute is unreliable under JUnit 5 nested/display-name containers)
- Skip classification: `CommerceOrderPostgresConcurrencyTest`=6, `RbacAccessCheckPostgresAcceptanceTest`=15 — both profile-gated (SPRING_PROFILES_ACTIVE=default ≠ pg-acceptance), UNEXPLAINED_SKIPS=0
- Critical invariants (from frozen XML): `FinanceModuleIntegrationTest` 11/0F/0E/0S · `AiSecurityNegativeTest` 7/0F/0E · `WorkflowY2CapabilityMigrationTest` 3/0F/0E

### 2.3 PG-acceptance companion (after Gate-G freeze)

Single invocation `SPRING_PROFILES_ACTIVE=pg-acceptance`, pristine `pg_acceptance` database:
`CommerceOrderPostgresConcurrencyTest` 6 executed + `RbacAccessCheckPostgresAcceptanceTest` 15 executed (`R0C12_RBAC_REAL_JWT`) = **21 / 0F / 0E / 0S**, exit code 0.

### 2.4 Process persistence binding (A6-9)

`PROCESS_PERSISTENCE_BINDING=PASS` — execution is a GitHub Actions workflow run (valid `GITHUB_RUN_ID`, `workflow_dispatch` event); the run lifecycle is owned by GitHub, independent of any chat session. Local Z-sandbox canonical execution is permanently disqualified (two mechanical interruptions: d717 run and A5-13 run, both classified `UNPROVEN_INFRA_INTERRUPTED`, `SUPPORTING_DIAGNOSTIC_EVIDENCE_ONLY`).

---

## 3. Gate H subgate mapping (mechanical, at the certified SHA)

| Subgate | Authoritative execution | Result |
|---------|------------------------|--------|
| install | PMV JOB A "Frontend dependencies" (npm ci); Web CI "Install dependencies" | success |
| lint | PMV JOB A "Frontend lint"; Web CI "Lint" | success |
| typecheck | PMV JOB A "Frontend type check" (sole authoritative typecheck) | success |
| web unit tests | PMV JOB A "Frontend unit tests"; Web CI "Test" (vitest) | success |
| production build | PMV JOB A "Frontend production build"; Web CI "Build Next.js application" | success |
| SDS | PMV JOB E "SDS compliance check"; Web CI "SDS Compliance Check" | success |
| logo | PMV JOB E "Logo governance check"; Web CI "Logo Governance Check" | success |
| brand | PMV JOB E "Brand name governance check"; Web CI "Brand Name Governance Check" | success |
| i18n parity | PMV JOB E "i18n key parity check" (sole authoritative parity check) | success |
| performance budget | PMV JOB E "Performance budget check"; Web CI "Performance Budget Check" | success |

## 4. Gate I subgate mapping (mechanical, at the certified SHA)

| Subgate | Authoritative execution | Result |
|---------|------------------------|--------|
| secret scan | PMV JOB E "Secret scanning" (canonical scanner; report artifact) | success |
| workflow security | PMV JOB E "Workflow security validation" (`check_workflow_security.py`) | success |
| RLS | PMV JOB D "HRM focused suite (RLS, tenant isolation, RBAC, audit, outbox, idempotency, IAM, contracts)" + canonical RLS classes (`CrmRlsTenantIsolationPostgresTest`, `TenantRlsTransactionContext`) | success |
| FORCE RLS | canonical migration/RLS assertion classes (`CrmPostgresMigrationTest`, `CrmFlywayHistoryAssertionTest`, `WorkflowSecurityNegativeTest`) | 0F/0E in frozen evidence |
| fail-open | canonical fail-closed negatives (`HrRlsFailClosedIntegrationTest`, `AiSecurityNegativeTest`) | 0F/0E in frozen evidence |
| guard removal | `FlywayJavaMigrationsChainConsistencyTest` (guard `assertClassAbsent`) in canonical suite | 0F/0E in frozen evidence |
| privilege expansion | PMV JOB C "Assert least-privilege role contract" + canonical `NOSUPERUSER=PASS`/`NOBYPASSRLS=PASS` | success |
| migration policy | canonical Flyway chain/history assertion tests (incl. CRM version ledger) | 0F/0E in frozen evidence |
| V15 Java migration absence | `FlywayJavaMigrationsChainConsistencyTest` in canonical frozen evidence | V15 absent, 0F/0E |
| production smoke fail-open | PMV JOB A "Smoke test — Frontend auth entry route" + PMV JOB C "Smoke test — Backend health" (operational smokes at exact SHA; fail-closed manifest accounting in JOB F) — production infrastructure NOT accessed (PRODUCTION=NOT_AUTHORIZED) | success |

PMV manifest (artifact `verification-manifest-34255652534`): `result=PASS`, `criticalFailures=[]`, `missingChecks=[]`.

---

## 5. Certification chain integrity

1. Engineering head `cb0bbedf` = merge(parents `94c5241e` + main `4b1b7199`) — forward-only; no rebase, no force-push at any point of the cycle.
2. Gate G PASS run 34255622382 completed 18:07:54Z at `cb0bbedf`; Gate H/I runs (34255637383, 34255652534) completed 17:27Z / 18:12:54Z at the same SHA (chronology held across the full cycle; at the previous head `94c5241e` the order was G 16:08:54Z → H/I 17:07Z; drift #4 was then integrated and all three re-certified at `cb0bbedf`).
3. `git merge-base --is-ancestor origin/main cb0bbedf` = true at certification time (A6-27 FINAL_CERTIFICATION_VALID=YES).
4. Supporting-only evidence (NOT certification): CI run 34217129677 (PG 16.15, Maven not mechanically bound); d717 local Gate G; A5-13 local attempt (both `UNPROVEN_INFRA_INTERRUPTED`).
5. Facility shakeout record: five mechanical authoring defects in the governance file were root-caused and fixed in-session (runner-context env, pre-checkout working-directory, checkout `git clean` vs evidence dir, bare-hash sha512, PG bool text-cast probe, stale-dir probe under `set -e`, testsuite-attribute vs testcase-element reconciliation). Every failed run produced fail-closed `UNPROVEN_OR_FAIL` artifacts — no false PASS was possible at any point.

## 6. Artifact index (GitHub Actions, retention ≥ 14 days)

- `r0c12-canonical-gate-g-cb0bbed-run-34255622382` — canonical console (full), frozen surefire XMLs (431), metadata, reconciliation, verdict, acceptance evidence, SHA256SUMS
- `verification-manifest-34255652534` — PMV final manifest
- PMV run 34255652534: JOB A/B/C/D/E fragments, secret-scan report, smoke evidence
- Web CI run 34255637383: vitest diagnostics, Next.js build output
