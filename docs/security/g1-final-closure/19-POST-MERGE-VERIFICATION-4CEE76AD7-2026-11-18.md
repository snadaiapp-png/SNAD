# G1 — Post-merge verification closure (PR-1 #1538) & G1 production-readiness record

**Date:** 2026-11-18 · **Merged SHA:** `4cee76ad71667b00b60570db25358df60c99cbb1` (official `main`) · **PR-1:** [#1538](https://github.com/snadaiapp-png/SNAD/pull/1538) MERGED 2026-11-18T11:22:19Z by `abdulrhmansenan1985-creator` (merge commit = the imported commit; PR head `7693c4806d164768c297d6169a4bff333bddb626`).
**Provenance chain (verified from PR body + local bundle):** verified local candidate `498c5acfd87720b3446cecbdd3df0082365f1e1a` (tree `11c998cdebf0d24ad7ed97c53750c8e446680d23`) delivered as `SNAD-G1-C4-VERIFIED-FINAL.zip` (SHA-256 `1c146b77f5028c5d0e7c1f17ad34275cc12f7bbec80a68d9d199d22b8ca471be`), imported via authorized Git Data API (new commit identity, identical tree: re-verified locally against the preserved thin bundle — `git rev-parse 498c5acfd^{tree}` = `11c998cd…`), plus a two-file CI fixture alignment (`scripts/production/tests/verify-flyway.test.sh`, `tests/security/flyway-auditor/pg17-acceptance.sh`: synthetic ledger versions `20261111.1→20261115.1`, drift scenario `→20261116.1`; verifier logic and fail-closed predicates unchanged — verified by diff in this engagement).

## 1. PMV / Maven / Surefire — actual CI results on the merged SHA

Post-Merge Main Verification [run 44294090764](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090764) (push, `main`, head `4cee76ad7`), all jobs completed:

| Job | Scope | Result | Window |
|---|---|---|---|
| JOB A — Frontend verification | deps, lint, typecheck, unit, build, smoke | **SUCCESS** | 11:22:25→11:24:42Z |
| JOB B — Backend compile | dependency resolution, compile, test-compile | **SUCCESS** | 11:22:25→11:24:17Z |
| **JOB C — PostgreSQL Direct integration** | host-native PostgreSQL, **full Maven suite**, role-contract proof, backend smoke | **SUCCESS** | 11:22:22→12:31:00Z (~69 min) |
| JOB D — HRM focused security/RLS | RLS, tenant isolation, RBAC, audit/outbox, IAM boundary | **SUCCESS** | 11:22:23→11:44:40Z |
| JOB E — Security & governance scans | secret scan, workflow security, governance | **SUCCESS** | 11:22:23→11:24:17Z |
| JOB F — Final evidence aggregation | fail-closed collect-all validator | **SUCCESS** | 12:31:03→12:31:21Z |

Other completed runs on the SHA: G3 Authenticated Acceptance [44294090547](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090547) **SUCCESS** (12:43:32→12:47:13Z — the G3 re-run requested by the closure order); HRM G4 T1 RED [44294090839](https://github.com/snadaiapp-png/SNAD/actions/runs/44294090839) **SUCCESS**; NVD Bulk Feed [44297323573](https://github.com/snadaiapp-png/SNAD/actions/runs/44297323573) **SUCCESS** (scheduled).
**Production-gated checks correctly SKIPPED (not counted as passing):** "Execute final production Workflow closure" and "R0C-12 Canonical Gate G" (skipped on the SHA); `production-release` workflow has no run since 2026-11-12 — consistent with `PRODUCTION_RELEASE=NO-GO`.

**Surefire raw logs:** not downloadable in this engagement (Actions log/artifact API requires authentication); conclusions above are from the public check-run/job records. Local full-suite parity evidence on the content-identical tree `498c5acfd` (delivered with PR-1): **4744 tests, 0 failures, 0 errors, 37 skipped, `mvn rc=0`, BUILD SUCCESS**, three-way reconciled (console = XML elements = class-line sum; 641 class XMLs; no fork crash, no OOM; shared-DB autopsy clean).

## 2. Skipped-test classification (no skipped test counted as passing)

The 37 full-suite skips are **exactly the five profile-gated PostgreSQL-acceptance classes**, skipped by design in the default profile (the CI workflow itself mandates: "The default-profile Maven Test Suite MUST NOT execute these tests"):

| Class | Skipped methods |
|---|---|
| `subscription.rbac.RbacAccessCheckPostgresAcceptanceTest` | 15 |
| `module.api.ModuleRegistryUatPostgresAcceptanceTest` | 10 |
| `commerce.CommerceOrderPostgresConcurrencyTest` | 6 |
| `platformiam.PlatformSessionInvalidationPostgresAcceptanceTest` | 3 |
| `platformiam.PlatformUserPostgresAcceptanceTest` | 3 |

They were executed separately under the pg-acceptance profile: **37/37 PASS, 0 skipped** (dedicated `pg_acceptance` database, dedicated credentials). CI-side per-test skip breakdown for JOB C on `4cee76ad7` is not independently extractable here (log access limitation, disclosed above); the workflow contract plus the content-identical local run define the classification.

## 3. Prior CI failure disposition (G1 Flyway Auditor Acceptance)

- [Run 44280887930](https://github.com/snadaiapp-png/SNAD/actions/runs/44280887930) (PR head `2cf1728`, 2026-11-18T09:58Z): **FAILURE** — synthetic ledger fixtures pinned the repo head at `20261111.1` while the merged chain head is `20261115.1`.
- Fix: the merge commit's fixture alignment (head scenario `20261115.1`, drift scenario `20261116.1`; "actual release verifier and fail-closed predicates unchanged").
- **Local re-validation on the merged tree (this engagement):** `scripts/production/tests/verify-flyway.test.sh` → **RESULT: GREEN** (all 20+ stub scenarios, incl. legacy-numeric-rows regression, pending/drift guards, forbidden audit identities incl. `service_role`, pooler-suffix acceptance, ACL/password/URL injection refusals, failed-migration and missing-checksum denials, runtime-history denial, Y2 sentinel); `tests/security/flyway-auditor/pg17-acceptance.sh` on a fresh isolated PG17 cluster → **PG17_FLYWAY_AUDITOR_ACCEPTANCE=PASS** (read-only auditor PASS; auditor write denied; broad ACL denied; runtime history privilege denied; pending schema denied).
- Migrator/Runtime Separation [run 44283363382](https://github.com/snadaiapp-png/SNAD/actions/runs/44283363382) on the fixed PR head (`7693c480`, content-identical to the merge): **SUCCESS** (10:13Z, after the fix).
- Classification: **REMEDIATED (CI-observed failure → fix → post-fix separation workflow SUCCESS + local GREEN).** The flyway-auditor workflow's own post-fix CI run on the fixed head could not be enumerated through the public API in this engagement (rate-limit/access limitation, disclosed); the pre-fix failure, the fix diff, the post-fix separation SUCCESS and the local GREEN are the recorded evidence.

## 4. G1 production readiness — remaining conditions (Issues #1462 / #1528 / #1530)

| Condition | Status | Evidence / blocker |
|---|---|---|
| C4 technical adjudication (migrations, owners, 12 definers, 26 RLS-off) | **DELIVERED (this PR-2)** — doc 17; owner risk dispositions still required | Issue #1462 checkbox wording also subsumes owner disposition of the residual register |
| PMV on exact merged SHA | **PASS (CI)** | §1 above |
| G3 re-run on final commit | **PASS (CI 44294090547)** | §1 above |
| Isolated backup/restore proof | **PASS (local)** — G6 scenario D on this tree: logical backup→restore, Flyway ledger md5-identical, RLS flags identical (7 tables), DML grants byte-identical, tenant A/B isolation after restore | `evidence-pr2/g5-g6/` |
| Flyway schema match (chain vs repo) | **PASS (local)** — S1 migrate+validate head `20261115.1`; G5 scenario B production-history-equivalent upgrade PASS | `evidence-pr2/separation-cert/S1-*.log` |
| **Production** Flyway head recheck (`20261111.1` → current) | **BLOCKED** — no authorized production access; #1528 mandatory gate | CAB #1528 |
| Production permissions/RLS verification in approved environment | **BLOCKED** — requires authorized production read; production facts of 2026-11-13 stand (doc 15) | #1528 / #1530 |
| Production-derived full-scope restore | **BLOCKED — OWNER APPROVAL PENDING** (#1530 all checklist items unchecked; historic partial restore `--no-owner --no-acl` insufficient) | #1530 / #1481 |
| Render / Vercel version verification | **BLOCKED** — no credentials; last logged Render image `48322d0b` ≠ `4cee76ad7` (**SHA-chain MISMATCH, refresh required before any release**); Vercel read 403 historically | #1528 |
| 3-user employee/manager/HR production acceptance | **SKIPPED/BLOCKED** — production workflow not dispatched (NO-GO) | §1 |
| Rollback readiness (production) | **BLOCKED** (local G6 restore path proven; production rollback plan requires the authorized restore above) | #1530 |
| CAB approval (exact-SHA release decision) | **NOT APPROVED** — #1528 open; new exact-SHA decision required for `4cee76ad7` | #1528 |
| Owner GO/NO-GO + risk acceptance on C4 register | **PENDING** — doc 17 §9 register delivered for decision | #1462 |
| Payments | **OFF** — separate independent approval required | policy |

**G1 certificate: NOT ISSUED.** All safety invariants remain: `P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.
