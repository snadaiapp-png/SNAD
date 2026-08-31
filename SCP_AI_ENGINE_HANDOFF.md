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

Record GitHub CI evidence for the exact HANDOFF_SHA.

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

Do NOT copy these values onto a newer SHA.

For HANDOFF_SHA write: CHECK_NAME, RUN_ID, JOB_ID, STATUS, CONCLUSION

- CHECK_NAME: Maven Test Suite
- RUN_ID: 33332810258
- JOB_ID: 99314330558
- STATUS: FAILURE
- CONCLUSION: Maven Test Suite failure on PostgreSQL Direct; root cause DUPLICATE_SPRING_MVC_MAPPING proven and fixed; 798 local errors NON_CAUSAL_TO_CI

---

## 7. Current Blocking Issue

The remaining authoritative blocker at the last verified stage was: Maven Test Suite

Previous failing GitHub job:

- RUN_ID = 33332810258
- JOB_ID = 99314330558
- SHA = a52f8c6f2e23ab89514c5991abd5e65f3312b0a3

Failed step: Run tests (PostgreSQL Direct)

The next engine MUST extract:

- first original failing test
- first original ApplicationContext failure
- deepest Caused by
- SQLSTATE if present
- failure signature
- unique root-cause count

ApplicationContext failure threshold exceeded is NOT the root cause.

---

## 8. Local Maven Evidence

One local execution reported:

TOTAL = 1943
FAILURES = 0
ERRORS = 798
SKIPPED = 18

Therefore: PASSED = 1127

MAVEN_LOCAL_RESULT = FAIL

The 798 errors MUST NOT automatically be classified as NON_CAUSAL_TO_CI.

The next engine must compare: LOCAL_FAILURE_SIGNATURE vs CI_FAILURE_SIGNATURE before classification.

---

## 9. Local PostgreSQL Warning

A previous local PostgreSQL environment had credential mismatches.

This was NOT proven as the GitHub CI root cause.

Do not modify product code merely to accommodate a contaminated local database.

Always distinguish: LOCAL_ENVIRONMENT_MISMATCH from CI_ROOT_CAUSE

---

## 10. Current Maven Mission

The next engine must execute: GitHub Maven CI log → first original failure → deepest cause → compare local/CI signatures → determine repository vs environment causality → minimum fix → regression test → targeted green → full Maven green → commit → push closure → fresh CI → require exact 6/6 → STOP BEFORE MERGE

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