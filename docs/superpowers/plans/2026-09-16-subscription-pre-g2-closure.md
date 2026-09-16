# Subscription PRE-G2 Exact-SHA Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one exact SHA for the Subscription/SCP branch that is fully certified across backend, PostgreSQL Direct, browser E2E, frontend, security, performance, and governance gates before merge.

**Architecture:** Keep stateful Subscription browser acceptance in its dedicated PostgreSQL Direct job and keep the generic Playwright matrix stateless. Diagnose failures from evidence before changes, then rerun all required gates only as a consequence of a new SHA.

**Tech Stack:** Java 21, Spring Boot, Maven Surefire, PostgreSQL Direct, Flyway, Next.js 16, React 19, TypeScript, Vitest, Playwright, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-16-subscription-pre-g2-closure-design.md`

## Global Constraints

- PostgreSQL Direct only; no Docker/Testcontainers for Subscription certification.
- Exact-SHA evidence only.
- Any new commit invalidates prior final certification evidence.
- No random reruns before failure classification.
- Mandatory Subscription skips = 0.
- No merge/deploy while any required gate is non-green.
- Stateful authenticated Subscription E2E runs only in its dedicated fixture-owning job.
- Production release begins only after PRE-G2 PASS.

---

### Task 1: Lock and classify the current baseline

**Files:**
- Read: PR #1062 and current branch refs
- Read: GitHub Actions runs for the exact branch SHA

**Interfaces:**
- Consumes: branch `feat/multi-org-subscription-billing-execution`, base `main`
- Produces: `BASELINE_SHA`, `MAIN_SHA`, ahead/behind status, required failing gates

- [ ] **Step 1: Read exact branch and main SHAs**

Run:
```bash
git fetch origin main feat/multi-org-subscription-billing-execution
git rev-parse origin/feat/multi-org-subscription-billing-execution
git rev-parse origin/main
```

Expected: immutable full SHAs.

- [ ] **Step 2: Prove branch is not behind main**

Run:
```bash
git rev-list --left-right --count origin/main...origin/feat/multi-org-subscription-billing-execution
```

Expected: right side may be >0, left side must be `0`.

- [ ] **Step 3: Classify every failed required workflow on the exact SHA**

Expected: each failure has a concrete failing job/step and ownership classification before code changes.

---

### Task 2: Fix generic Playwright ownership for Subscription acceptance

**Files:**
- Modify: `apps/web/playwright.standard.config.ts`
- Test: `apps/web/e2e/subscription-executive-acceptance.spec.ts`
- Reference: `apps/web/playwright.subscription-acceptance.config.ts`
- Reference: `.github/workflows/playwright-ci.yml`

**Interfaces:**
- Consumes: dedicated Subscription job with `SUBSCRIPTION_ADMIN_EMAIL`, `SUBSCRIPTION_ADMIN_PASSWORD`, isolated database and seed
- Produces: generic Playwright matrix that excludes the stateful Subscription acceptance spec while the dedicated Subscription job continues to include it

- [ ] **Step 1: Prove the current generic matrix incorrectly selects the Subscription spec**

Run:
```bash
cd apps/web
npx playwright test --config=playwright.standard.config.ts --list | grep subscription-executive-acceptance
```

Expected before fix: matches in the six generic projects.

- [ ] **Step 2: Add the dedicated-ownership exclusion**

Add to `testIgnore` in `apps/web/playwright.standard.config.ts`:
```ts
// Stateful Subscription acceptance owns its own PostgreSQL Direct database,
// seed, backend, frontend, and credentials. It runs exactly once through
// playwright.subscription-acceptance.config.ts in the dedicated CI job.
"**/subscription-executive-acceptance.spec.ts",
```

- [ ] **Step 3: Verify generic config no longer selects it**

Run:
```bash
cd apps/web
if npx playwright test --config=playwright.standard.config.ts --list | grep -q subscription-executive-acceptance; then
  echo "subscription acceptance leaked into generic matrix" >&2
  exit 1
