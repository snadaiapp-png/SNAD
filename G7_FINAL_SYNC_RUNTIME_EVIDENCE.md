# G7_FINAL_SYNC_RUNTIME_EVIDENCE

**Date:** 2026-08-12 · **Status: ⛔ SYNC_RUNTIME = BLOCKED** · No secrets printed.

## Runtime access
The 20 approved sync scenarios require the running backend (HTTP pull/push) **and** PostgreSQL (PRE/POST DB state). Neither is available → no scenario produced INPUT→HTTP→DB-before→DB-after→PASS/FAIL evidence.

## 20-scenario status
| # | Scenario | Status |
|---|----------|--------|
| 1 | Initial pull | BLOCKED |
| 2 | Incremental pull / cursor advancement | BLOCKED |
| 3 | Push (per-mutation ACK) | BLOCKED |
| 4 | Retry / partial batch | BLOCKED |
| 5 | Duplicate idempotency key | BLOCKED |
| 6 | Optimistic-lock conflict (412) | BLOCKED |
| 7 | Version mismatch | BLOCKED |
| 8 | Conflict C1 (same field) | BLOCKED |
| 9 | Conflict C2 (stale non-overlap) | BLOCKED |
| 10 | Conflict C3 (delete-vs-update) | BLOCKED |
| 11 | Conflict C4 (update-vs-delete) | BLOCKED |
| 12 | Conflict C7 (non-overlap merge) | BLOCKED |
| 13 | Conflict C9 (stale) | BLOCKED |
| 14 | Conflict C10 (cross-tenant) | BLOCKED |
| 15 | Conflict recording (`mobile_conflict_log`) | BLOCKED |
| 16 | Tenant isolation | BLOCKED |
| 17 | Unauthorized / forbidden | BLOCKED |
| 18 | Invalid mutation / ordering | BLOCKED |
| 19 | Replay | BLOCKED |
| 20 | Recovery | BLOCKED |

Mobile-side sync/conflict logic is unit-verified (`jest` 52/52 incl. conflict-resolver & push-sync suites) — but the **server + DB half of each scenario is BLOCKED**. No scenario counts as PASS without DB before/after evidence.

## Unblocking
Backend + PostgreSQL up → execute each scenario recording PRE_STATE / REQUEST / HTTP_RESPONSE / POST_STATE / DB_EFFECT / TENANT / VERSION / IDEMPOTENCY_RESULT / CONFLICT_CLASS.
