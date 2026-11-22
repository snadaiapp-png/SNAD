# G1 / C4 — ORDER 11: Control Plane Security & PostgreSQL Root-Cause Remediation

**Date:** 2026-11-21 · **Branch:** `security/g1-final-closure-remediation` (from official `main` @ `5b01882aebbf9c948e166676e589038f2b743bfe`)
**Basis:** owner executive order 11 (Control Plane Security & PostgreSQL Root-Cause Remediation) and the live failure chain of the drafted PR-2 P0-1/P1-3 delivery (V20261118_1/_2 first draft, uncommitted).
**Disposition:** `REMEDIATION = DELIVERED (this document + code + evidence)` — no risk is closed, waived or converted; every owner decision remains PENDING. `G1=HOLD` · `PRODUCTION=NO-GO` · `PAYMENTS=OFF`.

---

## 1 · Task 1 — Root-cause diagnosis (proven, live evidence)

**Failing tests:** `ExecutiveTenantProvisioningDefaultDomainPostgresTest.provisioningIsIdempotentOnTheDefaultDomainRow` and `.provisioningComposesDefaultGeneratedApplicationDomainFromBaseDomain` (2 errors).

**First PostgreSQL error (the root):** `ERROR: permission denied for table tenants` — SQLSTATE **42501**, raised on

```sql
UPDATE tenants SET name = $1, ... WHERE id = $10
```

executed with `session_user = sanad`, `current_user = sanad_control_plane` (a successful `SET ROLE sanad_control_plane` preceded it — membership `WITH INHERIT FALSE, SET TRUE` existed in the first draft).

**Surfaced error (the follow-on):** `SQL state [25P02]; current transaction is aborted, commands ignored until end of transaction block` on the `finally`-block `RESET ROLE` compensation — the 42501 had already aborted the transaction, so the compensation itself failed and the outer transaction rolled back.

**Privilege proof (isolated reproduction):**

| probe | result |
|---|---|
| `UPDATE tenants SET updated_at = now()` under `SET ROLE` (no WHERE) | `UPDATE 1` — UPDATE privilege present |
| `SELECT count(*) FROM tenants` under `SET ROLE` | `ERROR 42501 permission denied` — **SELECT absent** |
| ACL: `tenants` | `{sanad=arwdDxt/sanad, sanad_control_plane=awd/sanad}` — `awd` = INSERT/UPDATE/DELETE, **no `r` (SELECT)** |

**Root cause:** PostgreSQL requires SELECT privilege for the WHERE predicate of an UPDATE; the drafted control-plane grant omitted it, so every WHERE-keyed lifecycle statement failed with 42501, and the aborted transaction turned the RESET ROLE compensation into a 25P02. The migrations' postconditions only asserted INSERT/UPDATE/DELETE — never SELECT — which is why the gap reached the live run.

**Architectural violation (Order 11, Task 2):** the drafted design *itself* is forbidden — a separation resting on runtime **membership** in the administrative role with `SET ROLE` capability is no separation: any compromised runtime session could execute `SET ROLE sanad_control_plane` directly, with no authorization gate (the drafted `ControlPlaneRoleExecutor` javadoc admitted it was "NOT AN AUTHORIZATION MECHANISM").

## 2 · Task 2 — The dedicated-connection model (delivered)

**Contract (enforced fail-closed by V20261118_1/_2 in BOTH topologies):**

1. `sanad_control_plane` is a **dedicated LOGIN connection identity** — `NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS`, password provisioned ONLY by the deployment environment (CI per-run ephemeral secret, docker-init env var, runbook secret store). The migration chain never creates roles or credentials (V20261103_1 precedent).
2. **No membership in any direction** between `sanad` and `sanad_control_plane`, and none from the control-plane identity to `sanad_migrator` — asserted in preconditions AND re-asserted in postconditions after every ACL mutation. The runtime `SET ROLE sanad_control_plane` escalation path is **structurally absent**.
3. The identity holds exactly **SELECT, INSERT, UPDATE, DELETE** on `tenants` and `access_capabilities` (SELECT is the WHERE-keyed DML requirement — the proven 42501 root cause).
4. Separated topology: runtime keeps SELECT only (documented subdomain-resolution requirement); INSERT/UPDATE/DELETE/TRUNCATE revoked. Fixture topology: validate-only WARNING for the runtime revoke (owner-as-runner, documented skip class), dedicated-identity grant still applied, membership-absence contract still enforced.
5. Connection-level access (CONNECT on the database, USAGE on schema public) is granted idempotently by the chain (the runner owns the database in both certified topologies).

**Application layer:** `ControlPlaneRoleExecutor` (SET ROLE) is **deleted**. `ControlPlaneDataAccess` executes tenant/capability DML on a **dedicated Hikari pool** (`SanadControlPlaneHikariCP`) bound to `sanad.control-plane.datasource.*` (env `SANAD_CONTROL_PLANE_DB_URL/_USERNAME/_PASSWORD`). Credentials are isolated from the runtime configuration; when absent the executor **fails closed** — it never falls back to the runtime pool (that would silently recreate the forbidden shared-identity design). No `SET ROLE` exists anywhere in the approved path.

