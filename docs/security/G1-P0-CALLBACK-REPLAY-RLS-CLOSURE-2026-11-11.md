# G1 P0 — service_callback_replay RLS Closure (2026-11-11)

**Mandate:** SANAD G1 / P0 Security Remediation executive order (2026-11-11).
**Merge reference / base:** `cac06b8825a676971e731ace62a93f8d60e1a509` (main HEAD verified via GitHub API before branching).
**Branch:** `security/p0-service-callback-replay-rls`
**Status:** Remediation complete and certified on isolated infrastructure. **P0_SECURITY_HOLD=ACTIVE; PRODUCTION_RELEASE=NO-GO; PAYMENTS=OFF.** No production access was attempted at any point.

## 1. Forensic findings (verified at cac06b88)

1. `V20260724_2__create_crm_command_artifacts.sql` (vendor/postgresql) created
   `public.service_callback_replay` with a UUID `tenant_id` (FK → tenants ON DELETE
   CASCADE) but **no ENABLE/FORCE RLS and no policies**.
2. `V20261029_1` (the fail-closed tenant RLS wave) enumerates protected tables by
   module prefix sets; `service_callback_replay` (`service_` prefix) is absent from
   both `protected_tables` and `force_only_tables`.
3. `V20261104_2` closed the 19 historical `crm_*` tables — including the sibling
   `crm_integration_command_artifacts` created by the same `V20260724_2` — the
   `service_`-prefixed replay guard stayed open. `tests/security/rls_classify_tables.py`
   lists it in `AUDIT_49` (production snapshot: 49 tables without RLS, 2026-10-26).
4. `D6-TENANT-RLS-INVENTORY.md` classified the table as EXCEPTED with the follow-up
   "evaluate a per-tenant policy variant once callback authentication is established
   upstream". The upstream condition holds today: `WorkflowCallbackSecurity.verify()`
   validates the signed service JWT (including tenant binding) BEFORE
   `CallbackReplayStore.consume()` runs, so the tenant identity is trusted upstream.
5. Runtime role contract: `sanad` holds SELECT/INSERT/DELETE (V20261103_2 general set);
   UPDATE revoked by `V20261105_1` in the separated topology. The hourly sweep
   (`CallbackReplayStore.deleteExpired`, cron `0 17 * * * *`) ran cross-tenant with no
   tenant context — pre-closure this was un-RLS'd table-wide access.

## 2. Closure design (V20261111_1 + CallbackReplayStore)

* **ENABLE + FORCE ROW LEVEL SECURITY** on `public.service_callback_replay`.
* **Fail-closed tenant policies TO PUBLIC** (house predicate, `V20261029_1` contract):
  * `tenant_isolation_select` — `USING (tenant_id::text = NULLIF(current_setting('app.tenant_id', true), ''))`
  * `tenant_isolation_insert` — `WITH CHECK (same)`
  * `tenant_isolation_delete` — `USING (same)`
* **Deliberately NO UPDATE policy**: verified on PostgreSQL 17.11 that an UPDATE with
  no applicable policy is silently inert (zero rows matched — row values unchanged,
  including for the FORCE-bound table owner holding the UPDATE privilege). Combined
  with the separated-topology privilege revocation (V20261105_1), UPDATE is impossible
  for every role. No BYPASSRLS, no SECURITY DEFINER, no owner bypass.
* **Dedicated expired-row cleanup role** `sanad_callback_replay_cleanup`
  (environment-provisioned per the `sanad_migrator` contract precedent — the chain
  never creates roles): NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  holds exactly SELECT+DELETE on this one table; bounded by two policies
  (`expired_cleanup_select` / `expired_cleanup_delete`) to
  `expires_at <= CURRENT_TIMESTAMP` rows regardless of tenant. `sanad` holds a
  **NON-INHERITING (SET-only)** membership (`WITH INHERIT FALSE`) — policy role
  matching follows inheritance rules, so the cleanup policies never apply to the
  runtime identity itself; the runtime never gains standing cross-tenant privileges
  (executive order phase 1.4 / 2.6).
* **Code**: `consume()` binds the trusted tenant GUC inside its own transaction
  (the `TenantRlsTransactionContext` trusted scoped path) before the INSERT;
  `deleteExpired()` switches to the cleanup role inside its own transaction
  (`SET ROLE` + `RESET ROLE` in a finally block — a pooled connection can never leak
  the cleanup identity). The INSERT policy enforces the row's tenant equals the
  validated token's tenant at the database level.

## 3. Certification evidence

**Isolated host-native PostgreSQL 17.11 (Debian trixie build) + project-pinned
Flyway CLI 11.7.2, separated topology (`sanad.deployment_topology='separated`),
full 260-migration chain applied as `sanad_migrator`, chain head `20261111.1`,
`flyway validate` exit 0.**

