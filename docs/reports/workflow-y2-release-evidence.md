# Workflow Y2 Release Evidence — TRUE END-TO-END EXECUTION (Task 22)

<!-- STATUS_AUTHORITY: CURRENT — supersedes all prior workflow-y2-release-evidence.md revisions -->

Repository: `snadaiapp-png/SNAD`
Branch: `design/workflow-orchestration-spec`
PR: **#923** (base `main`)

- `INITIAL_REMOTE_HEAD` = `e1bfdc7bdf86f396d3b5404abbcf64cd332c30f4`
- `VERIFIED_IMPLEMENTATION_HEAD` = `ece9beedff85834c7807c3186cf53ddf5d97b044`
- `FINAL_EVIDENCE_HEAD` = the SHA of the commit that introduced this file revision (docs-only)
- `CURRENT_MAIN_SHA_AT_EXECUTION` = `7f30c4ff1f8c8f856bb17126fb6364c9eae6b291` (fully merged INTO this branch — conflict analysis §34 executed; PR mergeable=TRUE)
- Merge strategy: protected squash merge, authorized by repository ruleset (merge methods: merge/squash/rebase)

## 1. Workflow Flyway migrations (all seven, verified applied + validated)

V20260902_1 (identity bridge), V20260902_2 (definition graph), V20260902_3 (work items/approvals),
V20260902_4 (runtime context), V20260902_5 (SLA/incidents/attempts), V20260902_6 (events/notifications),
V20260902_7 (break-glass audit OVERRIDE).
Registry union after main integration: …20260830.2 → 20260901.1 (SCP canonicalization) → 20260902.1..7 (terminal).
`FLYWAY_COLLISION = NONE` (regression suites green; check "Verify 8 tables, 26 indexes, and tenant isolation" PASS).

## 2. Semantic browser matrix P01..P13 (Playwright, real stack: Spring Boot + PostgreSQL Direct + Next.js)

| Scenario | Result |
|---|---|
| P01 real multi-actor auth (10 actors) | PASS |
| P02 design → validate → simulate → PUBLISHED (Y2) | PASS |
| P03 published immutability fail-closed (409) + family chaining | PASS |
| P04 exact version pinning (5 unconditional pins) + new-version start | PASS |
| P05 real DIRECT HUMAN_TASK generation/assignment/completion/advance | PASS |
| P06 atomic two-actor WORK_POOL claim race (200+409, single owner) | PASS |
| P07 ANY_ONE approval closes step, siblings cancelled, no second approval | PASS |
| P08 ALL unanimity + reasoned rejection (400 blank / REJECT path) | PASS |
| P09 disabled-user B1 semantics (401 login, 403 commands, preserved work, explicit reassign) | PASS |
| P10 real incident lifecycle (deterministic SYSTEM_ACTION failure → OPEN → ack → RESOLVED) | PASS |
| P11 LEGACY/Y2 strangler cutover (unconditional pins both sides) | PASS |
| P12 true cross-tenant denial (real tenant B actor: 404/404/409/409; owner keeps access) | PASS |
| P13 real app Arabic RTL, 8 IA tabs, landmarks, error state, keyboard, axe-critical=0 | PASS |

`PLAYWRIGHT_RUN_ID` = 33826801493 · `PLAYWRIGHT_HEAD_SHA` = ece9beed… · TOTAL=13 · PASSED=13 · FAILED=0 · SKIPPED=0 · retries=0
Final-gate history on this branch: a5a3d855(FAIL→repaired), 7dfbd657(FAIL→repaired), d8d55f3d(FAIL→repaired), 03becd9d/a077a6fd(1d98e6dd SUCCESS), 346dc5f1(SUCCESS), ece9beed(SUCCESS).

## 3. Same-SHA certifications (VERIFIED_IMPLEMENTATION_HEAD)

- Web: `npm ci` + `npm test` = **750/750 PASS (61 files)** · `npm run lint` = **0 errors** · `npm run build` = **SUCCESS**
- Integrity validator (direct): **42/43 rules PASS**; Rule 5 dashboard-structure fails IDENTICALLY on main (source identical both branches) → classified `PRE_EXISTING_NON_WORKFLOW_FAILURE` (non-blocking per §26)
- Workflow Maven: included in full suite (workflow package green)
- Full Maven (PostgreSQL Direct service container): **Tests run: 2536, Failures: 0, Errors: 0, Skipped: 6** → `Maven Test Suite` required check PASS
- PostgreSQL Direct: PG16 service container; all workflow migrations applied exactly once, checksums valid
- Security: Current Tree Secret Scan PASS · Supplemental Secret Policy PASS (SANAD-FP-Y2-001 documented allowlist) · Workflow Security Policy PASS · Backend Container Hardening PASS · production-only npm audit locally = **0 vulnerabilities** (CI audit job hit npm-registry 503 outage ×3 — `CI_INFRA_DEFECT`, not a code finding; the 5 reported highs are dev-only)
- CRM / shared platform regression: `CRM Integration Tests` (PostgreSQL Direct) PASS; `CRM Modular Architecture Validation` PASS; `CRM API Contract Validation` PASS
- Tenant/security acceptance: `Verify 8 tables, 26 indexes, and tenant isolation` PASS

## 4. Required protected checks on VERIFIED_IMPLEMENTATION_HEAD (branch ruleset `min`)

Build Next.js Web · provenance · CRM Integration Tests · Maven Test Suite · CRM Deployment Readiness · Verify 8 tables/26 indexes/tenant isolation — **ALL SUCCESS**. Workflow Y2 Playwright Release Gate SUCCESS.

## 5. Product defects found and fixed during this execution (each with regression coverage)

1. `addStep` accepted mutations on PUBLISHED versions → service guard (409) + `WorkflowDefinitionImmutabilityTest`
2. Y2 graph runtime unreachable over REST → wired start/completion/approvals/system-actions through `WorkflowGraphExecutionService` (work items, approval requests, incidents now real end-to-end)
3. DIRECT work items could not be completed (claimed_by predicate) → repository admits the DIRECT assignee
4. Controller-thrown AccessDeniedException surfaced as 500 → mapped to 403 (fail-closed denial)
5. Approval policy aggregation advances Y2 instances only; LEGACY approvals keep legacy semantics
6. SYSTEM_ACTION registry (fail-closed) + E2E deterministic failing adapter under `workflow-e2e` profile
7. Multi-actor deterministic fixture seed (10 actors / 2 tenants) — `WorkflowE2eBootstrapConfig`

## 6. Known deferred scope / notes

- `Playwright E2E & Visual Regression` (non-required) initially ran the Y2 spec out of scope — fixed via default-config `testIgnore`; `PostgreSQL Acceptance Tests` (non-required) hit a transient surefire dependency-collection failure (CI infra) — classified, not blocking
- Audit READ API absent (workflow_transition_audit write-only) — P09/P10 audit assertions are covered by backend tests; endpoint deferred
- Branch protection requires ONE independent approving review (ruleset `min`, no bypass; self-approval prohibited) — merge proceeds the moment that approval is recorded

## 7. Verdicts

- `IMPLEMENTATION_VERDICT` = **PASS**
- `RELEASE_VERDICT` = **PASS** (all certification gates green at VERIFIED_IMPLEMENTATION_HEAD)
- `MERGE_READINESS_VERDICT` = **PASS** (mergeable=TRUE; protected checks green) — merge itself awaits the ruleset-mandated independent approval
- `PRODUCTION_VERDICT` = **PENDING** (post-merge: exact-SHA deploy → Flyway verify → smoke → observability watch)
