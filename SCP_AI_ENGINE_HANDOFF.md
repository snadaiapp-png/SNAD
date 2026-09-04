# SNAD — SCP AI ENGINE HANDOFF

## 1. Repository

Repository: snadaiapp-png/SNAD

Working branch: closure/scp-final-verification

Base: main

Protected safeguard: safeguard/scp-g1-g7-complete

PR: #924

Handoff SHA: a52f8c6f2e23ab89514c5991abd5e65f3312b0a3

---

## 2. Governance

Corrective work MUST remain on: closure/scp-final-verification

DO NOT:

- write directly to main
- modify safeguard/scp-g1-g7-complete
- force-push
- rewrite history
- disable tests
- weaken FORCE RLS
- grant SUPERUSER as a workaround
- grant BYPASSRLS as a workaround
- bypass Flyway validation without proof
- merge automatically

PostgreSQL governance: PostgreSQL Direct ONLY.

Tenant security invariants:

- FORCE RLS
- TenantRlsTransactionContext
- tenant GUC
- fail-closed tenant isolation
- least privilege

---

## 3. Proven Original SCP Regression

Original GitHub CI root cause: DUPLICATE_SPRING_MVC_MAPPING

Conflicting route: GET /api/v1/executive/audit

Original collision: PlatformOperationsQueryController#audit vs GovernanceController#audit

Impact: Maven Test Suite, CRM Integration Tests, PostgreSQL Acceptance Tests

---

## 4. Implemented Audit Fix

Legacy route: PlatformOperationsQueryController GET /api/v1/executive/audit

SCP additive route: GovernanceController GET /api/v1/executive/audit/v2

Web SCP client: GET /api/v1/executive/audit/v2

Regression coverage: ExecutiveAuditRouteCompatibilityTest

Web endpoint regression test: scp-api.test.ts

Do not reverse route ownership.

---

## 5. Important Closure Commits

Record the exact commit history from: git log --oneline safeguard/scp-g1-g7-complete..closure/scp-final-verification

Include every closure commit with: SHA, message, files affected, purpose

Do not summarize from memory.

---

## 6. Current CI Evidence

### RESOLVED — FINAL CERTIFICATION (engine order 01, 2026-08-31)

Maven root cause on cbab7ebb (RUN_ID 33382602609, JOB_ID 99458110666):
`Tests run: 2405, Failures: 7, Errors: 0, Skipped: 6` — ZERO ApplicationContext
failures; the earlier "context failure threshold" symptom belonged to the older
job 99314330558 only. All 7 failures were stale pinned contract tests:

- 6× "expected 20260828.1 but was 20260830.2" / unexpected
  [20260829.1, 20260829.2, 20260829.3, 20260829.4, 20260830.1, 20260830.2]
  in CrmFlywayHistoryAssertionTest (2), Crm008bFoundationAcceptanceTest (1),
  CrmPostgresMigrationTest (3) — the six intentional SCP migrations
  (V20260829_1..4, V20260830_1..2) were applied by Flyway but absent from pins.
- 1× PlatformApiCountTest: /api/v1/executive expected 46 but was 75 (29 new
  SCP endpoints); total pin 717 → 746.

CI_SQLSTATE = N/A (pure assertion failures, no SQL exceptions).
CI_UNIQUE_ROOT_CAUSES = 2 (stale Flyway inventory pins; stale API-count pins).
LOCAL_VS_CI_SIGNATURE_MATCH = YES (exact CI environment reproduced locally:
PostgreSQL 16 + ci.yml role provisioning → identical 7 failures).
The historical local "798 errors" were local-environment contamination
(broken local PostgreSQL credentials) — LOCAL_FAILURE_CAUSAL_TO_CI = NO.
Why only Maven fails: CRM gate selects crm.**.*IntegrationTest only and PG
acceptance only CommerceOrderPostgresConcurrencyTest — none of the four
pinned classes is in their selection.

FIX COMMIT = cef08f83ce56725fa2bece665adcb8d16f4c7128
(4 test files only; no product code, no CI config, no migration changes).

FINAL CI ON FIX COMMIT (RUN_ID 33392694483, JOB_ID 99489830215):
- Maven Test Suite = SUCCESS — `Tests run: 2405, Failures: 0, Errors: 0, Skipped: 6`,
  step "Run tests (PostgreSQL Direct)" = success, BUILD SUCCESS.
