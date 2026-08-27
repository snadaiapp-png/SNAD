# HRM-G0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate SNAD's existing HR foundation to the approved canonical, country-first HRM-G0 architecture while preserving tenant isolation, backward compatibility, and production safety.

**Architecture:** Execute the approved Evolutionary Modular HRM design as six independently reviewable workstreams. The sequence is Expand → Backfill → Reconcile → Verified Cutover → v1 compatibility → Contract legacy, with PostgreSQL Direct as the acceptance database and `/api/v2/hr` as the canonical API.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring Security/AOP/Validation, PostgreSQL 17 Direct, Flyway, JdbcTemplate/JPA where already established, springdoc OpenAPI 3.1 surface, Next.js 16.2, React 19.2, TypeScript 5.9, Vitest 4.1, Playwright 1.61.

**Spec:** `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`

## Global Constraints

- Baseline implementation starts from current protected `main`; the approved design baseline was `2dd8d1151ec0b231a51c13ee20722da6598e89e3`. Re-check all repository facts and migration-number collisions after rebasing to a newer `main`.
- PostgreSQL Direct is the backend acceptance runtime; do not introduce H2-only acceptance behavior.
- Country/jurisdiction resolution is G0.0 and precedes statutory HR behavior.
- Saudi Arabia is the first localized pack; AE, QA, BH, KW, OM use the same dynamic Country Pack mechanism; unsupported/unapproved countries use Global Mode only.
- No statutory numeric rule is production-authoritative without official-source provenance, effective dates, legal review, and automated test evidence.
- `User != Person != Employment != Assignment`.
- Legal Entity is the Employer of Record; Organization is the operational boundary.
- No operational hard delete of Employment.
- `TERMINATED` and `VOIDED` are terminal. Rehire creates a new Employment.
- Every ACTIVE Employment has exactly one effective PRIMARY Assignment.
- Position is policy-driven; when used, one Position is one seat and occupancy is derived from Assignments.
- RLS must fail closed when tenant context is absent or wrong; runtime DB role must not be superuser or BYPASSRLS.
- Current canonical `HR_MANAGER` remains exactly `HR.EMPLOYEE.READ`, `HR.EMPLOYEE.WRITE`, `HR.EMPLOYEE.ARCHIVE` until a separately reviewed role-template change is approved.
- Shared platform capabilities are contracts/SDKs; HR keeps producer-local outbox, audit evidence, and idempotency durability.
- `/api/v2/hr` is canonical. `/api/v1/hr` is compatibility-only; no new HR features are added to v1.
- Global Mode never claims certified local statutory compliance.
- Full Payroll, full GCC statutory catalogs, government submissions, Time & Attendance, Recruitment/Onboarding, Performance, Analytics, and electronic signature are outside G0.
- Never expose or commit passwords, JWTs, encryption keys, blind-index keys, cookies, API keys, or production secrets.

---

## Plan Suite and Dependency Graph

| Workstream | Plan | Depends on | Independent acceptance |
|---|---|---|---|
| WS1 Platform prerequisites | `2026-08-27-hrm-g0-01-platform-prerequisites.md` | approved spec | Country/Legal Entity/Work Location masters + crypto/platform contracts |
| WS2 HR Core & migration | `2026-08-27-hrm-g0-02-core-migration.md` | WS1 core master contracts | canonical Person/Employment/Structure/Assignment + backfill + fail-closed RLS |
| WS3 Country & compliance | `2026-08-27-hrm-g0-03-country-compliance.md` | WS1 + Employment/Jurisdiction schema from WS2 | CountryPolicyResolver + Global Mode + governed overrides + SA bootstrap |
| WS4 Security & integration | `2026-08-27-hrm-g0-04-security-integration.md` | WS1 + canonical resources from WS2 | scoped authorization + local audit/outbox/idempotency + IAM adapter |
| WS6 Contracts & compensation | `2026-08-27-hrm-g0-06-contract-compensation.md` | WS2 + WS3 + WS4 capability model | effective-dated contract/compensation foundations |
| WS5 API/UI/Cutover | `2026-08-27-hrm-g0-05-api-ui-cutover.md` | WS2–WS4; WS6 before final certification | v2, safe v1 adapter, operational UI, reconciliation and production gate |

Recommended execution order:

```text
WS1
 ↓
WS2
 ├──────────────┐
 ↓              ↓
WS3            WS4
 └──────┬───────┘
        ↓
       WS6
        ↓
       WS5
        ↓
HRM-G0 Production Certification
```

