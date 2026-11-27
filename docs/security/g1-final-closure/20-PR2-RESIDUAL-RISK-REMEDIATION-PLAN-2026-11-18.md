# G1 / C4 — PR-2 residual-risk remediation plan (nine risks, HIGH first)

**Date:** 2026-11-18 · **Branch:** `security/g1-c4-pr2-risk-adjudication` (from official `main` @ `4cee76ad71667b00b60570db25358df60c99cbb1`)
**Basis:** the residual-risk register of [17-C4-RISK-ADJUDICATION-2026-11-18.md](17-C4-RISK-ADJUDICATION-2026-11-18.md) §9 (nine entries, all `PENDING-OWNER`) and the live red-team findings of [18-RED-TEAM-REVIEW-2026-11-18.md](18-RED-TEAM-REVIEW-2026-11-18.md) (battery v2 evidence).
**Disposition:** `REMEDIATION_PLAN = DELIVERED (this document)` · every risk status remains **PENDING-OWNER** — this plan accepts, waives or closes **nothing** and converts no HOLD into PASS. `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`.

This is the engineering remediation plan the owner asked for, ordered by severity (HIGH first). Each entry states the objective, the concrete remediation steps, the fail-closed verification gate that proves closure, compatibility/rollback considerations, and the explicit owner decision required. Sequencing with dependencies is given at the end.

---

## Priority order

| Priority | Risk | Severity (doc 17 §9) |
|---|---|---|
| P0 | R-C4-01 `tenants` full CRUD by runtime, no RLS | **HIGH** |
| P0 | R-C4-02 tenant GUC not DB-authenticated | **HIGH (inherent)** |
| P1 | R-C4-04 `access_capabilities` DML by runtime | MEDIUM-HIGH |
| P1 | R-C4-05 pre-auth plane characterization + plaintext `identifier` | MEDIUM |
| P1 | R-C4-03 audit-ledger forged-row insertion | MEDIUM |
| P2 | R-C4-06 default-ACL future-grant growth | MEDIUM |
| P2 | R-C4-07 definer-function EXECUTE breadth (sanad 12/12; production `service_role` 11/12) | MEDIUM |
| P3 | R-C4-08 global audit-plane RLS decision | LOW-MEDIUM |
| P3 | R-C4-09 registry per-table necessity sweep (20 tables) | LOW-MEDIUM |

---

## P0-1 · R-C4-01 — `tenants` full CRUD by runtime, no RLS (HIGH)

**Trigger evidence (live):** RT-07/07B/07C (INSERT, exact count 1, read-back), RT-08/08B (UPDATE incl. `status`/`suspension_reason`, read-back `SUSPENDED|RT-PROBE`), RT-09/09B (DELETE, admin-proven absent) — the runtime role can enumerate, suspend or delete ANY tenant row with no DB-level guardrail.

**Objective:** the runtime identity can no longer perform unguarded control-plane DML on `tenants`; provisioning flows keep working through a least-privilege path.

**Remediation steps:**

1. Introduce a `sanad_control_plane` NOLOGIN role (cluster-wide, mirroring the `sanad_callback_replay_cleanup` pattern of `V20261111_1`): `NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`, granted to the runtime identity `WITH INHERIT FALSE` (SET-only), or — if the owner prefers hard separation — a distinct `sanad_provisioner` LOGIN used only by the provisioning service.
2. Migration `V20261118_1__tenants_control_plane_acl.sql` (topology-aware, forward-only, fail-closed on unknown topology — reuse the `V20261115_1` pattern): in the `separated` topology REVOKE `INSERT, UPDATE, DELETE` on `public.tenants` from `sanad`, GRANT them to `sanad_control_plane`; assert effective-privilege postconditions (`has_table_privilege` matrix) inside the migration.
3. Keep `SELECT` on `tenants` with the runtime role only if a documented requirement exists (tenant-name resolution); otherwise narrow to the specific columns via a view, or move resolution behind a definer function with tenant-scope guards.
4. Application change: provisioning endpoints open a dedicated transaction that does `SET LOCAL ROLE sanad_control_plane` (or uses the provisioner datasource) for tenant lifecycle DML only.

**Verification gate (fail-closed, added to the red-team battery and the separation cert):** RT-07/08/09 flip from `CONFIRMED` to `DENIED(42501)` for the plain runtime role, while a dedicated S-gate proves the control-plane path can still INSERT/UPDATE/DELETE exactly 1 sacrificial row with read-back; `S9`-style effective-ACL matrix asserts `sanad: SELECT(±)/INSERT=f/UPDATE=f/DELETE=f` on `tenants`.

