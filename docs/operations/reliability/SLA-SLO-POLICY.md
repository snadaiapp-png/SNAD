# SANAD SLA, SLO and Error-Budget Policy

**Effective:** 2026-07-17  
**Accountable:** Project Owner  
**Responsible:** Platform Operations Duty  
**Approvers:** Engineering Lead, Security Lead and affected Product Owner

## 1. Definitions

- **SLI:** Measured indicator of service behavior.
- **SLO:** Internal reliability target used for engineering and release governance.
- **SLA:** Customer-facing commitment. It is not contractual until included in an approved agreement.
- **Error budget:** Maximum permitted unreliability during a reporting period.
- **Eligible event:** Request, job or minute included by the measurement contract.
- **Good event:** Eligible event satisfying the success and latency contract.
- **Bad event:** Eligible event that fails, times out, violates the expected response contract or exceeds the critical latency threshold.

Availability is calculated as:

```text
availability = good eligible events / total eligible events × 100
```

Error-budget consumption is calculated as:

```text
budget consumed = observed bad events / permitted bad events × 100
```

## 2. Measurement rules

1. Reporting uses calendar months in `Asia/Riyadh`.
2. Sources must be production telemetry, synthetic checks, deployment records and incident records.
3. Client-generated cancellations are excluded only when independently distinguishable.
4. Approved maintenance may be excluded from an external SLA only when notified at least 72 hours in advance and limited to four hours per calendar month.
5. Approved maintenance remains visible in the internal SLO report.
6. Security emergency maintenance is always reported and requires Security Lead approval for any contractual exclusion.
7. Deferred defects, including backend or tunnel instability, remain bad events when they affect service.
8. Missing telemetry is treated as unknown and cannot be reported as successful service.
9. A service cannot pass an SLO using only a health endpoint when its user journey fails.
10. Authentication synthetics must validate the expected contract, including expected unauthenticated `401` behavior.

## 3. Service tiers

| Tier | Purpose | Internal availability SLO | External SLA policy target | Monthly error budget |
|---|---|---:|---:|---:|
| Tier 0 | Identity, tenant boundary, core business writes, financial integrity | 99.95% | 99.90% | 21.6 minutes |
| Tier 1 | Primary user experience and operational processing | 99.90% | 99.50% | 43.2 minutes |
| Tier 2 | Reporting, analytics and non-critical experience | 99.50% | 99.00% | 216 minutes |

The machine-readable targets and latency thresholds are maintained in `service-level-catalog.json`.

## 4. Error-budget policy

| Budget consumed | Required action |
|---:|---|
| 0–50% | Normal delivery; reliability work remains planned. |
| >50–75% | Warning; Service Owner reviews burn causes and allocates at least 20% of capacity to reliability. |
| >75–100% | High risk; high-risk releases require Engineering Lead and Service Owner approval. |
| >100% | Budget exhausted; freeze discretionary releases for the affected service. Permit only security, incident, rollback and proven reliability fixes unless the Project Owner records a time-bound exception. |

Budget exhaustion requires:

- An incident or reliability review.
- Named corrective actions with owners and due dates.
- Verification on an exact repository/deployment version.
- Explicit residual-risk decision.
- Recovery of the rolling burn rate before normal release authority resumes.

## 5. Burn-rate alerting

For Tier 0 and Tier 1 services:

- Fast page: projected monthly budget burn ≥14.4× over five minutes and one hour.
- Sustained page: projected burn ≥6× over thirty minutes and six hours.
- Ticket: projected burn ≥3× over one day.
- Review: any service consuming >50% of budget before half of the month.

## 6. Release governance

A release is blocked when:

- A Tier 0 service has exhausted its error budget.
- An unresolved SEV0 or SEV1 incident affects the release scope.
- Required telemetry is absent.
- The release would remove an SLI or invalidate its measurement contract.
- The Service Owner cannot identify rollback and recovery procedures.

Emergency and security releases remain allowed under Incident Commander control and must be reviewed after execution.

## 7. Reporting and approval

A monthly service report is required by the fifth business day of the following month. It must include every cataloged service, measured results, budget burn, exclusions, incidents, change failure rate, mean time to acknowledge, mean time to restore and corrective actions.

The Project Owner approves the report. Platform Operations prepares it. Engineering, Security and Product Owners approve exceptions affecting their domains.
