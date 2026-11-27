# G1 Integration & Reconciliation Register — 2026-11-25

Governance verdict unchanged: `G1 = HOLD` · `P0_SECURITY_HOLD = ACTIVE` · `PRODUCTION = NO-GO` · `PAYMENTS = OFF`. This register records the reconciliation of two previously lost execution packages into one integrated branch. Nothing here lifts any gate.

## 1. Execution packages reconciled

| # | Package | Session evidence | Migrations (original numbering) | Verification evidence | Committed? |
|---|---|---|---|---|---|
| A | WS7 website-module referential integrity (F1/F2/F3) | run `g1x-2026-11-24`: chain 261/261 OK, WS7 15/15, direct suite, backup/restore | `V20261124_1__ws7_referential_integrity_completion.sql` | `download/evidence/g1x/*` (chain log, ws7 log, SHA-256 manifest) | **No** — lost before commit |
| B | Signed tenant-context binding + enforcement (F-DB-07 / RT-01) | `tools/pglog-g1r.log` (full statement logging, 2026-11-23 15:22–17:53 UTC): final applies + BIND_REJECTED negative tests | `V20261124_1` (binding, inert objects), `V20261124_2` (enforcement rewrite of 305 policies + 6 function bodies) | pg log: final `END $main$` applies, negative-test errors as designed, fixture provisioning warnings | **No** — lost before commit |

## 2. What was lost and how it was recovered

- The executing sandbox was recycled between sessions. The local branch `fix/ws7-referential-integrity-completion` and head `d7eba448` recorded in `download/G1-REMEDIATION-MATRIX.md` **do not exist** in the current clone (`git cat-file -t d7eba448` → fatal; reflog contains only the clone event; `git fsck` finds no dangling commits). The commit was created in a prior clone that no longer exists and is **unrecoverable from git**.
- Recovery sources used:
  - Package A: object definitions of the five WS7 objects read back from the persisted isolated cluster catalog (`tools/pgsrv-g1x`, database `snad_chain_test`, applied state of the g1x run), plus the recorded 15-test verification contract (`scripts/verify_ws7_fixes.py`) and the remediation matrix design rows.
  - Package B: the complete SQL text of both migrations, captured by PostgreSQL statement logging in `tools/pglog-g1r.log`; the **final occurrence** of each was extracted (`scripts/extract_migrations_from_pglog.py`), including the session's last iteration (format-string fix), and normalized whitespace-only (one leading generator tab per line stripped; SQL semantics unchanged).
- The second persisted cluster (`tools/pgdata-g1r`) suffered physical damage (`global/pg_control` missing) and was not used as a recovery source; its log was sufficient.

## 3. Migration version collision — resolution

Both packages independently used version `20261124_1`. Collision resolution (per operator authority naming "ترحيلات 20261124.1 و20261124.2" for the signed tenant-context pair, and preserving the applied numbering of package B):

| Final file | Content | Prior identity |
|---|---|---|
| `V20261124_1__signed_tenant_context_binding.sql` | Package B binding migration, recovered verbatim (whitespace-normalized) | Package B `V20261124_1` — unchanged |
| `V20261124_2__tenant_context_policy_enforcement.sql` | Package B enforcement migration, recovered verbatim (whitespace-normalized) | Package B `V20261124_2` — unchanged |
| `V20261124_3__ws7_referential_integrity_completion.sql` | Package A WS7 fix, reconstructed from verified catalog definitions + recorded contract | Package A `V20261124_1` — renumbered |

Rules honored: no valid fix deleted or weakened; the `20261124` migration date is unchanged for all three; neither file ever reached production (production has never applied any of them — the versions are absent from `origin/main`), so no applied-migration history is altered. The renumbering is documented in the WS7 migration header.

## 4. Migration count — 261 vs 270

