# G8 FORENSIC RECONCILIATION REPORT (COMMAND 03-R)

> **Mode:** READ-ONLY forensic reconciliation · **No repository mutation, no push** (report artifact only)
> **Date:** 2026-08-20 · **Refs verified at execution time:** origin/main=`6bb46e80` · origin/g8/current-development-20260820 = origin/g8/track-cd-recovery-20260820 = `e4e389ee`

---

## 1. GIT TOPOLOGY (verified, not assumed)

| Item | Value |
|------|-------|
| MERGE_BASE (main ∩ G8) | `cf01ce8b` |
| MAIN_AHEAD | 4 (the separation commits) |
| G8_AHEAD | 2 (`d04fabad` fixes + `e4e389ee` evidence) |
| PRESERVATION_BRANCHES_IDENTICAL | **YES** (`git diff --exit-code` clean) |
| Left-right | `<` 023508c9, 82ecb795, b49fa0cd, 6bb46e80 · `>` d04fabad, e4e389ee |
| Other remotes observed | `remediation/final-corrective-closure-no-g8`, `remediation/final-full-corrective-closure`, `security/remove-exposed-prod-credential` (parity/workflow awareness — not analyzed in depth here) |

## 2. TREE DELTAS (three comparison types — §3)

| Comparison | Files | Breakdown |
|------------|------:|-----------|
| **A. Tree-to-Tree** `main..G8` | **70** | 51 additions, 19 modified, 0 deletions (G8 side) — §31: CURRENT_TREE_DIFF_FILES=70, ADDITIONS=51, MODIFICATIONS=19, DELETIONS=0 |
| **B. Merge-base→G8** `main...G8` | **3** | CallerDatasetService.java (cursor/entityId fixes), CallerDatasetPostgresTest.java, G8_EXECUTION_03_CALL_EVENTS_OFFLINE_REPORT.md |
| **C. Historical** | | `92f5a389→G8`: **69** (all G8 work since G7 close) · `e257802b→G8`: **49** (Track C/D + fixes + evidence) · `cf01ce8b→G8`: **3** (last local fixes + evidence) |

## 3. SEPARATION COMMITS (§4) — read from diffs, not messages

| Commit | Files | Classification |
|--------|------:|----------------|
| `023508c9` | 48 (−4656) | **G8_REVERT** (mobile Track D + workflow counts + db.ts) |
| `82ecb795` | 31 (−3152) | **G8_REVERT** (Track A/B + baseline/evidence docs + matrix) |
| `b49fa0cd` | 1 (+3) | **SHARED_CORRECTIVE_CHANGE** — `.gitleaksignore` +3 (synthetic G7 documentation fixtures) |
| `6bb46e80` | finish (−7787 net) | **G8_REVERT** (completes the removal) |

**§5 — MAIN_CHANGES_TO_PRESERVE_IN_FUTURE_G8_INTEGRATION = [`b49fa0cd`]** (only non-G8 content; G7-fixtures allowlist, **NOT_RELEVANT** to G8 code — §23: NEEDED_ON_G8_BRANCH = NO).

## 4. GIT TOPOLOGY RISK GATE (§6) — P0

- Files deleted on main since merge-base: **50**
- Files changed on G8 since merge-base: **3**
- ⇒ For ~49 paths, main's deletion is the ONLY change since the merge base → in a three-way merge **the deletion wins** and a direct PR from the preservation branch would NOT restore G8.

```
DIRECT_G8_TO_MAIN_MERGE = UNSAFE (NO)
DIRECT_G8_TO_MAIN_PR    = UNSAFE (NO)
```

## 5. MIGRATION REALITY (§13–§18)

| Version | Current Main | G8 Branch | Description |
|---------|:-----------:|:---------:|-------------|
| 20260820.10 | **ABSENT** | **PRESENT** | seed CRM caller-ID capabilities |
| 20260820.11 | **ABSENT** | **PRESENT** | create crm_call_events + RLS enable + policy |
| 20260820.12 | **ABSENT** | **PRESENT** | force RLS on crm_call_events |
| 20260820.13 | **ABSENT** | **PRESENT** | seed CRM.CALL_EVENT read/write |