`tests/security/callback-replay-rls/run-rls-cert.sh` — **37/37 probes PASSED**:
* S0 role contracts (migrator/runtime/cleanup + SET-only membership MEMBER=t/USAGE=f/SET=t).
* S1 Flyway as migrator, head `20261111.1`, validate OK.
* S2 catalog: ENABLE+FORCE, exactly 5 policies, tenant predicate ×3, expiry-bounded ×2,
  no UPDATE policy, cleanup ACL = SELECT|DELETE only, sanad ACL = S/I/D (no UPDATE).
* **M1** Tenant A cannot read (0 rows) or modify (UPDATE 42501 / DELETE 0-effect) B's rows.
* **M2** Tenant B symmetric (cannot see/delete A's rows).
* **M3** missing-GUC and empty-GUC fail-closed (SELECT 0; INSERT 42501
  "new row violates row-level security policy"; DELETE 0).
* **M4** mismatched-tenant INSERT rejected 42501 in BOTH directions.
* **M5** cross-tenant DELETE inert; admin-verified physical rows preserved (1|1).
* **M6** cleanup path: runtime (non-inheriting member) sees only its own rows
  (incl. own expired); the sweep as the runtime identity with `SET ROLE` saw 2
  expired / deleted 2 / saw 0 after; fresh rows preserved (2|2); cleanup role cannot
  INSERT (42501 permission denied) nor see/delete fresh rows; migrator cannot SET ROLE
  (42501, not a member); owner (FORCE-bound, NOBYPASSRLS) sees zero rows without
  tenant context; owner UPDATE zero-effect + values unchanged.
* **M7** consume INSERT ok under tenant policy; jti/nonce uniqueness intact
  (23505) in-tenant and cross-tenant; superuser tenant-deletion cascade documented
  (restricted identities must bind the tenant GUC first — the ModuleResetService
  contract already does).

Release catalog gate (`docs/security/G1-RELEASE-CATALOG-GATE-2026-11-10.sql`,
extended to 14 checks incl. `TABLE:service_callback_replay` and the two cleanup-role
checks): **14/14 PASS** on the isolated certified chain.

**Java acceptance (PostgreSQL Direct, real connections, fixture topology bound by
FORCE RLS):**
* `CallbackReplayRlsPostgresTest` (new, mandatory matrix) — **8/8 PASS**.
* `WorkflowCallbackSecurityPostgresTest` (updated to the tenant-scoped contract) — **5/5 PASS**.
* `R0C13G02SchemaPostgresTest` (chain-head bump) — **9/9 PASS**.
* `scripts/production/tests/verify-flyway.test.sh` (release-audit semantics, new head
  20261111.1 + drift scenario 20261112.1) — **21/21 GREEN**.

## 4. Integration surface updated

* Provisioning: `docker/postgres-init/02-create-migrator-role.sh`, `.github/workflows/ci.yml`
  (all 4 PostgreSQL Direct provisioning blocks), `tests/security/g5/run-cert.sh`,
  `tests/security/migrator-separation/run-separation-cert.sh`,
  `docs/security/migrator-runtime-separation/RUNBOOK.md §2c`.
* Expected chain heads bumped `20261107.1 → 20261111.1`: separation cert, G5 cert
  (incl. failure-injection `V20261112_1` above the new head), Flyway auditor
  acceptance synthetic head, `verify-flyway.test.sh`, `R0C13G02SchemaPostgresTest`.
* New CI workflow: `.github/workflows/callback-replay-rls-cert.yml` (isolated PG17 +
  Flyway 11.7.2 + the 37-probe matrix + 14/14 catalog gate on PRs touching the chain).
* `D6-TENANT-RLS-INVENTORY.md`: the `service_callback_replay` exception is now
  CLOSED (6/25 L1 gap tables fixed).

## 5. Production status — NOT EXECUTED (by mandate)

* `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_RELEASE=NO-GO`, `PAYMENTS=OFF`.
* Production catalog was **not** contacted (no authorized read-only credentials were
  provided in this execution context; phase 1.5 of the order is therefore **BLOCKED —
  UNVERIFIED**, not failed).
* Required production sequence once authorized: RUNBOOK §2c role provisioning →
  backup/restore evidence (RPO/RTO) → CAB approval → canonical migrator workflow
  (Flyway 11.7.2 as `sanad_migrator`, exactly that identity, `expected_head=20261111.1`)
  → post-migration re-run of the 14/14 catalog gate + the M1-M7 probes against
  production → closure evidence to the independent security reviewer.
* This certification is NOT a production authorization.