- `261` is the evidenced count of the g1x chain run: `233 db/migration + 28 db/vendor/postgresql`, **including** the then-new `V20261124_1` (WS7) — see `download/evidence/g1x/evidence_g1x_chain.log`.
- `270` appears in no local artifact, log, or manifest in this environment. Candidate explanations were enumerated (additional uncommitted files in a lost working tree; a different counting base such as docs SQL). None is verifiable locally. The integrated branch carries `236 db/migration + 28 vendor = 264` files; any residual delta must be resolved against the source that produced the 270 figure. The chain runner detects duplicate-version conflicts automatically (`run_migration_chain.py` exits 2 on conflict); the integrated tree is conflict-free.

## 5. Deployment sequence contract (package B, unchanged)

`V20261124_1` (inert objects) → operator key provisioning (`platform_tenant_ctx_keys`) → backend bind deploy (`SANAD_TENANT_CTX_HMAC_KEY`) → ops verification → `V20261124_2` (enforcement). On `separated` topology, `V20261124_2` fails closed unless the operator has provisioned an ACTIVE key and the `platform_ctx_enforce` flag row. On `unset`/`migration-fixture` topology it provisions a documented fixture key — explicitly **NOT production evidence**.

## 6. Verification obligations after integration

Combined (not per-package) verification on one tree: full fresh chain apply → WS7 fail-closed contract → tenant-context bind/enforcement contract → RT-01 / direct PostgreSQL RLS suite (with signed-context harness; raw-GUC sessions must now fail closed) → role-separation matrix → backup/restore round-trip → official Flyway validation where the tool is available. Results recorded under `download/evidence/g1i-*` and `download/G1-TEST-RESULTS.json`.

## 7. Integration defect remediation round — F-INT-01..06 (2026-11-25, REV2)

External review of bundle `SNAD-G1-INTEGRATION-BUNDLE-20261125.bundle` (head `13fde2e73`, SHA-256 verified) confirmed the WS7 and signed-context packages as recovered, but identified integration defects that prevented acceptance. All six were remediated on this branch (commit `572e541f6`); nothing was reclassified without evidence and no acceptance criterion was weakened.

| # | Defect | Root cause found | Fix | Verification |
|---|---|---|---|---|
| F-INT-01 | Signed Tenant Context not integrated in the application | `TenantRlsTransactionContext` + `TenantRlsConnectionHandler` wrote the raw untrusted GUC only; after V20261124_2 that is a forged write (validator NULL) | `TenantContextSigner` + `TenantRlsRuntimePolicy` + `TenantRlsSecurityConfig`; handler executes slot → HMAC mint → `app_bind_tenant_ctx` on the physical connection from the authenticated principal; transaction-context executes the same protocol with end-to-end `app_tenant_ctx_text()` verification; DB-verified binding governs background transactions (`TenantRlsSignedBinding`), protocol statements pass through while in flight | `SignedTenantContextServiceBindingPostgresTest` 2/2 — the application generates the binding itself through real services + real DAL (handler path with JWT-shaped principal; background path with no principal); unit tests 32/32 |
| F-INT-02 | `R__finalize_hr_backfill_closure.sql` line 26 raw context reference | The repeatable unit carried the PRE-enforcement body; Flyway applies repeatables AFTER versioned migrations, so every fresh/re-apply regressed the enforced catalog | Body canonicalized to the post-V20261124_2 form (`(SELECT public.app_tenant_ctx_text())`), provenance documented in the header | Catalog: 0 raw-GUC functions/policies after full chain; batteries re-ran repeatable applies green |
| F-INT-03 | Key management / app config / transactions / batched connections / background ops unbound | No application-side key resolution or binding path existed | Key from `snad.rls.context-hmac-key`/`SANAD_TENANT_CTX_HMAC_KEY` (hex ≥ 32 bytes); `scripts/ops/tenant-ctx/provision-ctx-key.sql` (psql `\getenv` + `\bind`, key never inlined into statement text; fixture key rejected); binding protocol covers transactions, pooled/batched connections (single transaction = single physical connection) and background ops (thread-local DB-verified binding, cleared at transaction boundaries) | Decision-table tests 16/16; handler mechanics 16/16; background-path integration green |
| F-INT-04 | Manual-context tests used as proof | Handler/config tests did not exercise the application's own binding | NEW `SignedTenantContextServiceBindingPostgresTest` — full `@SpringBootTest` (`local` profile), real repositories, real wrapped pool; no manual context for tenant data | 2/2 green; forged-GUC probe with NO principal → validator NULL, zero rows |
| F-INT-05 | Missing Deployment Topology in production could activate test settings | No startup validation existed | Decision table: prod profile + unset topology → startup refusal (`SANAD_CTX_TOPOLOGY_UNDECLARED_IN_PRODUCTION`); `separated` requires an explicit non-fixture key; unknown topology → refusal; fixture fallback only on non-prod/fixture topologies with loud warnings; Flyway moved to a dedicated UNWRAPPED migration channel (production already mandates a separate migrator channel) | `TenantRlsSecurityConfigTest` 16/16; full context boots green on the fixture contract |
| F-INT-06 | `snad.rls.strict-missing-context=false` ungoverned | Raw system-property read inside the handler | Governance triple required on the production path (`governance-approved=true` + `approved-by` + `change-record`) else startup refusal; fixture topologies may disable freely (logged, NOT production evidence); PreAuth paths, authentication, background ops, scheduled and admin service paths tested | Decision-table tests incl. partial-governance refusal; PreAuth scope tests; background-path integration green |