- MAIN_LATEST = **20260820.9** (97 migration files) · G8_LATEST = **20260820.13** (101 files)
- **PRODUCTION DB (§14): NOT_EXECUTABLE READ-ONLY from this session** (no credentialed access to the Supabase pooler; creds live only in CI secrets). **However, P0 evidence exists:** the Publish Render Backend Image workflow (autodeploy from main) returned **SUCCESS on `c92ebba4` (V20260820_10) and on `cf01ce8b` (V20260820_11/12/13)** — run IDs 32373596315 and 32398869782 — and **SUCCESS on `6bb46e80`** (G8-free main) — run 32400614742. Conclusion chain:
  - prod most likely has **V20260820_10..13 applied and LIVE objects** (access_capabilities rows, crm_call_events + RLS/FORCE, uk on users — note: `uk_users_tenant_id` came from V5, not G8);
  - the 6bb46e80 deploy **booted OK without the G8 migrations on classpath** ⇒ production Flyway is effectively tolerant (FLYWAY_VALIDATE_ON_MIGRATE not enforcing missing-migration failure — recommend operator confirm).
- `MIGRATION_COLLISION` (main added ≥.10 after split): **NO** (main has nothing ≥ 20260820.9) → `MIGRATION_RENUMBER_REQUIRED = CONDITIONAL` (only if prod history proves otherwise; **DO NOT renumber/delete per §16**).
- `MIGRATION_P0_RISK = YES — P0_MIGRATION_RECONCILIATION_RISK` (applied-in-prod vs removed-from-main). Future integration MUST ship a migration set that satisfies prod history (validate/checksum/ordering) — §12 of the directive's spirit.

## 6. VERIFICATION REALITY (§10–§12, §27)

| Gate | Status | Evidence |
|------|--------|----------|
| FINAL_G8_BRANCH_CI (e4e389ee) | **NOT_EXECUTED** — 0 GitHub runs on the branch (branch pushes trigger no Post-Merge) | `gh run list` sha filter = 0 |
| Backend compile | **PASS (52/52 unit batch)** on branch tip | 0 errors |
| Backend unit tests (A/B/C/D cores) | **PASS** | 52/52: CallEventServiceTest 10 · CallerPhoneVectorsParity 2 · TokenProvider 4 · PhoneNumberNormalizer 6 · CallerIdentificationService 18 · AddressCommunicationUseCases 4 · ModuleResetRegistry 8 |
| Mobile typecheck | **PASS** | `tsc --noEmit` clean |
| Mobile tests | **PASS** | 94/94 (incl. parity, HMAC, offline statuses, P95 ≤100ms, sync idempotency/rebuild/corruption) |
| Secret scan | **PASS** | 0 findings on G8 files (160 pre-existing local-path findings = CI-allowlisted set) |
| PostgreSQL Direct tests | **NOT_EXECUTED here** (no credentialed PG in this session); previously executed in CI on main SHAs — final-fix equivalents UNVERIFIED | — |
| API context tests (calls/dataset) | **NOT_EXECUTED here** (local H2 cannot parse G7-era migrations — documented limitation) | — |
| OpenAPI governance | Branch artifact = **152 paths/198 ops** (verified by direct count) · Contract-validation workflow does not run on branches → **NOT_EXECUTED** for e4e389ee | main committed = 147/193 |
| Migration governance tests (CrmPostgresMigrationTest etc.) | **NOT_EXECUTED here** (PG-gated) | — |

**§37 distinction (explicit):**
- CODE EXISTS → **YES** (all files at e4e389ee)
- CODE WAS TESTED PREVIOUSLY → **YES** (52/52 + 94/94 + 3 CI loops on main SHAs, before the last two fix commits)
- CURRENT BRANCH PASSES NOW → **PARTIAL** (all locally-runnable gates PASS; PG/API/contract/migration gates NOT_EXECUTED)
- CODE IS SAFE TO REINTEGRATE → **NOT YET** (pending the gates above + migration reconciliation + owner authorization)

