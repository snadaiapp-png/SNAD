# G0–G11 Canonical Acceptance Matrix

**Mandate:** SANAD P0 FINAL REMEDIATION executive order, Article 2
**Compiled (UTC):** 2026-11-02T19:20Z · **Compiler:** Z.ai execution agent (owner-delegated)
**Verified `main` HEAD:** `6f963bfb971e4c4f41031eb1de69912d2363874b` (= PR #1469 merge commit, verified live via API + local clone)
**Evidence classes:** VERIFIED = directly observed via API/clone/live read-only connection · INFERRED = derived from verified artifacts · BLOCKED = requires authorization or access not currently available

> **Rules applied:** no `IN_PROGRESS`/`CANCELLED`/`SKIPPED` result is counted as success;
> success on an older SHA is never auto-transferred to a newer SHA; every row cites its
> evidence. Production is under `P0_SECURITY_HOLD=ACTIVE`, `PAYMENTS=OFF`,
> `PRODUCTION_RELEASE=NO-GO` — unchanged.

## 0. Current-state snapshot (Article 2, items 1–10)

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Latest SHA on `main` | **VERIFIED** `6f963bfb…3874b` | `git rev-parse HEAD` after `fetch --all` (local clone, 2026-11-02T17:59Z) + `GET /repos/snadaiapp-png/SNAD/branches/main` |
| 2 | Issue #1462 | **OPEN** (24 comments, updated 2026-11-02T17:05Z) | `GET /repos/…/issues/1462` |
| 3 | Post-Merge on `6f963bfb` | **ALL 5 JOBS SUCCESS** (run 41486042237, completed 18:13:08Z): A Frontend ✓ B Backend compile ✓ **C PostgreSQL Direct ✓** D HRM security/RLS ✓ E Security scans ✓ | `GET /repos/…/actions/runs/41486042237/jobs` |
| 4 | JOB C — PostgreSQL Direct | **SUCCESS (terminal)** | same run — job `postgres-direct-integration` |
| 5 | JOB D — HRM Security/RLS | **SUCCESS (terminal)** | same run — job `hrm-focused-security-rls` |
| 6 | Workflow "Production 3-User Final Gate" | **SKIPPED** on `6f963bfb` (run 41486042205) — gate conditions not met at push time; not counted as success | `GET /repos/…/actions/runs?head_sha=6f963bfb…` |
| 7 | Maven/Web/CRM checks on `6f963bfb` | Web CI ✓ · CRM Deployment Readiness ✓ · Publish Render Backend Image ✓ · Stage 07 Provenance ✓ · G2 Recovery Dispatch ✓ · Y2 Orchestrator ✓ · SCP One-Time Recovery ✓ (all `push` runs on the SHA) | same API query |
| 8 | Open security PRs | 30 open PRs; security-relevant: #1451 (Zid integration), #1409 (G1 pre-auth fail-closed, draft), #1050 (CRM RLS drift), #1056–#1058 (subscription WS1–WS3, drafts), #1028/#1029 (subscription closure) | `GET /repos/…/pulls?state=open` |
| 9 | Live backend image (Render `sanad-backend`, srv-d8ragqkm0tmc73bviqq0) | **LIVE**, image `ghcr.io/snadaiapp-png/snad-backend:48322d0bae97082d69ee143b7af26962d6e14ef8` (deployed 2026-10-28) — **behind `main`**; `FLYWAY_ENABLED=true` on the live service (runtime-run Flyway — the shared model) | Render API `GET /v1/services/{id}` + `/deploys` + `/env-vars` (read-only) |
| 10 | Actual `flyway_schema_history` (production Supabase `tkbrvupemreqabwzdpyq`, read-only connection **as the live runtime role**) | **head = `20261021.1`, 242 success rows** — production is 21 chain migrations behind `main` head `20261103.2`; history owned by `postgres`; runtime role holds NO membership, NO ownership, TRUNCATE denied, history-UPDATE denied, schema-CREATE denied; **1,273 out-of-band grant rows** to the runtime role (≈254 tables — chain-managed grants are only the protected set); **`sanad_migrator` does not exist in production** | Live read-only psql session (pooler, `sslmode=require`), 2026-11-02 ≈19:15Z |

## Production-impact finding (from item 9+10)

The live topology is *split-owner* (postgres owns, restricted `sanad` runs with DML),
but the migration path is still coupled to the runtime: `FLYWAY_ENABLED=true` +
`sanad` has no DDL ⇒ **any deployment carrying the 21 pending migrations will fail
at boot** (Flyway-as-runtime cannot `CREATE`). The chain cannot be advanced in
production without either the bootstrap superuser (shared model) or the dedicated
migrator identity. This is exactly the P0 gap Article 3 closes.

## 1. Gate matrix

| Gate | Requirement | Test / evidence instrument | Environment | SHA | Result |
|---|---|---|---|---|---|
| **G0** Release baseline | Repo, branch protection, image provenance intact | API + `branch-protection` (6 required checks, 1 approving review, `enforce_admins=true`); "Stage 07 Artifact Provenance" success; live image pinned by digest `sha256:f1df37f5…` | GitHub / Render | `6f963bfb` | **VERIFIED** (with the live-image-lags-main note above) |
| **G1** Flyway upgrade certification | Fresh + production-history-equivalent upgrade, checksums, failure injection, no repair/baseline | G5 engine (PR #1469, run on `6f963bfb` via merged workflow, scenarios A/B/C/D green); **this PR extends it**: upgrade from prod watermark `20261021.1` with admin ownership transfer → migrator-run pending set → head `20261103.2`, checksum join vs prod ledger 100%, tamper→validate fail, backup/restore 29/29 | Isolated PG 17.6 + Flyway 11.7.2 | `6f963bfb` + this PR | **PASS (isolated)** · production application **BLOCKED** (G8/G9 authorization + runbook §2 not yet executed — `sanad_migrator` absent in prod) |
| **G2** RLS & tenant isolation | ENABLE/FORCE, USING/WITH CHECK, A/B, missing GUC, pre-auth ACL, no owner bypass, no context leakage | Post-Merge JOB C/D suites (3,507 tests, success on `6f963bfb`); 57/57 six-table matrix (03); this PR's 35-probe matrix re-proves A/B, missing/empty GUC, FORCE-RLS catalog, pre-auth planes under the separated topology | Isolated PG (CI) | `6f963bfb` + this PR | **PASS (isolated)** · production re-certification pending G9/G10 |
| **G3** Users/Auth/IAM | creation, login, refresh, reset, revocation, tenant discovery, RBAC, mismatch, escalation denial | JOB C full suite (AUTH01–AUTH18 class, per docs/security/g1-final-closure/04); this PR: user INSERT as runtime under GUC, refresh/reset token lifecycle incl. trigger-maintained pre-auth index, `g1_resolve_login_tenants` without GUC | Isolated PG | `6f963bfb` + this PR | **PASS (isolated)** |
| **G4** Subscriptions & workers | creation, trial expiry, plan change, suspension, cancellation, provisioning, idempotency, workers, cross-tenant denial | JOB C suite (SUB01–SUB08, WRK01–WRK12 per doc 04); this PR: `tenant_subscriptions` INSERT as runtime (**F-01 closed**), `subscription_commands` append-only (INSERT ok / UPDATE+DELETE 42501) | Isolated PG | `6f963bfb` + this PR | **PASS (isolated)** (F-01 P2 finding closed by V20261103_2) |
| **G5** CI regression | Maven + Web + CRM + Post-Merge green on exact SHA | All `push` runs on `6f963bfb` (item 7 table) — terminal success | GitHub Actions | `6f963bfb` | **PASS** |
| **G6** Backup/Restore | real dump→restore→schema/RLS/ACL identity + A/B re-test | G5 Scenario D (PR #1469); this PR: `pg_dump -Fc` → restore as migrator role → 252 history rows, ownership=migrator, FORCE RLS 6/6, tenant A=1/B=0, no-GUC=0, denials hold (29/29) | Isolated PG 17.6 | `6f963bfb` + this PR | **PASS (isolated)** · production restore rehearsal **BLOCKED** (owner must deliver an authorized production backup — unchanged blocker, doc 06) |
| **G7** Independent security review | named independent reviewer, APPROVED/REJECTED/CHANGES_REQUIRED | Evidence package: this matrix + `docs/security/migrator-runtime-separation/*` + workflow artifacts | — | this PR | **BLOCKED** — no independent reviewer record exists; owner self-approval does not satisfy the gate (per docs 07 and the executive order Article 8) |
| **G8** Release authorization | explicit production approval package | — | — | — | **NOT REQUESTED** — `PRODUCTION_RELEASE=NO-GO` stands; this PR prepares the protected path only |
| **G9** Protected deployment | migrator-workflow + deploy + verification plan | Runbook §2–§3 + `database-migrate-production-migrator.yml` ready; production prerequisites absent (role, topology GUC, secrets) | Production | — | **BLOCKED** (authorization) |
| **G10** Production G1 certification | live catalog + history + lifecycle probes on the deployed version | Probe set defined (runbook §6, read-only, proven safe in this session) | Production | — | **BLOCKED** (depends on G9) |
| **G11** Final closure | Issue #1462 closed with all criteria | — | — | — | **OPEN** — correctly remains open |

## 2. Canonical status block

```text
G0_RELEASE_BASELINE=VERIFIED (live image lags main — deployment gate owned by G8/G9)
G1_FLYWAY_UPGRADE=PASS (isolated certification; production application BLOCKED on runbook §2 + G8)
G2_RLS_TENANT_ISOLATION=PASS (isolated)
G3_USERS_AUTH_IAM=PASS (isolated)
G4_SUBSCRIPTIONS_WORKERS=PASS (isolated; F-01 closed)
G5_CI_REGRESSION=PASS (on 6f963bfb; this PR's checks pending at review time)
G6_BACKUP_RESTORE=PASS (isolated; production rehearsal BLOCKED on authorized backup)
G7_INDEPENDENT_SECURITY_REVIEW=BLOCKED (no independent reviewer)
G8_PRODUCTION_RELEASE=NOT_REQUESTED (NO-GO)
G9_PROTECTED_DEPLOYMENT=BLOCKED (authorization + production prerequisites)
G10_G1_PRODUCTION_CERTIFICATION=BLOCKED (depends on G9)
G11_FINAL_ISSUE_CLOSURE=OPEN
```

## 3. Required owner actions (unblocking sequence)

1. Review & merge this PR (after its required checks pass).
2. Appoint the independent security reviewer (G7) — the evidence package is this
   matrix + the workflow artifacts + `docs/security/migrator-runtime-separation/`.
3. Deliver an authorized production backup for the G6 production rehearsal.
4. Execute runbook §2 (one-time separation setup on Supabase) and store
   `PROD_FLYWAY_MIGRATOR_*` secrets.
5. Request G8 authorization; then G9 (migrator workflow + deploy), G10 probes,
   G11 closure.