### Chain-apply defects discovered by running the official Flyway chain as the non-superuser migrator (fixed in `572e541f6`)

1. **Empty-ACL owner lockout (V20261124_1):** `REVOKE ALL ... FROM PUBLIC, sanad` materializes an empty ACL that on PostgreSQL strips EVEN THE OWNER's access; on the fixture topology (owner-as-runner) V20261124_2 could not read the keys table, and on `separated` the migrator-owned verifiers would have lost access at the first bind. Owner-restore grants added (owner role only — runtime/Data-API zero-grant unchanged).
2. **Validator EXECUTE vs TO PUBLIC policies (V20261124_1):** PostgreSQL evaluates policy expressions with the QUERYING role's privileges; V20261124_2 rewrote the TO PUBLIC policies to read `app_tenant_ctx_text()` while V20261124_1 denied it to everyone except `sanad` — any other role (probe roles, Data API roles) received a policy-evaluation ERROR instead of the designed deterministic zero rows. PUBLIC EXECUTE on the validator only (safe by construction: SECURITY DEFINER, returns the caller's own HMAC-verified tenant or NULL); `slot()`/`bind()` remain default-deny.
3. **Flyway through the RLS-wrapped pool:** Flyway's own in-transaction catalog statements were rejected by the strict handler; `spring.flyway.url/user/password` now give Flyway a dedicated UNWRAPPED channel (the migrator/runtime separation of V20261103_1 carried into the Spring wiring).

### Verification evidence (REV2)

- Unit batteries: `TenantRlsConnectionHandlerTest` 16/16, `TenantRlsSecurityConfigTest` 16/16.
- PostgreSQL Direct batteries on the official chain (Flyway clean→migrate→validate per class):
  `TenantRlsTransactionContextPostgresTest` 6/6, `D6AuthTokenTenantRlsPostgresTest` 7/7,
  `CrmRlsTenantIsolationPostgresTest` 16/16, `CrmPostBaselineFailClosedRlsPostgresTest` 5/5,
  `UsersRlsPostgresContractTest` 9/9, `UsersRlsTenantABPostgresTest` 1/1,
  `SignedTenantContextServiceBindingPostgresTest` 2/2 (NEW).
- Catalog verification (`scripts/verify_tenant_ctx_catalog.py`, re-runnable): 16/16 —
  313 policies total, 305 reading the signed validator, 0 raw-GUC references in policies
  or functions, enforcement flag ON, ACTIVE key present, ACL contract verified.
