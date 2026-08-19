# G7 Mobile Offline Foundation — Correctness Hardening Plan

Date: 2026-08-19
Branch: `g7-correctness-hardening-20260819`
Baseline: `41d5eecb4efaecbb5e77e9e0f559cf190534f31b`

## Objective

Repair the G7 mobile-offline synchronization foundation without expanding product scope. The branch must stop before merge to `main` and before deployment.

## Non-negotiable invariants

1. Delta pull must never skip committed changes because multiple rows share the same row version.
2. Initial/bootstrap pull must include rows that existed before G7 and therefore have `sync_version = 0`.
3. `sync_version` remains a row-level optimistic-concurrency token; the delta cursor is a separate monotonic change-sequence.
4. A failed mutation in a push batch must not poison or roll back independent mutations.
5. UPDATE mutations must bind each payload value as its own JDBC parameter.
6. Conflict skip/defer must use conflict status, not an invalid resolution value.
7. All seven syncable CRM entity tables must participate consistently in row-versioning and change capture.
8. Tenant isolation must remain fail-closed under PostgreSQL RLS/FORCE RLS.
9. Existing API contracts remain backward compatible unless correctness requires a migration-safe extension.
10. No merge or deployment is performed from this branch.

## Execution sequence

### Phase 1 — Regression characterization
- Add focused regression tests for UPDATE parameter binding.
- Add tests for conflict skip semantics.
- Add change-cursor tests covering page boundaries and bootstrap rows.
- Add migration assertions for activity row-version trigger and change-feed RLS.

### Phase 2 — Database correctness
- Add a new Flyway repair migration; never mutate already-applied G7 migrations.
- Add `mobile_change_log` with a tenant-scoped monotonic `change_id`.
- Backfill all seven syncable CRM tables into the change log.
- Add change-capture triggers for account, contact, lead, opportunity, task, note, and activity.
- Add missing `crm_activities` sync-version trigger when the table exists.
- Enable and FORCE RLS on the change log with fail-closed tenant policy.

### Phase 3 — Pull synchronization
- Replace row-version cursoring with `mobile_change_log.change_id` cursoring.
- Preserve entity `sync_version` in response for ETag/If-Match semantics.
- Return tombstones for soft-deleted rows.
- Advance cursor by scanned change sequence, not entity version.
- Make pagination deterministic and lossless.

### Phase 4 — Push synchronization
- Fix scalar JDBC binding for UPDATE payloads.
- Isolate mutations with independent transactions while preserving tenant RLS context.
- Ensure idempotency recording belongs to the same mutation transaction.
- Keep per-mutation ACK semantics.

### Phase 5 — Conflict correctness
- Implement explicit skip/defer operation as `status = 'RESOLUTION_PENDING'` with `resolution = NULL`.
- Validate allowed resolution values before database write.
- Keep retention/expiry behavior unchanged unless tests prove a defect.

### Phase 6 — Supporting runtime state
- Reconcile device registry, sync cursor, and sync log usage with the active APIs.
- Wire only behavior required by the existing G7 contracts; do not create unused duplicate persistence models.
- Reconcile mobile idempotency with the existing canonical audit/idempotency path.

### Phase 7 — Verification and handoff
- Run focused G7 tests.
- Run backend module test/build gates available on the branch.
- Run mobile tests/build gates available on the branch.
- Inspect resulting diff for destructive SQL, cross-tenant regressions, and API drift.
- Create a review-ready pull request if repository policy permits, but do not merge it.
- Do not trigger production deployment.
