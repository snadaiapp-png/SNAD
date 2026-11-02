# SIX-TABLE-RLS-ACCEPTANCE-MATRIX.md

**Tested tree:** PR #1465 head `d0694a00f…` (migration content = exactly what is on the branch). **Environment:** isolated PostgreSQL 17.6, Flyway 11.7.2, fresh full chain (250 migrations) applied as migrator `postgres`, restricted runtime role `sanad LOGIN NOSUPERUSER NOBYPASSRLS` created before migrations (per chain precondition). Probes run **as `sanad`** with transaction-local `app.tenant_id` GUC unless stated. Raw logs: `evidence-raw/sixtable-matrix-GREEN-v2.log`, `flyway-rehearsal-FINAL.log`.

**Result: 57/57 PASS (GREEN)** after remediation of one **newly discovered defect** (§3).

## 1. Defect found by this audit (RED → fixed)

The first matrix execution produced **5 PASS / 47 FAIL** with `permission denied for table provisioning_jobs / provisioning_job_steps / subscription_commands` as `sanad`: **`V20261031_1` (T2 batch 2) applied ENABLE+FORCE RLS and policies but granted zero table privileges to the restricted runtime role.** This is the same masked-gap class that PR #1465's `V20261102_1` fixed for the token tables (migration-owner-as-runtime fixtures never observed it). Fail-closed, but it breaks provisioning/subscription availability for the security-preferred runtime identity.

**Remediation (RED→GREEN, in the same PR branch, no history rewrite):**
- `V20261102_2__t2_batch2_runtime_grants.sql` — evidence-derived least privilege from a call-site audit of `apps/sanad-platform/src/main/java` at this head:
  - `provisioning_jobs`: SELECT, INSERT, UPDATE (INSERT: `LifecycleController` provision/retry; UPDATE: `ProvisioningJobRunner` transitions; **no DELETE call site → not granted**)
  - `provisioning_job_steps`: SELECT, INSERT, UPDATE (upsert `ON CONFLICT DO UPDATE` in `recordStep()`; **no DELETE call site → not granted**)
  - `subscription_commands`: SELECT, INSERT (ledger write `SubscriptionChangeService`, history read `SubscriptionDetailService`; **append-only — no UPDATE/DELETE call sites → neither granted**)
  - Preconditions (restricted `sanad` required; tables must exist), exit guard re-asserts exact ACL surface + FORCE RLS, and in the separate-owner deployment model asserts the ledger stays append-only (guard scoped to `current_user <> 'sanad'` — owner-as-`sanad` fixture databases legitimately hold owner privileges; documented in-migration).
- No REVOKE, no policy change, no RLS change, no new role, no SECURITY DEFINER, no BYPASSRLS. Forward-only.

## 2. Per-table matrix (post-fix, 57 probes)

| Requirement | refresh_tokens | password_reset_tokens | login_tenant_index | provisioning_jobs | provisioning_job_steps | subscription_commands |
|---|---|---|---|---|---|---|
| Exists / real name / owner | ✔ (`postgres`) | ✔ | ✔ (kind values `email`/`username` verified live) | ✔ | ✔ — **real name `provisioning_job_steps`**; no `provisioning_steps` table exists anywhere in the chain | ✔ |
| RLS state | ENABLE+FORCE | ENABLE+FORCE | **No RLS by documented pre-auth design** (V20261011_4 contract) + ACL closure | ENABLE+FORCE | ENABLE+FORCE (policy joins parent job; guard asserts no `tenant_id` column) | ENABLE+FORCE |
| Policy shape | fail-closed `NULLIF(current_setting('app.tenant_id',true))` + not-null guard | same | n/a (ACL model) | fail-closed NULLIF | parent-join EXISTS, same GUC | fail-closed NULLIF |
| Runtime ACL (`sanad`) | SELECT/INSERT/UPDATE/DELETE (`V20261102_1`, trigger-synchronisation needs all four) | same | SELECT/INSERT/DELETE; **UPDATE revoked** | SELECT/INSERT/UPDATE | SELECT/INSERT/UPDATE | **SELECT/INSERT only** (append-only) |
| A reads own / A reads B | ✔ 1 / ✔ 0 | ✔ / ✔ | n/a (pre-auth plane) | ✔ / ✔ | ✔ / ✔ (direct + via parent) | ✔ / ✔ |
| A mutates B (upd/del/ins) | ✔ 0 rows, 0 rows, 42501 | ✔ | UPDATE as sanad → 42501 | ✔ | cross-tenant step insert → 42501 | ✔ (UPDATE/DELETE → 42501 by ACL, cross-tenant INSERT → 42501 by RLS) |
| Missing GUC / empty GUC / invalid tenant GUC | ✔ 0 / ✔ 0 / ✔ 0 | ✔ | n/a | ✔ / n/a | ✔ | ✔ |
| Revoked token not resolvable (`status='ACTIVE'` + `expires_at>now()` filter) | ✔ | expired reset token ✔ | n/a | — | — | — |
| Identity immutability (trigger contract) | ✔ `23514 AUTH_TOKEN_IDENTITY_IMMUTABLE` on hash change | — | — | — | — | — |
| Pool-recycled connections (transaction-local GUC; new txn without GUC sees 0) | ✔ `2|0|0` | ✔ | ✔ | ✔ | ✔ | ✔ |
| Pre-auth discovery without GUC | ✔ via `auth_token_tenant_index` (hash→tenant) | ✔ | ✔ `login_tenant_index` email→tenant | — | — | — |
| Workers / background paths | sync trigger (INVOKER) | same | users sync trigger (DEFINER, pinned `search_path`) | runner request-scoped; background uses tenant-local GUC per V20261031_1 notes | upsert under job context | dunning/expiry per-tenant GUC pattern |
| Concurrency (A and B sessions simultaneously) | ✔ 2 vs 1 | — | — | — | — | — |
| Privilege hygiene | no TRUNCATE/REFERENCES/TRIGGER to `sanad`; ALTER TABLE → 42501; direct trigger-fn call → 42501; PUBLIC schema CREATE revoked; 12 SECURITY DEFINER functions all with pinned `search_path`; definer token functions pinned | | | | | |

## 3. Accepted residual (documented, out of P0 scope)

`login_tenant_index` stores the login identifier in plaintext by design (possession of DB access already implies `users` access); hashing to the `auth_token_tenant_index` pattern is the recommended follow-up. `sanad` can write forged rows into the two pre-auth index tables, but every downstream read is re-verified under FORCE RLS against the token/user tables with the GUC derived from the mapping — forged mappings cause fail-closed rejection, not cross-tenant reads (verified: `PA04`).
