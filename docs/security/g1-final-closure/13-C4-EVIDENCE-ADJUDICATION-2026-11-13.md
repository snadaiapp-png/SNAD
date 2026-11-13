# G1 / Issue #1462 — Criterion 4: source + PostgreSQL catalog evidence adjudication

**Reviewed 2026-11-13 | exact merged main SHA:** `d54e8e161c518c305a6ddaddb2a0a0a28b7cc96a`  
**Disposition:** `C4_TECHNICAL_REHEARSAL = PASS_WITH_BOUNDED_SCOPE`; `C4_INDEPENDENT_FINAL_SIGNOFF = PENDING`; `ISSUE_C4_CHECKBOX = OPEN`.  
No production database inspection was performed. This document does **not** represent an independent human security certificate.

## Primary evidence reviewed

1. [PR #1522](https://github.com/snadaiapp-png/SNAD/pull/1522) merged at `d54e8e161c518c305a6ddaddb2a0a0a28b7cc96a`.
2. [Separated-topology PG17.6 CI](https://github.com/snadaiapp-png/SNAD/actions/runs/43347940014), head `e9cdfed40f5ded24875bee9d14c8d316e4c8cea0`, concluded SUCCESS; original artifact `migrator-runtime-separation-evidence` ID **14002188717** inspected, including `c4-catalog-artifacts/catalog.txt`, `hr-temp-shadow-negative.txt`, `hr-search-path-gate.txt`, `negative-create.log`, `separation-run.log` and `S1-validate.log`. This is the PR's technical tree, not production. 
3. [G5/G6 Flyway / backup-restore](https://github.com/snadaiapp-png/SNAD/actions/runs/43347940479), same head, concluded SUCCESS; G5/G6 artifact ID **14002921104**. Job log shows scenarios A/B/C, fail-closed injected migration failures and D backup/restore PASS. **G5/G6 fixture uses its documented fixture topology and cannot replace separated-role C4 tests.**
4. [Post-Merge Main Verification](https://github.com/snadaiapp-png/SNAD/actions/runs/43356250471), [CI](https://github.com/snadaiapp-png/SNAD/actions/runs/43356250400), and [Artifact Provenance](https://github.com/snadaiapp-png/SNAD/actions/runs/43356250554): completed SUCCESS on exact merge SHA. Production 3-user final gate [43356250413](https://github.com/snadaiapp-png/SNAD/actions/runs/43356250413) was SKIPPED.
5. Source: `V20261103_2__migrator_ownership_and_runtime_acl.sql`, repeatable `R__finalize_hr_backfill_closure.sql`, `V20261011_4__pre_auth_tenant_resolver.sql`, `V20261029_3__workflow_external_action_preauth_token_index.sql`, `V20261105_2__workflow_preauth_index_runtime_read_only.sql`, and `tests/security/g1-c4/run-catalog-probes.sh`.

## Catalog findings (isolated separated-topology PostgreSQL 17.6)

- 12/12 `public` SECURITY DEFINER routines are owned by `sanad_migrator`, not `sanad`. Catalog identifies **10 HR functions** all pinned to `search_path=pg_catalog, public, pg_temp` and **2 trigger functions** pinned to `search_path=pg_catalog, public`: `sync_login_tenant_index()`, `sync_workflow_external_action_token_index()`.
- The two trigger bodies in the checked-in versioned SQL use explicit `public.login_tenant_index` and `public.workflow_external_action_token_index` names respectively and `NEW`/`OLD` pseudorecords. Their lack of `pg_temp` in the configured search_path is not automatically exploitable; **dynamic malicious trigger-invocation/bypass testing beyond existing functional acceptance is not independently demonstrated**.
- In the isolated catalog, all 12 routines show `proacl = {sanad_migrator=X/sanad_migrator,sanad=X/sanad_migrator}`; **zero PUBLIC EXECUTE for the 12**. `sanad` can EXECUTE all 12 by explicit grant—this is *not* the same as PUBLIC access and needs to be assessed as part of the runtime invocation threat model. Trigger-returning functions cannot be called as regular SQL routines, but can run through attached triggers on permitted DML. HR routines include tenant-scope guards and may be callable by runtime; verify every high-privilege function's tenant scoping and intended exposed operations against the application contract.
- `sanad` is LOGIN, NOSUPERUSER, NOBYPASSRLS, NOCREATEDB, NOCREATEROLE, NOINHERIT; `sanad_migrator` is LOGIN on the rehearsal only. `pg_has_role('sanad','sanad_migrator','MEMBER') = false`; `public` schema owner is `pg_database_owner`, with database owned by the migrator. Runtime `CREATE` in `public` denied **SQLSTATE 42501**. `pg_default_acl` has migrator default EXECUTE grants to runtime for future functions; preserve this as a privilege-growth **review item**, not a proven exploit.
- 324 catalog objects/relations have owner `sanad_migrator` in the separated test report; no runtime TRUNCATE in the captured public relation inventory. The **raw catalog** lists broad runtime DML grants under `V20261103_2`, so per-table ACL review and security-invoker/definer boundary remain an operational review obligation.
- C4 CI prints `C4_ROLE_OWNER_HR_PUBLIC_EXECUTE_ASSERTIONS=PASS`, `C4_HR_DEFINER_SEARCH_PATH_PINNED=10/10`, `C4_HR_TEMP_SHADOW_NEGATIVE=PASS expected=1970-01-01`, and `C4_RUNTIME_CREATE_DENIED_42501=PASS`. This proves only the exercised cases; there was **no blanket exploitability proof**.

## Remaining acceptance conditions: must not silently waive

- **C4-A01 Independent reviewer:** an explicitly scope-bound human review of the *original* PostgreSQL artifacts and these 12 functions, including confirmation of actual function bodies at the run SHA, expected `EXECUTE` exposure, inherited/default ACL, `pg_temp` ordering and recorded fixture limitations. PR #1522's 2 native GitHub APPROVED reviews establish an administrative PR review, **not proof that all original C4 evidence was independently verified**. Prior documentation still disputes whether the GitHub reviewer account is owner-linked. Do not certify independence without resolving the conflict.
- **C4-A02 Negative execution cases:** exercise denied-privilege and tenant-boundary function invocation (especially permitted direct HR function EXECUTE) under a low-privilege authenticated runtime, both positive tenant and foreign/no-tenant contexts; test trigger-mediated index access and effective permission against the intended threat model on an isolated separated topology. Existing tests already verify some index flows, but full matrix mapping to the 12-function inventory is incomplete.
- **C4-A03 Fixture versus separated versus production:** G5/G6's fixture topology does not establish separated ownership. PG17.6 separated rehearsal does establish its own catalog, not production. There is no live production catalog or pool verification; those are also required by G1's next separate release gate.
- **C4-A04 Migration immutability:** versioned V files were not altered in PR #1522; repeatable R was intentionally updated with synchronized three source SHA-256 markers. `flyway validate` and integration tests passed in PR CI. Production migration history remains out-of-scope pending CAB authorization.

### Reviewer decision requested (scope limited)

`[ ] APPROVED C4 — independent artifact + 12-function review and fixture-limited matrix complete`
`[ ] CHANGES REQUESTED — detailed finding IDs and required remediation attached`
`[ ] HOLD — independent account/identity conflict or missing raw/dynamic evidence remains`

A single native GitHub APPROVE of this *document* records administrative approval, not substitute for actually reviewing artifacts and adjudicating C4-A01 through C4-A04. Mark C4's issue checkbox **only after** recorded evidence satisfies its exact wording.

**Current:** `P0_SECURITY_HOLD=ACTIVE`; `G1=HOLD`; `PRODUCTION_RELEASE=NO-GO`; `PAYMENTS=OFF`.
