# Workflow Y2 Release Evidence — Wave 4 / Gate G4 (Task 22)

<!-- STATUS_AUTHORITY: CURRENT — supersedes the historical PR #923 evidence previously stored at this path -->

Repository: `snadaiapp-png/SNAD`  
PR: **#997** → `main`  
Branch: `feat/workflow-y2-wave4-g4`  
Scope: **Wave 4 / Tasks 20–22 only**  
Production actions: **OUT OF SCOPE / NOT EXECUTED**

- `BASELINE_MAIN_SHA` = `05b64dfc2f42f25548112dd3b1ab50026641c075`
- `VERIFIED_IMPLEMENTATION_HEAD` = `ae7e0da4c3660fc045abe553d255aaa37752ed12`
- `FINAL_EVIDENCE_HEAD` = the commit containing this revision; GitHub PR head + exact-head check suite are the authority for its SHA
- `GOVERNING_DATABASE_PATH` = **POSTGRESQL_DIRECT_HOST_NATIVE**
- `DOCKER_TESTCONTAINERS_G4_AUTHORITY` = **FORBIDDEN**
- Merge strategy after all closure gates: **squash**, fail-closed with `expected_head_sha`

## 1. Task 20 — Security / authority closure

Task 20 release behavior was re-certified rather than inferred from historical evidence.

Authoritative G4 run on `VERIFIED_IMPLEMENTATION_HEAD`:

- Workflow: `Workflow Y2 G4 Release Gate`
- Run ID: **34239392263**
- Result: **SUCCESS**
- `G4 PostgreSQL Direct Baseline` — SUCCESS
- `G4 Flag OFF Regression` — SUCCESS
- `G4 Backend Test Matrix (P01-P12)` — SUCCESS
- `G4 Web Static Gate` — SUCCESS
- `G4 Playwright E2E (P01-P13)` — SUCCESS
- Browser artifact: `g4-workflow-y2-playwright-report`, artifact ID **10061530043**
- Artifact digest: `sha256:0c04bf53438fa220392d9e2b9c58d42a00631608a137daa030ea266f892c275d`

Security and operational gates on the same implementation SHA:

- `Security Baseline` run **34239392431** — **SUCCESS**
- `Pre-Merge Operational Smoke` run **34239392273** — **SUCCESS**
- `SNAD Identity Governance` run **34239392223** — **SUCCESS**
- `Backup Restore Validation` run **34239392212** — **SUCCESS**

Hardening found during this closure execution:

1. A real HTTP/RLS actionability regression was traced to reads occurring without a transaction boundary; fail-closed RLS made a valid employee appear absent.
2. An initial `@Transactional` repair was rejected after a startup gate exposed that `WorkflowActionabilityService` is `final` and therefore cannot be CGLIB-subclassed.
3. Final repair uses a read-only `TransactionTemplate` inside the final service, preserving the class contract while providing the PostgreSQL `SET LOCAL` transaction boundary required by tenant RLS.
4. `WorkflowActionabilityRlsTransactionBoundaryTest` provides regression coverage for the canonical user→employee lookup through the RLS-protected path.
5. Hard-coded Java E2E browser credentials were removed; test/bootstrap credentials are ephemeral/runtime supplied.

## 2. Task 21 — Strangler / runtime compatibility closure

The G4 backend/browser matrix proves the Wave 4 cutover contract, including P01–P13 and the LEGACY/Y2 coexistence path, on the same implementation SHA.

Additional exact-head web/browser evidence:

- `Web CI` run **34239392096** — **SUCCESS**
- General `Playwright E2E & Visual Regression` run **34239392236** — **SUCCESS**
- `CRM Deployment Readiness` run **34239392390** — **SUCCESS**
- `Stage 07 Artifact Provenance` run **34239392426** — **SUCCESS**
- `Compile Diagnostics` run **34239392074** — **SUCCESS**

`Performance Baseline` run **34239392169** is **SUCCESS** and is retained as supplemental evidence only. It is not the governing PostgreSQL certification path because that general workflow uses service containers; G4 authority remains the host-native PostgreSQL Direct gates above.

## 3. Task 22 — PostgreSQL Direct / protected CI certification

Full protected CI on `VERIFIED_IMPLEMENTATION_HEAD`:

