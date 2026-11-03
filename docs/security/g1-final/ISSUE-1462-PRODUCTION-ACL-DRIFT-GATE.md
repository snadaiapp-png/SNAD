# Issue #1462 — Production ACL drift remediation (DRAFT)

**State: UNVERIFIED; production NO-GO; payments OFF.** This PR does not authorize deployment.

## Baseline (read-only production observation, 2026-11-03)
Production Flyway latest success: `20261021.1`; role `sanad_migrator` absent; `sanad` NOBYPASSRLS; runtime has UPDATE/DELETE on `public.subscription_commands`; 49 public tables have RLS disabled. These are observations, not acceptance results.

## Existing repository contracts
- `V20261102_2`: append-only `subscription_commands` SELECT/INSERT.
- `V20261103_1`: migrator role contract.
- `V20261103_2`: ownership/runtime ACL separation and topology guard.
- This PR adds `V20261103_3` as a **forward-only** correction. Never edit already-applied migration checksums.

## v2 (2026-11-03, same PR): topology-aware guard
The v1 precondition set (`runtime owns ledger; separation required`) failed closed in
the CI `migration-fixture` topology, where Spring-Boot/Flyway contexts legitimately
run the chain AS `sanad` (owner-as-runner) — P0001 in every context-dependent suite
(Maven, PostgreSQL Acceptance, CRM Integration, g5-g6 and separation certs; CI runs
2026-11-03, PR head 7ea40f3). v2 mirrors the `V20261103_2` topology model:

- `separated`: original strict behaviour — ownership precondition + REVOKE +
  EFFECTIVE-privilege postconditions (`has_table_privilege`).
- `migration-fixture` / unset: loud `SANAD_TOPOLOGY_FIXTURE` WARNING, REVOKE still
  normalizes the EXPLICIT ACL, postcondition asserts the EXPLICIT grant surface via
  `aclexplode` (owner implicit privileges are not ACL-revocable by design — same
  documented caveat as `V20261102_2`; this path is NOT a separation certification
  path).
- unknown topology value: fail closed (P0001 class guard, ERRCODE 22023).

CI companion updates in this PR: expected chain heads `20261103.2 -> 20261103.3` in
`tests/security/g5/run-cert.sh` (scenarios A/B/C) and
`tests/security/migrator-separation/run-separation-cert.sh`; the scenario-C failure
injection file moves to `V20261103_4__inject_failure.sql` (must sit numerically above
the new chain head) and its no-history-row check now pins `20261103.4`.

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