**Call sites rewritten** to the dedicated connection: `ExecutiveTenantProvisioningService` (profile UPDATE + trial mirror), `ExecutivePlatformService` (profile + status UPDATE), `AdminPlatformService` (status UPDATE), `RegistrationProvisioner` (tenant INSERT), `CredentialBootstrapService.ensureTenant` (tenant INSERT).

**Cross-connection atomicity:** the dedicated connection commits independently of the surrounding runtime transaction (no XA on this platform). `RegistrationProvisioner.provision` registers a rollback-synchronized compensating DELETE on the control-plane connection: after a runtime-transaction rollback (its FK children are gone by then), the freshly created tenant row is removed, restoring effective single-transaction atomicity for the common failure modes and protecting the unique subdomain from permanent consumption. Best-effort failures are logged loudly and are operationally detectable (ACTIVE tenant with no organization/user rows). `CredentialBootstrapService.ensureTenant` is naturally self-healing (findBySubdomain reuse path).

## 3 · Task 3 — Verification (live, fail-closed)

| # | Required proof | Gate | Result |
|---|---|---|---|
| 1 | Runtime INSERT/UPDATE/DELETE = DENIED | separation cert S2 (`S2_TENANTS_*_DENIED`, `S2_CAPS_*_DENIED`) + battery RT-07/08/09, RT-12/13/14 | **DENIED(42501), 12/12** |
| 2 | Runtime SET ROLE control plane = DENIED | cert `S2_SETROLE_CP_DENIED` + battery RT-39 + `ControlPlanePoolIsolationPostgresTest.runtimeSetRoleToControlPlaneIsDenied42501` | **DENIED(42501), 3/3** |
| 3 | Control-plane authorized DML = PASS | cert `S2CP_*` (dedicated connection, exact lifecycle + runtime read-backs) + battery RT-36/RT-37 + Java test `controlPlaneAuthorizedWhereKeyedDmlPassesOnDedicatedConnection` | **PASS, all** |
| 4 | Cross-tenant escalation = DENIED | cert S4 (tenant A/B isolation, GUC fail-closed) + battery A/B probes | **DENIED/0-row, all** |
| 5 | Connection-pool privilege leakage = DENIED | cert `S2CP_CONNECTION_IDENTITY` + `S2CP_RUNTIME_IDENTITY_UNCHANGED` + battery RT-40/RT-40B + Java `runtimePoolNeverHoldsControlPlaneIdentity` | **PASS — identities isolated in both directions** |
| 6 | Flyway migration & validation = PASS | cert S1 (as `sanad_migrator`, head `20261118.2`, validate OK) + `CrmPostgresMigrationTest`/`CrmFlywayHistoryAssertionTest` | **PASS** |
| 7 | Security certification + red-team battery | `run-separation-cert.sh` (96 probes, S0–S10) + `redteam-attack-surface-battery.sh` v4 (79 probes) | **PASSED / COMPLETE, 0 failures** (re-run bound to the final SHA at delivery) |
| 8 | Full Maven suite | `mvn test` on the final code | see the delivery evidence pack (order11-mvn-full-suite.log + Surefire reconciliation) |

The previously failing WS6 tests now PASS (2/2). The recovered failure chain is preserved in this document and in the ORDER 11 diagnosis record (`order11-diagnosis.txt` in the evidence pack).

## 4 · Documented residual items (owner decisions)

1. **`sanad_callback_replay_cleanup` SET ROLE pattern (V20261111_1)** remains in `CallbackReplayStore` — a narrow, pre-existing, committed compensating control for the callback-replay ledger, out of Order 11's tenant/capability scope. A future order may migrate it to the dedicated-connection model for uniformity.
2. **pgjdbc driver behavior:** the PostgreSQL JDBC driver itself issues `SET ROLE 'sanad'` (the connection's own login role) during read-only state management after an `rds_superuser` catalog probe. This is identity-preserving (own role), not an escalation; certification asserts `current_user`/`session_user` and forbids the control-plane identity from ever appearing on the runtime pool.
3. **Cross-connection atomicity** (§2) is compensation-based, not XA; the residual crash-window (JVM death between tenant INSERT and runtime rollback) is detectable and documented.
4. RT-26 (forged audit-row INSERT) remains a CONFIRMED, accepted-by-plan exposure (R-C4-03, doc 20) — unchanged by this order.

`G1=HOLD` · `PRODUCTION=NO-GO` · `PAYMENTS=OFF` — final certification requires the owner risk decisions, the production evidence, and the approvals defined in the closure gates (#1462, #1528, #1530, #1481).
