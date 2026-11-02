# 09 — PROTECTED PRODUCTION VERIFICATION & CHANGE-REQUEST GATE (Phase 6.2)

**PROTECTED_PRODUCTION_VERIFICATION = BLOCKED (stop point honored — no production change was made, requested, or scheduled).**

## 6.2 The production change request — drafted, NOT submitted for execution

Per the command, before any migration or deployment a change request must exist and receive **explicit approval**. The draft below is complete except for the fields that require live production data (blocked by 08). **This command does not authorize running Flyway on production or deploying the application; nothing below is an approval.**

```text
CHANGE_REQUEST_ID:            CR-G1-FINAL-CLOSURE-001 (DRAFT — awaiting owner approval)
TARGET_ENVIRONMENT:           production (Render backend service + its PostgreSQL database)
CURRENT_PRODUCTION_SHA:       UNVERIFIED (requires 08 probe; last known-good reference 1d80c573… from 2026-10-23 REM-P0-006 records is STALE and must not be assumed current)
TARGET_RELEASE_SHA:           f95a30b64e75815d571e582e1b555c01bcd53abe
CURRENT_FLYWAY_VERSION:       UNVERIFIED (requires 08 probe; must be >= 20261031.1 or the Scenario-C refusal path applies — see 05 §4.3)
TARGET_FLYWAY_VERSION:        v20261102.2 (250 migrations)
BACKUP_ID:                    MISSING (requires owner-delivered authorized backup — 06)
RESTORE_REHEARSAL_RESULT:     BLOCKED (06)
MIGRATION_PLAN:               forward-only flyway migrate to v20261102.2; NO repair, NO baseline, NO outOfOrder; abort on any checksum/validation difference
MAINTENANCE_WINDOW:           TBD by owner
EXPECTED_IMPACT:              2 new least-privilege ACL migrations (V20261102_1/V20261102_2 already in the chain) + app deploy of f95a30b6; no destructive DDL
ROLLBACK_OR_RECOVERY_PLAN:    restore from the authorized backup taken immediately pre-change (06 gate); app rollback to previous Render deploy; database forward-only — rollback = restore, not downgrade
MONITORING_PLAN:              post-deploy smoke (auth login/refresh, subscription read, provisioning worker heartbeat), error-rate watch for 60 min
SECURITY_REVIEW_REFERENCE:    BLOCKED (07 — independent review not yet recorded)
AUTHORIZED_APPROVER:          TBD — explicit, scope-specific owner authorization REQUIRED
```

## 6.3 Execution after authorization — preconditions checklist (all must be green first)

1. 08 probes executed → production identity proven (mandatory stop-condition #1).
2. 06 backup delivered → restore rehearsal PASS with measured timings.
3. 07 independent review recorded on `f95a30b6`.
4. Post-merge run `41415645715` on `f95a30b6` terminal PASS (02).
5. Owner's explicit, scope-specific approval of THIS change request.

Only after 1–5 may §6.3 of the command (install → preflight → migrate → ledger verify → deploy → non-destructive smoke → isolation checks with **authorized synthetic tenants only** → monitor) execute. None of those steps were executed; production remains untouched.

## The Y2 gate as production-authorization enforcement (verified, not assumed)

- Run `41415840956`: **correct refusal** — the SHA-binding guard rejected a stale image-publication SHA (02 §1.2). **Do not "fix" this failure; it is the control working.**
- Run `41415955410`: clean no-op — commit message contains no `PRODUCTION-RELEASE-AUTHORIZED` marker → no dispatch.
- SCP One-Time Production Recovery + G2 Recovery Release Dispatch: one-time recovery markers absent → dispatch steps **skipped**.
- Therefore: no production release, no deployment, no production SQL write occurred from this command. `PRODUCTION_RELEASE=NO-GO` stands, and the automated merge/deployment prohibitions remain in force.