WS3 and WS4 may run in parallel only after WS2 has committed the canonical IDs and repository interfaces they both consume. Do not parallel-edit the same migrations or canonical aggregate classes.

### Task 1: Establish an isolated execution baseline

**Files:**
- Read/import: `docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md`
- Read/import: all HRM-G0 plan files under `docs/superpowers/plans/`.
- No production file changes in this task.

**Interfaces:**
- Consumes: approved design and this plan suite.
- Produces: isolated implementation worktree/branch based on current `main`, with no uncommitted production changes.

- [ ] **Step 1: Create the isolated worktree using the required Superpowers workflow**

Run after invoking `superpowers:using-git-worktrees`:

```bash
git fetch origin
git worktree add ../SNAD-hrm-g0 -b feat/hrm-g0-foundation origin/main
cd ../SNAD-hrm-g0
```

Expected: clean worktree on `feat/hrm-g0-foundation`.

- [ ] **Step 2: Verify baseline**

```bash
git status --short
git rev-parse HEAD
git log --oneline -5
```

Expected: clean status. If `main` advanced beyond `2dd8d1151ec0b231a51c13ee20722da6598e89e3`, treat the newer `main` as execution baseline and re-run repository/preflight checks before migrations.

- [ ] **Step 3: Import the exact approved design and latest plan suite without relying on future commit SHAs**

```bash
git fetch origin docs/hrm-g0-foundation-design
git checkout origin/docs/hrm-g0-foundation-design -- \
  docs/superpowers/specs/2026-08-27-hrm-g0-foundation-design.md \
  docs/superpowers/specs/2026-08-27-hrm-g0-cross-decision-review.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-implementation-plan.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-01-platform-prerequisites.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-02-core-migration.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-03-country-compliance.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-04-security-integration.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-05-api-ui-cutover.md \
  docs/superpowers/plans/2026-08-27-hrm-g0-06-contract-compensation.md
git add docs/superpowers/specs docs/superpowers/plans
git commit -m "docs(hrm): import approved G0 design and plans"
```

Expected: before implementation begins, the only changes relative to `origin/main` are the approved HRM design/plan documents.

- [ ] **Step 4: Verify Flyway sequence is still free**

```bash
find apps/sanad-platform/src/main/resources/db/migration -maxdepth 1 -type f \
  -name 'V20260827_*' -print | sort
```

Expected at the approved baseline: no existing `V20260827_*` files. If newer `main` contains collisions, renumber the entire HRM migration block contiguously before creating any migration; do not interleave duplicate versions.

- [ ] **Step 5: Record preflight evidence**

```bash
mkdir -p docs/hrm/g0/evidence
{
  echo "BASE_SHA=$(git rev-parse HEAD)"
  echo "STATUS=$(test -z "$(git status --porcelain)" && echo CLEAN || echo DIRTY)"
  echo "DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > docs/hrm/g0/evidence/00-preflight.txt
git add docs/hrm/g0/evidence/00-preflight.txt
git commit -m "docs(hrm): record G0 implementation preflight"
```

Expected: evidence file records the exact implementation baseline.

### Task 2: Execute WS1 and gate platform prerequisites

**Files:**
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-01-platform-prerequisites.md`

**Interfaces:**
- Produces: country registry, Legal Entity, LegalEntity↔Organization eligibility, Work Location, platform crypto contract, and reusable event/audit/idempotency contracts.

- [ ] Execute every checkbox in WS1 using TDD.
- [ ] Run the WS1 focused Maven tests named in that plan.
- [ ] Confirm no HR table migration references a Legal Entity/Work Location object that WS1 has not created.
- [ ] Record `WS1_PLATFORM_PREREQUISITES=PASS` only after the plan's acceptance tests pass.

Verification command:

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=PlatformCountryRegistryIntegrationTest,LegalEntityOrganizationEligibilityIntegrationTest,PlatformCryptographyServiceTest \
  test
```

Expected: BUILD SUCCESS.

### Task 3: Execute WS2 and gate canonical HR core

**Files:**
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-02-core-migration.md`

**Interfaces:**
- Consumes: WS1 master-data IDs/contracts.
- Produces: Person, Employment/history, Org Units/Jobs/Positions versions, Assignments, deterministic backfill, tenant migration state, fail-closed RLS.

- [ ] Execute WS2 task-by-task using TDD and PostgreSQL Direct.
- [ ] Stop cutover if any migration row is ambiguous or blocked.
- [ ] Confirm physical Employment DELETE is no longer reachable from the canonical path.
- [ ] Record `WS2_HR_CORE=PASS` only when backfill and RLS acceptance pass.

Verification command:

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCanonicalBackfillIntegrationTest,HrEmploymentLifecycleIntegrationTest,HrAssignmentTemporalConstraintTest,HrRlsFailClosedIntegrationTest \
  test
```

