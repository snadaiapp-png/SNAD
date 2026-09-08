# Workflow Y2 Release Evidence — Final G4 Closure

<!-- STATUS_AUTHORITY: FINAL_G4_CLOSURE -->

Repository: `snadaiapp-png/SNAD`  
Implementation PR: **#997**  
Scope: **Workflow Orchestration Y2 V1 — Tasks 1–22 / Gates G0–G4**  
Governing database path: **POSTGRESQL_DIRECT_HOST_NATIVE**  
Docker/Testcontainers as G4 authority: **FORBIDDEN**  
Production deployment/cutover: **SEPARATE RELEASE PHASE**

## 1. Final closure identity

- `G3_BASELINE_MAIN_SHA` = `05b64dfc2f42f25548112dd3b1ab50026641c075`
- `VERIFIED_IMPLEMENTATION_HEAD` = `ae7e0da4c3660fc045abe553d255aaa37752ed12`
- `FINAL_EVIDENCE_HEAD` = `928ed95c4eeb55baa3ac20e6dbc838e393b74073`
- `FINAL_G4_PR` = `#997`
- `FINAL_G4_MERGED_MAIN_SHA` = `cf27d0e260691ce16b4ef884e408f8943977870c`
- `MERGE_METHOD` = `SQUASH`
- `MERGE_TIME_UTC` = `2026-09-08T15:48:06Z`
- `INDEPENDENT_REVIEWER` = `abdulrhmansenan1985-creator`
- `INDEPENDENT_REVIEW` = **APPROVED**
- `UNRESOLVED_REVIEW_THREADS` = `0`
- `G4_FINAL_CLOSURE` = **COMPLETE**

The final merge was fail-closed against exact head `928ed95c4eeb55baa3ac20e6dbc838e393b74073`. GitHub returned squash merge SHA `cf27d0e260691ce16b4ef884e408f8943977870c`, and `main` was verified to point to that SHA after merge.

## 2. Task 20 — Security / authority closure

Task 20 was re-certified on the final evidence head rather than inferred from historical PRs.

Final exact-head release gate:

- Workflow: `Workflow Y2 G4 Release Gate`
- Run ID: **34242744543**
- Head SHA: `928ed95c4eeb55baa3ac20e6dbc838e393b74073`
- Result: **SUCCESS**

Covered release behavior includes:

- cross-tenant fail-closed enforcement;
- stale-version / optimistic-lock conflict handling;
- idempotency and concurrent action races;
- break-glass constraints and audit semantics;
- RLS-protected employee/actionability reads;
- server-authoritative command routing.

Hardening discovered during closure:

1. A real HTTP/RLS actionability regression was traced to employee reads occurring without the transaction boundary required for PostgreSQL `SET LOCAL` tenant context.
2. An initial annotation-based repair was rejected because `WorkflowActionabilityService` is `final` and Spring CGLIB could not proxy it.
3. The final repair uses a read-only `TransactionTemplate` inside the final service, preserving the class contract while providing the RLS transaction boundary.
4. `WorkflowActionabilityRlsTransactionBoundaryTest` locks the regression down.
5. Hard-coded Java E2E browser credentials were removed; the E2E/bootstrap path uses runtime/ephemeral credentials.

## 3. Task 21 — Strangler / compatibility closure

The final G4 matrix proves the intended coexistence contract:

- running LEGACY instances remain LEGACY;
- Y2 starts resolve to a concrete published Y2 version and stay pinned;
- no instance executes on both engines;
- no automatic in-flight LEGACY → Y2 migration occurs;
- rollback changes future-start resolution only and never rewrites running-instance engine generation.

The operational runbook is `docs/runbooks/workflow-y2-cutover.md`.

## 4. Task 22 — Final exact-head certification

All relevant final-head workflows were terminal and green on `928ed95c4eeb55baa3ac20e6dbc838e393b74073`:

| Workflow | Run ID | Result |
|---|---:|---|
| Workflow Y2 G4 Release Gate | 34242744543 | SUCCESS |
| CI | 34242744861 | SUCCESS |
| Security Baseline | 34242744679 | SUCCESS |
| Web CI | 34242744444 | SUCCESS |
| Playwright E2E & Visual Regression | 34242744667 | SUCCESS |
| Performance Baseline | 34242744424 | SUCCESS |
| Pre-Merge Operational Smoke | 34242744767 | SUCCESS |
| CRM Deployment Readiness | 34242744872 | SUCCESS |
| SNAD Identity Governance | 34242744869 | SUCCESS |
| CRM G1 Schema Isolation | 34242744457 | SUCCESS |
| Backup Restore Validation | 34242744723 | SUCCESS |
| Stage 07 Artifact Provenance | 34242744521 | SUCCESS |
| Compile Diagnostics | 34242744939 | SUCCESS |
| Service Decomposition Validation | 34242744709 | SUCCESS |
| Master Backlog Validation | 34242744453 | SUCCESS |
| CRM Web Lint Diagnostics | 34242744630 | SUCCESS |

The protected CI includes the required Maven, CRM integration, and PostgreSQL acceptance gates. The dedicated Workflow G4 authority uses host-native PostgreSQL Direct.

General workflows that use service containers are supplemental only and are not substituted for the PostgreSQL Direct G4 authority.

## 5. Review and merge proof

Repository ruleset `min` requires one approving review, dismisses stale reviews after pushes, and has no bypass actors.

Final sequence:

1. Exact-head G4/CI/security/browser gates completed successfully.
2. PR #997 was marked Ready for review.
3. `abdulrhmansenan1985-creator` submitted **APPROVED** at `2026-09-08T15:46:19Z`.
4. Review threads = `0`.
5. PR remained mergeable with head `928ed95c4eeb55baa3ac20e6dbc838e393b74073`.
6. Squash merge executed with `expected_head_sha` locked to that exact head.
7. GitHub returned `cf27d0e260691ce16b4ef884e408f8943977870c`.
8. Post-merge verification proved PR #997 `merged=true` and `main` at the same returned SHA.

## 6. Superseded recovery evidence

- PR #955 (`recovery/task22-semantic-hardening`) is **SUPERSEDED / CLOSED / NOT MERGED**.
- PR #923 is historical implementation evidence only; it is not current release authority.
- PR #997 plus this report are the final Y2 V1 G4 implementation authority.

Do not reopen or merge recovery-only branches to reconstruct release state.

## 7. Explicit deferred scope — not implementation defects

The approved Y2 V1 plan intentionally excludes:

- `QUORUM / N_OF_M` approval policy;
- full BPMN gateway/event semantics beyond the controlled V1 set;
- arbitrary user-authored executable scripting;
- Workflow-owned SMS/WhatsApp provider implementation;
- automatic migration of already-running LEGACY instances into Y2;
- replacing source-module domain records with Workflow-owned records;
- replacing PostgreSQL with event sourcing.

These are future-scope items, not incomplete Tasks 1–22.

## 8. Production handoff

`G4_FINAL_CLOSURE=COMPLETE` proves implementation/release-candidate quality. It does **not** by itself assert that the merged build has been deployed or that a Y2 family has been cut over in production.

Production proceeds through the repository's canonical `SANAD Production Release` workflow (`.github/workflows/production-release.yml`) using the exact current `main` SHA, followed by the production preflight/canary/cutover procedure in `docs/runbooks/workflow-y2-cutover.md`.

The exact production release candidate must be resolved **after** this post-G4 cleanup PR is protected-merged; the dispatch workflow itself rejects any SHA that is not the current `main` head.

## 9. Final implementation verdict

```text
WORKFLOW_Y2_V1_TASKS_1_22 = COMPLETE
GATES_G0_G4               = COMPLETE
FINAL_EXACT_HEAD_TESTED   = 928ed95c4eeb55baa3ac20e6dbc838e393b74073
INDEPENDENT_REVIEW        = APPROVED
PR_997                     = MERGED
G4_MERGED_MAIN_SHA         = cf27d0e260691ce16b4ef884e408f8943977870c
G4_FINAL_CLOSURE           = COMPLETE
PRODUCTION_DEPLOYMENT      = SEPARATE_PHASE
PRODUCTION_Y2_CUTOVER      = SEPARATE_PHASE
```