**Compatibility/rollback:** the provisioning service must be updated in the same release (dual-mode: keep grants until the app patch is deployed, then a second migration flips the REVOKE — or gate both behind the same deploy). Rollback = re-GRANT via forward-only revert migration, never by editing history.

**Owner decision required:** choice of SET-role path vs. separate provisioner login; whether runtime keeps `SELECT` on `tenants`; approval to schedule P0.

## P0-2 · R-C4-02 — tenant GUC context not DB-authenticated (HIGH, inherent)

**Trigger evidence (live):** RT-01 (hostile session `SET app.tenant_id` to tenant B and read exactly the admin-computed foreign-tenant row count — `CONFIRMED(1)`), doc 17 §6.

**Objective:** a stolen runtime SQL session cannot act inside another tenant's RLS scope merely by setting a GUC.

**Remediation steps (owner selects one; they are alternatives, not cumulative):**

1. **Pooler/session-level enforcement (least invasive):** pin every runtime connection to a transaction-mode pooler (Supabase Supavisor/pgbouncer transaction mode) where `SET`/`RESET` of custom GUCs is reset at transaction end and the GUC is set exclusively by the trusted `TenantRlsTransactionContext` via `SET LOCAL`; add a battery probe that a session-level `SET app.tenant_id` (non-LOCAL) does not survive into the next transaction.
2. **Per-tenant DB roles (strongest):** synthesize `sanad_t_<tenant-hash>` roles and set `app.tenant_id` from `current_user` inside the RLS policies (`tenant_id = NULLIF(current_setting('app.tenant_id', true),'')` replaced/augmented by `pg_has_role(current_user, 'sanad_t_'||md5(tenant_id), 'MEMBER')`-style binding); the GUC then becomes advisory, not authoritative. Requires a role-provisioning migration and pooler role routing.
3. **Signed session context:** the application sets `app.tenant_ctx = <tenant|nonce|hmac(key)>`; policies verify the HMAC (definer function, key held by migrator-owned SECURITY DEFINER with locked-down ACL). Rejects any hand-crafted GUC value.

**Verification gate:** new battery probes: `SET app.tenant_id='<foreign>'; SELECT count(*) ...` must return **0** (or 42501) under the chosen design; the existing S4 A/B matrix stays green; a hostile-session simulation suite runs in CI.

**Compatibility/rollback:** option 1 is connection-layer only (no schema change); options 2–3 are schema+app changes needing a compatibility migration window. All keep the current JWT→GUC discipline until the new mechanism is proven.

**Owner decision required:** which option to fund; acceptance that option 1 narrows but does not eliminate the exposure (a compromised in-transaction session can still act inside the tenant it is currently serving — inherent to any per-request context model).

## P1-3 · R-C4-04 — `access_capabilities` DML by runtime (MEDIUM-HIGH)

**Trigger evidence (live):** RT-12 (INSERT `RT.FAKE`, exact count 1), RT-13/13B (UPDATE status → read-back `INACTIVE`), RT-14/14B (DELETE, admin-proven absent) — a compromised runtime session can mint/deactivate/delete authorization-relevant capability codes.

**Objective:** capability-registry DML is control-plane-only.

**Remediation steps:** same `sanad_control_plane` pattern as P0-1 — migration `V20261118_2__access_capabilities_control_plane_acl.sql`: REVOKE `INSERT, UPDATE, DELETE` on `public.access_capabilities` from `sanad`, GRANT to the control-plane role, effective-privilege postconditions asserted in-migration; capability management endpoints adopt the control-plane transaction.

**Verification gate:** RT-12/13/14 flip to `DENIED(42501)` for plain runtime; the RBAC acceptance suite (`rb12_inactiveCapability_denied`) and Y2 sentinel-capability tests stay green on the new path.

**Compatibility/rollback:** shipped together with P0-1 (single control-plane release); rollback via forward-only re-GRANT.

**Owner decision required:** confirm the only intended writer is the platform control plane (doc 17 §2.4 R-C4-04 analysis), then approve.

## P1-4 · R-C4-05 — pre-auth plane: characterization open + plaintext `identifier` (MEDIUM)

