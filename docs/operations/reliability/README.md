# SANAD Reliability and Incident Operations

**Effective date:** 2026-07-17  
**Policy owner:** Project Owner (`@snadaiapp-png`)  
**Operating timezone:** Asia/Riyadh  
**Scope:** Production and production-like SANAD services

This directory is the authoritative operating model for service levels, error budgets, on-call ownership, escalation, incident handling and monthly service reporting.

## Authoritative artifacts

- `SLA-SLO-POLICY.md` — definitions, calculations, targets and error-budget controls.
- `INCIDENT-MANAGEMENT.md` — severity model, command roles, response workflow and evidence.
- `ON-CALL-ESCALATION.md` — duty ownership, paging and escalation.
- `AUTH-SESSION-RELIABILITY.md` — REM-P0-002 BFF/authentication controls, production synthetic and closure gate.
- `service-level-catalog.json` — machine-readable service targets.
- `on-call-roster.json` — machine-readable accountable duty roster.
- `templates/INCIDENT-REPORT.md` — live incident record.
- `templates/POST-INCIDENT-REVIEW.md` — blameless corrective-action review.
- `templates/MONTHLY-SERVICE-REPORT.md` — monthly SLI/SLO/error-budget report.

## Effective operating decision

Internal SLOs are effective immediately. External customer SLAs are policy targets only and become contractual only through an approved customer agreement and commercial-go-live decision.

Deferred backend or tunnel risks are not excluded from availability, latency, incident or error-budget calculations. Deferral changes remediation priority, not operational truth.

Implemented application controls do not close a reliability finding until their exact deployed SHA, production observation window, incident evidence and accountable acceptance are complete.

## Mandatory evidence

Every service-level claim must identify:

1. Reporting period and timezone.
2. Metric source and query/run identifier.
3. Total eligible events or minutes.
4. Good and bad events.
5. Exclusions with approval evidence.
6. SLO result and error-budget consumption.
7. Incidents and corrective actions.
8. Accountable approval.

Narrative statements without reproducible evidence are not service-level proof.