- Workflow: `CI`
- Run ID: **34239392410**
- Overall result: **SUCCESS**
- `Maven Test Suite` — **SUCCESS**
- `CRM Integration Tests` — **SUCCESS**
- `PostgreSQL Acceptance Tests` — **SUCCESS**

All three jobs start **host-native PostgreSQL** and explicitly use **no containers** for this governing certification.

Artifacts:

| Artifact | ID | SHA-256 digest |
|---|---:|---|
| `surefire-reports` | `10062558382` | `71f496ff42f4ad3cd27ca5eef7daebf623e2d22938fde1d0f6c42af4a0827c9c` |
| `crm-surefire-reports` | `10061401714` | `681b1f4f0527b7d875ef1555a9e81f6c9b74c0bd73dfa27369a8fa4cd2eb345c` |
| `pg-acceptance-surefire-reports` | `10061396028` | `0b4b810bb3afd9ee68b81c769db07f952961b7f197252681946c2ec0f0a08d31` |
| `g4-workflow-y2-playwright-report` | `10061530043` | `0c04bf53438fa220392d9e2b9c58d42a00631608a137daa030ea266f892c275d` |

Other same-SHA green checks include CRM G1 Schema Isolation, Service Decomposition Validation, Master Backlog Validation and CRM Web Lint Diagnostics. ERP/HRM human preview jobs were skipped and are not G4 governing gates.

## 4. Baseline / drift / cleanup

Immediately before this Task 22 evidence update:

- PR #997 = **OPEN / DRAFT / MERGEABLE**
- PR base = `main@05b64dfc2f42f25548112dd3b1ab50026641c075`
- PR head = `ae7e0da4c3660fc045abe553d255aaa37752ed12`
- `main` had not drifted from the PR baseline
- no transient helper workflow or disposable repair file is part of the PR changed-file inventory
- no production deployment, production migration, or production operation was performed

## 5. Independent review authority

Repository ruleset `min` is active on the default branch and requires **1 approving review**. It has **no bypass actors** and `dismiss_stale_reviews_on_push=true`.

Therefore:

- `INDEPENDENT_REVIEW` = **PENDING LIVE GITHUB APPROVAL**
- This evidence revision **does not assert or simulate a human approval**.
- The PR must first be re-certified on the exact `FINAL_EVIDENCE_HEAD`.
- Only after that exact-head certification may the PR be marked Ready for review and an independent reviewer requested.
- Any later push invalidates a prior review and requires a fresh approval.

## 6. Exact-head post-evidence closure contract

This document update intentionally changes the PR SHA. Historical success on `VERIFIED_IMPLEMENTATION_HEAD` is implementation evidence, not permission to merge the new head.

Before merge, GitHub must prove on the exact `FINAL_EVIDENCE_HEAD`:

1. Workflow Y2 G4 Release Gate = SUCCESS.
2. Required protected CI, including Maven Test Suite / CRM Integration / PostgreSQL acceptance as triggered = SUCCESS.
3. Web / Security / Pre-Merge Smoke and other required contexts = SUCCESS where triggered/required.
4. PR remains mergeable against the same authoritative `main` baseline or any base movement is explicitly re-evaluated.
5. An independent human review is `APPROVED` after the final push.
6. No unresolved blocking review finding exists.
7. Merge uses `squash` with `expected_head_sha = FINAL_EVIDENCE_HEAD`.
8. Post-merge verification proves PR merged and `main` points to the returned squash merge SHA.

## 7. Verdict at evidence creation

- `TASK20_IMPLEMENTATION_VERDICT` = **PASS**
- `TASK21_IMPLEMENTATION_VERDICT` = **PASS**
- `TASK22_IMPLEMENTATION_HEAD_CERTIFICATION` = **PASS**
- `POST_EVIDENCE_EXACT_HEAD_RECERTIFICATION` = **PENDING**
- `INDEPENDENT_REVIEW_VERDICT` = **PENDING**
- `MERGE_READINESS_VERDICT` = **BLOCKED_FAIL_CLOSED**
- `G4_FINAL_CLOSURE` = **PENDING_EXACT_HEAD_RECERTIFICATION_AND_INDEPENDENT_REVIEW**
