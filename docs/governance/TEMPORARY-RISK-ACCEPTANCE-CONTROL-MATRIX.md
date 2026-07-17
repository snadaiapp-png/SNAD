# Temporary Risk Acceptance Control Matrix

Decision: `docs/governance/TEMPORARY-RISK-ACCEPTANCE-2026-07-18.md`

| Finding | Compensating controls during acceptance | Immediate suspension trigger |
|---|---|---|
| REM-P0-001 | Production-readiness checks, backend health monitoring, incident escalation, rollback readiness | Repeated loss of backend connectivity or related SEV0/SEV1 |
| REM-P0-002 | Hourly synthetic, BFF fail-closed behavior, session controls, error-budget reporting | Unexplained authentication outage, session integrity failure or related SEV0/SEV1 |
| REM-P0-004 | Issue #516 remains authoritative; later outputs are not automatically approved | Material decision based on an unclassified deliverable |
| REM-P0-005 | Available backups retained, change rollback maintained, failures documented | Confirmed data loss or inability to restore required data |
| REM-P0-006 | CI security gates remain fail-closed; security incidents escalate immediately | Confirmed breach, tenant isolation failure or critical vulnerability exploitation |
| REM-P1-009 | Secret scanning remains active; no new sensitive repository content without review | Confirmed secret or prohibited sensitive-data exposure |

This matrix is compensating control evidence only. It does not satisfy the closure criteria for any listed finding.
