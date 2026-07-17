# P1-008 Closure Decision — Service Levels and Incident Operations

**Date:** 2026-07-17  
**Decision owner:** Project Owner  
**Finding:** P1-008 — SLA/SLO, error budgets, on-call ownership, escalation, incident management and monthly reporting were not approved.

## Decision

```text
P1-008: CLOSED AS A GOVERNANCE AND OPERATING-MODEL DEFECT
INTERNAL SLO POLICY: ACTIVE
EXTERNAL CUSTOMER SLA: NOT CONTRACTUAL UNTIL APPROVED IN CUSTOMER AGREEMENTS
```

## Accepted controls

- Tiered SLA/SLO policy and measurement contracts.
- Machine-readable service catalog with owners, availability and latency targets.
- Error-budget thresholds and release restrictions.
- SEV0–SEV3 incident model with response and communication targets.
- Incident command roles, authority, lifecycle and closure evidence.
- 24×7 SEV0/SEV1 duty ownership and timed escalation.
- Structured GitHub incident intake.
- Incident, PIR and monthly service-report templates.
- Automated monthly service-review issue creation.
- CI validation preventing silent removal or corruption of operational controls.

## Operational boundaries

This closes the absence of an approved operating model. It does not assert that current production services meet their SLOs.

Deferred backend and tunnel defects remain open. Any failure they cause is counted as a bad event, incident and error-budget consumption.

The current interim on-call roster assigns accountable primary and secondary duty to the Project Owner account. This provides unambiguous ownership but retains a staffing concentration risk. A staffed rotation is required before external SLA commitments become contractual.

## First reporting obligation

The first report covers the partial period from 2026-07-17 through 2026-07-31 and is due by 2026-08-05. Missing historical telemetry must be reported as unknown, not converted to successful service.

## Evidence

- `docs/operations/reliability/`
- `scripts/ci/validate_operational_governance.py`
- `.github/workflows/operational-governance-validation.yml`
- `.github/workflows/monthly-service-review.yml`
- `.github/ISSUE_TEMPLATE/incident.yml`
- Executive remediation Issue `#516`