**Trigger evidence (live):** RT-04 (distinct tenant count in `login_tenant_index` readable without GUC — exact admin-matching value), RT-05 (`auth_token_tenant_index` readable), doc 16 §2 A / doc 17 §2.1.

**Remediation steps:**

1. Write the pre-RLS characterization contract (doc 15 gate follow-up): for `login_tenant_index` and `auth_token_tenant_index`, state exactly which pre-auth flows read which columns, the maximum exposure window, and the required failure behavior; get owner sign-off.
2. Hash `login_tenant_index.identifier` (email/phone at rest): migration `V20261118_3__login_tenant_index_identifier_hash.sql` — add `identifier_hash bytea` (HMAC-SHA-256 with a migrator-owned key, deterministic for lookup), backfill, dual-read cutover in `g1_resolve_login_tenants`, then drop plaintext in a follow-up migration after one retention cycle.
3. Keep the documented runtime ACL (SELECT/INSERT/DELETE per V20261131_1-era grants) but re-verify the trigger pair (`sync_login_tenant_index`) against the hashed column (C4-F11 open item).

**Verification gate:** battery probes: `SELECT identifier FROM login_tenant_index` returns hash material only (or is revoked); `g1_resolve_login_tenants` still resolves the S5 fixture login exactly (1 row); pre-auth regression suite green.

**Compatibility/rollback:** dual-read cutover avoids a hard break; rollback = keep plaintext column until the second migration (no in-place destructive change).

**Owner decision required:** approve the characterization contract and the hashing key custody (migrator-owned vs. KMS).

## P1-5 · R-C4-03 — audit ledger accepts forged runtime INSERTs (MEDIUM)

**Trigger evidence (live):** RT-26 (forged `RT_FORGED_PROBE` row accepted, exact count 1) + RT-26B (admin field-level verification `id|tenant|action|result`), doc 17 §2.3 — append-only guarantees integrity-against-modification, not per-row authenticity.

**Remediation steps:**

1. Correlation monitoring (detection): alert on audit rows whose `(actor_tenant_id, resource_type)` combination is not produced by a known emitting code path; sampled back-stop query wired into the operations dashboard.
2. Write-role separation (prevention, optional): route audit INSERTs through a definer function `platform_audit_log_emit(...)` (SECURITY DEFINER, migrator-owned, EXECUTE granted to runtime, internals validate actor/correlation invariants and stamp `server_gen` marker); REVOKE direct INSERT on `platform_audit_logs` from `sanad` in a later migration once all emitters are migrated.
3. Extend S9/S10 to assert the emit-path contract if option 2 is adopted.

**Verification gate:** battery: direct INSERT by plain runtime is denied (post-option-2) or, pre-option-2, the monitoring detector flags RT-26-style rows on the disposable cluster in a self-test; S9 append-only matrix unchanged green.

**Compatibility/rollback:** option 2 requires migrating every audit emitter — schedule after P0/P1 ACL work; option 1 is additive and immediately deployable.

**Owner decision required:** detection-only now vs. prevention path; audit retention/custody owner.

## P2-6 · R-C4-06 — default-ACL future-grant growth (MEDIUM)

**Trigger evidence:** `pg_default_acl` for `sanad_migrator` in `public`: functions `f:{sanad=X}`, tables `r:{sanad=arwd}`, sequences `S:{sanad=rU}` (doc 17 §5, S7 snapshot).

**Remediation steps:** after the P0/P1 migrations land, narrow the default ACLs to the actually-required sets (likely `r:{sanad=arwd}`→`ar` for new tables post-control-plane split, `S:{sanad=rU}` kept for serial/identity usage, functions reviewed per C4-F necessity) — first on a disposable replica restored from a production-shape dump (doc 15 gate 3 procedure), comparing the full migration replay + runtime acceptance matrix before/after.

**Verification gate:** G5/G6-style replica replay: Flyway chain applies cleanly and the separation certification passes 75/75 + battery green on a cluster whose default ACLs were narrowed before the replay; diff of effective grants on objects created after the narrowing == expected set exactly.

**Compatibility/rollback:** default ACLs only affect NEW objects — a failed replay is contained on the replica; no production change until the replica run is green.

**Owner decision required:** approve the narrowed grant sets.

## P2-7 · R-C4-07 — definer-function EXECUTE breadth (MEDIUM)

**Trigger evidence:** `sanad` EXECUTE 12/12 definer functions; production `service_role` EXECUTE 11/12 (doc 15 historical read-only facts; production re-verification BLOCKED); doc 17 §3 per-signature table.

