# 04 — AUTH, USERS, SUBSCRIPTIONS & WORKERS E2E ACCEPTANCE (Phase 3)

**Exact SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe`
**Environment:** the same isolated chain-only PostgreSQL 17.6 database (`rehearsal_final_a`) used in 03 — i.e., the environment **matches the chain-mandated runtime model**: control-plane/bootstrap operations as migrator (`postgres`), all application runtime operations as the restricted role `sanad` with transaction-local `app.tenant_id` (exactly how the application connects per `SPRING_DATASOURCE_USERNAME: sanad` in CI and `DATABASE_USERNAME` in the supported compose/Render topologies).
**Driver:** `scripts/finalclosure_e2e_lifecycle.py` — 38 lifecycle assertions.
**PAYMENTS=OFF maintained:** no payment processor was invoked anywhere; payment-side tables were asserted untouched before/after (`billing_invoices`, `subscription_billing_payment_attempts`, `finance_payments`, `bp_payment_events` all `0|0|0|0` → unchanged).

## 3.1 Result on the pure chain (no out-of-band anything)

`RESULT: PASS=36 FAIL=2 → E2E=RED(2)` — log `finalclosure-e2e-lifecycle-pure-chain.log`.

The two failures are **one real finding**, not noise:

### Finding F-01 (P2 — availability under the split-identity topology, fail-closed, no data exposure)

- **Symptom:** as `sanad`, `SELECT` on `public.tenant_subscriptions` → `permission denied` (SUB01/SUB02).
- **Root cause:** the migration chain grants `sanad` exactly eight tables (03 §2.0). `tenant_subscriptions` — the subscription aggregate root read/written by `SubscriptionChangeService` (native `UPDATE` at line 364, `SELECT` at 470/485), `SubscriptionCommandService` (UPDATE ×3), `BillingStateService`, and the read services — has **no runtime grant anywhere in the chain**. This is the **same masked-gap defect class** that `V20261102_1`/`V20261102_2` (PR #1465) fixed for the token/provisioning/command planes: the migration owner bypasses ACLs via ownership, so CI fixtures running in the owner topology never observe it.
- **Why CI is green anyway (topology evidence):** CI provisions the app role as **database owner** (`CREATE DATABASE sanad OWNER sanad; ALTER DATABASE sanad OWNER TO sanad` in `post-merge-verification.yml`/`ci.yml`), so ACLs are implicit. Render runs Flyway **as** `DATABASE_USERNAME` at startup (`FLYWAY_ENABLED=true`, `render.yaml`), which makes the runtime role the table owner on fresh databases — again the owner topology. The gap only materializes in the **split topology** (migrator superuser + restricted app role — the Supabase-style model), where the application is **fail-closed**: every subscription read/write is denied; nothing leaks.
- **Impact:** availability-only, and only for deployments that choose the split topology with a role that has no business-table ACLs. Isolation is not weakened anywhere (all six tables remain FORCE-RLS'd, policies `TO public`, proven in 03).
- **Fix contract (verified locally, see §3.2):** `GRANT SELECT, INSERT, UPDATE ON TABLE public.tenant_subscriptions TO sanad;` — derived from a full call-site audit at f95a30b6: DELETE has **zero** call sites (not granted), INSERT exists in `SaasAdministrationService` + workflow E2E bootstrap only, UPDATE in the three lifecycle/billing services. Recommended as a follow-up migration (`V20261102_3`-class, same precondition/exit-guard pattern as V20261102_1/2) **in a separate authorized PR after this closure cycle** — deliberately NOT merged by this command because a new migration would shift the certification SHA mid-cycle while the post-merge run for `f95a30b6` is still queued (02 §1.1).

## 3.2 Fix-contract verification (out-of-band, clearly labeled — NOT part of the chain)

A clone of the chain DB (`e2e_fixed`, server-side `CREATE DATABASE … TEMPLATE`) received exactly the F-01 grant above; nothing else changed. Re-run of the identical 38-test driver:

`RESULT: PASS=38 FAIL=0 → E2E=GREEN` — log `finalclosure-e2e-lifecycle-F01fix-verification.log`.

This proves the finding's fix contract is complete and minimal. It does **not** prove the fix is in the chain — that requires the follow-up PR + CI.

## 3.3 What the 38 tests prove (lifecycle coverage demanded by the command)

**Users & authentication (AUTH01–AUTH18):**
1. pre-auth tenant discovery resolves `user@a.test → tenant A` via `login_tenant_index` with no GUC (login before authentication);
2. session issue as runtime role under tenant GUC succeeds; `auth_token_tenant_index` auto-synced;
3. session restore: valid token resolvable by owner tenant only; invisible to tenant B;
4. refresh rotation in one transaction (new ACTIVE + old → USED): old token unresolvable, new token resolvable — no reuse window;
5. revocation: `REVOKED` token unresolvable;
6. expiry: expired-but-ACTIVE token unresolvable;
7. cross-tenant token insert refused `42501` (WITH CHECK);
8. password reset: issue → resolve → consume (`USED`) → reuse unresolvable; foreign-tenant invisible;
9. repeated miss-lookups are bounded in time (10 lookups ≪ 5 s) — the data-layer contract behind the "login UI hangs after 401" regression: fail-closed results return immediately (no row-lock/scan pathology at this schema).

**Subscriptions (SUB01–SUB08, PAYMENTS=OFF):**
- trial subscription visible to own tenant only; foreign invisible;
- suspend/reactivate executed through the **append-only command ledger** (two `subscription_commands` rows, ordered history);
- foreign actor appending a command for tenant A's subscription → `42501`;
- ledger UPDATE → `42501`, DELETE → `42501` (append-only enforced at ACL level, stronger than policy);
- payment-side tables byte-identical before/after (`0|0|0|0`) — no unauthorized financial artifact.

**Provisioning workers (WRK01–WRK12):**
- job create (`PENDING`) as runtime role; foreign tenant sees nothing;
- worker state machine transitions (`RUNNING`→ steps → `SUCCEEDED`; `RETRYING` transition enum-bound);
- step records constrained to jobs within tenant scope;
- job/step **DELETE denied** (`42501` — not granted; workers finalize, never erase);
- transactional rollback probe: step insert + job update then `ROLLBACK` leaves **zero** trace;
- worker privilege boundary: no TRUNCATE/REFERENCES/TRIGGER grants; `rolsuper=false, rolbypassrls=false`.

## 3.4 Verdict

- **Auth/User lifecycle: PASS** (18/18) on the pure chain.
- **Provisioning workers: PASS** (12/12) on the pure chain.
- **Subscription lifecycle: PASS with finding F-01** — the ledger-plane (in scope) is fully green on the pure chain; the aggregate-table read/write works in the owner topology and fails closed under the split topology; fix contract verified (§3.2).
- **E2E_PHASE3_STATUS: PASS_WITH_FINDING(F-01, P2, fix-verified, follow-up PR required)** — no certification blocker within the six-table gate; F-01 is recorded for the release decision in 10/11.
