# FINAL RELEASE DECISION — P0 RLS closure batch 2

```text
P0_SECURITY_HOLD = ACTIVE
G1_CERTIFICATION = HOLD   (latest G1 run #27 SUCCESS is bound to sha 1d80c573, 2026-10-19 — predates 5e854ef; re-certification required after merge + protected deployment)
PAYMENTS = OFF            (code-level BillingProviderModeGuard verified; release-pipeline negative smoke verified)
PRODUCTION_RELEASE = NO-GO
```

## Decision

**NO-GO for protected deployment in this batch.** Per mandate Articles 11-12-14, deployment requires: terminal-green CI on the exact SHA (runs 41221313667 / 41221313694 were still IN_PROGRESS at last check; Maven Test Suite and Post-Merge JOB C/JOB D not terminal), independent security review approval, protected-release authorization token, and G1 re-certification bound to the new SHA. None of these exist yet. Nothing in this branch bypasses any gate.

## What this batch delivers for the gate sequence

1. Forward-only migration `V20261031_1` with fail-closed preconditions + in-migration catalog verification.
2. Worker access-path repair (`TrialExpirationService`) required by the FORCE RLS state #1460 already created on `tenant_subscriptions`.
3. Real PostgreSQL-direct evidence (RED → GREEN) captured on an isolated instance as role `sanad`.
4. T2 dispositions for all six Issue-#1462 tables (two via PR #1458, four here — see T2-SIX-TABLE-CLOSURE.md).

## Required before GO (checklist)

- [ ] Maven Test Suite + Post-Merge Verification terminal-green on merge SHA
- [ ] PR CI green on this branch (incl. CrmFlywayHistoryAssertionTest / CrmPostgresMigrationTest with the registered 20261031.1 version)
- [ ] Independent reviewer approval (mandate Article 11) — executor self-review does not substitute
- [ ] PR #1458 merge coordination: batch-1 registers `20261028.2` as LATEST_MIGRATION_VERSION; whichever of the two batches merges second must register BOTH versions in order (20261028.2 → 20261031.1)
- [ ] Protected deployment via the authorized chain only (PRODUCTION-RELEASE-AUTHORIZED contract)
- [ ] G1 re-run bound to the deployed SHA
