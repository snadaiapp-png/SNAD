# G1 / C4 — RLS-OFF 26-table re-enumeration and risk-summary correction

> **Provenance note (2026-11-18, PR-2):** this document was authored 2026-11-17 as PR-1
> delivery evidence (`SNAD-G1-C4-PR-DELIVERY` package) but was not part of the merged
> PR-1 tree; it is added to the repository by PR-2 unchanged as the historical record.
>
> **Correction (2026-11-18, PR-2):** the live catalog reconciliation demanded by §1 of this
> document has been executed on the merged chain (`4cee76ad7`, head `20261115.1`). The live
> RLS-OFF set has the same count (26) but a different composition than §2 below:
> **`tenant_quota` is RLS ENABLED + FORCE** (fail-closed tenant policy — own-tenant DML only,
> foreign-tenant INSERT denied 42501), and **`flyway_schema_history`** (runtime-privilege-free)
> is part of the live 26. The authoritative per-table/per-function adjudication is
> [17-C4-RISK-ADJUDICATION-2026-11-18.md](17-C4-RISK-ADJUDICATION-2026-11-18.md) §1–§2;
> the static-replay limitation noted in §1 is the root cause. Historical content below is
> retained unchanged.

**Date:** 2026-11-17  
**Base:** official `main` @ `0988c4f6058fbe46d54e6077ec96176f8d816b89` (PR #1535) + this branch  
**Scope:** Criterion 4 exception review — re-enumeration and classification of the 26 RLS-OFF public tables, correction of the risk-summary count, and the enforcement gates that now cover the audit-ledger exception.  
**Disposition:** `C4_RLS_OFF_REENUMERATION = RECORDED`; `C4_EXCEPTION_DISPOSITIONS = PENDING-DECISION` (not waived); `G1=HOLD`; `PRODUCTION_RELEASE=NO-GO`; `PAYMENTS=OFF`. This document is an evidence-grounded technical record, not a production authorization and not an independent security certificate.

## 1. Method

1. **Static source replay** of the complete versioned migration chain (260 `V*.sql` files across `db/migration` and `db/vendor/postgresql`, ordered by Flyway version, including the dynamic `FOREACH ... EXECUTE format('ALTER TABLE public.%I ... ROW LEVEL SECURITY', ...)` enablement loops in `V20261029_1` and module migrations). Tooling: `scripts/rls_off_inventory.py` logic reproduced for this audit; raw output preserved in the delivery evidence (`rls-off-static-inventory.txt`).
2. **Reconciliation** against the isolated separated-topology PostgreSQL 17 catalog inventory documented in [15-C4-INDEPENDENT-DECISION-PACKET-2026-11-13.md](15-C4-INDEPENDENT-DECISION-PACKET-2026-11-13.md) gate 4 (321 base/partitioned public tables; 295 ENABLE+FORCE; 26 RLS-OFF) and the live CI evidence generator `tests/security/g1-c4/c4-effective-privilege-inventory.sql`.

**Static result:** 320 final tables, 294 ENABLE+FORCE, 26 RLS-OFF. The static count differs from the isolated-catalog count by exactly one table on the RLS-enabled side (320/294 vs 321/295); the RLS-OFF set matches **26/26 exactly**. The one-table delta is a known static-analysis limitation (a table created or renamed inside a conditional/DO block that the replay does not fully resolve); the authoritative count for release decisions remains the **live isolated catalog inventory**, which must be re-captured on this branch's head by the `migrator-runtime-separation` workflow before any disposition change.

## 2. The 26 RLS-OFF tables — re-enumerated and classified

All 26 tables, their creating migration, tenant-scoping, and current disposition. "Runtime ACL" is the effective grant model for `sanad` after `V20261103_2` + `V20261115_1`.

### Category A — Credential / pre-auth plane (3 tables, tenant-bearing)

Runtime holds scoped DML by documented protected-set design. RLS deliberately deferred pending pre-auth path characterization (documented non-silent exception in `V20261029_1`).

| # | Table | Created by | Runtime ACL | Disposition |
|---|---|---|---|---|
| 1 | `auth_token_tenant_index` | V20261028_2 | SELECT, INSERT, UPDATE, DELETE (V20261101_1) | DOCUMENTED-EXCEPTION; pre-RLS characterization contract open |
| 2 | `login_tenant_index` | V20261011_4 | SELECT, INSERT, DELETE (V20261031_1) | DOCUMENTED-EXCEPTION; pre-RLS characterization contract open |
| 3 | `workflow_external_action_token_index` | V20261029_3 | SELECT (read-only, V20261105_2) | DOCUMENTED-EXCEPTION; token-rotation regression (7 cases) green in isolation |

### Category B — Tenant control-plane (2 tables)

| # | Table | Created by | Runtime ACL | Disposition |
|---|---|---|---|---|
| 4 | `tenants` | V1 | SELECT, INSERT, UPDATE, DELETE (general set) | **REVIEW REQUIRED** — flagged in doc 15 gate 4; control-plane entry-point and fail-closed tests required before any RLS/privilege change |
| 5 | `tenant_quota` | V20260706_1 | SELECT, INSERT, UPDATE, DELETE (general set) | **REVIEW REQUIRED** — plus documented `tenant_id` VARCHAR(64) type drift (V20261029_1) |

### Category C — Audit ledger (1 table, tenant-bearing)

| # | Table | Created by | Runtime ACL | Disposition |
|---|---|---|---|---|
| 6 | `platform_audit_logs` | V17 | **SELECT, INSERT only — append-only enforced by `V20261115_1`** (UPDATE/DELETE/TRUNCATE revoked) | HARDENED (append-only). RLS decision **PENDING** (global audit plane; indiscriminate FORCE RLS is explicitly not applied — doc 15 gate 4). Enforced by S9 (certification) and S10 (protected-deployment gate) |

### Category D — Global platform registries / catalogs (20 tables, no `tenant_id` by design)

Control-plane scope: platform-wide reference data, product/plan registries and RBAC capability registry. `anon`/`authenticated` effective SELECT is denied on all 26 (verified live, doc 15 gate 4); runtime DML is the control plane's own write path.

| # | Table | Created by |
|---|---|---|
| 7 | `modules` | V20260814_1 |
| 8 | `module_capabilities` | V20260814_2 |
| 9 | `applications` | V20260829_1 |
| 10 | `products` | V20260829_2 |
| 11 | `plan_versions` | V20260829_2 |
| 12 | `prices` | V20260829_4 |
| 13 | `country_currencies` | V20260829_4 |
| 14 | `product_entitlements` | V20260829_4 |
| 15 | `plan_module_entitlements` | V20260814_2 |
| 16 | `saas_plans` | V19 |
| 17 | `saas_plan_entitlements` | V19 |
| 18 | `system_services` | V18 |
| 19 | `platform_countries` | V20260904_2 |
| 20 | `hr_country_packs` | V20260905_12 |
| 21 | `hr_compliance_rules` | V20260905_12 |
| 22 | `protected_system_roles` | V20261001_9 |
| 23 | `application_iam_contracts` | V20261005_1 |
| 24 | `partners` | V20261003_2 |
| 25 | `access_capabilities` | V7 |
| 26 | `usage_metrics` | V20260830_2 |

Category D disposition: **DOCUMENTED-EXCEPTION, per-table necessity review PENDING** — each registry still requires the owner/privilege-necessity/control-plane-entry-point review demanded by doc 15 gate 4. `usage_metrics` is a metric-definition registry (code/unit/aggregation), not tenant usage data; its global scope is by design but remains part of the same pending review sweep.

## 3. Risk-summary count correction ("the 8")

A risk summary citing **8** residual risks was delivered on 2026-11-16 as part of the `SNAD-G1-C4-PR-DELIVERY-2026-11-16` package, referencing commits `8bc6395d7196a8d7ef67a1edae721cbc194af02f` (PR-1) and `ec6a67d528677dd466ad10768f1bdf3c4e6fde0d` (PR-2). Verification on 2026-11-17 against the official repository `snadaiapp-png/SNAD`:

- Both SHAs are **absent from every remote ref** (all 2391 refs: branches, tags and pull-request heads, enumerated server-side via `git ls-remote`).
- The delivery package itself is **not attached** to the executing environment and could not be imported.
- No document in the official repository records a residual-risk count of 8.

**Correction:** the figure 8 is **not adoptable**. It is unverifiable against the official chain of custody. The authoritative residual-risk status for C4 remains what the repository itself evidences: per-table dispositions above are `PENDING-DECISION` or `DOCUMENTED-EXCEPTION`, and **no residual risk in this document is accepted**. Per [SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14](../../governance/SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14.md), any risk acceptance requires a documented decision by the authorized project owner with an actual compensating control and a time-bound remediation plan; none is recorded here.

## 4. Enforcement now in place (this branch)

1. **`V20261115_1__platform_audit_logs_append_only_runtime_acl.sql`** — topology-aware, forward-only; revokes runtime UPDATE/DELETE/TRUNCATE on `platform_audit_logs` in the separated topology with effective-privilege postconditions; fail-closed on unknown topology; validate-only in the disposable fixture topology.
2. **S9 (separation certification, `run-separation-cert.sh`)** — runtime append-only enforcement: live identity contract (`current_user`/`session_user`, restricted-role flags), exact membership inventory, effective ACL matrix (`t|t|f|f|f`) on **both** append-only ledgers, inherited-privilege bypass census, and live DML proof (INSERT allowed; UPDATE/DELETE/TRUNCATE denied with SQLSTATE 42501).
3. **S10 (protected deployment enforcement)** — `scripts/production/verify-append-only-gate.sh` wired into `production-release.yml` after the Flyway compatibility verification; fails closed unless the V20261115_1 security effect is present (database-level `separated` topology from `pg_db_role_setting`, session/catalog consistency, restricted runtime contract, migrator-owned ledgers, Flyway 20261115.1 recorded — necessary never sufficient — effective append-only ACLs, and zero member-role ledger-write privileges). Self-tested on an isolated cluster across the 7 required cases (separated, migration-fixture, missing/empty/unknown topology, session-level override, inherited-privilege bypass + restoration) by `tests/security/g1-c4/run-s10-protected-deployment-gate.sh`, integrated into `migrator-runtime-separation.yml`.

## 5. What is explicitly NOT claimed

- No production catalog was inspected; no production mutation, release dispatch, signature or payment change occurred.
- The static 320-vs-321 one-table delta is disclosed, not masked; the live isolated inventory on this head is the required reconciliation step (CI).
- `C4_EXCEPTION_DISPOSITIONS` remain pending; nothing here converts a PENDING or HOLD status into PASS.
- Local execution on a non-CI runner (PostgreSQL 17.12 deviation, unprivileged user-local cluster) is recorded as evidence rehearsal; the authoritative certification run is GitHub CI on the exact merge SHA.