- CRM Integration Tests = SUCCESS · PostgreSQL Acceptance Tests = SUCCESS
- Required checks on SHA cef08f83…: 6/6 SUCCESS (exact branch-protection names:
  Build Next.js Web, provenance, CRM Integration Tests, Maven Test Suite,
  CRM Deployment Readiness, Verify 8 tables, 26 indexes, and tenant isolation).
- Non-required: Playwright E2E, Performance Baseline, Security Baseline,
  CRM API Contract Validation, CRM G1 Schema Isolation, Backup Restore
  Validation, Compile Diagnostics — ALL SUCCESS. Zero failures on any of the
  26 check runs for the SHA.

A final documentation checkpoint commit (this file) moves the PR head; per
the SHA identity rule, fresh CI re-certifies that final PR head SHA before
PR #924 is switched to Ready for Review.

### Historical evidence (pre-resolution)

Known previous results on a52f8c6f2e23ab89514c5991abd5e65f3312b0a3 included:

- CRM Integration Tests = SUCCESS
- PostgreSQL Acceptance Tests = SUCCESS
- Web CI = SUCCESS
- provenance = SUCCESS
- CRM Deployment Readiness = SUCCESS
- CRM API Contract Validation = SUCCESS
- CRM G1 Schema Isolation = SUCCESS
- Playwright = SUCCESS
- Performance Baseline = SUCCESS
- Security Baseline = SUCCESS

Maven Test Suite = FAILURE

For that HANDOFF_SHA the record was: CHECK_NAME Maven Test Suite, RUN_ID
33332810258, JOB_ID 99314330558, STATUS FAILURE — superseded by the final
certification above.

---

## 7. Current Blocking Issue

RESOLVED. There is no remaining blocker.

The Maven Test Suite blocker was diagnosed and fixed (see section 6):
root cause = stale pinned contract-test expectations after the intentional
SCP surface expansion (6 migrations + 29 executive endpoints); fixed by
commit cef08f83ce56725fa2bece665adcb8d16f4c7128; fresh CI certifies Maven
SUCCESS with 0 failures / 0 errors and 6/6 required checks green.

---

## 8. Local Maven Evidence

RESOLVED. A fresh CI-equivalent local environment (user-space PostgreSQL 16
provisioned exactly per ci.yml: role sanad NOSUPERUSER + database ownership
transfer + test_migration DB + crm_contact_rls_test_user, same env vars)
reproduced the CI failures EXACTLY before the fix: 24 targeted tests, 7
failures, 0 errors — identical signatures. After the fix the full suite was
executed locally in three exhaustive package chunks (820 + 338 + 1247 =
2405 tests, 0 failures, 0 errors, 6 skipped, exit 0 per chunk) and the class
set matched the CI surefire artifact exactly (322/322 XMLs).
The historical local result (TOTAL 1943 / ERRORS 798) came from a local
database with credential mismatches — classified LOCAL_ENVIRONMENT_MISMATCH,
NON_CAUSAL_TO_CI (the contaminated local PostgreSQL, not repository code).

---

## 9. Local PostgreSQL Warning

A previous local PostgreSQL environment had credential mismatches.

This was NOT proven as the GitHub CI root cause.

Do not modify product code merely to accommodate a contaminated local database.

Always distinguish: LOCAL_ENVIRONMENT_MISMATCH from CI_ROOT_CAUSE

---

## 10. Current Maven Mission

COMPLETED by engine order 01 (2026-08-31): GitHub Maven CI log → first
original failure (7 assertion failures, first = CrmPostgresMigrationTest.
upgradesExistingPlatformThroughCrmRbacAndCompletion) → deepest cause (stale
version/API pins vs intentional SCP surface) → local/CI signature match YES →
root cause TEST_CONFIGURATION proven HIGH confidence → minimum fix (4 test
files) → targeted green (24/24) → full Maven green (2405/0/0/6) → commit
cef08f83 → pushed closure → fresh CI RUN_ID 33392694483 → exact 6/6 →
STOP BEFORE MERGE honored.

---

## 11. Hard Maven Gate

No certification while: Maven Test Suite = FAILURE or MAVEN_FAILURES > 0 or MAVEN_ERRORS > 0

Required: MAVEN_FAILURES = 0, MAVEN_ERRORS = 0, MAVEN_EXIT_CODE = 0, MAVEN_RESULT = PASS

---

## 12. Required Branch Protection Checks

Final required checks:

1. Build Next.js Web = SUCCESS
2. provenance = SUCCESS
3. CRM Integration Tests = SUCCESS
4. Maven Test Suite = SUCCESS
5. CRM Deployment Readiness = SUCCESS
6. Verify 8 tables, 26 indexes, and tenant isolation = SUCCESS