**Remediation steps:**

1. Per-function necessity review (12 signatures, C4-F01..F12): for each, record caller inventory (application code paths, triggers, migrations) — functions with no runtime caller lose `sanad` EXECUTE via `V20261118_4__definer_execute_necessity.sql` (topology-aware, postcondition-asserted).
2. Production `service_role` re-verification is a **G1 production-readiness condition** (doc 19 §4): once authorized production access exists, re-capture the production ACL and REVOKE the unjustified 11/12 grants in a production-scheduled migration.
3. Keep the trigger functions (`sync_login_tenant_index`, `sync_workflow_external_action_token_index`) governed by table ownership, not runtime EXECUTE, if the caller inventory shows no direct runtime invocation.

**Verification gate:** battery/definer matrix: HR negative 20/20 + positive 2/2 stay green on the retained set; revoked functions return 42501 to runtime; catalog inventory shows EXECUTE grantee set == the reviewed allow-list exactly.

**Compatibility/rollback:** revocations are forward-only re-GRANTable; run the caller inventory as a static check in CI to prevent drift.

**Owner decision required:** the 12 per-function dispositions and the production re-verification window.

## P3-8 · R-C4-08 — global audit-plane RLS decision (LOW-MEDIUM)

**Trigger evidence:** `platform_audit_logs` is RLS-OFF by documented decision (global audit plane; indiscriminate FORCE RLS explicitly not applied — doc 15 gate 4, doc 17 §2.3).

**Remediation steps:** owner decision memo on the audit read path: (a) keep global with runtime `SELECT` (current; monitoring compensates), (b) FORCE RLS with a tenant-scope policy + separate admin read path, or (c) revoke runtime SELECT and expose reads via a definer reporting function. If (b)/(c): extend S9 accordingly.

**Verification gate:** whichever option is chosen, the battery gains the matching exact-count probe (e.g., cross-tenant audit SELECT returns 0 or is denied).

**Owner decision required:** the option itself; this plan recommends none.

## P3-9 · R-C4-09 — registry per-table necessity sweep (LOW-MEDIUM)

**Trigger evidence:** 20 global registry tables (`modules` … `flyway_schema_history`) with runtime SELECT (and general-set DML where granted) pending per-table necessity review (doc 17 §2.4).

**Remediation steps:** sweep each of the 20 tables: confirm intended reader/writer, narrow runtime grants to the necessity set (SELECT-only for pure registries; DML moved to the control-plane role where P0/P1 already touched it), record one disposition line per table in the closure doc; `flyway_schema_history` stays MIGRATOR-ONLY by privilege (already zero runtime privileges — verified RT-29/29B/30).

**Verification gate:** catalog-gate extension asserting the final per-table grant matrix == the recorded disposition table exactly (fail-closed on drift); full Maven suite green on the merge SHA.

**Owner decision required:** approve the 20-line disposition table.

---

## Sequencing and dependencies

```
P0-1 (tenants control-plane role)  ──┬──> P1-3 (capabilities, same role/release)
P0-2 (GUC enforcement option)      ──┘        │
                                             v
                          P2-6 (default ACL narrowing; AFTER P0/P1 grants settle)
P1-4 (pre-auth hashing)  ──> C4-F11 trigger re-verify
P1-5 (audit emit path)   ──> S9/S10 extension (optional prevention)
P2-7 (definer necessity) ──> production service_role re-verification (G1 condition)
P3-8 / P3-9 (owner decisions, then narrow grants)
```

P0-1 and P0-2 are the release blockers this plan exists for; everything else can follow in the same or subsequent releases. No step in this plan may convert any `PENDING-OWNER` status — completion of a remediation changes the *evidence*, and closure requires the owner's documented disposition per the Owner Risk Acceptance Register rules.

## What this plan is NOT

- Not a risk acceptance, waiver or closure; not a production, migration or payment authorization.
- Not a schedule commitment on behalf of the owner.
- Not a G1 certificate condition fulfillment by itself; G1 remains HOLD until the doc 19 §4 conditions (including #1462 owner dispositions, #1528 CAB approval on the exact SHA, #1530 recovery authorization) are met by the authorized parties.

`P0_SECURITY_HOLD=ACTIVE` · `G1=HOLD` · `PRODUCTION_RELEASE=NO-GO` · `PAYMENTS=OFF`