Expected: BUILD SUCCESS with zero unresolved migration rows in unambiguous test fixtures.

### Task 4: Execute WS3 and WS4 with controlled parallelism

**Files:**
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-03-country-compliance.md`
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-04-security-integration.md`

**Interfaces:**
- WS3 produces `CountryPolicyResolver`, `ComplianceEngine`, `ComplianceDecision`, governed override workflow.
- WS4 produces scoped authorization, local audit/outbox/idempotency, IAM and Platform Audit adapters.

- [ ] Run WS3 and WS4 in separate task branches/subagents only after WS2 canonical interfaces are stable.
- [ ] Do not let either workstream modify the other's migration files.
- [ ] Integrate against the port/event names specified in the subplans; do not independently invent duplicate interfaces.
- [ ] Run both focused test sets after integration.

Verification command:

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCountryPolicyResolverTest,HrComplianceOverrideIntegrationTest,HrScopedAuthorizationIntegrationTest,HrAuditOutboxAtomicityIntegrationTest,HrIdempotencyIntegrationTest \
  test
```

Expected: BUILD SUCCESS; hard statutory override is denied; controlled override enforces four-eyes; scope escalation is denied.

### Task 5: Execute WS6 contract and compensation foundation

**Files:**
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-06-contract-compensation.md`

**Interfaces:**
- Consumes: canonical Employment, country resolver, scoped capabilities, audit service.
- Produces: effective-dated contracts and compensation terms without Payroll calculation.

- [ ] Execute every WS6 checkbox.
- [ ] Verify no payroll formula, statutory deduction, payslip, or GL posting code is introduced.
- [ ] Verify compensation read/write requires independent capabilities and audit.

Verification command:

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrEmploymentContractIntegrationTest,HrCompensationIntegrationTest,HrSensitiveReadAuditIntegrationTest \
  test
```

Expected: BUILD SUCCESS.

### Task 6: Execute WS5 API/UI and verified cutover

**Files:**
- Plan: `docs/superpowers/plans/2026-08-27-hrm-g0-05-api-ui-cutover.md`

**Interfaces:**
- Consumes: all canonical backend resources and security/compliance contracts.
- Produces: `/api/v2/hr`, safe `/api/v1/hr` compatibility adapter, operational Arabic/RTL HR UI, reconciliation/certification evidence.

- [ ] Execute API v2 contract tasks before UI tasks.
- [ ] Keep v1 compatibility reads operational while forbidding unsafe status/delete semantics.
- [ ] Run backend contract and compatibility tests.
- [ ] Run frontend unit tests, lint, and build.
- [ ] Run Playwright/human preview acceptance before production merge.

Verification commands:

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrApiV2ContractTest,HrV1CompatibilityIntegrationTest \
  test

cd apps/web
npm test
npm run lint
npm run build
```

Expected: all commands pass.

### Task 7: Run the immutable HRM-G0 certification matrix

**Files:**
- Create: `docs/hrm/g0/evidence/HRM-G0-CERTIFICATION.md`
- Modify: `apps/web/app/hr/hr-execution-data.ts` only after all evidence is PASS.

**Interfaces:**
- Consumes: evidence from WS1–WS6.
- Produces: one release verdict and only then updates execution metadata.

- [ ] **Step 1: Run the full backend suite**

```bash
mvn -f apps/sanad-platform/pom.xml test
```

Expected: BUILD SUCCESS; failures=0; errors=0.

- [ ] **Step 2: Run full web verification**

```bash
cd apps/web
npm test
npm run lint
npm run build
```

Expected: all pass.

- [ ] **Step 3: Execute security/database acceptance against PostgreSQL Direct**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrRlsFailClosedIntegrationTest,HrScopedAuthorizationIntegrationTest,HrSensitiveReadAuditIntegrationTest,HrAuditOutboxAtomicityIntegrationTest \
  test
```

Expected: no-context/wrong-tenant/cross-tenant access denied; runtime role assertions show `rolsuper=false` and `rolbypassrls=false`.

- [ ] **Step 4: Execute country/compliance acceptance**

```bash
mvn -f apps/sanad-platform/pom.xml \
  -Dtest=HrCountryPolicyResolverTest,HrComplianceOverrideIntegrationTest \
  test
