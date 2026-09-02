# Workflow Y2 — Release Evidence

> **Generated:** 2026-09-03 (Task 22 execution)
> **Verdict authority:** CI run on FINAL_HEAD (see CI RUN section below)

---

## 1. Repository identity

| Field | Value |
|-------|-------|
| Repository | snadaiapp-png/SNAD |
| Branch | design/workflow-orchestration-spec |
| Start HEAD | 669954afdc9272daff52ddf159a4bc4e73b29b86 |
| Final HEAD | 669954afdc9272daff52ddf159a4bc4e73b29b86 |
| Main HEAD | 7f30c4ff1f8c8f856bb17126fb6364c9eae6b291 |
| Merge base | a8a7ce4da18f7f1b03e6a54933ff886a3f6484e5 |
| PR | #923 (OPEN, DRAFT, NOT MERGED) |

## 2. Tasks completed

Tasks 1..21 implemented and CI-verified prior to Task 22.
Task 22 = this document (final release verification).

## 3. Migration inventory

### Branch migrations (Workflow Y2)

| Version | File |
|---------|------|
| V20260830_1 | workflow_y2_identity_and_capabilities |
| V20260830_2 | workflow_y2_definition_graph |
| V20260830_3 | workflow_y2_work_items_approvals |
| V20260830_4 | workflow_y2_runtime_context |
| V20260830_5 | workflow_y2_sla_incidents_execution |
| V20260830_6 | workflow_y2_events_notifications |
| V20260830_7 | workflow_break_glass_override |

### Main migrations (same version range)

| Version | File |
|---------|------|
| V20260830_1 | scp_lifecycle_and_provisioning |
| V20260830_2 | scp_usage_metering_and_rbac |

## 4. Migration collision analysis

**COLLISION DETECTED = YES**

Flyway version prefix `20260830.1` and `20260830.2` exist on BOTH the branch
(workflow Y2) and main (SCP control-plane) with completely different content.
Upon merge, Flyway will detect duplicate version numbers and refuse to start.

**Resolution path (out of scope for Task 22):** The workflow Y2 migrations must
be renumbered to avoid the SCP version range (e.g., `V20260902_x`). This is a
mechanical rename plus guard-test update. The schema content itself is correct
and has been verified by CI.

## 5. Flyway history

Flyway history verified through the CI PostgreSQL Acceptance Tests suite.
All 7 workflow Y2 migrations apply successfully in the CI environment.

**LOCAL STATUS:** PostgreSQL Direct not available on this Windows workstation.
Full suite requires the CI PostgreSQL 16 service container.

## 6. PostgreSQL Direct result

| Gate | Status | Evidence |
|------|--------|----------|
| PostgreSQL Acceptance Tests | PASS | CI run 33667103894 (latest green) |
| RLS enforcement | PASS | CI + Task 20 cross-tenant matrix |
| FK integrity | PASS | CI + Task 20 cross-tenant FK failures |
| JSONB contracts | PASS | CI + Task 19/20 JSONB fixtures |
| Optimistic locking | PASS | CI + Task 7/14 concurrency tests |

## 7. Test results (from CI runs)

### Full Maven suite — local execution

**STATUS:** FAILED (895 errors, 1945 tests run)
**ROOT CAUSE:** No PostgreSQL instance available on local Windows workstation.
All failures are `ApplicationContext` load failures requiring a database
connection. The CI environment provides PostgreSQL 16 as a service container.

### Full Maven suite — CI environment

**STATUS:** PASS (latest green run: 33667103894 on 669954af)

| Job | Status |
|-----|--------|
| Maven Test Suite | SUCCESS |
| PostgreSQL Acceptance Tests | SUCCESS |
| CRM Integration Tests | SUCCESS |

### Workflow test counts (from CI)

| Metric | Value |
|--------|-------|
| Workflow test files | 25+ |
| Workflow test cases | 150+ |
| Failures | 0 |
| Errors | 0 |

## 8. Security gates

| Gate | Status | Source |
|------|--------|--------|
| Cross-tenant fail-closed | PASS | WorkflowY2TenantIsolationTest (9 cases) |
| Break-glass audited | PASS | WorkflowBreakGlassTest (6 cases) |
| Security negatives | PASS | WorkflowSecurityNegativeTest |
| Idempotency | PASS | WorkflowIdempotencyTest |
| Concurrency | PASS | WorkflowWorkItemConcurrencyTest, WorkflowParallelExecutionTest |
| Cutover | PASS | WorkflowY2CutoverTest (18 cases) |

## 9. Web verification

| Gate | Status | Count |
|------|--------|-------|
| Vitest | PASS | 717/717 (run at 4808cad9) |
| Lint | PASS | 0 errors, 50 warnings |
| Next.js build | PASS | All routes compile |
| TypeScript | PASS | No type errors |

## 10. Playwright E2E

**STATUS:** BLOCKED — Playwright requires a running backend + PostgreSQL Direct,
neither of which is available on this local workstation. The CI environment has
a Playwright job but it targets the deployed preview, not a local backend.

## 11. Known risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Flyway version collision with main | HIGH | Renumber workflow migrations at merge time |
| Local PostgreSQL unavailable | MEDIUM | CI provides authoritative verification |
| Playwright E2E not run locally | MEDIUM | CI Playwright job covers deployed preview |

## 12. Deferred scope (by design, not defects)

- QUORUM / N_OF_M approvals
- Full BPMN gateway/event model
- Arbitrary executable scripting
- Native SMS provider
- Native WhatsApp provider
- Automatic migration of running LEGACY instances
- Cross-tenant workflow execution
- Exactly-once distributed delivery guarantee

## 13. Verdicts

```
IMPLEMENTATION_VERDICT = PASS
MERGE_READINESS_VERDICT = BLOCKED
RELEASE_VERDICT = BLOCKED

BLOCKER_ID = FLYWAY_VERSION_COLLISION
ROOT_CAUSE = Branch V20260830_1/2 collide with main V20260830_1/2 (SCP)
EVIDENCE = git ls-tree comparison shows duplicate version prefixes
SAFE_FIX = Renumber workflow migrations to V20260902_x at merge time
WHAT_REMAINS = Mechanical renumbering + guard-test version updates
```

## 14. CI runs (fresh evidence)

| Run | Commit | Status |
|-----|--------|--------|
| 33667103894 | 669954af (cutover fixture fix) | SUCCESS |
| 33655602753 | 19a532e3 (break-glass fixture fix) | SUCCESS |
| 33661925313 | b149edc3 (operational read model RED) | FAILURE (expected RED) |
| 33662650979 | 72df592d (cutover implementation) | FAILURE (fixture issues) |
| 33664135930 | e4331e13 (cutover fixture fix) | FAILURE (remaining issues) |
