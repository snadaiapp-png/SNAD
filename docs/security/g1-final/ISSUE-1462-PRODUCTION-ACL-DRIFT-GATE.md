# Issue #1462 — Production ACL drift remediation (DRAFT)

**State: UNVERIFIED; production NO-GO; payments OFF.** This PR does not authorize deployment.

## Baseline (read-only production observation, 2026-11-03)
Production Flyway latest success: `20261021.1`; role `sanad_migrator` absent; `sanad` NOBYPASSRLS; runtime has UPDATE/DELETE on `public.subscription_commands`; 49 public tables have RLS disabled. These are observations, not acceptance results.

## Existing repository contracts
- `V20261102_2`: append-only `subscription_commands` SELECT/INSERT.
- `V20261103_1`: migrator role contract.
- `V20261103_2`: ownership/runtime ACL separation and topology guard.
- This PR adds `V20261103_3` as a **forward-only** correction. Never edit already-applied migration checksums.

## Execution order and hard stop
1. Obtain independent reviewer approval of the **specific production change**, role and ownership topology, recovery prerequisites, and execution window.
2. Run `flyway info` and `flyway validate` on the exact deployment artifact; reconcile the production history from `20261021.1` through `20261103_2` in a disposable PG17 database with matching topology. Never baseline, repair, or skip without a separately reviewed plan.
3. Prove backup and isolated restore, encrypted at rest and authenticated over TLS. Verify data counts, owners, ACL, RLS, Auth/Storage scope and integrity.
4. Run migration chain and A/B direct PostgreSQL tests under **real restricted runtime credentials**, not postgres or a BYPASSRLS role.
5. Check `sanad` has SELECT/INSERT but not UPDATE/DELETE on ledger, with effective inherited grants included; confirm table owner is not sanad.
6. Test tenant A sees A and not B; tenant B sees B and not A; absent tenant GUC fails closed; SELECT/INSERT/UPDATE/DELETE and WITH CHECK negatives are enforced on all relevant tables; test login/refresh/provisioning/subscriptions.
7. Attach real SQLSTATE, runner logs, CI Maven/Surefire, exact SHA, post-merge and G1 evidence to #1462. Any failure/skip in a mandatory gate = HOLD.

## Rollback/recovery
PostgreSQL Flyway transactional DDL rolls back the migration on failure. **After commit**, do not undo a security REVOKE merely to restore availability; halt deployment and use reviewed forward-only remediation. If production data recovery is necessary, use the independently validated isolated restore procedure and explicit incident authorization. No fabricated PASS.

## Approval criteria
Only a reviewed exact-SHA PR, real isolated tests, production-safe backup/restore proof, protected deployment, and successful post-deployment verification can support G1. This PR alone does not grant G1 certification or payment activation.