## 7. REINTEGRATION PLANING (§8, §32–§33) — DESIGN ONLY

```
RECOMMENDED_REINTEGRATION_STRATEGY = STRATEGY A (primary):
  new integration branch from current/future main
  → replay the 10 G8 commits deliberately (36d0512c, c92ebba4, b3169320,
    c6546b6e, 0f66ba7a, 3b380ab0, fa894eb9, cf01ce8b, d04fabad, e4e389ee)
  → apply main's corrective change (b49fa0cd already present on main)
  → reconcile migrations against PROD history (P0 gate) — never renumber/delete
  → regenerate OpenAPI + TS from the integration branch runtime
  → reconcile governance (CrmPostgresMigrationTest latest, counts 152/198,
    catalog, board) on the new branch
  → full CI (Post-Merge-equivalent incl. PostgreSQL Direct + API tests)
  → only then: PR
```

- Rationale: separation was a **pure G8 revert** (no corrective overlap) ⇒ replay applies cleanly; tree-level restore (Strategy B) is the fallback if any replay conflicts; Strategy C (reverting the separation commits) is acceptable but reintroduces the same 7787 lines in one shot — less auditable.
- COMMITS_TO_REPLAY = the 10 above · MAIN_COMMITS_TO_PRESERVE = [b49fa0cd]
- GENERATED_FILES_TO_REBUILD = `docs/crm/contracts/openapi/crm-openapi.json`, `apps/web/lib/api/generated/crm-api-types.ts`
- MIGRATIONS_REQUIRING_SPECIAL_ACTION = **V20260820_10..13** (prod-history reconciliation first)
- FILES_REQUIRING_MANUAL_RECONCILE = migration-governance tests + workflow contract-validation counts (152/198)
- PRESERVATION_BRANCH_ROLE = preservation/development source · FINAL_PR_BRANCH = NOT_CREATED · **no PR from the preservation branch** (§33)

## 8. SECURITY / RBAC / CATALOG / DEPENDENCIES (§20–§22, §28–§30)

- `CALLER_DATASET_MASTER_KEY`: config key exists (`sanad.caller-dataset.master-key` env-driven) · **no committed secret · no default · fails closed** (TokenProvider test) — value never printed.
- RBAC: `CRM.CALLER_ID.READ/READ_RESTRICTED` + `CRM.CALL_EVENT.READ/WRITE` present in G8 migrations; **absent from main's migration set**; prod DB state unknown (likely applied per §5) — drift must be reconciled at integration.
- Error catalog: main = 0 caller codes · G8 = 3 (`CALLER_PHONE_INVALID`, `CALL_EVENT_NOT_FOUND`, `CALL_EVENT_INVALID_TRANSITION`).
- G7 shared audit: main's only post-split G7-touching change = `.gitleaksignore` (allowlist only — NOT_RELEVANT to G8 reapplication). No drift in storage/encryption/sync/device/PostgreSQL harness on main.
- Dependency matrix: every G8 component depends on main ONLY via stable pre-split infrastructure (canonical comm methods, RBAC framework, RLS harness, G7 SQLite/encryption, OpenAPI pipeline, secret scanner) — none changed on main after the split (verified above) ⇒ **reconciliation needed only at generated/governance level**.

---

**Verdict summary:** G8 preserved = YES (identical branches, e4e389ee) · Track A/B/C/D = IMPLEMENTED / **PENDING FINAL VERIFICATION** (PG/API/contract/migration gates + prod DB read) · direct merge = UNSAFE · P0 production migration drift = LIKELY (operator must confirm prod flyway history) · safe path = replay-on-fresh-integration-branch after gates. **No mutation performed; no PR; waiting on owner decision.**