```

Expected: SA resolves only certified/effective rules; unsupported country resolves Global Mode; uncertified statutory action is blocked; hard override denied; controlled override requires independent approval.

- [ ] **Step 5: Generate certification report from actual evidence**

Create `docs/hrm/g0/evidence/HRM-G0-CERTIFICATION.md` containing the exact SHA and explicit results for every gate from §20 of the spec. Do not write PASS for a gate that lacks command/test evidence.

Required report skeleton:

```text
BASE_SHA=<exact SHA>
SCHEMA_MIGRATIONS=PASS|FAIL
BACKFILL_RECONCILIATION=PASS|FAIL
UNRESOLVED_MIGRATION_ROWS=<integer>
RLS_NO_CONTEXT=DENY|FAIL
RLS_WRONG_TENANT=DENY|FAIL
RUNTIME_BYPASSRLS=FALSE|FAIL
RUNTIME_SUPERUSER=FALSE|FAIL
COUNTRY_RESOLUTION=PASS|FAIL
GLOBAL_FALLBACK=PASS|FAIL
SA_PACK_RESOLUTION=PASS|FAIL
HARD_RULE_OVERRIDE=DENY|FAIL
CONTROLLED_OVERRIDE=PASS|FAIL
FOUR_EYES_APPROVAL=PASS|FAIL
API_V2_CONTRACT=PASS|FAIL
V1_SAFE_COMPATIBILITY=PASS|FAIL
ARABIC_RTL=PASS|FAIL
FULL_BACKEND_TESTS=PASS|FAIL
WEB_BUILD=PASS|FAIL
PRODUCTION_SMOKE=PASS|FAIL
BACKEND_5XX=NONE|FAIL
HRM_G0_CERTIFIED=YES|NO
```

The angle-bracket values in this evidence skeleton are runtime output slots, not implementation placeholders; the certification task replaces every slot with observed evidence before commit.

- [ ] **Step 6: Update HR execution metadata only if certified**

If and only if every required gate is satisfied, update `apps/web/app/hr/hr-execution-data.ts` G0 tasks/status to evidence-backed completion and point `stageReport` to the certification artifact. If any gate fails, leave G0 non-complete and record the blocker.

- [ ] **Step 7: Commit certification evidence**

```bash
git add docs/hrm/g0/evidence apps/web/app/hr/hr-execution-data.ts
git commit -m "docs(hrm): certify G0 foundation"
```

Expected: commit contains evidence plus metadata only; no unreviewed runtime changes.

### Task 8: PR, preview, production release and rollback readiness

**Files:**
- No new runtime logic in this task.
- Evidence: `docs/hrm/g0/evidence/HRM-G0-CERTIFICATION.md`

**Interfaces:**
- Produces: protected-branch PR and human preview gate; no direct push to `main`.

- [ ] Inspect the final diff for secrets, country-law hard-coding, direct cross-module DB access, physical Employment DELETE, and automatic HR_MANAGER privilege expansion.

```bash
git diff --check origin/main...HEAD
git diff --name-status origin/main...HEAD
rg -n "DELETE FROM hr_employees|HRM_PII_ENCRYPTION_KEY|HRM_PII_BLIND_INDEX_KEY" \
  apps docs --glob '!docs/superpowers/**'
```

Expected: no committed secret values; no canonical physical Employee delete; key names may appear only as configuration variable names, never values.

- [ ] Push the feature branch and open a PR to `main`.

```bash
git push -u origin feat/hrm-g0-foundation
```

- [ ] Wait for all protected-branch required checks plus any HRM-specific checks introduced by the implementation.
- [ ] Verify preview deployment and perform human acceptance on `/hr` including Arabic/RTL, employee directory, Employee 360, org chart, permission-gated PII/compensation, compliance warning/override flow, and safe error states.
- [ ] Do not merge if production migration rehearsal or rollback rehearsal is missing.
- [ ] After explicit human preview approval, merge through the protected PR workflow only.
- [ ] Deploy the exact merged SHA and verify production smoke against that SHA before declaring HRM-G0 released.

## Final Definition of Done

HRM-G0 is complete only when all six workstream plans are fully checked, the §20 design acceptance matrix is evidence-backed, required CI passes, human preview is accepted, the exact merged SHA is deployed, production smoke passes, and `HRM_G0_CERTIFIED=YES`. A successful build alone is not certification.