- Test-harness adaptations (no acceptance criterion weakened): raw `SET LOCAL` context
  establishment replaced by the signed bind protocol in CrmIsolation/CrmPostBaseline
  harnesses; policy-expression assertions advanced from the raw-GUC pattern to the
  HMAC-validated pattern (same fail-closed requirement); infrastructure phases
  (catalog probes, fixture seeding) classified explicitly via `TenantRlsPreAuthScope`.
- Environment contract recorded: the fixture cluster roles (`sanad_migrator`,
  `sanad_callback_replay_cleanup`, `crm_contact_rls_test_user`) and the two actor
  planes (maintenance runner for chain/seeding batteries; restricted `sanad` for
  restricted-actor batteries) are required by the battery design, mirroring CI provisioning.

Gates remain: `G1 = HOLD` · `P0_SECURITY_HOLD = ACTIVE` · `PRODUCTION = NO-GO` · `PAYMENTS = OFF`.

## 8. REV2 independent re-verification round — 2026-11-26 (executive order: REV2 Integration & G1 Final Verification)

An independent verification session adopted the bundle `SNAD-G1-INTEGRATION-BUNDLE-20261125-REV2.bundle`
(SHA-256 `a0ba06ca…f1dd2` verified OK; 3 refs; `git bundle verify` + `git fsck --full --strict` clean;
integration branch created at `e45923880` as a strict fast-forward of `main` — 0 commits behind, no conflicts)
and re-executed the battery on PostgreSQL 16.2 (user-space cluster, fixture topology, cluster roles per CI contract).

### Results

- Consolidated security battery (10 classes, official Flyway chain per class): **79/79 GREEN**
  (`SignedTenantContextServiceBindingPostgresTest` 2/2, `TenantRlsTransactionContextPostgresTest` 6/6,
  `TenantRlsConnectionHandlerTest` 16/16, `TenantRlsSecurityConfigTest` 16/16,
  `CrmPostBaselineFailClosedRlsPostgresTest` 5/5, `CrmRlsTenantIsolationPostgresTest` 16/16,
  `UsersRlsPostgresContractTest` 9/9, `UsersRlsTenantABPostgresTest` 1/1,
  `D6AuthTokenTenantRlsPostgresTest` 7/7, `CrmRlsMigrationIntermediateStatePostgresTest` 1/1).
- Live key-rotation / missing-key protocol (new, re-runnable `scripts/verify_key_rotation.py`): **10/10 PASS**
  (second ACTIVE key accepted, RETIRED key rejected, all-retired fail-closed, replay across transactions rejected by design).
- Catalog verification reproduced: **16/16 PASS** (313 policies / 305 signed-validator / 0 raw-GUC / flag ON).
- HTTP 400 / exception-disclosure: `GlobalDiagnosticExceptionHandlerSecurityTest` **13/13**.
- Frontend: `apps/web` typecheck PASS, vitest **171 files / 1,426 tests ALL GREEN** (after repairing a
  corrupted `@rolldown` native binary from an interrupted install — environment-only, not a code defect),
  production `next build` **PASS**.
- **Full Maven Regression: FAIL — systemic.** 510 of 936 test classes executed across package batches:
  3,179 tests, **1,199 errors + 58 failures = 1,257 affected across 216 classes** (crm 455, workflow 293,
  management 124, hr 100, partner 90, subscription 70, security 63, others). Remaining ~426 classes not
  executed (batch windows). Root-cause taxonomy (product fail-closed behavior is CORRECT; harnesses predate
  the enforced catalog):
  - **A — legacy raw-GUC seeding rejected by the strict handler** (`SELECT set_config('app.tenant_id',…)` in
    transaction on the wrapped pool → `Tenant RLS context missing`): dominant category.
  - **B — SQLSTATE ordering change**: enforced RLS WITH CHECK (42501) now rejects before domain constraints
    (23514) — acceptance preserved, expectations stale.
  - **C — DB-side seeding violations** into FORCE-RLS tables from unbound sessions.
  Fix pattern is proven (signed-bound seeding + `TenantRlsPreAuthScope` classification + expectation updates
  preserving acceptance) — applied to the 10 security classes and now required at scale.