Final requirement: REQUIRED_CHECKS_TOTAL = 6, REQUIRED_CHECKS_GREEN = 6, REQUIRED_CHECKS_FAILED = 0

All must certify the SAME SHA.

---

## 13. SHA Identity Rule

Final certification requires: CLOSURE_SHA == PR_HEAD_SHA == CI_CERTIFIED_SHA

Do not reuse old CI results after any new commit.

---

## 14. Non-required but Important Health Checks

Also record: PostgreSQL Acceptance Tests, CRM API Contract Validation, Playwright E2E & Visual Regression, Performance Baseline, Security Baseline, CRM G1 Schema Isolation

A new regression introduced by the Maven fix must not be hidden.

---

## 15. Final State Allowed

Only two outcomes are allowed: READY_TO_MERGE or BLOCKED

Never output: CERTIFICATION_COMPLETE while Maven is failing.

---

## 16. Merge Governance

Even after READY_TO_MERGE: DO NOT merge automatically.

Final field: MERGE_PERFORMED = NO until explicit owner instruction.

---

## 17. Exact Next Action for New AI Engine

START HERE:

1. Confirm repository HEAD equals HANDOFF_SHA.
2. Confirm PR #924 head equals HANDOFF_SHA.
3. Fetch Maven GitHub job logs.
4. Extract FIRST ORIGINAL failure.
5. Extract deepest root cause.
6. Compare local and CI failure signatures.
7. Determine actual causality.
8. Implement the minimum proven fix.
9. Run targeted tests.
10. Run full Maven suite.
11. Require zero failures and zero errors.
12. Commit only required files.
13. Push closure branch.
14. Obtain new SHA.
15. Wait for fresh CI.
16. Require exact 6/6 required checks.
17. Mark PR ready.
18. STOP BEFORE MERGE.

---

## 18. COMMIT THE HANDOFF DOCUMENT

```bash
git add SCP_AI_ENGINE_HANDOFF.md
git diff --cached --check
git diff --cached
```

Then: `git commit -m "docs(scp): checkpoint closure for AI engine handoff"`

---

## 19. PUSH HANDOFF CHECKPOINT

```bash
git push origin closure/scp-final-verification
```

Then: `git rev-parse HEAD`, `git ls-remote origin refs/heads/closure/scp-final-verification`

The resulting SHA becomes: FINAL_HANDOFF_SHA

---

## 19. VERY IMPORTANT — OLD CI IS NOW STALE

Because adding the Handoff file creates a new commit:

FINAL_HANDOFF_SHA != a52f8c6f...

Therefore: Do not consider previous CI results as certification for the new SHA.

However: Do not start Maven fix repair after this commit.

Leave that for the new engine.

---

## 20. UPDATE HANDOFF SHA IF NECESSARY

Because the Handoff file itself contains a previous SHA, do not enter a loop of commits to update SHA within the file.

Use within the file: HANDOFF_BASE_SHA = <SHA before handoff-doc commit>

And in the final delivery message print: FINAL_HANDOFF_SHA = <commit containing the handoff file>

Do not make another commit just to modify SHA inside the document.

---

## 21. FINAL VERIFY

Execute: `git status --short`, `git branch --show-current`, `git rev-parse HEAD`

Required: WORKTREE_CLEAN = YES, BRANCH = closure/scp-final-verification

Then confirm remote matches local.

---

## 22. FINAL OUTPUT — STOP HERE

Print:

```
SNAD — AI ENGINE HANDOFF COMPLETE

REPOSITORY = snadaiapp-png/SNAD

BRANCH = closure/scp-final-verification

PRE_HANDOFF_SHA =

FINAL_HANDOFF_SHA =

REMOTE_SHA =

PR_NUMBER = 928

PR_HEAD_SHA =

WORKTREE_CLEAN = YES

AUDIT_FIX_PRESERVED = YES

HANDOFF_DOCUMENT = SCP_AI_ENGINE_HANDOFF.md

KNOWN_BLOCKER = Maven Test Suite

MAVEN_FIX_COMPLETED = NO

READY_TO_MERGE = NO

MERGE_PERFORMED = NO

NEXT_ENGINE_START_POINT = SCP_AI_ENGINE_HANDOFF.md
```

---

## CRITICAL STOP

After raising the Handoff:

- Do NOT execute any new Maven fix.
- Do NOT merge.
- Do not change PR #924 to Ready.

The current task is only: SAVE → COMMIT → PUSH → DOCUMENT → HANDOFF → STOP

until the execution engine is replaced and the new engine takes over the task.