fi
```

Expected: exit 0.

- [ ] **Step 4: Verify dedicated config still selects exactly the Subscription acceptance test**

Run:
```bash
cd apps/web
npx playwright test --config=playwright.subscription-acceptance.config.ts --list
```

Expected: `subscription-executive-acceptance.spec.ts` is present under project `subscription-acceptance`.

- [ ] **Step 5: Commit**

```bash
git add apps/web/playwright.standard.config.ts
git commit -m "fix(ci): isolate subscription acceptance from generic playwright"
```

---

### Task 3: Close Maven/backend failures on the new SHA

**Files:**
- Diagnose from: `apps/sanad-platform/target/surefire-reports/**`
- Modify only the production/test files implicated by the actual failing test
- Relevant Subscription tests:
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/api/LifecycleControllerCommandBoundaryTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/catalog/ApplicationCatalogLifecyclePostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/change/SubscriptionAnchorPostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/provisioning/EntitlementProvisioningIsolationPostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/provisioning/ProvisioningJobRunnerTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/rbac/G1GDynamic403ForensicPostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/billing/R0C13G02SchemaPostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/billing/R0C13G06SettlementReconciliationPostgresTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/SubscriptionDetailServiceTest.java`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/usage/UsageMeteringServiceTest.java`

**Interfaces:**
- Consumes: exact Maven failure evidence
- Produces: Maven suite with `Failures=0`, `Errors=0`, and no unexplained mandatory skips

- [ ] **Step 1: Read the failed Surefire class and assertion from CI artifacts/logs**

Expected: exact class + exact failing assertion/exception.

- [ ] **Step 2: Reproduce the first failing Surefire testcase exactly**

After downloading/extracting the `surefire-reports` artifact into `apps/sanad-platform/target/surefire-reports`, resolve the first failure/error and execute it:
```bash
cd apps/sanad-platform
selector="$(python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET
for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
    for suite in suites:
        for tc in suite.iter("testcase"):
            if tc.find("failure") is not None or tc.find("error") is not None:
                cls = (tc.get("classname") or "").split(".")[-1]
                name = tc.get("name") or ""
                print(f"{cls}#{name}")
                raise SystemExit(0)
raise SystemExit("no failing testcase found in Surefire XML")
PY
)"
test -n "$selector"
mvn test -B -ntp -Dtest="$selector" -DfailIfNoTests=true
```

Expected: the selected testcase reproduces the same failure before any implementation change.

- [ ] **Step 3: Add or preserve the minimal regression assertion**

The regression test must encode the observed failure, not a broader speculative refactor.

- [ ] **Step 4: Apply the minimal production fix**

Modify only the implicated code path.

- [ ] **Step 5: Run the focused test again**

Expected: PASS.

- [ ] **Step 6: Run the full Maven suite on PostgreSQL Direct**

Run:
```bash
cd apps/sanad-platform
mvn test -B -ntp -Dsurefire.useFile=false
```

Expected: build success; failures/errors zero.

- [ ] **Step 7: Commit only if code changed**

Commit message must identify the concrete defect.

---

### Task 4: Verify focused Subscription backend certification

**Files:**
- Read/test the Subscription tests listed in Task 3
- No production modification unless a focused test exposes a real defect

**Interfaces:**
- Consumes: Maven-green candidate SHA
- Produces: focused Subscription evidence with failures/errors/mandatory-skips all zero

- [ ] **Step 1: Run non-PostgreSQL Subscription unit/contract tests**

Run:
```bash
cd apps/sanad-platform
mvn test -B -ntp -DfailIfNoTests=true \
  -Dtest='LifecycleControllerCommandBoundaryTest,PlanVersionRouteOwnershipTest,PriceRouteOwnershipTest,ApplicationCatalogServiceTest,ProvisioningJobRunnerTest,SubscriptionDetailServiceTest,UsageMeteringServiceTest'
```

Expected: PASS.

- [ ] **Step 2: Run the PostgreSQL Direct Subscription companion**

Use the same host-native PostgreSQL role/database contract as CI, then run:
```bash
cd apps/sanad-platform
mvn test -B -ntp -DfailIfNoTests=true \
  -Dtest='ApplicationCatalogLifecyclePostgresTest,SubscriptionAnchorPostgresTest,EntitlementProvisioningIsolationPostgresTest,G1GDynamic403ForensicPostgresTest,R0C13G02SchemaPostgresTest,R0C13G06SettlementReconciliationPostgresTest'
```

Expected: all selected tests execute; failures=0; errors=0; mandatory skips=0.

---

### Task 5: Verify dedicated Subscription browser acceptance

**Files:**
- `apps/web/e2e/subscription-executive-acceptance.spec.ts`
- `apps/web/playwright.subscription-acceptance.config.ts`
- `apps/sanad-platform/src/test/resources/sql/subscription-acceptance-seed.sql`
- `.github/workflows/playwright-ci.yml`

**Interfaces:**
- Consumes: isolated PostgreSQL Direct database, seeded executive admin, Spring Boot backend, Next.js frontend
- Produces: proof of list → detail → generic CANCEL denial → governed cancel → CANCELLED UI/API readback → command ledger → audit

- [ ] **Step 1: Require exact-SHA checkout in the dedicated job**

Expected: checked-out SHA equals PR head SHA.

- [ ] **Step 2: Require least-privilege PostgreSQL role**

Expected role flags: `false|false|false|false` for superuser/createdb/createrole/bypassrls.

- [ ] **Step 3: Run the dedicated Playwright config exactly once**

Run in CI:
```bash
npx playwright test --config=playwright.subscription-acceptance.config.ts --reporter=html,list
```

Expected: PASS and no retry masking.

- [ ] **Step 4: Confirm durable acceptance assertions**

Required assertions:
- generic lifecycle `CANCEL` returns 409
- governed UI cancellation succeeds
- detail readback is `CANCELLED`
- command ledger contains `ACTIVE → CANCELLED`
- audit contains `SUBSCRIPTION.CANCEL`

---

### Task 6: Exact-SHA PRE-G2 certification

**Files:**
- No source changes during this task

**Interfaces:**
- Consumes: final candidate SHA after Tasks 2–5
- Produces: PRE-G2 PASS or a fail-closed blocker

- [ ] **Step 1: Lock candidate SHA and verify `behind main = 0`**

- [ ] **Step 2: Require terminal success on the same SHA for**

```text
CI
Web CI
Security Baseline
Pre-Merge Operational Smoke
Playwright E2E & Visual Regression
Subscription E2E
Performance Baseline
Compile Diagnostics
CRM API Contract Validation
CRM G1 Schema Isolation
CRM Modular Architecture Validation
Service Decomposition Validation
SNAD Identity Governance
Backup Restore Validation
Workflow Y2 G4 Release Gate
```

- [ ] **Step 3: Verify no unresolved review threads**

Expected: 0.

- [ ] **Step 4: Declare PRE_G2_REVIEW**

Only:
```text
PRE_G2_REVIEW = PASS
```
when every required condition above is proven on one SHA.

---

### Task 7: PR governance closure

**Files:**
- Update PR #1062 description/status only after PRE-G2 PASS

**Interfaces:**
- Consumes: certified exact SHA
- Produces: accurate review-ready PR

- [ ] **Step 1: Replace stale RED-tests-only PR description with the certified implementation/evidence summary**
- [ ] **Step 2: Mark PR ready for review**
- [ ] **Step 3: Re-read head SHA and ensure it did not change**

Expected: head remains the certified SHA.

---

### Task 8: Merge and production release gate

**Files:**
- No feature changes permitted

**Interfaces:**
- Consumes: approved PR at certified exact SHA
- Produces: exact-main release and production evidence

- [ ] **Step 1: Merge only the certified PR head**
- [ ] **Step 2: Capture new exact `main` SHA**
- [ ] **Step 3: Require main CI/release gates green on that SHA**
- [ ] **Step 4: Deploy backend through canonical release path and verify live immutable image SHA**
- [ ] **Step 5: Verify Flyway migration history includes the Subscription lifecycle migration**
- [ ] **Step 6: Verify Vercel production exact-main identity**
- [ ] **Step 7: Run isolated production Subscription smoke/acceptance**
- [ ] **Step 8: Roll back on any failed production gate**

Production completion must be proven independently from PRE-G2 repository certification.
