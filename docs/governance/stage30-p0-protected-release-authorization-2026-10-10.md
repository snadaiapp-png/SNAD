# Stage 30 P0 — Protected Release Authorization Candidate

Date: 2026-10-10 (Asia/Riyadh)
Source merge: [PR #1353](https://github.com/snadaiapp-png/SNAD/pull/1353)
Security remediation baseline: `b1d76fdf7dc108f4801a8fc90a310ce62e76755c`
Tracking: [#1352](https://github.com/snadaiapp-png/SNAD/issues/1352)

## Forensic finding
The source fix reached main but not Production. The immutable publisher runs on `push: main`, while Workflow Y2 orchestrator proceeds only for a **successful publication triggered by push**, on exact main SHA, where the **HEAD commit message** includes the exact token `PRODUCTION-RELEASE-AUTHORIZED`, and the commit maps to one merged main PR. The #1353 squash commit lacks the token. Manually dispatching the publisher does not authorize Workflow Y2, because `workflow_run.event == 'push'` is required.

## Governed correction
This documentation-only PR is a new, narrowly scoped release authorization **candidate**, not a bypass or direct Render modification. The final squash merge message must contain the exact token `PRODUCTION-RELEASE-AUTHORIZED`; a marker in this document or an unmerged commit alone does **not** qualify. Use the new squash merge SHA as the immutable image/release identity, not the older #1353 SHA. GitHub protected review and exact-head CI must pass before merge.

## Non-negotiable checks
- [ ] Independent protected review and CI success on exact PR head
- [ ] Current main drift rechecked before merge
- [ ] Safety review of CRM no-context worker/admin compatibility and intended tenant DB role
- [ ] Governed risk revalidation documented; `CURRENT-STATUS.json` suspended prior temporary risk acceptance is NOT magically renewed by this PR
- [ ] Squash merge with expected-head lock and explicit authorization marker in resulting merge commit message
- [ ] Confirm publisher ran as a push on exact new main SHA, GHCR image digest available
- [ ] Confirm Workflow Y2 detected authorization and dispatched canonical release (rollback enabled)
- [ ] Verify Render live digest and exact SHA match
- [ ] Verify Flyway `20261010.1` and fail-closed `crm_accounts` policy on the runtime authoritative database
- [ ] Verify payment provider stays DISABLED, readiness, incident rollback, and least-privileged tenant A/B isolation before P0 PASS

## Scope
Internal non-paid pilot security remediation only. No paid customer Go-Live, no LIVE provider, no payment collection, no production SQL executed by this change. No changes to runtime code or deployment guardrails.