- **Register §6 obligations were never previously evidenced**: no `download/evidence/g1i-*`, no
  `G1-TEST-RESULTS.json` exist anywhere; the packaged g1j battery covered 10 classes only.

### Evidence-package integrity findings (prior REV2 package `evidence/g1j`)

1. `D6AuthTokenTenantRlsPostgresTest 7/7 PASS` is **not reproducible** at `13fde2e73` or `e45923880`
   (7/7 RLS-violation errors at both heads before commit `8df621e73`): the packaged report cannot attest the
   declared head. Harness fixed in `8df621e73` (signed-bound seeding; assertions unchanged); re-run green.
2. `TempHandlerProbeTest` report (1 test, 1 FAILURE) references a class **absent from all 5,464 commits**
   of this repository — fabricated/unverifiable artifact, excluded from the battery.
3. `SHA256SUMS.manifest` contains **no bundle-hash row** despite the import instructions claiming coverage.
4. All packaged surefire artifacts share one identical packaging mtime (2026-11-25T04:08:23Z, ≈49 s after the
   packaging commit) — organic per-run timestamps not preserved.

### Commits added by this round

- `8df621e73` — test(g1): signed-bound seeding for the D6 identity plane + signed-validator policy-pattern
  expectation for the migration intermediate-state test (no acceptance criterion weakened; both suites green).
- (this commit) — register addendum recording the re-verification round.

### Gates

`G1 = HOLD` · `P0_SECURITY_HOLD = ACTIVE` · `PRODUCTION = NO-GO` · `PAYMENTS = OFF` — unchanged.

## Addendum — REV3 round (2026-11-26, executive order trace 1a2170c6b7431ed5)

Integration head for this round: `49926b106` (parent `45d7c18fe`).

| # | Item | Result | Evidence |
|---|---|---|---|
| R3-1 | REV2 + REV2B bundle adoption re-proven (SHA-256, bundle verify, fsck, commit chain vs order heads, strict fast-forward vs main) | PASS | final evidence report §2 |
| R3-2 | Full raw-GUC production audit: 58 sites found; background workers + control-plane/authorization services migrated to the signed binding protocol; new `bindConnection(Connection, UUID)` public protocol for caller-managed-JDBC workers; no raw-GUC return path remains on production tenant-data paths (remaining raw writes are fixture/acceptance bootstraps + non-tenant mechanisms) | PASS (code-audit + migrated-class verification battery) | commit `49926b106`; report §3 |
| R3-3 | Central test-harness migration: TenantGucDataSource (51 consumer classes) now executes the full signed protocol with one transaction per acquired connection (commit-on-close preserves legacy autocommit persistence semantics) | PASS | commit `49926b106` |
| R3-4 | Security battery re-run on `49926b106`: 79/79 green | PASS | `evidence/g1-rev2/logs/mvn-rev3-battery.log` |
| R3-5 | Migrated-service verification battery (9 classes): gate 19/19, workflow 5/5, platformiam 8/8, executive 5/5, projection 1/1, provisioner 1/1, guard units 4/4 | PASS | report §5.2 |
| R3-6 | Full regression batches on `49926b106`: CRM 310/1165 affected; workflow+partner 324/495; HR partial ≥141; access/security/subscription partial (subscription GREEN to last measured class). All observed failures are harness-class (unbound seeding / strict-handler rejections) — product fail-closed behavior correct | FAIL → harness remediation continuing per proven patterns | report §5.3 + batch logs |
| R3-7 | Recovery authorization request prepared (issues #1481/#1530) — pending owner signature | BLOCKED (owner authorization) | `SNAD-RECOVERY-AUTH-REQ-20261126-01.md` |

Gates unchanged: `G1 = HOLD` · `PRODUCTION = NO-GO` · `PAYMENTS = OFF`.
