# SANAD Incident Management Standard

**Effective:** 2026-07-17  
**Applies to:** Production, production data, customer-impacting integrations and material security events

## 1. Severity model

| Severity | Definition | Acknowledge | Incident Commander | Update cadence | Target containment | PIR deadline |
|---|---|---:|---:|---:|---:|---:|
| SEV0 | Active security compromise, cross-tenant exposure, material data loss/corruption, financial integrity breach or complete critical-platform outage | 5 min | 10 min | 15 min | 30 min | 2 business days |
| SEV1 | Major customer-visible outage, widespread authentication failure, critical workflow unavailable or severe sustained degradation | 10 min | 15 min | 30 min | 60 min | 3 business days |
| SEV2 | Partial degradation, limited customer impact, recoverable processing delay or repeated non-critical failure | 30 min | 60 min | 2 hours | 4 hours | 5 business days when recurring/material |
| SEV3 | Minor defect, isolated inconvenience or operational request without active material impact | 1 business day | Not mandatory | Daily/as agreed | Planned | Optional |

Containment targets are operating objectives, not promises to conceal uncertainty. Resolution status must be evidence-based.

## 2. Command roles

- **Incident Commander:** Owns severity, decisions, cadence, escalation and closure.
- **Technical Lead:** Directs diagnosis, mitigation, rollback and recovery.
- **Communications Lead:** Publishes internal and approved external updates.
- **Scribe:** Maintains UTC and Riyadh timestamps, decisions, evidence and action log.
- **Security Lead:** Mandatory for suspected security, privacy, tenant-isolation or secrets events.
- **Data/Financial Integrity Lead:** Mandatory for accounting, ledger, payroll, inventory valuation or data-corruption events.
- **Service Owner:** Accepts restoration evidence and corrective actions.

One person may temporarily hold multiple roles, but Incident Commander and Technical Lead should be separated for SEV0 when staffing permits.

## 3. Lifecycle

1. **Detect:** Alert, user report, synthetic failure, audit signal or change event.
2. **Declare:** Open an incident record, assign severity and page the duty owner.
3. **Stabilize:** Stop harmful changes, protect data, isolate scope and preserve evidence.
4. **Diagnose:** Establish facts, timeline, affected tenants/services and leading hypothesis.
5. **Mitigate:** Roll back, fail over, disable a feature or apply a controlled fix.
6. **Validate:** Verify user journeys, data integrity, security controls and telemetry.
7. **Communicate:** Maintain scheduled updates until restoration.
8. **Resolve:** Incident Commander records restoration time and residual risk.
9. **Review:** Produce a blameless PIR and track actions to verified closure.

## 4. Mandatory incident evidence

Every SEV0–SEV2 incident record must include:

- Incident ID and severity.
- Detection, acknowledgement, command, mitigation and restoration timestamps.
- Impacted services, tenants, regions, data and business processes.
- Exact deployment/configuration versions.
- Alert, log, trace and synthetic identifiers.
- Decisions, owners and communications.
- Data-integrity and security assessment.
- Rollback/recovery evidence.
- Residual risk and follow-up actions.

## 5. Communication rules

- State known facts, customer impact and next update time.
- Separate confirmed facts from hypotheses.
- Do not publish secrets, personal data, exploit details or tenant identifiers.
- SEV0 external communication requires Project Owner and Security Lead approval.
- SEV1 external communication requires Project Owner or delegated Communications Lead approval.
- Missed update cadence is itself recorded in the PIR.

## 6. Closure criteria

An incident is resolved only when:

- The affected user journey is verified.
- Required data reconciliation is complete or explicitly tracked.
- Security exposure is contained and assessed.
- Monitoring confirms stability.
- The Service Owner accepts restoration evidence.
- Residual risks have owners and due dates.

Closing an alert or restoring a health endpoint alone is insufficient.

## 7. Post-incident review

SEV0 and SEV1 always require a PIR. The review is blameless and must identify system conditions, control gaps, detection gaps and organizational contributors. Action items must be specific, owned, dated and validated on exact versions. Repeated overdue actions are escalated to the Project Owner.
