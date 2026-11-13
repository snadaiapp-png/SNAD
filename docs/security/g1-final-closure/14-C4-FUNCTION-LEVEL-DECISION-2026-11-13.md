# G1 #1462 — C4 finding-by-finding technical decision (NOT independent human sign-off)

**Decision:** `C4_TECHNICAL_PROBES = PASS_WITH_LIMITATIONS` · `C4_FINAL_APPROVAL = HOLD` · `C4_CHECKBOX = UNCHECKED`.  
**Production:** `P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.  
**Source revision:** main `7b73b7fc90f53a7953d97addf543a3fdf4173e36`.  
**Test revision:** `e9cdfed40f5ded24875bee9d14c8d316e4c8cea0` ([isolated PG17.6 run 43347940014](https://github.com/snadaiapp-png/SNAD/actions/runs/43347940014), artifact **14002188717**, path `c4-catalog-artifacts/catalog.txt`).  
**Note:** This is a source-and-artifact technical adjudication, not a credentialed independent review, authorization to deploy, or a claim of comprehensive function-level dynamic testing.

## Exact raw-catalog matrix: all 12 SECURITY DEFINER functions

For **every row** below, raw `pg_proc` inventory reports `owner=sanad_migrator` and `proacl={sanad_migrator=X/sanad_migrator,sanad=X/sanad_migrator}`. Therefore **sanad CAN EXECUTE** via explicit ACL; the raw `PUBLIC` executable surface is empty in this fixture. This is not proof of least privilege, no-tenant denial, or inability to exploit each function.

| # | pg_proc signature | search_path from LIVE PG17.6 catalog | Exact finding | Missing function-level runtime acceptance |
|---|---|---|---|---|
| 1 | `hr_precheck_tenant(uuid)` | `pg_catalog, public, pg_temp` | Owner/ACL/path catalog PASS | direct call own/cross/no-GUC and authorization semantics NOT VERIFIED |
| 2 | `hr_reconcile_tenant(uuid)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC + side-effect review NOT VERIFIED |
| 3 | `hr_backfill_tenant(uuid)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC + privileged DML review NOT VERIFIED |
| 4 | `hr_reconcile_tenant_report(uuid)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC + report disclosure NOT VERIFIED |
| 5 | `sync_login_tenant_index()` | **`pg_catalog, public, pg_temp`** | Owner/ACL catalog PASS; source at `V20261011_4` defines `pg_catalog, public`, but **live catalog is authoritative for tested state**; underlying later definition/change origin UNRESOLVED | trigger-mediated misuse and role execution grant review NOT VERIFIED |
| 6 | `sync_workflow_external_action_token_index()` | **`pg_catalog, public`** | Owner/ACL catalog PASS; body writes explicitly to qualified `public.workflow_external_action_token_index` | negative trigger/access-path review NOT VERIFIED |
| 7 | `hr_assert_migration_tenant_scope(uuid)` | `pg_catalog, public, pg_temp` | Owner/ACL/path catalog PASS | direct bypass/tenant-guard negative cases NOT VERIFIED |
| 8 | `hr_default_migration_as_of_date(uuid)` | same | **pg_temp.hr_employees shadow attempt PASS** for one synthetic tenant with expected `1970-01-01` | other inputs/tenant boundaries NOT VERIFIED |
| 9 | `hr_precheck_tenant(uuid,date)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC negative calls NOT VERIFIED |
| 10 | `hr_reconcile_tenant_report(uuid,date)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC report disclosure NOT VERIFIED |
| 11 | `hr_reconcile_tenant(uuid,date)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC + side-effect review NOT VERIFIED |
| 12 | `hr_backfill_tenant(uuid,date)` | same | Owner/ACL/path catalog PASS | own/cross/no-GUC + privileged DML review NOT VERIFIED |

**Correction to prior document #13:** The claim that *both* trigger routines have `search_path=pg_catalog, public` is **DISPROVEN** by the actual PG17.6 `catalog.txt`. `sync_login_tenant_index()` has `pg_catalog, public, pg_temp` in the live catalog. The #13 document should not be used as a final uncorrected certificate. No new SQL execution was performed in preparing this correction.

## Effective privilege and ownership determination

- `sanad`: `rolcanlogin=true`, `rolsuper=false`, `rolbypassrls=false`, `rolcreatedb=false`, `rolcreaterole=false`, `rolinherit=false`. Runtime membership in `sanad_migrator` is **false**.
- Migrator `sanad_migrator` owns all 12 SECURITY DEFINER functions and the catalog's public relations (324 object rows). Database `separation_cert` is migrator-owned, while the `public` namespace is owned by `pg_database_owner` and effectively controlled by database owner. Runtime schema CREATE is **false** and the actual CREATE probe failed with **SQLSTATE 42501**.
- 12/12 SECURITY DEFINER `PUBLIC EXECUTE` = **none in the tested catalog**. **12/12 `sanad EXECUTE` = present**, a deliberate or over-broad executable privilege that requires per-function necessity analysis. In PostgreSQL trigger-returning functions cannot be invoked as ordinary SQL functions, but attached triggers execute on DML; that does not negate the need to review object access/trigger semantics.
- Default privileges recorded for `sanad_migrator` in `public`: `f:{sanad=X}` (all future function EXECUTE), `r:{sanad=arwd}` (future table DML), `S:{sanad=rU}` (future sequence usage/read). These privileges **do not automatically prove a leak**, but make future functions and table grants a documented least-privilege growth concern. A negative authorization matrix is required to accept them.
- The raw catalog lists 0 rows for explicit PUBLIC security-definer EXECUTE, 0 rows for `search_path=public` (both in the observed test database), and 324 public relations. It does **not** contain per-function current-user/tenant A/B/no-GUC call transcripts.

## Proven test cases, limitations, and sign-off requirements

| Gate | Evidence | Outcome |
|---|---|---|
| 260 Flyway ledger rows, head versioned `20261111.1` | raw `S1-validate.log`, `catalog.txt` | PASS on isolated PG17.6 |
| Role/no-migrator-membership and SQLSTATE 42501 runtime DDL denial | `catalog.txt`, `negative-create.log` | PASS |
| HR pinned path, 10/10 CI assertion | `hr-search-path-gate.txt`, PG `pg_proc` rows | PASS |
| Synthetic `pg_temp.hr_employees` shadow negative call | `hr-temp-shadow-negative.txt`, `c4-probe-run.log` | PASS for **one** tested function |
| G5/G6 historical upgrade and restore | [run 43347940479](https://github.com/snadaiapp-png/SNAD/actions/runs/43347940479) | PASS in **fixture topology**; not separated owner proof |
| Merged code CI | [PR #1522](https://github.com/snadaiapp-png/SNAD/pull/1522), [Post-Merge 43356250471](https://github.com/snadaiapp-png/SNAD/actions/runs/43356250471) | SUCCESS |
| Docs PR #1523 review and Post-Merge | [PR #1523](https://github.com/snadaiapp-png/SNAD/pull/1523) one blank native APPROVED; [Post-Merge 43370039554](https://github.com/snadaiapp-png/SNAD/actions/runs/43370039554) SUCCESS | GitHub administrative sign-off / CI PASS, **not function-specific independent decision** |
| 12-function direct/no-tenant/cross-tenant and trigger-mediated negative matrix | Not present as complete raw test record | **BLOCKED/UNVERIFIED** |
| Independent reviewer's personal identity and non-owner control, historical conflict | Self-attestation in [#1520](https://github.com/snadaiapp-png/SNAD/pull/1520) versus historical owner-linked record | **UNRESOLVED** |
| Production Postgres catalog, application pool and real production three-user gate | Not part of isolated C4; three-user workflow SKIPPED | **NOT VERIFIED — separate later gate** |

## Decision

**Independent reviewer decision: NOT ISSUED. Technical adjudicator decision: C4 = HOLD.** The two controls with concrete evidence are search-path/ownership and privilege inventory; only a subset of permitted function invocations was actually exercised. Neither a GitHub approval without supporting findings nor success of unrelated CI constitutes a full C4 review.

**Actions required from the authorized independent reviewer (not delegated to this report):**
1. Review the original artifact and source for all **12** entries, confirm the `sync_login_tenant_index` source/catalog difference and no shadowing/bypass opportunity in trigger context.
2. Review whether `sanad` really needs EXECUTE on all 12 and future routine EXECUTE; decide on least-privilege policy and file specific remediation if needed.
3. Approve a **complete isolated negative test matrix** for every directly invokable definer routine (tenant A/B, unset/empty GUC, protected mutations and disclosures), and both trigger flows. Record real SQLSTATE, row counts and rollback behavior. No assertion of PASS until performed.
4. Record a **clear decision**: `APPROVED — C4 isolated scope` only if the preceding evidence exists and scope/identity classification is accurate; otherwise `CHANGES_REQUESTED` or `HOLD`, with the exact outstanding findings. Explain reviewer independence; if owner-linked, the review is **internal**, never claim independent.
5. Keep Issue #1462 C4 checkbox unchecked until its **independent review of migrations, owners, SECURITY DEFINER search_path/ACLs, and fixture-limited cases** is evidenced.

No production SQL, migration, deployment, payment enablement or G1 certificate is authorized.
